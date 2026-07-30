package com.marianhello.bgloc.service;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.DAOFactory;

public class LocationServiceProxy implements LocationService, LocationServiceInfo {
    private static final String TAG = LocationServiceProxy.class.getSimpleName();
    private final Context mContext;
    private final LocationServiceIntentBuilder mIntentBuilder;

    public LocationServiceProxy(Context context) {
        mContext = context;
        mIntentBuilder = new LocationServiceIntentBuilder(context);
    }

    @Override
    public void configure(Config config) {
        // do not start service if it was not already started
        // FIXES:
        // https://github.com/mauron85/react-native-background-geolocation/issues/360
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/551
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/552
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.CONFIGURE, config)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void registerHeadlessTask(String taskRunnerClass) {
        Intent intent = mIntentBuilder
                .setCommand(CommandId.REGISTER_HEADLESS_TASK, taskRunnerClass)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void startHeadlessTask() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.START_HEADLESS_TASK)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void stopHeadlessTask() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.STOP_HEADLESS_TASK)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void executeProviderCommand(int command, int arg) {
        // TODO
    }

    @Override
    public void start() {
        Intent intent = mIntentBuilder.setCommand(CommandId.START).build();
//        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        // start service to keep service running even if no clients are bound to it
        executeIntentCommand(intent);
    }

    @Override
    public void startForegroundService() {
        Intent intent = mIntentBuilder.setCommand(CommandId.START_FOREGROUND_SERVICE).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundServiceCompat(intent);
        } else {
            mContext.startService(intent);
        }
    }

    /**
     * Delivers {@code intent} via {@code startForegroundService()} with the permission guard this
     * class has always used. Extracted (v5.0 — A5) so the retry path in
     * {@link #executeIntentCommand(Intent)} does not duplicate the logic.
     *
     * @return true when the OS accepted the call
     */
    private boolean startForegroundServiceCompat(Intent intent) {
        if (!hasLocationPermission()) {
            // Do NOT fall back to startService(): would create a non-foreground service that crashes
            // on first location update. Caller must request the permission first.
            Log.w(TAG, "Cannot start foreground service: ACCESS_FINE_LOCATION/COARSE_LOCATION not granted");
            return false;
        }
        // Note: ACCESS_BACKGROUND_LOCATION is only required when the service is started from
        // background (e.g. BootCompletedReceiver). When called from foreground, the OS allows
        // a location-typed FGS to run with only fine/coarse location and inherit "while-in-use".
        try {
            mContext.startForegroundService(intent);
            return true;
        } catch (Exception e) {
            // Android 12+ may throw ForegroundServiceStartNotAllowedException.
            Log.e(TAG, "startForegroundService blocked: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return false;
        }
    }

    private boolean hasLocationPermission() {
        // v4.5.1 — ContextCompat handles API < 23 safely.
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void stop() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.STOP).build();
        executeIntentCommand(intent);
    }

    @Override
    public void stopForeground() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.STOP_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public void startForeground() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.START_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public boolean isStarted() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isStarted();
    }

    public boolean isRunning() {
        if (isStarted()) {
            return LocationServiceImpl.isRunning();
        }
        return false;
    }

    @Override
    public boolean isBound() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isBound();
    }

    private void executeIntentCommand(Intent intent) {
        // The plugin dispatches these from cordova.getThreadPool(), a bare executor with no
        // uncaught-exception handler. On Android 8+ startService() from the background throws
        // IllegalStateException / BackgroundServiceStartNotAllowedException, which killed the
        // whole process — the app disappeared and tracking with it. startForegroundService()
        // elsewhere in this class was already guarded; this path was not.
        try {
            mContext.startService(intent);
        } catch (Exception e) {
            // v5.0 — A5: on Android 8+ this always throws when the caller is in the background,
            // so START / STOP / CONFIGURE issued from a background context were dropped without a
            // trace. The same intent can legally be delivered with startForegroundService() when
            // the service is meant to run in the foreground (which is the only case where the
            // 5 s "must promote itself" promise is kept), so retry there before giving up.
            final int commandId = commandIdOf(intent);
            // Only commands whose handler actually calls startForeground() may be retried this
            // way. startForegroundService() is a promise to the OS that the service promotes
            // itself within 5 s; deliver e.g. REGISTER_HEADLESS_TASK through it and onStartCommand
            // takes the processCommand() branch (it never calls startForegroundService() when the
            // intent carries a command), the promise is broken and the OS throws
            // ForegroundServiceDidNotStartInTimeException — killing the process, which is worse
            // than the dropped command this retry is meant to avoid.
            final boolean promotes = commandId == CommandId.START
                    || commandId == CommandId.START_FOREGROUND_SERVICE
                    || commandId == CommandId.START_FOREGROUND;
            if (promotes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldRunInForeground()) {
                if (startForegroundServiceCompat(intent)) {
                    Log.w(TAG, "startService failed for cmdId " + commandId + " ("
                            + e.getClass().getSimpleName() + "); delivered via startForegroundService");
                    return;
                }
            }
            Log.e(TAG, "Command LOST: cmdId " + commandId + " not delivered to LocationService: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Command id carried by the intent, or {@link CommandId#INVALID} — for logging only. */
    private int commandIdOf(Intent intent) {
        try {
            if (LocationServiceIntentBuilder.containsCommand(intent)) {
                return LocationServiceIntentBuilder.getCommand(intent).getId();
            }
        } catch (Exception ignored) {
            // logging path only, never mask the original failure
        }
        return CommandId.INVALID;
    }

    /**
     * Whether the stored configuration asks for a foreground service. Only consulted from the
     * retry path above: startForegroundService() promises the OS that the service promotes itself
     * within 5 s, and LocationServiceImpl only does that when startForeground is configured.
     * A missing row means the service falls back to Config.getDefault(), whose startForeground is
     * true, so the promise still holds.
     */
    private boolean shouldRunInForeground() {
        try {
            Config config = DAOFactory.createConfigurationDAO(mContext).retrieveConfiguration();
            return config == null || !Boolean.FALSE.equals(config.getStartForeground());
        } catch (Exception e) {
            Log.w(TAG, "Could not read stored config for the foreground retry: " + e.getMessage());
            return false;
        }
    }
}
