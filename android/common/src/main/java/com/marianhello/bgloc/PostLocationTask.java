package com.marianhello.bgloc;

import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.SessionLocationDAO;
import com.marianhello.bgloc.http.UrlTemplateResolver;
import com.marianhello.logging.LoggerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Location task to post/sync locations from location providers
 *
 * All locations updates are recorded in local db at all times.
 * Also location is also send to all messenger clients.
 *
 * If option.url is defined, each location is also immediately posted.
 * If post is successful, the location is deleted from local db.
 * All failed to post locations are coalesced and send in some time later in one single batch.
 * Batch sync takes place only when number of failed to post locations reaches syncTreshold.
 *
 * If only option.syncUrl is defined, locations are send only in single batch,
 * when number of locations reaches syncTreshold.
 *
 */
public class PostLocationTask {
    private final LocationDAO mLocationDAO;
    private final SessionLocationDAO mSessionDAO;
    private final PostLocationTaskListener mTaskListener;
    private final ConnectivityListener mConnectivityListener;

    private final ExecutorService mExecutor;

    private volatile boolean mHasConnectivity = true;
    private volatile Config mConfig;

    private org.slf4j.Logger logger;

    public interface PostLocationTaskListener
    {
        void onSyncRequested();
        void onRequestedAbortUpdates();
        void onHttpAuthorizationUpdates();
    }

    public PostLocationTask(LocationDAO dao, PostLocationTaskListener taskListener,
                            ConnectivityListener connectivityListener) {
        this(dao, null, taskListener, connectivityListener);
    }

    public PostLocationTask(LocationDAO dao, SessionLocationDAO sessionDAO,
                            PostLocationTaskListener taskListener,
                            ConnectivityListener connectivityListener) {
        logger = LoggerManager.getLogger(PostLocationTask.class);
        logger.info("Creating PostLocationTask");

        mLocationDAO = dao;
        mSessionDAO = sessionDAO;
        mTaskListener = taskListener;
        mConnectivityListener = connectivityListener;

        mExecutor = Executors.newSingleThreadExecutor();
    }

    public void setConfig(Config config) {
        mConfig = config;
    }

    public void setHasConnectivity(boolean hasConnectivity) {
        mHasConnectivity = hasConnectivity;
    }

    public void clearQueue() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mLocationDAO.deleteUnpostedLocations();
            }
        });
    }

    public void add(final BackgroundLocation location) {
        if (mConfig == null) {
            logger.warn("PostLocationTask has no config. Did you called setConfig? Skipping location.");
            return;
        }

        // v3.5 Phase 4: mock location policy. Detection is already in BackgroundLocation
        // (isFromMockProvider). Here we apply the policy.
        if (location != null && location.isFromMockProvider()) {
            String policy = mConfig.getMockLocationPolicy(); // "allow" | "flag" | "drop"
            if ("drop".equals(policy)) {
                logger.info("Mock location dropped (mockLocationPolicy=drop)");
                return;
            }
            // "flag": keep it, but make sure the mock marker is actually recorded so the
            // `@mocked` / `@isFromMockProvider` placeholders resolve to a real boolean instead of
            // null. Without this the flag never reached the backend and 'flag' == 'allow'.
            if ("flag".equals(policy)) {
                location.setIsFromMockProvider(true);
            }
            // "allow": no-op.
        }

        // Honour maxLocations. The unbounded overload was the only one ever called, so every
        // synced location stayed as a dead row forever and the table grew without limit.
        // v5.0.1 — `maxLocations: 0` significa NO PERSISTIR ("the total count never exceeds
        // maxLocations", docs/api.md). Antes caía en la rama sin límite, es decir justo lo
        // contrario de lo pedido: quien lo configuraba para no almacenar obtenía crecimiento
        // ilimitado. Sin fila en la tabla el id es -1 y post() no intenta borrarla ni encolarla.
        Integer maxLocations = mConfig.getMaxLocations();
        long locationId;
        if (maxLocations != null && maxLocations == 0) {
            locationId = -1;
        } else if (maxLocations != null && maxLocations > 0) {
            locationId = mLocationDAO.persistLocation(location, maxLocations);
        } else {
            locationId = mLocationDAO.persistLocation(location);
        }
        location.setLocationId(locationId);

        if (mSessionDAO != null && mSessionDAO.isSessionActive()) {
            mSessionDAO.persistSessionLocation(location);
        }

        try {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    post(location);
                }
            });
        } catch (RejectedExecutionException ex) {
            if (locationId >= 0) {
                mLocationDAO.updateLocationForSync(locationId);
            }
        }
    }

    public void shutdown() {
        shutdown(60);
    }

    public void shutdown(int waitSeconds) {
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
                mLocationDAO.deleteUnpostedLocations();
            }
        } catch (InterruptedException e) {
            mExecutor.shutdownNow();
        }
    }

    private void post(final BackgroundLocation location) {
        long locationId = location.getLocationId();
        // v5.0.1 — persistLocation devuelve -1 cuando la fila no se guardó (insert fallido, o
        // maxLocations: 0). Sin esta guarda, deleteLocationById(-1) construye una URI que el
        // UriMatcher del ContentProvider no reconoce -> IllegalArgumentException en el hilo del
        // executor, y updateLocationForSync(-1) marcaba SYNC_PENDING una fila inexistente.
        final boolean isPersisted = locationId >= 0;

        if (mHasConnectivity && mConfig.hasValidUrl()) {
            if (postLocation(location)) {
                if (isPersisted) {
                    mLocationDAO.deleteLocationById(locationId);
                }

                return; // if posted successfully do nothing more
            } else if (isPersisted) {
                mLocationDAO.updateLocationForSync(locationId);
            }
        } else if (isPersisted) {
            mLocationDAO.updateLocationForSync(locationId);
        }

        // v5.0.1 — R15: antes exigía syncUrl; con solo `url` los fallos no se reintentaban nunca.
        if (mConfig.hasEffectiveSyncUrl()) {
            Integer configThreshold = mConfig.getSyncThreshold();
            int threshold = (configThreshold != null) ? configThreshold : 100;
            long syncLocationsCount = mLocationDAO.getLocationsForSyncCount(System.currentTimeMillis());
            // v5.0.1 — `> 0` ademas del umbral: con `syncThreshold: 0` (que v4 aceptaba y 5.0.1
            // vuelve a aceptar) la condicion `>= 0` es siempre cierta, asi que se pedia un sync al
            // framework en CADA posicion aunque no hubiera nada pendiente — ~8600 arranques del
            // proceso :sync al dia sin enviar nada.
            if (syncLocationsCount > 0 && syncLocationsCount >= threshold) {
                logger.debug("Attempt to sync locations: {} threshold: {}", syncLocationsCount, threshold);
                mTaskListener.onSyncRequested();
            }
        }
    }

    private boolean postLocation(BackgroundLocation location) {
        logger.debug("Executing PostLocationTask#postLocation");

        // LocationTemplate.locationToJson returns Object (JSONObject for HashMapLocationTemplate,
        // JSONArray for ArrayListLocationTemplate). Resolve to the concrete type before calling
        // the matching HttpPostService.postJSON overload.
        Object jsonLocation;
        try {
            jsonLocation = mConfig.getTemplate().locationToJson(location);
        } catch (JSONException e) {
            logger.warn("Location to json failed: {}", location.toString());
            return false;
        }

        String urlTemplate = mConfig.getUrl();
        // URL templating: substitute {lat}, {lon}, {timestamp_iso}, {device_id}, ... using the
        // current location plus any static queryParams. For "single" mode this is per-location;
        // for "batch" mode only static queryParams placeholders apply (location-derived ones
        // would not make sense for an array).
        String resolvedUrl = UrlTemplateResolver.resolve(urlTemplate, location, mConfig.getQueryParams());

        String method = mConfig.getHttpMethod();
        String mode = mConfig.getHttpMode();
        logger.debug("Posting to url: {} method: {} mode: {} headers: {}",
                resolvedUrl, method, mode, mConfig.getHttpHeaders());
        int responseCode;

        try {
            if ("single".equals(mode) || "GET".equals(method)) {
                // GET cannot carry a JSON array body; force per-location request.
                if (jsonLocation instanceof JSONArray) {
                    responseCode = HttpPostService.postJSON(resolvedUrl, (JSONArray) jsonLocation, mConfig.getHttpHeaders(), method);
                } else {
                    responseCode = HttpPostService.postJSON(resolvedUrl, (JSONObject) jsonLocation, mConfig.getHttpHeaders(), method);
                }
            } else {
                JSONArray jsonLocations = new JSONArray();
                jsonLocations.put(jsonLocation);
                responseCode = HttpPostService.postJSON(resolvedUrl, jsonLocations, mConfig.getHttpHeaders(), method);
            }
        } catch (Exception e) {
            mHasConnectivity = mConnectivityListener.hasConnectivity();
            logger.warn("Error while posting locations: {}", e.getMessage());
            return false;
        }

        if (responseCode == 285) {
            // Okay, but we don't need to continue sending these

            logger.debug("Location was sent to the server, and received an \"HTTP 285 Updates Not Required\"");

            if (mTaskListener != null)
                mTaskListener.onRequestedAbortUpdates();
        }

        if (responseCode == 401) {
            if (mTaskListener != null)
                mTaskListener.onHttpAuthorizationUpdates();
        }

        // All 2xx statuses are okay
        boolean isStatusOkay = responseCode >= 200 && responseCode < 300;

        if (!isStatusOkay) {
            // v5.0.1 — CORRECCION DE REGRESION. v5.0.0 devolvia `true` en los 4xx "permanentes"
            // para que la posicion saliera de la cola, con el argumento de que reintentar un
            // payload invalido no puede tener exito. El efecto real en produccion fue mucho peor:
            // `post()` interpreta `true` como "entregada" y hace deleteLocationById(), asi que un
            // backend devolviendo 400 durante un despliegue —o un bug de serializacion del propio
            // plugin— BORRABA cada posicion del turno sin dejar rastro ni cola.
            //
            // v4 devolvia false para cualquier no-2xx y la posicion caia a updateLocationForSync(),
            // esperando al syncUrl. Se restaura ese comportamiento: perder datos del cliente nunca
            // es preferible a una cola que crece, y `maxLocations` ya acota la tabla.
            // El log distingue permanente de transitorio para poder diagnosticar sin borrar nada.
            if (isPermanentHttpFailure(responseCode)) {
                logger.error("Server rejected location (HTTP {}). Queued for sync instead of "
                        + "dropping; check url/postTemplate/httpHeaders.", responseCode);
            } else {
                logger.warn("Server error while posting locations responseCode: {}", responseCode);
            }
            return false;
        }

        return true;
    }

    /**
     * True for statuses that will never succeed on retry.
     *
     * <p>401 is excluded because the app can refresh credentials and the plugin already raises
     * {@code http_authorization} for it; 408 (timeout) and 429 (rate limit) are explicitly
     * transient. 3xx is excluded too: it is followed by HttpURLConnection, and if it surfaces here
     * it usually means a streamed body could not be replayed, which a retry may resolve.
     */
    private static boolean isPermanentHttpFailure(int responseCode) {
        if (responseCode < 400 || responseCode >= 500) {
            return false;
        }
        return responseCode != 401 && responseCode != 408 && responseCode != 429;
    }
}
