/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.service;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.Manifest;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.ConnectivityListener;
import com.marianhello.bgloc.sync.NotificationHelper;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.PostLocationTask;
import com.marianhello.bgloc.ResourceResolver;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.LocationTransform;
import com.marianhello.bgloc.headless.AbstractTaskRunner;
import com.marianhello.bgloc.headless.ActivityTask;
import com.marianhello.bgloc.headless.LocationTask;
import com.marianhello.bgloc.headless.StationaryTask;
import com.marianhello.bgloc.headless.Task;
import com.marianhello.bgloc.headless.TaskRunner;
import com.marianhello.bgloc.headless.TaskRunnerFactory;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.provider.LocationProviderFactory;
import com.marianhello.bgloc.provider.ProviderDelegate;
import com.marianhello.bgloc.sync.AccountHelper;
import com.marianhello.bgloc.sync.SyncService;
import com.marianhello.logging.LoggerManager;
import com.marianhello.logging.UncaughtExceptionLogger;

import org.chromium.content.browser.ThreadUtils;
import org.json.JSONException;

import java.util.Locale;

import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.containsCommand;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.containsMessage;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.getCommand;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.getMessage;

public class LocationServiceImpl extends Service implements ProviderDelegate, LocationService {

    public static final String ACTION_BROADCAST = ".broadcast";

    /**
     * CommandId sent by the service to
     * any registered clients with error.
     */
    public static final int MSG_ON_ERROR = 100;

    /**
     * CommandId sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_ON_LOCATION = 101;

    /**
     * CommandId sent by the service to
     * any registered clients whenever the devices enters "stationary-mode"
     */
    public static final int MSG_ON_STATIONARY = 102;

    /**
     * CommandId sent by the service to
     * any registered clients with new detected activity.
     */
    public static final int MSG_ON_ACTIVITY = 103;

    public static final int MSG_ON_SERVICE_STARTED = 104;

    public static final int MSG_ON_SERVICE_STOPPED = 105;

    public static final int MSG_ON_ABORT_REQUESTED = 106;

    public static final int MSG_ON_HTTP_AUTHORIZATION = 107;

    /** notification id */
    private static int NOTIFICATION_ID = 1;

    private ResourceResolver mResolver;
    private Config mConfig;
    private LocationProvider mProvider;
    private Account mSyncAccount;

    private org.slf4j.Logger logger;

    private final IBinder mBinder = new LocalBinder();
    private HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;
    private LocationDAO mLocationDAO;
    private PostLocationTask mPostLocationTask;
    private String mHeadlessTaskRunnerClass;
    private TaskRunner mHeadlessTaskRunner;

    private long mServiceId = -1;
    private static boolean sIsRunning = false;
    private boolean mIsInForeground = false;

    private PowerManager.WakeLock mWakeLock;
    private static final String WAKE_LOCK_TAG = "com.marianhello.bgloc:LocationServiceWakeLock";

    /** Last time we received a location (for watchdog). */
    private volatile long mLastLocationTime = 0L;
    private static final long WATCHDOG_INTERVAL_MS = 60_000L;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sIsRunning || mProvider == null || mConfig == null) return;
            if (!Boolean.TRUE.equals(mConfig.getEnableWatchdog())) return;
            long now = System.currentTimeMillis();
            if (mLastLocationTime > 0 && (now - mLastLocationTime) > WATCHDOG_INTERVAL_MS) {
                logger.info("Location watchdog: no update in {}s, restarting provider", WATCHDOG_INTERVAL_MS / 1000);
                try {
                    mProvider.onStop();
                    mProvider.onStart();
                } catch (Exception e) {
                    logger.warn("Watchdog restart failed", e);
                }
            }
            mMainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    /** Session start time for notification elapsed time (showTime). */
    private volatile long mSessionStartTime = 0L;
    /** Accumulated distance in meters for notification (showDistance). */
    private volatile double mSessionDistanceMeters = 0.0;
    private volatile double mLastLat = 0.0;
    private volatile double mLastLon = 0.0;
    private volatile boolean mHasLastLocation = false;
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 1000L;
    private final Runnable mNotificationUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sIsRunning || !mIsInForeground || mConfig == null) {
                return;
            }
            boolean showTime = Boolean.TRUE.equals(mConfig.getShowTime());
            boolean showDistance = Boolean.TRUE.equals(mConfig.getShowDistance());
            if (!showTime && !showDistance) {
                return;
            }
            updateForegroundNotification();
            mMainHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS);
        }
    };

    private static LocationTransform sLocationTransform;
    private static LocationProviderFactory sLocationProviderFactory;

    private class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("Client binds to service");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        logger.debug("Client rebinds to service");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        logger.debug("All clients have been unbound from service");

        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sIsRunning = false;

        UncaughtExceptionLogger.register(this);

        logger = LoggerManager.getLogger(LocationServiceImpl.class);
        logger.info("Creating LocationServiceImpl");

        mServiceId = System.currentTimeMillis();

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("LocationServiceImpl.Thread", Process.THREAD_PRIORITY_BACKGROUND);
        }
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());

        mResolver = ResourceResolver.newInstance(this);

        mSyncAccount = AccountHelper.CreateSyncAccount(this, mResolver.getAccountName(),
                mResolver.getAccountType());

        String authority = mResolver.getAuthority();
        ContentResolver.setIsSyncable(mSyncAccount, authority, 1);
        ContentResolver.setSyncAutomatically(mSyncAccount, authority, true);

        mLocationDAO = DAOFactory.createLocationDAO(this);

        mPostLocationTask = new PostLocationTask(mLocationDAO,
                new PostLocationTask.PostLocationTaskListener() {
                    @Override
                    public void onRequestedAbortUpdates() {
                        handleRequestedAbortUpdates();
                    }

                    @Override
                    public void onHttpAuthorizationUpdates() {
                        handleHttpAuthorizationUpdates();
                    }

                    @Override
                    public void onSyncRequested() {
                        SyncService.sync(mSyncAccount, mResolver.getAuthority(), false);
                    }
                }, new ConnectivityListener() {
            @Override
            public boolean hasConnectivity() {
                return isNetworkAvailable();
            }
        });

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        NotificationHelper.registerServiceChannel(this);
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying LocationServiceImpl");

        // workaround for issue #276
        if (mProvider != null) {
            mProvider.onDestroy();
        }

        if (mHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mHandlerThread.quitSafely();
            } else {
                mHandlerThread.quit(); //sorry
            }
        }

        if (mPostLocationTask != null) {
            mPostLocationTask.shutdown();
        }


        unregisterReceiver(connectivityChangeReceiver);

        sIsRunning = false;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.debug("Task has been removed");
        // workaround for issue #276
        Config config = getConfig();
        if (config.getStopOnTerminate()) {
            logger.info("Stopping self");
            stopSelf();
        } else {
            logger.info("Continue running in background");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // when service was killed and restarted we will restart service
            start();
            return START_STICKY;
        }

        boolean containsCommand = containsCommand(intent);
        logger.debug(
                String.format("Service in [%s] state. cmdId: [%s]. startId: [%d]",
                        sIsRunning ? "STARTED" : "NOT STARTED",
                        containsCommand ? getCommand(intent).getId() : "N/A",
                        startId)
        );

        if (containsCommand) {
            LocationServiceIntentBuilder.Command cmd = getCommand(intent);
            processCommand(cmd.getId(), cmd.getArgument());
        } else {
            // Could be a BOOT-event, or the OS just randomly restarted the service...
            startForegroundService();
        }

        if (containsMessage(intent)) {
            processMessage(getMessage(intent));
        }

        return START_STICKY;
    }

    private void processMessage(String message) {
        // currently we do not process any message
    }

    private void processCommand(int command, Object arg) {
        try {
            switch (command) {
                case CommandId.START:
                    start();
                    break;
                case CommandId.START_FOREGROUND_SERVICE:
                    startForegroundService();
                    break;
                case CommandId.STOP:
                    stop();
                    break;
                case CommandId.CONFIGURE:
                    configure((Config) arg);
                    break;
                case CommandId.STOP_FOREGROUND:
                    stopForeground();
                    break;
                case CommandId.START_FOREGROUND:
                    startForeground();
                    break;
                case CommandId.REGISTER_HEADLESS_TASK:
                    registerHeadlessTask((String) arg);
                    break;
                case CommandId.START_HEADLESS_TASK:
                    startHeadlessTask();
                    break;
                case CommandId.STOP_HEADLESS_TASK:
                    stopHeadlessTask();
                    break;
            }
        } catch (Exception e) {
            logger.error("processCommand: exception", e);
        }
    }

    @Override
    public synchronized void start() {
        if (sIsRunning) {
            return;
        }

        if (mConfig == null) {
            logger.warn("Attempt to start unconfigured service. Will use stored or default.");
            mConfig = getConfig();
            // TODO: throw JSONException if config cannot be obtained from db
        }

        logger.debug("Will start service with: {}", mConfig.toString());

        if (!hasLocationPermission()) {
            logger.warn("Cannot start location service: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION not granted");
            stopSelf();
            return;
        }

        mPostLocationTask.setConfig(mConfig);
        mPostLocationTask.clearQueue();

        LocationProviderFactory spf = sLocationProviderFactory != null
                ? sLocationProviderFactory : new LocationProviderFactory(this);
        mProvider = spf.getInstance(mConfig.getLocationProvider());
        mProvider.setDelegate(this);
        mProvider.onCreate();
        mProvider.onConfigure(mConfig);

        sIsRunning = true;

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            }
        }
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
            logger.debug("Wake lock acquired");
        }

        if (Boolean.TRUE.equals(mConfig.getEnableWatchdog())) {
            mLastLocationTime = System.currentTimeMillis();
            mMainHandler.removeCallbacks(mWatchdogRunnable);
            mMainHandler.postDelayed(mWatchdogRunnable, WATCHDOG_INTERVAL_MS);
        }

        mSessionStartTime = System.currentTimeMillis();
        mSessionDistanceMeters = 0.0;
        mLastLat = 0.0;
        mLastLon = 0.0;
        mHasLastLocation = false;

        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                mProvider.onStart();
                if (mConfig.getStartForeground()) {
                    startForeground();
                }
            }
        });

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_SERVICE_STARTED);
        bundle.putLong("serviceId", mServiceId);
        broadcastMessage(bundle);
    }

    @Override
    public synchronized void startForegroundService() {
        start();
        startForeground();
    }

    @Override
    public synchronized void stop() {
        if (!sIsRunning) {
            return;
        }

        mMainHandler.removeCallbacks(mWatchdogRunnable);
        mMainHandler.removeCallbacks(mNotificationUpdateRunnable);

        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
                logger.debug("Wake lock released");
            } catch (Exception e) {
                logger.warn("Wake lock release failed", e);
            }
        }

        if (mProvider != null) {
            mProvider.onStop();
        }

        mIsInForeground = false;
        stopForeground(true);
        stopSelf();

        broadcastMessage(MSG_ON_SERVICE_STOPPED);
        sIsRunning = false;
    }

    /**
     * Returns true if the app has at least one of the location runtime permissions.
     * Required before starting a location foreground service on API 34+.
     */
    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** FOREGROUND_SERVICE_TYPE_LOCATION = 4 when compileSdk >= 34. */
    private static final int FOREGROUND_SERVICE_TYPE_LOCATION = 4;

    /**
     * Reads this service's foregroundServiceType from the merged AndroidManifest (API 34+).
     * Uses ComponentInfoFlags.of(0) (not GET_META_DATA) so getServiceInfo returns complete ServiceInfo.
     * Returns the real value; never invents 0x4. If unknown, returns 0 so callers must not call startForeground.
     * Requires compileSdk 33+ (ComponentInfoFlags); 34+ for ServiceInfo.foregroundServiceType.
     */
    private int getManifestForegroundServiceType() {
        if (Build.VERSION.SDK_INT < 34) return 0;

        try {
            ComponentName cn = new ComponentName(this, LocationServiceImpl.class);

            ServiceInfo si;
            if (Build.VERSION.SDK_INT >= 33) {
                si = getPackageManager().getServiceInfo(
                        cn,
                        PackageManager.ComponentInfoFlags.of(0)
                );
            } else {
                si = getPackageManager().getServiceInfo(cn, 0);
            }

            int t = getForegroundServiceTypeFromServiceInfo(si);
            logger.info("Manifest foregroundServiceType=0x{}", Integer.toHexString(t));
            return t;
        } catch (Throwable e) {
            logger.warn("getManifestForegroundServiceType failed: {}", e.getMessage());
            return 0;
        }
    }

    /** Read foregroundServiceType from ServiceInfo (field exists in API 34; use reflection to compile with compileSdk 33). */
    private int getForegroundServiceTypeFromServiceInfo(ServiceInfo si) {
        try {
            java.lang.reflect.Field f = ServiceInfo.class.getField("foregroundServiceType");
            Object v = f.get(si);
            return (v instanceof Integer) ? (Integer) v : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public void startForeground() {
        if (sIsRunning && !mIsInForeground) {
            if (!hasLocationPermission()) {
                logger.warn("Cannot start foreground: location permission not granted");
                return;
            }
            Config config = getConfig();
            String contentText = buildNotificationContentText(config);
            Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                    config.getNotificationTitle(),
                    contentText,
                    config.getLargeNotificationIcon(),
                    config.getSmallNotificationIcon(),
                    config.getNotificationIconColor());

            if (mProvider != null) {
                mProvider.onCommand(LocationProvider.CMD_SWITCH_MODE,
                        LocationProvider.FOREGROUND_MODE);
            }
            // Android 14+ (API 34): type must match the merged manifest. If we get 0, do not start (avoid crash from invented type).
            if (Build.VERSION.SDK_INT >= 34) {
                super.startForeground(NOTIFICATION_ID, notification, 0x8);
            } else {
                super.startForeground(NOTIFICATION_ID, notification);
            }
            mIsInForeground = true;
            scheduleNotificationUpdater();
        }
    }

    @Override
    public synchronized void stopForeground() {
        if (sIsRunning && mIsInForeground) {
            mMainHandler.removeCallbacks(mNotificationUpdateRunnable);
            stopForeground(true);
            if (mProvider != null) {
                mProvider.onCommand(LocationProvider.CMD_SWITCH_MODE,
                        LocationProvider.BACKGROUND_MODE);
            }
            mIsInForeground = false;
        }
    }

    /** Resource names for optional app-localized notification labels (showTime / showDistance). */
    private static final String RES_NOTIFICATION_TIME_LABEL = "plugin_bgloc_notification_time_label";
    private static final String RES_NOTIFICATION_DISTANCE_LABEL = "plugin_bgloc_notification_distance_label";

    private String getNotificationLabel(String resourceName, String defaultValue) {
        Context app = getApplicationContext();
        int id = app.getResources().getIdentifier(resourceName, "string", app.getPackageName());
        return (id != 0) ? app.getString(id) : defaultValue;
    }

    private String buildNotificationContentText(Config config) {
        String base = config.getNotificationText() != null ? config.getNotificationText() : "ENABLED";
        if (Boolean.TRUE.equals(config.getShowTime())) {
            String timeLabel = getNotificationLabel(RES_NOTIFICATION_TIME_LABEL, "Time");
            base += "\n" + timeLabel + ": " + formatElapsed(mSessionStartTime);
        }
        if (Boolean.TRUE.equals(config.getShowDistance())) {
            String distanceLabel = getNotificationLabel(RES_NOTIFICATION_DISTANCE_LABEL, "Distance");
            base += "\n" + distanceLabel + ": " + formatDistance(mSessionDistanceMeters);
        }
        return base;
    }

    private static String formatElapsed(long startTimeMs) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startTimeMs);
        long s = (elapsed / 1000L) % 60L;
        long m = (elapsed / 60000L) % 60L;
        long h = elapsed / 3600000L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private static String formatDistance(double meters) {
        return String.format(Locale.US, "%.2f km", meters / 1000.0);
    }

    private void updateForegroundNotification() {
        if (!sIsRunning || !mIsInForeground || mConfig == null) {
            return;
        }
        String contentText = buildNotificationContentText(mConfig);
        Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                mConfig.getNotificationTitle(),
                contentText,
                mConfig.getLargeNotificationIcon(),
                mConfig.getSmallNotificationIcon(),
                mConfig.getNotificationIconColor());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private void scheduleNotificationUpdater() {
        mMainHandler.removeCallbacks(mNotificationUpdateRunnable);
        if (mConfig == null) {
            return;
        }
        boolean showTime = Boolean.TRUE.equals(mConfig.getShowTime());
        boolean showDistance = Boolean.TRUE.equals(mConfig.getShowDistance());
        if ((showTime || showDistance) && sIsRunning && mIsInForeground) {
            mMainHandler.postDelayed(mNotificationUpdateRunnable, NOTIFICATION_UPDATE_INTERVAL_MS);
        }
    }

    @Override
    public synchronized void configure(Config config) {
        if (mConfig == null) {
            mConfig = config;
            return;
        }

        final Config currentConfig = mConfig;
        mConfig = config;

        mPostLocationTask.setConfig(mConfig);

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sIsRunning) {
                    if (currentConfig.getStartForeground() == true && mConfig.getStartForeground() == false) {
                        stopForeground();
                    }

                    if (mConfig.getStartForeground() == true) {
                        if (currentConfig.getStartForeground() == false) {
                            // was not running in foreground, so start in foreground
                            startForeground();
                        } else {
                            // was running in foreground, so just update existing notification
                            String contentText = buildNotificationContentText(mConfig);
                            Notification notification = new NotificationHelper.NotificationFactory(LocationServiceImpl.this).getNotification(
                                    mConfig.getNotificationTitle(),
                                    contentText,
                                    mConfig.getLargeNotificationIcon(),
                                    mConfig.getSmallNotificationIcon(),
                                    mConfig.getNotificationIconColor());

                            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            notificationManager.notify(NOTIFICATION_ID, notification);
                            scheduleNotificationUpdater();
                        }
                    }
                }

                if (currentConfig.getLocationProvider() != mConfig.getLocationProvider()) {
                    boolean shouldStart = mProvider.isStarted();
                    mProvider.onDestroy();
                    LocationProviderFactory spf = new LocationProviderFactory(LocationServiceImpl.this);
                    mProvider = spf.getInstance(mConfig.getLocationProvider());
                    mProvider.setDelegate(LocationServiceImpl.this);
                    mProvider.onCreate();
                    mProvider.onConfigure(mConfig);
                    if (shouldStart) {
                        mProvider.onStart();
                    }
                } else {
                    mProvider.onConfigure(mConfig);
                }
            }
        });
    }

    @Override
    public synchronized void registerHeadlessTask(String taskRunnerClass) {
        logger.debug("Registering headless task");
        mHeadlessTaskRunnerClass = taskRunnerClass;
    }

    @Override
    public synchronized void startHeadlessTask() {
        if (mHeadlessTaskRunnerClass != null) {
            TaskRunnerFactory trf = new TaskRunnerFactory();
            try {
                mHeadlessTaskRunner = trf.getTaskRunner(mHeadlessTaskRunnerClass);
                ((AbstractTaskRunner) mHeadlessTaskRunner).setContext(this);
            } catch (Exception e) {
                logger.error("Headless task start failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void stopHeadlessTask() {
        mHeadlessTaskRunner = null;
    }

    @Override
    public synchronized void executeProviderCommand(final int command, final int arg1) {
        if (mProvider == null) {
            return;
        }

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProvider.onCommand(command, arg1);
            }
        });
    }

    @Override
    public void onLocation(BackgroundLocation location) {
        mLastLocationTime = System.currentTimeMillis();
        if (Boolean.TRUE.equals(mConfig != null ? mConfig.getShowDistance() : null)) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            if (mHasLastLocation) {
                float[] dist = new float[1];
                Location.distanceBetween(mLastLat, mLastLon, lat, lon, dist);
                mSessionDistanceMeters += (double) dist[0];
            }
            mLastLat = lat;
            mLastLon = lon;
            mHasLastLocation = true;
            if (mIsInForeground && mConfig != null && (Boolean.TRUE.equals(mConfig.getShowTime()) || Boolean.TRUE.equals(mConfig.getShowDistance()))) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateForegroundNotification();
                    }
                });
            }
        }
        logger.debug("New location {}", location.toString());

        location = transformLocation(location);
        if (location == null) {
            logger.debug("Skipping location as requested by the locationTransform");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_LOCATION);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new LocationTask(location) {
            @Override
            public void onError(String errorMessage) {
                logger.error("Location task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Location task result: {}", value);
            }
        });

        postLocation(location);
    }

    @Override
    public void onStationary(BackgroundLocation location) {
        logger.debug("New stationary {}", location.toString());

        location = transformLocation(location);
        if (location == null) {
            logger.debug("Skipping location as requested by the locationTransform");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_STATIONARY);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new StationaryTask(location){
            @Override
            public void onError(String errorMessage) {
                logger.error("Stationary task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Stationary task result: {}", value);
            }
        });

        postLocation(location);
    }

    @Override
    public void onActivity(BackgroundActivity activity) {
        logger.debug("New activity {}", activity.toString());

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ACTIVITY);
        bundle.putParcelable("payload", activity);
        broadcastMessage(bundle);

        runHeadlessTask(new ActivityTask(activity){
            @Override
            public void onError(String errorMessage) {
                logger.error("Activity task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Activity task result: {}", value);
            }
        });
    }

    @Override
    public void onError(PluginException error) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ERROR);
        bundle.putBundle("payload", error.toBundle());
        broadcastMessage(bundle);
    }

    private void broadcastMessage(int msgId) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", msgId);
        broadcastMessage(bundle);
    }

    private void broadcastMessage(Bundle bundle) {
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return super.registerReceiver(receiver, filter, null, mServiceHandler, RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ex) {
            // if was not registered ignore exception
        }
    }

    public Config getConfig() {
        Config config = mConfig;
        if (config == null) {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                config = dao.retrieveConfiguration();
            } catch (JSONException e) {
                logger.error("Config exception: {}", e.getMessage());
            }
        }

        if (config == null) {
            config = Config.getDefault();
        }

        mConfig = config;
        return mConfig;
    }

    public static void setLocationProviderFactory(LocationProviderFactory factory) {
        sLocationProviderFactory = factory;
    }

    private void runHeadlessTask(Task task) {
        if (mHeadlessTaskRunner == null) {
            return;
        }

        logger.debug("Running headless task: {}", task);
        mHeadlessTaskRunner.runTask(task);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public LocationServiceImpl getService() {
            return LocationServiceImpl.this;
        }
    }

    private BackgroundLocation transformLocation(BackgroundLocation location) {
        if (sLocationTransform != null) {
            return sLocationTransform.transformLocationBeforeCommit(this, location);
        }

        return location;
    }

    private void postLocation(BackgroundLocation location) {
        mPostLocationTask.add(location);
    }

    public void handleRequestedAbortUpdates() {
        broadcastMessage(MSG_ON_ABORT_REQUESTED);
    }

    public void handleHttpAuthorizationUpdates() {
        broadcastMessage(MSG_ON_HTTP_AUTHORIZATION);
    }

    /**
     * Broadcast receiver which detects connectivity change condition
     */
    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean hasConnectivity = isNetworkAvailable();
            mPostLocationTask.setHasConnectivity(hasConnectivity);
            logger.info("Network condition changed has connectivity: {}", hasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public long getServiceId() {
        return mServiceId;
    }

    public boolean isBound() {
        LocationServiceInfo info = new LocationServiceInfoImpl(this);
        return info.isBound();
    }

    public static boolean isRunning() {
        return sIsRunning;
    }

    public static void setLocationTransform(@Nullable LocationTransform transform) {
        sLocationTransform = transform;
    }

    public static @Nullable LocationTransform getLocationTransform() {
        return sLocationTransform;
    }
}
