package com.marianhello.bgloc;

import android.os.Build;

import com.marianhello.logging.LoggerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import org.json.JSONTokener;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class HttpPostService {
    public static final int BUFFER_SIZE = 1024;
    /** Timeout to establish connection (ms). Prevents sync notification from staying stuck. */
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /**
     * Timeout to read response (ms). v5.0 — A4: was 120_000, i.e. two minutes per attempt.
     * The upload runs on the sync thread while the service holds a wake lock, so a server that
     * accepts the connection and then wedges used to pin that thread (and the CPU) for two
     * minutes per attempt, and again on every retry. Matched to CONNECT_TIMEOUT_MS: no legitimate
     * batch POST needs longer to start answering, and failing fast leaves the queue on disk for
     * the next sync anyway.
     */
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * v5.0.1 — R6: bajar el timeout de 120 s a 30 s (petición A4) es correcto para un POST de UNA
     * posición: un servidor colgado retenía el hilo de sync y el wake lock dos minutos por intento.
     * Pero la subida por lote puede legítimamente tardar más (un backend que importa 100 filas de
     * forma síncrona), y 30 s la hacía fallar donde v4 funcionaba. El lote conserva el margen de v4.
     */
    private static final int BATCH_READ_TIMEOUT_MS = 120_000;

    private static final org.slf4j.Logger logger = LoggerManager.getLogger(HttpPostService.class);

    /** v5.0.1 — R11: true cuando syncMode == 'single'. Lo fija SyncAdapter antes de subir. */
    private boolean mPerItemSync = false;

    /** v5.0.1 — R6: 30 s por defecto; la ruta de lote lo sube a BATCH_READ_TIMEOUT_MS. */
    private int mReadTimeoutMs = READ_TIMEOUT_MS;

    private String mUrl;
    private String mMethod = "POST";
    private HttpURLConnection mHttpURLConnection;
    /**
     * Number of leading locations accepted by the server in form-urlencoded batch mode
     * (one request per location). {@code -1} means the body went out as a single
     * all-or-nothing request, so partial confirmation does not apply.
     */
    private int mAcceptedItemCount = -1;

    public interface UploadingProgressListener {
        void onProgress(int progress);
    }

    public HttpPostService(String url) {
        mUrl = url;
    }

    public HttpPostService(String url, String method) {
        mUrl = url;
        mMethod = normalizeMethod(method);
    }

    public HttpPostService(final HttpURLConnection httpURLConnection) {
        mHttpURLConnection = httpURLConnection;
    }

    public void setMethod(String method) {
        mMethod = normalizeMethod(method);
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isEmpty()) return "POST";
        String m = method.trim().toUpperCase();
        if (m.equals("POST") || m.equals("GET") || m.equals("PUT") || m.equals("PATCH")) return m;
        return "POST";
    }

    /** Returns true when the HTTP method has no request body (GET). */
    private boolean isBodyless() {
        return "GET".equals(mMethod);
    }

    private HttpURLConnection openConnection() throws IOException {
        if (mHttpURLConnection == null) {
            mHttpURLConnection = (HttpURLConnection) new URL(mUrl).openConnection();
            mHttpURLConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            mHttpURLConnection.setReadTimeout(mReadTimeoutMs);
            // Follow 3xx. Common when a backend starts forcing HTTPS or moves domain; without it
            // the redirect surfaced as a plain failure and every location was queued forever.
            mHttpURLConnection.setInstanceFollowRedirects(true);
        }
        return mHttpURLConnection;
    }

    public int postJSON(JSONObject json, Map headers) throws IOException {
        String jsonString = "null";
        if (json != null) {
            jsonString = json.toString();
        }
        return postJSONString(jsonString, headers);
    }

    public int postJSON(JSONArray json, Map headers) throws IOException {
        String jsonString = "null";
        if (json != null) {
            // Always serialize as an array. It used to unwrap a single-element array into a bare
            // object, which made httpMode:'batch' indistinguishable from 'single' for one
            // location: a server that expects `[{...}]` in batch mode got `{...}` and answered 400.
            // Callers that genuinely want one object use postJSON(JSONObject, ...).
            jsonString = json.toString();
        }
        return postJSONString(jsonString, headers);
    }

    /**
     * v5.0.1 — H5: `Content-Type` admite parametros (`application/x-www-form-urlencoded;
     * charset=UTF-8`, escritura muy habitual). Comparar la cabecera completa por igualdad hacia
     * que TODO el manejo form-urlencoded se desactivara y se enviara JSON crudo declarado como
     * formulario: HTTP 400 en todas las posiciones — el mismo fallo de produccion que este
     * fichero acaba de corregir, pero por otra puerta.
     */
    /** Busca la cabecera Content-Type sin distinguir mayusculas (HTTP no las distingue). */
    private static String contentTypeFromHeaders(Map headers) {
        if (headers == null) {
            return null;
        }
        for (Object keyObj : headers.keySet()) {
            if (keyObj instanceof String && ((String) keyObj).equalsIgnoreCase("Content-Type")) {
                Object value = headers.get(keyObj);
                return value != null ? String.valueOf(value) : null;
            }
        }
        return null;
    }

    private static boolean isFormUrlEncoded(String contentType) {
        if (contentType == null) {
            return false;
        }
        int paramIdx = contentType.indexOf(';');
        String mediaType = (paramIdx >= 0 ? contentType.substring(0, paramIdx) : contentType).trim();
        return mediaType.equalsIgnoreCase("application/x-www-form-urlencoded");
    }

    public int postJSONString(String body, Map headers) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }

        String contentType = null;
        {
            for (Object keyObj : headers.keySet()) {
                String key = (String) keyObj;
                if (key.equalsIgnoreCase("Content-Type")) {
                    contentType = (String) headers.get(key);
                    break;
                }
            }
        }

        // v5.0.1 — form-urlencoded + JSON array: NO existe forma de expresar un array como
        // parametros planos, asi que se envia UNA peticion por elemento, exactamente igual que
        // hace postJSONFile() en la ruta de sync. Sin esto:
        //   - v4 desenrollaba los arrays de 1 elemento a objeto (httpMode 'batch' + form
        //     acababa plano) y funcionaba con OsmAnd/Traccar;
        //   - v5 quito ese desenrollado (correcto para servidores de batch JSON real) y
        //     jsonToUrlEncoded emitia `locations=<json>`, que ningun decoder OsmAnd entiende
        //     → HTTP 400 en TODAS las posiciones con httpMode 'batch' + form-urlencoded.
        // iOS ya desenrollaba el caso de 1 elemento (MAURPostLocationTask.m), asi que ademas
        // se habia roto la paridad entre plataformas.
        if (contentType != null
                && isFormUrlEncoded(contentType)
                && !isBodyless()) {
            JSONArray asArray = null;
            try {
                Object parsed = new JSONTokener(body).nextValue();
                if (parsed instanceof JSONArray) {
                    asArray = (JSONArray) parsed;
                }
            } catch (Exception ignored) {
                // no es JSON: se envia tal cual mas abajo
            }
            if (asArray != null) {
                return postFormUrlEncodedArray(asArray, headers);
            }
        }

        return postJSONStringInternal(body, contentType, headers);
    }

    /**
     * v5.0.1 — form-urlencoded + array. Un array NO se puede expresar como parametros planos.
     *
     * <p>1 elemento (el caso de httpMode 'batch' en tiempo real, y el que iOS ya cubria en
     * MAURPostLocationTask.m): se desenrolla y se envia por ESTA conexion — mismo formato de
     * cable que v4, y compatible con la conexion inyectada en tests.
     *
     * <p>N elementos: una peticion por elemento, como ya hace postJSONFile() en la ruta de sync.
     * Requiere mUrl (constructor con URL); con una conexion inyectada y N>1 no hay forma de abrir
     * peticiones adicionales, asi que se cae al comportamiento anterior (`locations=<json>`).
     */
    private int postFormUrlEncodedArray(JSONArray arr, Map headers) throws IOException {
        // v5.0.1 — se conserva el Content-Type TAL CUAL lo configuro la app. Estaba fijado al
        // literal sin parametros, asi que un `...; charset=UTF-8` desaparecia justo en la ruta
        // que se acaba de arreglar para reconocerlo.
        String formContentType = contentTypeFromHeaders(headers);
        if (!isFormUrlEncoded(formContentType)) {
            formContentType = "application/x-www-form-urlencoded";
        }
        int len = arr.length();
        if (len == 0) {
            mAcceptedItemCount = 0;
            return 200;
        }
        if (len == 1) {
            JSONObject only = arr.optJSONObject(0);
            if (only == null) {
                logger.error("form-urlencoded requires an object postTemplate; the single element "
                        + "is not a JSON object and cannot be flattened to key=value. Sending "
                        + "unflattened body; expect the server to reject it.");
                return postJSONStringInternal(arr.toString(),
                        formContentType, headers);
            }
            {
                int code = postJSONStringInternal(only.toString(),
                        formContentType, headers);
                mAcceptedItemCount = (code >= 200 && code < 300) ? 1 : 0;
                return code;
            }
        }
        if (mUrl == null) {
            // Conexion inyectada: sin URL no se pueden abrir peticiones adicionales.
            return postJSONStringInternal(arr.toString(),
                    formContentType, headers);
        }
        // H1: un template de tipo array (postTemplate: ["@latitude","@longitude"]) produce
        // elementos que NO son objetos y por tanto no se pueden aplanar a `clave=valor`. Saltarlos
        // en silencio significaba 0 peticiones + return 200 + la posicion borrada del disco:
        // perdida total sin ni un log. Se detecta antes de enviar nada y se cae al cuerpo sin
        // aplanar, que al menos produce un error visible del servidor.
        for (int i = 0; i < len; i++) {
            if (arr.optJSONObject(i) == null) {
                logger.error("form-urlencoded requires an object postTemplate; element {} is not "
                        + "a JSON object and cannot be flattened to key=value. Sending unflattened "
                        + "body; expect the server to reject it.", i);
                return postJSONStringInternal(arr.toString(),
                        formContentType, headers);
            }
        }
        for (int i = 0; i < len; i++) {
            JSONObject item = arr.optJSONObject(i);
            int code;
            try {
                // Instancia nueva por peticion: HttpURLConnection no se reutiliza.
                // Se preserva mMethod (POST/PUT/PATCH configurado por la app).
                code = new HttpPostService(mUrl, mMethod).postJSONStringInternal(
                        item.toString(), formContentType, headers);
            } catch (IOException e) {
                mAcceptedItemCount = i;
                throw e;
            }
            if (code < 200 || code >= 300) {
                mAcceptedItemCount = i;
                return code;
            }
            // H8: 285 ("Updates Not Required") es 2xx, asi que el bucle lo tragaba y devolvia 200
            // -> onRequestedAbortUpdates nunca se emitia y el tracking seguia pese a que el
            // servidor pedia parar. Este item SI se acepto, de ahi el i + 1.
            if (code == 285) {
                mAcceptedItemCount = i + 1;
                return code;
            }
        }
        mAcceptedItemCount = len;
        return 200;
    }

    private int postJSONStringInternal(String body, String contentType, Map headers) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }

        if (contentType == null) {
            contentType = "application/json";
        }

        HttpURLConnection conn = this.openConnection();
        conn.setRequestMethod(mMethod);

        // Set headers (including Content-Type) up-front; needed for both bodyless and body requests.
        if (!isBodyless()) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                conn.setRequestProperty(pair.getKey(), pair.getValue());
            }
        }

        // GET: no body; data is expected to live in the URL (URL templating).
        if (isBodyless()) {
            conn.setDoOutput(false);
            return consumeAndDisconnect(conn);
        }

        // Prepare body according to Content-Type so header and body always match.
        String finalBody = body;
        if (isFormUrlEncoded(contentType)) {
            try {
                finalBody = jsonToUrlEncoded(body);
            } catch (Exception e) {
                finalBody = body;
            }
        }

        // Use byte length, not String.length(), so multi-byte UTF-8 characters
        // (ñ, é, emoji, ...) match the Content-Length the server expects.
        byte[] outputBytes = finalBody.getBytes(StandardCharsets.UTF_8);
        conn.setDoOutput(true);
        // (long) overload: the int one is deprecated since API 19 and overflows past 2 GB.
        conn.setFixedLengthStreamingMode((long) outputBytes.length);

        java.io.OutputStream os = null;
        try {
            os = conn.getOutputStream();
            os.write(outputBytes);
        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
        }
        return consumeAndDisconnect(conn);
    }
    
    /**
     * Reads the status code, drains the response body and releases the connection.
     *
     * <p>Nothing used to consume the response or call {@code disconnect()}. At one POST per fix,
     * 24/7, per vehicle, every reply left an undrained socket that never returned to the keep-alive
     * pool, so file descriptors and buffers accumulated until the process degraded. Draining is
     * also what lets HttpURLConnection reuse the connection at all.
     */
    private static int consumeAndDisconnect(HttpURLConnection conn) throws IOException {
        try {
            int code = conn.getResponseCode();
            drainQuietly(code >= 400 ? conn.getErrorStream() : getInputStreamQuietly(conn));
            return code;
        } finally {
            conn.disconnect();
        }
    }

    private static java.io.InputStream getInputStreamQuietly(HttpURLConnection conn) {
        try {
            return conn.getInputStream();
        } catch (Throwable t) {
            // setDoInput(false), or a mock in tests.
            return null;
        }
    }

    private static void drainQuietly(java.io.InputStream in) {
        if (in == null) return;
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            while (in.read(buf) != -1) { /* discard */ }
        } catch (Throwable ignored) {
            // Draining is best-effort; never let it mask the real status code.
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static String getContentTypeFromHeaders(Map headers) {
        if (headers == null) return null;
        for (Object keyObj : headers.keySet()) {
            String key = (String) keyObj;
            if (key != null && key.equalsIgnoreCase("Content-Type")) {
                return (String) headers.get(key);
            }
        }
        return null;
    }

    /**
     * Converts JSON string (object or array) to application/x-www-form-urlencoded.
     * Object: flat key=value&key2=value2. Array: single key "locations" with URL-encoded JSON array.
     */
    private String jsonToUrlEncoded(String jsonString) throws Exception {
        Object json = new JSONTokener(jsonString).nextValue();
        if (json instanceof JSONArray) {
            return "locations=" + URLEncoder.encode(jsonString, StandardCharsets.UTF_8.name());
        }
        JSONObject jsonObj = (JSONObject) json;
        StringBuilder result = new StringBuilder();
        Iterator<String> keys = jsonObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // v4.5.4: skip null / JSONObject.NULL values. Previously these were
            // serialised as the literal string "null", which servers like Traccar
            // reject (Traccar OsmAndProtocolDecoder throws NumberFormatException
            // on "speed=null"). Placeholders that resolve to no value (@speed,
            // @events, @battery, etc.) end up as JSONObject.NULL in the batch and
            // must be omitted from form-urlencoded bodies. Using `isNull` covers
            // both the reference-equality and the literal "null" string cases.
            if (jsonObj.isNull(key)) {
                continue;
            }
            Object raw = jsonObj.opt(key);
            if (raw == null) {
                continue;
            }
            String value = raw.toString();
            // NOTE: no "null".equals(value) check here. jsonObj.isNull(key) above already covers
            // real nulls and JSONObject.NULL, so this only ever discarded a *genuine* String whose
            // content happened to be "null" — a plausible driver name, plate or free-text field
            // that then vanished from the POST with no trace.
            if (result.length() > 0) {
                result.append("&");
            }
            result.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    public int postJSONFile(File file, Map headers, UploadingProgressListener listener) throws IOException {
        long fileSize = file.length();
        return postJSONFile(new FileInputStream(file), fileSize, headers, listener);
    }

    public int postJSONFile(InputStream stream, Map headers, UploadingProgressListener listener) throws IOException {
        return postJSONFile(stream, stream.available(), headers, listener);
    }

    public int postJSONFile(InputStream stream, long streamSize, Map headers, UploadingProgressListener listener) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }
        mReadTimeoutMs = BATCH_READ_TIMEOUT_MS; // v5.0.1 — R6: margen de v4 para lotes grandes
        String contentType = getContentTypeFromHeaders(headers);
        if (contentType == null) {
            contentType = "application/json";
        }
        final boolean isFormUrlEncoded = isFormUrlEncoded(contentType);
        // v5.0.1 — R11: `syncMode` se parseaba, validaba y persistia, pero SyncAdapter no lo leia
        // NUNCA: el cuerpo lo fijaba BatchManager, que siempre escribe un array. En Android el modo
        // real lo decidia el Content-Type, no la opcion. iOS si lo respetaba, asi que
        // `syncMode:'single'` funcionaba en iOS y no hacia nada en Android.
        // Ahora una peticion por posicion se dispara si el Content-Type lo exige (form-urlencoded,
        // que no puede expresar un array) o si el usuario pidio 'single'.
        final boolean perItem = isFormUrlEncoded || mPerItemSync;
        // Prepare body according to Content-Type (same as post to url): form body when form-urlencoded, else JSON
        // Read full body so we can convert when needed
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long progress = 0;
        while ((bytesRead = stream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            progress += bytesRead;
            if (listener != null && streamSize > 0) {
                int percentage = (int) ((progress * 100L) / streamSize);
                // Only report 100 once, from the write path below, when the bytes have actually
                // reached the connection. Reporting it here too made the sync notification show
                // "completed" as soon as the local batch file had been read.
                if (percentage < 100) {
                    listener.onProgress(percentage);
                }
            }
        }
        stream.close();
        byte[] bodyBytes = baos.toByteArray();
        String jsonString = new String(bodyBytes, StandardCharsets.UTF_8);

        // Una peticion por posicion: obligatorio con form-urlencoded (un array no se puede
        // aplanar) y opcional con syncMode 'single'.
        if (perItem) {
            try {
                Object parsed = new JSONTokener(jsonString).nextValue();
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    int len = arr.length();
                    if (len == 0) {
                        if (listener != null) listener.onProgress(100);
                        return 200;
                    }
                    // v5.0.1 — misma guarda que postFormUrlEncodedArray (H1): con un postTemplate
                    // de tipo array los elementos no son objetos, y getJSONObject(i) lanzaba
                    // JSONException a MITAD del bucle, con parte del lote ya enviado; el catch
                    // caía al POST unico y reenviaba todo (duplicados). Se detecta antes de
                    // enviar nada y se cae limpiamente al POST unico.
                    for (int i = 0; i < len; i++) {
                        if (arr.optJSONObject(i) == null) {
                            logger.error("perItem sync requiere un postTemplate de tipo objeto; el "
                                    + "elemento {} no lo es. Se envia el lote en una sola peticion.", i);
                            throw new JSONException("perItem: element " + i + " is not an object");
                        }
                    }
                    for (int i = 0; i < len; i++) {
                        JSONObject item = arr.getJSONObject(i);
                        // Preserve the configured method: the 1-arg constructor silently forced
                        // POST, so a backend configured with syncHttpMethod PUT/PATCH answered
                        // 405 and the batch was retried forever.
                        HttpPostService perRequest = new HttpPostService(mUrl, mMethod);
                        int code;
                        try {
                            // postJSON(JSONObject) respeta el Content-Type de headers: plano si es
                            // form-urlencoded, {...} si es JSON. Ambos modos quedan cubiertos.
                            code = perRequest.postJSON(item, headers);
                        } catch (IOException e) {
                            // Network died mid-batch: items 0..i-1 are already on the server.
                            mAcceptedItemCount = i;
                            throw e;
                        }
                        if (listener != null && len > 0) {
                            listener.onProgress((i + 1) * 100 / len);
                        }
                        // v5.0.1 — 285 ("abort updates") es 2xx, así que este bucle lo trataba
                        // como éxito, seguía iterando y devolvía 200: onRequestedAbortUpdates no
                        // se emitía nunca y el dispositivo seguía trackeando pese a que el
                        // servidor pedía parar. Mismo fix que ya lleva postFormUrlEncodedArray.
                        // El item i SÍ fue aceptado, por eso el contador es i + 1.
                        if (code == 285) {
                            mAcceptedItemCount = i + 1;
                            return code;
                        }
                        if (code < 200 || code >= 300) {
                            mAcceptedItemCount = i;
                            return code;
                        }
                    }
                    mAcceptedItemCount = len;
                    if (listener != null) {
                        listener.onProgress(100);
                    }
                    return 200;
                }
            } catch (JSONException e) {
                // H2: era catch (Exception), que tragaba la IOException relanzada arriba cuando la
                // red se cortaba a mitad del lote. En vez de propagar a SyncAdapter (que haria
                // setBatchPartiallyCompleted y reintentaria solo el resto), caia al POST unico con
                // `locations=<json>`: reenviaba los items ya aceptados (duplicados) y, si ese POST
                // devolvia 2xx, marcaba el lote entero como completado -> perdida. Solo el parseo
                // JSON debe caer aqui; IOException tiene que subir.
                // Fall through to single-POST with jsonToUrlEncoded (e.g. array wrap)
            }
        }

        byte[] outputBytes;
        if (isFormUrlEncoded) {
            try {
                String formBody = jsonToUrlEncoded(jsonString);
                outputBytes = formBody.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                outputBytes = bodyBytes;
            }
        } else {
            outputBytes = bodyBytes;
        }

        HttpURLConnection conn = this.openConnection();
        conn.setRequestMethod(mMethod);
        if (isBodyless()) {
            conn.setDoOutput(false);
            // No headers loop / body for GET; we just consume the response.
            Iterator<Map.Entry<String, String>> hit = headers.entrySet().iterator();
            while (hit.hasNext()) {
                Map.Entry<String, String> pair = hit.next();
                if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                    conn.setRequestProperty(pair.getKey(), pair.getValue());
                }
            }
            if (listener != null) listener.onProgress(100);
            return consumeAndDisconnect(conn);
        }
        // Do NOT setDoInput(false) here: it prevented reading the response/error body, so a
        // rejected batch left only a bare status code in the log and was impossible to diagnose.
        conn.setDoOutput(true);
        // minSdk is 24, so the API 19+ (long) overload is always available.
        conn.setFixedLengthStreamingMode((long) outputBytes.length);
        conn.setRequestProperty("Content-Type", contentType);
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                conn.setRequestProperty(pair.getKey(), pair.getValue());
            }
        }

        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(conn.getOutputStream());
            os.write(outputBytes);
            if (listener != null) {
                listener.onProgress(100);
            }
        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
        }

        return consumeAndDisconnect(conn);
    }

    public static int postJSON(String url, JSONObject json, Map headers) throws IOException {
        return postJSON(url, json, headers, "POST");
    }

    public static int postJSON(String url, JSONArray json, Map headers) throws IOException {
        return postJSON(url, json, headers, "POST");
    }

    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener) throws IOException {
        return postJSONFile(url, file, headers, listener, "POST");
    }

    public static int postJSON(String url, JSONObject json, Map headers, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSON(json, headers);
    }

    public static int postJSON(String url, JSONArray json, Map headers, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSON(json, headers);
    }

    /** v5.0.1 — R11: sobrecarga que propaga syncMode ('single' => una peticion por posicion). */
    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener, String method, int[] acceptedOut, boolean perItemSync) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        service.mPerItemSync = perItemSync;
        // v5.0.1 — el try/finally NO es opcional: al cortarse la red a mitad de lote,
        // postJSONFile guarda mAcceptedItemCount y RELANZA la IOException. Sin finally, la
        // asignación se saltaba, acceptedOut[0] quedaba en -1, SyncAdapter no llamaba a
        // setBatchPartiallyCompleted y las posiciones ya aceptadas por el servidor se reenviaban
        // en el siguiente lote -> duplicados en cada corte de red. Es el mismo motivo por el que
        // la sobrecarga de 6 argumentos lo lleva.
        try {
            return service.postJSONFile(file, headers, listener);
        } finally {
            if (acceptedOut != null && acceptedOut.length > 0) {
                acceptedOut[0] = service.mAcceptedItemCount;
            }
        }
    }

    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSONFile(file, headers, listener);
    }

    /**
     * Same as {@link #postJSONFile(String, File, Map, UploadingProgressListener, String)} but also
     * reports how many leading locations the server accepted.
     *
     * <p>In form-urlencoded batch mode the body is sent as one request per location. When request
     * <em>k</em> fails, requests 0..k-1 were already accepted by the server. Returning only the
     * failing status code made the caller treat the whole batch as unsent and resend all of it,
     * duplicating every already-accepted location — on a flaky link that happened on most batches.
     *
     * @param acceptedOut single-element array that receives the number of accepted locations, or
     *                    {@code -1} when the body was sent as a single all-or-nothing request.
     */
    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener,
                                   String method, int[] acceptedOut) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        try {
            return service.postJSONFile(file, headers, listener);
        } finally {
            if (acceptedOut != null && acceptedOut.length > 0) {
                acceptedOut[0] = service.mAcceptedItemCount;
            }
        }
    }
}
