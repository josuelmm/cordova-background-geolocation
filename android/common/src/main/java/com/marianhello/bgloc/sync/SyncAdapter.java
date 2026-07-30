package com.marianhello.bgloc.sync;

import android.accounts.Account;
import android.app.NotificationManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.HttpPostService;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.service.LocationServiceImpl;
import com.marianhello.logging.LoggerManager;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter implements HttpPostService.UploadingProgressListener {

    private static final int NOTIFICATION_ID = 666;

    ContentResolver contentResolver;
    private ConfigurationDAO configDAO;
    private NotificationManager notificationManager;
    private BatchManager batchManager;
    private boolean notificationsEnabled = true;
    private volatile Config currentSyncConfig;

    private org.slf4j.Logger logger;

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        this(context, autoInitialize, false);
    }


    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {

        super(context, autoInitialize);
        logger = LoggerManager.getLogger(SyncAdapter.class);

        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        contentResolver = context.getContentResolver();
        configDAO = DAOFactory.createConfigurationDAO(context);
        batchManager = new BatchManager(this.getContext());
        notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationHelper.registerSyncChannel(context);
    }

    /*
     * Specify the code you want to run in the sync adapter. The entire
     * sync adapter runs in a background thread, so you don't have to set
     * up your own background processing.
     */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {

        Config config = null;
        try {
            config = configDAO.retrieveConfiguration();
        } catch (JSONException e) {
            logger.error("Error retrieving config: {}", e.getMessage());
            syncResult.stats.numParseExceptions++;
            return;
        }

        if (config == null || !config.hasValidSyncUrl() || !Boolean.TRUE.equals(config.getSyncEnabled())) {
            if (config == null) {
                logger.warn("Sync skipped: no config");
            } else if (!Boolean.TRUE.equals(config.getSyncEnabled())) {
                logger.info("Sync skipped: sync disabled in config");
            }
            return;
        }

        //noinspection ConstantConditions
        notificationsEnabled = !config.hasNotificationsEnabled() || config.getNotificationsEnabled();
        currentSyncConfig = config;

        Long batchStartMillis = System.currentTimeMillis();
        boolean isForced = (extras != null) && extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
        Integer configThreshold = config.getSyncThreshold();
        int syncThreshold = isForced ? 0 : (configThreshold != null ? configThreshold : 100);
        logger.debug("Sync request isForced: {}, batchId: {}, syncThreshold: {}, config: {}", isForced, batchStartMillis, syncThreshold, config.toString());

        File file = null;
        try {
            file = batchManager.createBatch(batchStartMillis, syncThreshold, config.getTemplate());
        } catch (IOException e) {
            logger.error("Failed to create batch: {}", e.getMessage());
            syncResult.stats.numIoExceptions++;
            return;
        }

        if (file == null) {
            logger.info("Nothing to sync");
            return;
        }

        logger.info("Syncing startAt: {}", batchStartMillis);
        String url = config.getSyncUrl();
        HashMap<String, String> httpHeaders = new HashMap<String, String>();
        if (config.getHttpHeaders() != null) {
            httpHeaders.putAll(config.getHttpHeaders());
        }
        httpHeaders.put("x-batch-id", String.valueOf(batchStartMillis));

        // For URL templating in sync mode we can only resolve static queryParams keys; per-location
        // placeholders (like {lat}) cannot apply to a multi-location batch. If the user wants per-location
        // URL substitution they should use httpMode="single" + url= ... (real-time) or syncMode="single".
        String resolvedUrl = com.marianhello.bgloc.http.UrlTemplateResolver.resolve(url, null, config.getQueryParams());
        String syncMethod = config.getSyncHttpMethod();
        // Receives the number of locations the server accepted before a mid-batch failure.
        int[] accepted = new int[]{ -1 };
        try {
            if (uploadLocations(file, resolvedUrl, httpHeaders, syncMethod, accepted)) {
                logger.info("Batch sync successful");
                batchManager.setBatchCompleted(batchStartMillis);
            } else {
                logger.warn("Batch sync failed due server error");
                // Confirm whatever the server already took so it is not resent as a duplicate.
                // The remainder keeps its batch_start and is picked up by the next batch, whose
                // batchStartMillis is larger and therefore matches `batch_start < ?`.
                if (accepted[0] > 0) {
                    batchManager.setBatchPartiallyCompleted(batchStartMillis, accepted[0]);
                }
                syncResult.stats.numIoExceptions++;
            }
        } finally {
            // Always remove the temp file. It used to be deleted only on success, so every failed
            // sync left a locations*.json behind and they accumulated indefinitely.
            if (file.exists() && !file.delete()) {
                logger.warn("Batch file has not been deleted: {}", file.getAbsolutePath());
            }
        }
    }

    private boolean uploadLocations(File file, String url, HashMap httpHeaders, String method, int[] acceptedOut) {
        NotificationCompat.Builder builder = null;

        if (notificationsEnabled) {
            builder = new NotificationCompat.Builder(getContext(), NotificationHelper.SYNC_CHANNEL_ID);
            builder.setOngoing(true);
            builder.setContentTitle(currentSyncConfig.getNotificationSyncTitle());
            builder.setContentText(currentSyncConfig.getNotificationSyncText());
            builder.setSmallIcon(android.R.drawable.ic_dialog_info);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        // v3.5 Phase 4: emit syncStart event.
        Bundle syncStart = new Bundle();
        syncStart.putInt("action", LocationServiceImpl.MSG_ON_SYNC_START);
        broadcastMessage(syncStart);

        // Count locations being uploaded, streaming.
        //
        // This used to slurp the whole batch file into a ByteArrayOutputStream, copy it into a
        // String, and parse it into a JSONArray — three full copies in heap — purely to read
        // arr.length(). HttpPostService then read the same file again. With a large syncThreshold
        // or a queue accumulated offline that was an OutOfMemoryError in the ":sync" process, and
        // the batch could never be sent (so it was retried forever). JsonReader walks the array
        // and counts without materialising it.
        int locationsAttempted = countLocationsStreaming(file);

        try {
            int responseCode = HttpPostService.postJSONFile(url, file, httpHeaders, this, method, acceptedOut);

            // All 2xx statuses are okay
            boolean isStatusOkay = responseCode >= 200 && responseCode < 300;

            if (responseCode == 285) {
                // Okay, but we don't need to continue sending these

                logger.debug("Location was sent to the server, and received an \"HTTP 285 Updates Not Required\"");

                Bundle bundle = new Bundle();
                bundle.putInt("action", LocationServiceImpl.MSG_ON_ABORT_REQUESTED);
                broadcastMessage(bundle);
            }

            if (responseCode == 401) {
                Bundle bundle = new Bundle();
                bundle.putInt("action", LocationServiceImpl.MSG_ON_HTTP_AUTHORIZATION);
                broadcastMessage(bundle);
            }

            if (builder != null) {
                if (isStatusOkay) {
                    builder.setContentText(currentSyncConfig.getNotificationSyncCompletedText());
                } else {
                    builder.setContentText(currentSyncConfig.getNotificationSyncFailedText() + " (HTTP " + responseCode + ")");
                }
            }

            if (!isStatusOkay) {
                logger.warn("Batch sync failed: server returned HTTP {} (check server logs or sync URL)", responseCode);
                Bundle errBundle = new Bundle();
                errBundle.putInt("action", LocationServiceImpl.MSG_ON_SYNC_ERROR);
                errBundle.putInt("httpStatus", responseCode);
                errBundle.putString("message", "HTTP " + responseCode);
                broadcastMessage(errBundle);
            } else {
                Bundle okBundle = new Bundle();
                okBundle.putInt("action", LocationServiceImpl.MSG_ON_SYNC_SUCCESS);
                okBundle.putInt("sent", locationsAttempted);
                broadcastMessage(okBundle);
            }

            return isStatusOkay;
        } catch (IOException e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("Error uploading locations (network/IO): {}", errMsg);

            if (builder != null) {
                builder.setContentText(currentSyncConfig.getNotificationSyncFailedText() + ": " + errMsg);
            }
            Bundle errBundle = new Bundle();
            errBundle.putInt("action", LocationServiceImpl.MSG_ON_SYNC_ERROR);
            errBundle.putInt("httpStatus", 0);
            errBundle.putString("message", errMsg);
            broadcastMessage(errBundle);
        } finally {
            logger.info("Syncing endAt: {}", System.currentTimeMillis());

            if (builder != null) {
                builder.setOngoing(false);
                builder.setProgress(0, 0, false);
                builder.setAutoCancel(true);
                notificationManager.notify(NOTIFICATION_ID, builder.build());

                Handler h = new Handler(Looper.getMainLooper());
                long delayInMilliseconds = 5000;
                h.postDelayed(new Runnable() {
                    public void run() {
                        logger.info("Notification cancelledAt: {}", System.currentTimeMillis());
                        notificationManager.cancel(NOTIFICATION_ID);
                    }
                }, delayInMilliseconds);
            }
        }

        return false;
    }

    public void onProgress(int progress) {
        logger.debug("Syncing progress: {} updatedAt: {}", progress, System.currentTimeMillis());

        Config c = currentSyncConfig;
        if (notificationsEnabled && c != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), NotificationHelper.SYNC_CHANNEL_ID);
            builder.setOngoing(true);
            builder.setContentTitle(c.getNotificationSyncTitle());
            builder.setContentText(c.getNotificationSyncText());
            builder.setSmallIcon(android.R.drawable.ic_dialog_info);
            builder.setProgress(100, progress, false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        // v3.5 Phase 4: forward progress percentage to JS via syncProgress event.
        Bundle progBundle = new Bundle();
        progBundle.putInt("action", LocationServiceImpl.MSG_ON_SYNC_PROGRESS);
        progBundle.putInt("progress", progress);
        broadcastMessage(progBundle);
    }

    /**
     * Counts the top-level elements of the batch JSON array without loading it into memory.
     *
     * @return the number of locations, or 0 if the file cannot be read/parsed (best effort — this
     *         value only feeds the sync progress events).
     */
    private int countLocationsStreaming(File file) {
        android.util.JsonReader reader = null;
        try {
            reader = new android.util.JsonReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
            int count = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                reader.skipValue();
                count++;
            }
            reader.endArray();
            return count;
        } catch (Throwable t) {
            logger.debug("Could not count batch locations: {}", t.getMessage());
            return 0;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void broadcastMessage(Bundle bundle) {
        Intent intent = new Intent(LocationServiceImpl.ACTION_BROADCAST);
        intent.putExtras(bundle);
        // This adapter runs in the ":sync" process (see SyncService in the manifest), and
        // LocalBroadcastManager only delivers within a single process — every sync event emitted
        // here used to be dropped before reaching the plugin. Send a real broadcast restricted to
        // our own package so it crosses the process boundary while staying internal.
        Context appContext = getContext().getApplicationContext();
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }
}
