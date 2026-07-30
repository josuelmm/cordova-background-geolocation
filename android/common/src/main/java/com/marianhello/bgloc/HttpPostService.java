package com.marianhello.bgloc;

import android.os.Build;

import org.json.JSONArray;
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
            mHttpURLConnection.setReadTimeout(READ_TIMEOUT_MS);
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

    public int postJSONString(String body, Map headers) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }

        String contentType = null;
        for (Object keyObj : headers.keySet()) {
            String key = (String) keyObj;
            if (key.equalsIgnoreCase("Content-Type")) {
                contentType = (String) headers.get(key);
                break;
            }
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
        if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
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
        String contentType = getContentTypeFromHeaders(headers);
        if (contentType == null) {
            contentType = "application/json";
        }
        final boolean isFormUrlEncoded = contentType.equalsIgnoreCase("application/x-www-form-urlencoded");
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

        // When form-urlencoded and body is a JSON array, send one POST per location (same flat
        // format as real-time posting) so the same server endpoint accepts both.
        if (isFormUrlEncoded) {
            try {
                Object parsed = new JSONTokener(jsonString).nextValue();
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    int len = arr.length();
                    if (len == 0) {
                        if (listener != null) listener.onProgress(100);
                        return 200;
                    }
                    for (int i = 0; i < len; i++) {
                        JSONObject item = arr.getJSONObject(i);
                        // Preserve the configured method: the 1-arg constructor silently forced
                        // POST, so a backend configured with syncHttpMethod PUT/PATCH answered
                        // 405 and the batch was retried forever.
                        HttpPostService perRequest = new HttpPostService(mUrl, mMethod);
                        int code;
                        try {
                            code = perRequest.postJSON(item, headers);
                        } catch (IOException e) {
                            // Network died mid-batch: items 0..i-1 are already on the server.
                            mAcceptedItemCount = i;
                            throw e;
                        }
                        if (listener != null && len > 0) {
                            listener.onProgress((i + 1) * 100 / len);
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
            } catch (Exception e) {
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
