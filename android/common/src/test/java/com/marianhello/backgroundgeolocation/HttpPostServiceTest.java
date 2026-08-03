package com.marianhello.backgroundgeolocation;

import android.os.Build;

import com.marianhello.bgloc.HttpPostService;
import com.marianhello.bgloc.HttpPostService.UploadingProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class HttpPostServiceTest {
    @Mock
    HttpURLConnection mockHttpURLConnection;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPostJSONThrowsMalformedURLException() throws IOException {
        exception.expect(MalformedURLException.class);
        HttpPostService.postJSON(null, (JSONObject) null, null);
    }

    @Test
    public void testPostJSONThrowsUnknownHostException() throws IOException {
        exception.expect(UnknownHostException.class);
        HttpPostService.postJSON("http://unknown/json", (JSONObject) null, null);
    }

    @Test
    public void testPostJSONResult() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        assertThat(service.postJSON((JSONObject) new JSONObject(), null), is(200));
        verify(mockHttpURLConnection).setRequestMethod("POST");
    }


    @Test
    public void testPostJSONShouldPostHeaders() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HashMap headers = new HashMap();
        headers.put("foo", "bar");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON((JSONObject) null, headers);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        verify(mockHttpURLConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockHttpURLConnection).setRequestProperty("foo", "bar");
    }

    @Test
    public void testPostJSONObject() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON(new JSONObject(), null);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        assertThat(outputStream.toString(), is("{}"));
    }

    @Test
    public void testPostJSONArray() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON(new JSONArray(), null);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        assertThat(outputStream.toString(), is("[]"));
    }

    // ===== v5.0.1 — regresion HTTP 400 con form-urlencoded =====
    // httpMode 'batch' envuelve la posicion en un array de 1 elemento. v4 lo desenrollaba y
    // salia plano; v5.0.0 quito el desenrollado y jsonToUrlEncoded emitia `locations=<json>`,
    // que ningun decoder OsmAnd/Traccar entiende -> 400 en TODAS las posiciones.

    @Test
    public void testFormUrlEncodedSingleElementArrayIsFlattened() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        JSONObject location = new JSONObject();
        try {
            location.put("id", "20868910");
            location.put("lat", 3.395178869049847);
            location.put("lon", -76.52847412973642);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONArray batch = new JSONArray();
        batch.put(location);

        HashMap headers = new HashMap();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        assertThat(service.postJSON(batch, headers), is(200));

        String body = outputStream.toString();
        assertThat("no debe envolver en locations=", body.contains("locations="), is(false));
        assertThat(body.contains("id=20868910"), is(true));
        assertThat(body.contains("lat=3.395178869049847"), is(true));
        assertThat(body.contains("lon=-76.52847412973642"), is(true));
    }

    @Test
    public void testFormUrlEncodedObjectStaysFlat() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        JSONObject location = new JSONObject();
        try {
            location.put("lat", 1.5);
        } catch (Exception e) {
            throw new IOException(e);
        }

        HashMap headers = new HashMap();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON(location, headers);
        assertThat(outputStream.toString(), is("lat=1.5"));
    }

    @Test
    public void testJsonContentTypeKeepsArrayShape() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        JSONObject location = new JSONObject();
        try {
            location.put("lat", 1.5);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONArray batch = new JSONArray();
        batch.put(location);

        // Con application/json el array SI se conserva: httpMode 'batch' debe seguir
        // distinguiendose de 'single' para servidores que esperan [{...}].
        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON(batch, null);
        assertThat(outputStream.toString(), is("[{\"lat\":1.5}]"));
    }

    @Test
    public void testFormUrlEncodedWithCharsetParamIsStillFlattened() throws IOException {
        // H5: `; charset=UTF-8` es escritura habitual. Comparar la cabecera entera por igualdad
        // desactivaba el aplanado y reabria el HTTP 400 de produccion por otra puerta.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        JSONObject location = new JSONObject();
        try {
            location.put("lat", 1.5);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONArray batch = new JSONArray();
        batch.put(location);

        HashMap headers = new HashMap();
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON(batch, headers);

        String body = outputStream.toString();
        assertThat(body.contains("locations="), is(false));
        assertThat(body, is("lat=1.5"));
    }

    @Test
    public void testFormUrlEncodedArrayTemplateDoesNotSilentlySucceed() throws IOException {
        // H1: un postTemplate de tipo array produce elementos no-objeto, imposibles de aplanar.
        // El parche inicial los saltaba con `continue` y devolvia 200 sin enviar NADA: el caller
        // borraba la posicion del disco. Debe enviarse algo y que el servidor lo rechace visible.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(400);

        JSONArray inner = new JSONArray();
        try {
            inner.put(3.39);
            inner.put(-76.52);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONArray batch = new JSONArray();
        batch.put(inner);

        HashMap headers = new HashMap();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        int code = service.postJSON(batch, headers);

        assertThat("no debe devolver 200 sin enviar nada", code, is(400));
        assertThat("debe haber escrito un cuerpo", outputStream.size() > 0, is(true));
    }

    @Test
    public void testPostJSONObjectNull() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON((JSONObject) null, null);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        assertThat(outputStream.toString(), is("null"));
    }

    @Test
    public void testPostJSONArrayNull() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSON((JSONArray) null, null);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        assertThat(outputStream.toString(), is("null"));
    }

    @Test
    public void testPostString() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        service.postJSONString("test", null);
        verify(mockHttpURLConnection).setRequestMethod("POST");
        verify(mockHttpURLConnection).setRequestProperty("Content-Type", "application/json");
        assertThat(outputStream.toString(), is("test"));
    }

    @Test
    public void testPostStream() throws Exception {
        // No SDK_INT manipulation needed: minSdk is 24, so HttpPostService always uses the
        // (long) setFixedLengthStreamingMode overload this test verifies.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        String body = "test";
        InputStream inputStream = new ByteArrayInputStream(body.getBytes());
        HashMap headers = new HashMap();
        headers.put("foo", "bar");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        assertThat(service.postJSONFile(inputStream, headers, null), is(200));
        verify(mockHttpURLConnection).setRequestMethod("POST");
        verify(mockHttpURLConnection).setRequestProperty("foo", "bar");
        verify(mockHttpURLConnection).setFixedLengthStreamingMode((long) body.length());
        //verify(mockHttpURLConnection).setChunkedStreamingMode(0);
        assertThat(outputStream.toString(), is("test"));
    }

    @Test
    public void testJSONPostFile() throws Exception {
        // No SDK_INT manipulation needed: minSdk is 24, so HttpPostService always uses the
        // (long) setFixedLengthStreamingMode overload this test verifies.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);
        when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

        File file = new File("./README.md");

        HashMap headers = new HashMap();
        headers.put("foo", "bar");

        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        assertThat(service.postJSONFile(file, headers, null), is(200));
        verify(mockHttpURLConnection).setRequestMethod("POST");
        verify(mockHttpURLConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockHttpURLConnection).setRequestProperty("foo", "bar");
        verify(mockHttpURLConnection).setFixedLengthStreamingMode((long) file.length());
        //verify(mockHttpURLConnection).setChunkedStreamingMode(0);
    }

    @Test
    public void testJSONPostFileProgressListener() throws IOException {
        HttpPostService service = new HttpPostService(mockHttpURLConnection);
        UploadingProgressListener mockListener = mock(UploadingProgressListener.class);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockHttpURLConnection.getOutputStream()).thenReturn(outputStream);

        int bodySize = HttpPostService.BUFFER_SIZE * 5;
        byte[] body = new byte[bodySize];
        new Random().nextBytes(body);
        InputStream inputStream = new ByteArrayInputStream(body);

        service.postJSONFile(inputStream, null, mockListener);
        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).onProgress(20);
        inOrder.verify(mockListener).onProgress(40);
        inOrder.verify(mockListener).onProgress(60);
        inOrder.verify(mockListener).onProgress(80);
        inOrder.verify(mockListener).onProgress(100);
    }
}
