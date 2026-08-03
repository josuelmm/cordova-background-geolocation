package com.marianhello.backgroundgeolocation;

import com.marianhello.bgloc.HttpPostService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regresiones de la ruta POR-ELEMENTO de {@code postJSONFile} (syncMode 'single' y
 * form-urlencoded). Esa ruta crea una conexión nueva por item, así que el mock de
 * {@code HttpURLConnection} de {@link HttpPostServiceTest} no la alcanza: aquí se usa un servidor
 * HTTP real en localhost, que es justo la combinación que nunca se probó y por la que el
 * despliegue rompió.
 */
@RunWith(RobolectricTestRunner.class)
public class HttpPostServicePerItemSyncTest {

    private ServerSocket server;
    private Thread acceptLoop;
    private volatile boolean running;
    private String baseUrl;
    /** Códigos que el servidor devolverá, uno por petición recibida. -1 = cerrar sin responder. */
    private final List<Integer> responses = Collections.synchronizedList(new ArrayList<Integer>());
    /** Cuerpos recibidos, en orden. */
    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    /** Retardo artificial de la respuesta, para reproducir un backend lento. */
    private volatile int responseDelayMs = 0;

    // com.sun.net.httpserver no está en el classpath de los unit tests de Android, así que el
    // stub es un ServerSocket con el mínimo de HTTP/1.1 que HttpURLConnection necesita.
    @Before
    public void setUp() throws IOException {
        server = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
        baseUrl = "http://127.0.0.1:" + server.getLocalPort() + "/";
        running = true;
        acceptLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    Socket socket = null;
                    try {
                        socket = server.accept();
                        handle(socket);
                    } catch (Exception e) {
                        // socket cerrado en tearDown, o cliente desconectado: fin de esta conexión.
                        // Se captura Exception y no solo IOException a propósito: si una
                        // RuntimeException (p.ej. una cabecera Content-Length malformada) matara
                        // este hilo, todos los tests siguientes de la clase se colgarían 30 s
                        // contra un puerto que ya no acepta, y el fallo real quedaría enterrado.
                    } finally {
                        if (socket != null) {
                            try { socket.close(); } catch (IOException ignored) { }
                        }
                    }
                }
            }
        });
        acceptLoop.setDaemon(true);
        acceptLoop.start();
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        int b;
        while (!head.toString().endsWith("\r\n\r\n") && (b = in.read()) != -1) {
            head.append((char) b);
        }
        int contentLength = 0;
        for (String line : head.toString().split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(body, read, contentLength - read);
            if (n < 0) break;
            read += n;
        }
        received.add(new String(body, 0, read, "UTF-8"));

        int index = received.size() - 1;
        int code = index < responses.size() ? responses.get(index) : 200;
        if (code < 0) {
            // Simula un corte de red: cierra sin responder -> IOException en el cliente.
            return;
        }
        if (responseDelayMs > 0) {
            try { Thread.sleep(responseDelayMs); } catch (InterruptedException ignored) { }
        }
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + code + " X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                .getBytes("UTF-8"));
        out.flush();
    }

    @After
    public void tearDown() {
        running = false;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
        }
    }

    private File batchFile(String json) throws IOException {
        File f = File.createTempFile("batch", ".json");
        f.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(json.getBytes("UTF-8"));
        } finally {
            fos.close();
        }
        return f;
    }

    private static Map<String, String> jsonHeaders() {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Content-Type", "application/json");
        return h;
    }

    private static final String THREE_ITEMS =
            "[{\"lat\":1.0,\"lon\":1.0},{\"lat\":2.0,\"lon\":2.0},{\"lat\":3.0,\"lon\":3.0}]";

    /**
     * HTTP 285 = "abort updates". Es 2xx, así que el bucle por elemento lo trataba como éxito,
     * seguía enviando y devolvía 200: onRequestedAbortUpdates no se emitía nunca y el dispositivo
     * seguía trackeando pese a que el servidor pedía parar.
     */
    @Test
    public void abortUpdates285StopsTheLoopAndIsReportedToTheCaller() throws Exception {
        responses.add(200);
        responses.add(285);
        responses.add(200);
        int[] accepted = new int[]{-1};

        int code = HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), jsonHeaders(),
                null, "POST", accepted, true);

        assertEquals("el 285 debe llegar al llamante, no enmascararse como 200", 285, code);
        assertEquals("no debe enviarse el tercer item tras el 285", 2, received.size());
        assertEquals("el item que devolvió 285 SÍ fue aceptado", 2, accepted[0]);
    }

    /** Un no-2xx corta el bucle y solo cuenta como aceptados los anteriores. */
    @Test
    public void permanentFailureStopsAndReportsOnlyTheAcceptedPrefix() throws Exception {
        responses.add(200);
        responses.add(200);
        responses.add(400);
        int[] accepted = new int[]{-1};

        int code = HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), jsonHeaders(),
                null, "POST", accepted, true);

        assertEquals(400, code);
        assertEquals(3, received.size());
        assertEquals(2, accepted[0]);
    }

    /**
     * Corte de red a mitad de lote: postJSONFile guarda el contador y RELANZA la IOException.
     * Sin el try/finally en la sobrecarga de 7 argumentos, acceptedOut se quedaba en -1, el
     * SyncAdapter no marcaba el lote como parcialmente completado y las posiciones ya aceptadas
     * por el servidor se reenviaban -> duplicados en cada corte de red.
     */
    @Test
    public void networkDropMidBatchStillReportsTheAcceptedPrefix() throws Exception {
        responses.add(200);
        responses.add(200);
        responses.add(-1); // cierra sin responder
        int[] accepted = new int[]{-1};

        try {
            HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), jsonHeaders(),
                    null, "POST", accepted, true);
            fail("se esperaba IOException al cortarse la conexión");
        } catch (IOException expected) {
            // esperado
        }

        assertEquals("las 2 posiciones ya aceptadas no deben reenviarse", 2, accepted[0]);
    }

    /** Todo correcto: una petición por elemento y el lote entero contado como aceptado. */
    @Test
    public void happyPathSendsOneRequestPerLocation() throws Exception {
        int[] accepted = new int[]{-1};

        int code = HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), jsonHeaders(),
                null, "POST", accepted, true);

        assertEquals(200, code);
        assertEquals(3, received.size());
        assertEquals(3, accepted[0]);
        assertTrue("cada petición lleva un objeto, no el array", received.get(0).startsWith("{"));
    }

    /**
     * form-urlencoded: el cuerpo debe salir plano (`lat=..&lon=..`), nunca `locations=<json>` ni
     * un array JSON. Es el fallo exacto que provocó HTTP 400 en producción.
     */
    @Test
    public void formUrlEncodedSendsFlatBodiesOneRequestPerLocation() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        int[] accepted = new int[]{-1};

        int code = HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), headers,
                null, "POST", accepted, true);

        assertEquals(200, code);
        assertEquals(3, received.size());
        for (String body : received) {
            assertTrue("cuerpo no aplanado: " + body, body.contains("lat=") && body.contains("lon="));
            assertTrue("no debe envolverse en locations=: " + body, !body.startsWith("locations="));
            assertTrue("no debe ser JSON: " + body, !body.startsWith("[") && !body.startsWith("{"));
        }
        assertEquals(3, accepted[0]);
    }

    /**
     * v5.0.1 — el timeout de lectura tiene que HEREDARSE en las peticiones por elemento.
     *
     * Caso real que lo destapó: un Traccar con el DeviceForwarder caído tarda ~2 min en responder.
     * Las peticiones por elemento se creaban con `new HttpPostService(url, method)`, que arranca
     * con el timeout por defecto, así que el margen de v4 (120 s) nunca llegaba a la única ruta
     * que lo usa con form-urlencoded. Resultado: timeout a los 30 s, cero posiciones confirmadas,
     * el mismo lote reintentado indefinidamente y duplicados en el servidor.
     *
     * El servidor de este test tarda 1,2 s en responder: con un timeout heredado de 120 s pasa; si
     * alguien vuelve a dejar las hijas en 30 s este test sigue pasando, así que además se
     * comprueba la propagación directamente sobre createPerItemService().
     */
    @Test
    public void perItemRequestsInheritTheReadTimeout() throws Exception {
        // createPerItemService() es package-private en com.marianhello.bgloc y este test vive en
        // com.marianhello.backgroundgeolocation, asi que se invoca por reflexion.
        HttpPostService parent = new HttpPostService(baseUrl, "POST");
        java.lang.reflect.Method factory =
                HttpPostService.class.getDeclaredMethod("createPerItemService");
        factory.setAccessible(true);
        HttpPostService child = (HttpPostService) factory.invoke(parent);
        assertEquals("la peticion por elemento debe heredar el timeout del padre",
                readTimeoutOf(parent), readTimeoutOf(child));
        assertTrue("el timeout por defecto debe ser el de v4 (120 s), no los 30 s de v5.0.0",
                readTimeoutOf(parent) >= 120_000);
    }

    /** Un backend lento (1,2 s) no debe hacer fallar el lote. */
    @Test
    public void slowServerDoesNotTimeOutThePerItemBatch() throws Exception {
        responseDelayMs = 1200;
        int[] accepted = new int[]{-1};

        int code = HttpPostService.postJSONFile(baseUrl, batchFile(THREE_ITEMS), jsonHeaders(),
                null, "POST", accepted, true);

        assertEquals(200, code);
        assertEquals(3, received.size());
        assertEquals(3, accepted[0]);
    }

    private static int readTimeoutOf(HttpPostService service) throws Exception {
        java.lang.reflect.Field f = HttpPostService.class.getDeclaredField("mReadTimeoutMs");
        f.setAccessible(true);
        return (Integer) f.get(service);
    }
}
