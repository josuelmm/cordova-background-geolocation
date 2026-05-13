/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

Differences to original version:

1. new methods isLocationEnabled, mMessageReciever, handleMessage
*/

package com.tenforwardconsulting.bgloc.cordova;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.marianhello.bgloc.BackgroundGeolocationFacade;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.PluginDelegate;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.cordova.ConfigMapper;
import com.marianhello.bgloc.cordova.PluginRegistry;
import com.marianhello.bgloc.oem.BatteryOemHelper;
import com.marianhello.bgloc.cordova.headless.JsEvaluatorTaskRunner;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

public class BackgroundGeolocationPlugin extends CordovaPlugin implements PluginDelegate {

    public static final String LOCATION_EVENT = "location";
    public static final String STATIONARY_EVENT = "stationary";
    public static final String ACTIVITY_EVENT = "activity";
    public static final String FOREGROUND_EVENT = "foreground";
    public static final String BACKGROUND_EVENT = "background";
    public static final String AUTHORIZATION_EVENT = "authorization";
    public static final String START_EVENT = "start";
    public static final String STOP_EVENT = "stop";
    public static final String ABORT_REQUESTED_EVENT = "abort_requested";
    public static final String HTTP_AUTHORIZATION_EVENT = "http_authorization";

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_SWITCH_MODE = "switchMode";
    public static final String ACTION_LOCATION_ENABLED_CHECK = "isLocationEnabled";
    public static final String ACTION_SHOW_LOCATION_SETTINGS = "showLocationSettings";
    public static final String ACTION_SHOW_APP_SETTINGS = "showAppSettings";
    public static final String ACTION_GET_STATIONARY = "getStationaryLocation";
    public static final String ACTION_GET_ALL_LOCATIONS = "getLocations";
    public static final String ACTION_GET_VALID_LOCATIONS = "getValidLocations";
    public static final String ACTION_GET_VALID_LOCATIONS_AND_DELETE = "getValidLocationsAndDelete";
    public static final String ACTION_DELETE_LOCATION = "deleteLocation";
    public static final String ACTION_DELETE_ALL_LOCATIONS = "deleteAllLocations";
    public static final String ACTION_GET_CURRENT_LOCATION = "getCurrentLocation";
    public static final String ACTION_GET_CONFIG = "getConfig";
    public static final String ACTION_GET_LOG_ENTRIES = "getLogEntries";
    public static final String ACTION_CHECK_STATUS = "checkStatus";
    public static final String ACTION_REGISTER_EVENT_LISTENER = "addEventListener";
    public static final String ACTION_START_TASK = "startTask";
    public static final String ACTION_END_TASK = "endTask";
    public static final String ACTION_REGISTER_HEADLESS_TASK = "registerHeadlessTask";
    public static final String ACTION_FORCE_SYNC = "forceSync";
    public static final String ACTION_CLEAR_SYNC = "clearSync";
    public static final String ACTION_GET_PENDING_SYNC_COUNT = "getPendingSyncCount";
    public static final String ACTION_START_SESSION = "startSession";
    public static final String ACTION_GET_SESSION_LOCATIONS = "getSessionLocations";
    public static final String ACTION_CLEAR_SESSION = "clearSession";
    public static final String ACTION_GET_SESSION_LOCATIONS_COUNT = "getSessionLocationsCount";
    public static final String ACTION_GET_PLUGIN_VERSION = "getPluginVersion";
    public static final String ACTION_GET_DIAGNOSTICS = "getDiagnostics";
    // v3.6 Phase 5
    public static final String ACTION_IS_IGNORING_BATTERY_OPT       = "isIgnoringBatteryOptimizations";
    public static final String ACTION_REQUEST_IGNORE_BATTERY_OPT    = "requestIgnoreBatteryOptimizations";
    public static final String ACTION_OPEN_BATTERY_SETTINGS         = "openBatterySettings";
    public static final String ACTION_OPEN_AUTOSTART_SETTINGS       = "openAutoStartSettings";
    public static final String ACTION_GET_MANUFACTURER_HELP         = "getManufacturerHelp";
    public static final String ACTION_TRIGGER_SOS                   = "triggerSOS";
    // v4.5: runtime permission helpers — opt-in. The app drives the flow; the plugin
    // simply asks the OS dialog (or returns the current state on iOS where Apple does
    // not surface separate runtime gates for background location / activity recognition).
    public static final String ACTION_REQUEST_BACKGROUND_PERMISSION   = "requestBackgroundLocationPermission";
    public static final String ACTION_REQUEST_ACTIVITY_PERMISSION     = "requestActivityRecognitionPermission";
    public static final String ACTION_REQUEST_NOTIFICATION_PERMISSION = "requestNotificationPermission";

    /** Plugin version; keep in sync with plugin.xml. */
    public static final String PLUGIN_VERSION = "4.5.3";

    private BackgroundGeolocationFacade facade;

    private CallbackContext callbackContext;

    private org.slf4j.Logger logger;

    public static class ErrorPluginResult {
        public static PluginResult from(String message, int code) {
            JSONObject json = new JSONObject();
            try {
                json.put("code", code);
                json.put("message", message);
            } catch (JSONException e) {
                // not interested
            }
            return new PluginResult(PluginResult.Status.ERROR, json);
        }

        public static PluginResult from(String message, Throwable cause, int code) {
            JSONObject json = new JSONObject();
            try {
                json.put("code", code);
                json.put("message", message);
                json.put("cause", from(cause));
            } catch (JSONException e) {
                // not interested
            }
            return new PluginResult(PluginResult.Status.ERROR, json);
        }

        public static PluginResult from(PluginException e) {
            JSONObject json = new JSONObject();
            try {
                json.put("code", e.getCode());
                json.put("message", e.getMessage());
                if (e.getCause() != null) {
                    json.put("cause", from(e.getCause()));
                }
            } catch (JSONException ex) {
                // not interested
            }

            return new PluginResult(PluginResult.Status.ERROR, json);
        }

        private static JSONObject from(Throwable e) {
            JSONObject error = new JSONObject();
            try {
                error.put("message", e.getMessage());
            } catch (JSONException e1) {
                // not interested
            }
            return error;
        }
    }

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();

        logger = LoggerManager.getLogger(BackgroundGeolocationPlugin.class);
        facade = new BackgroundGeolocationFacade(this.getContext(), this);
        facade.resume();
    }

    public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) {
        Context context = getContext();

        if (ACTION_REGISTER_EVENT_LISTENER.equals(action)) {
            logger.debug("Registering event listeners");
            this.callbackContext = callbackContext;

            return true;
        }
        else if (ACTION_START.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    facade.start();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_STOP.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    facade.stop();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_SWITCH_MODE.equals(action)) {
            try {
                int mode = data.getInt(0);
                facade.switchMode(mode);
            } catch (JSONException e) {
                logger.error("Switch mode error: {}", e.getMessage());
                sendError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
            }

            return true;
        } else if (ACTION_CONFIGURE.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        Config config = ConfigMapper.fromJSONObject(data.getJSONObject(0));
                        facade.configure(config);
                        callbackContext.success();
                    } catch (JSONException e) {
                        logger.error("Configuration error: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Configuration error", e, PluginException.CONFIGURE_ERROR));
                    } catch (PluginException e) {
                        logger.error("Configuration error: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from(e));
                    }
                }
            });

            return true;
        } else if (ACTION_LOCATION_ENABLED_CHECK.equals(action)) {
            logger.debug("Location services enabled check");
            try {
                callbackContext.success(facade.locationServicesEnabled() ? 1 : 0);
            } catch (PluginException e) {
                logger.error("Location service checked failed: {}", e.getMessage());
                callbackContext.sendPluginResult(ErrorPluginResult.from(e));
            }

            return true;
        } else if (ACTION_SHOW_LOCATION_SETTINGS.equals(action)) {
            BackgroundGeolocationFacade.showLocationSettings(context);

            return true;
        } else if (ACTION_SHOW_APP_SETTINGS.equals(action)) {
            BackgroundGeolocationFacade.showAppSettings(context);

            return true;
        } else if (ACTION_GET_STATIONARY.equals(action)) {
            try {
                BackgroundLocation stationaryLocation = facade.getStationaryLocation();
                if (stationaryLocation != null) {
                    callbackContext.success(stationaryLocation.toJSONObject());
                } else {
                    callbackContext.success();
                }
            } catch (JSONException e) {
                logger.error("Getting stationary location failed: {}", e.getMessage());
                callbackContext.sendPluginResult(ErrorPluginResult.from("Getting stationary location failed", e, PluginException.JSON_ERROR));
            }

            return true;
        } else if (ACTION_GET_ALL_LOCATIONS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getAllLocations());
                    } catch (JSONException e) {
                        logger.error("Getting all locations failed: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Converting locations to JSON failed", e, PluginException.JSON_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_GET_VALID_LOCATIONS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getValidLocations());
                    } catch (JSONException e) {
                        logger.error("Getting valid locations failed: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Converting locations to JSON failed", e, PluginException.JSON_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_GET_VALID_LOCATIONS_AND_DELETE.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getValidLocationsAndDelete());
                    } catch (JSONException e) {
                        logger.error("Getting valid locations and delete failed: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Converting locations to JSON failed", e, PluginException.JSON_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_DELETE_LOCATION.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        Long locationId = data.getLong(0);
                        facade.deleteLocation(locationId);
                        callbackContext.success();
                    } catch (JSONException e) {
                        logger.error("Delete location failed: {}", e.getMessage());
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Delete location failed", e, PluginException.JSON_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_DELETE_ALL_LOCATIONS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    facade.deleteAllLocations();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_GET_CURRENT_LOCATION.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    int timeout = data.optInt(0, Integer.MAX_VALUE);
                    long maximumAge = data.optLong(1, Long.MAX_VALUE);
                    boolean enableHighAccuracy = data.optBoolean(2, false);
                    try {
                        BackgroundLocation location = facade.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
                        callbackContext.success(location.toJSONObject());
                    } catch (JSONException e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from(e.getMessage(), 2));
                    } catch (PluginException e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from(e));
                    }
                }
            });

            return true;
        } else if (ACTION_GET_CONFIG.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        Config config = facade.getConfig();
                        callbackContext.success(ConfigMapper.toJSONObject(config));
                    } catch (JSONException e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Error getting config", e, PluginException.JSON_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_GET_LOG_ENTRIES.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        int limit = data.getInt(0);
                        int offset = data.getInt(1);
                        String minLevel = data.getString(2);
                        callbackContext.success(getLogs(limit, offset, minLevel));
                    } catch (Exception e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Getting logs failed", e, PluginException.SERVICE_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_CHECK_STATUS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(checkStatus());
                    } catch (Exception e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("Checking status failed", e, PluginException.SERVICE_ERROR));
                    }
                }
            });

            return true;
        } else if (ACTION_START_TASK.equals(action)) {
            callbackContext.success(1);
            return true;
        } else if (ACTION_END_TASK.equals(action)) {
            callbackContext.success();
            return true;
        } else if (ACTION_REGISTER_HEADLESS_TASK.equals(action)) {
            logger.debug("Registering headless task");
            try {
                PluginRegistry.getInstance().registerHeadlessTask(data.getString(0));
                facade.registerHeadlessTask(JsEvaluatorTaskRunner.class.getName());
            } catch (JSONException e) {
                callbackContext.sendPluginResult(ErrorPluginResult.from("Registering headless task failed", e, PluginException.JSON_ERROR));
            }
            return true;
        } else if (ACTION_FORCE_SYNC.equals(action)) {
            logger.debug("Forced location sync requested");
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    facade.forceSync();
                    callbackContext.success();
                }
            });
            return true;
        } else if (ACTION_CLEAR_SYNC.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    facade.clearSync();
                    callbackContext.success();
                }
            });
            return true;
        } else if (ACTION_GET_PENDING_SYNC_COUNT.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        long count = facade.getPendingSyncCount();
                        callbackContext.success((int) Math.min(count, Integer.MAX_VALUE));
                    } catch (Exception e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("getPendingSyncCount failed", e, PluginException.SERVICE_ERROR));
                    }
                }
            });
            return true;
        } else if (ACTION_START_SESSION.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    facade.startSession();
                    callbackContext.success();
                }
            });
            return true;
        } else if (ACTION_GET_SESSION_LOCATIONS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        callbackContext.success(getSessionLocations());
                    } catch (JSONException e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("getSessionLocations failed", e, PluginException.JSON_ERROR));
                    }
                }
            });
            return true;
        } else if (ACTION_CLEAR_SESSION.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    facade.clearSession();
                    callbackContext.success();
                }
            });
            return true;
        } else if (ACTION_GET_SESSION_LOCATIONS_COUNT.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int count = facade.getSessionLocationsCount();
                        callbackContext.success(count);
                    } catch (Exception e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("getSessionLocationsCount failed", e, PluginException.SERVICE_ERROR));
                    }
                }
            });
            return true;
        } else if (ACTION_GET_PLUGIN_VERSION.equals(action)) {
            callbackContext.success(PLUGIN_VERSION);
            return true;
        } else if (ACTION_GET_DIAGNOSTICS.equals(action)) {
            runOnWebViewThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        callbackContext.success(buildDiagnostics());
                    } catch (Exception e) {
                        callbackContext.sendPluginResult(ErrorPluginResult.from("getDiagnostics failed", e, PluginException.SERVICE_ERROR));
                    }
                }
            });
            return true;
        } else if (ACTION_IS_IGNORING_BATTERY_OPT.equals(action)) {
            Context ctx = cordova.getActivity().getApplicationContext();
            callbackContext.success(BatteryOemHelper.isIgnoringBatteryOptimizations(ctx) ? 1 : 0);
            return true;
        } else if (ACTION_REQUEST_IGNORE_BATTERY_OPT.equals(action)) {
            BatteryOemHelper.requestIgnoreBatteryOptimizations(cordova.getActivity());
            // Resolve with the (possibly unchanged) current state; the user accepts the dialog asynchronously.
            Context ctx = cordova.getActivity().getApplicationContext();
            callbackContext.success(BatteryOemHelper.isIgnoringBatteryOptimizations(ctx) ? 1 : 0);
            return true;
        } else if (ACTION_OPEN_BATTERY_SETTINGS.equals(action)) {
            BatteryOemHelper.openBatterySettings(cordova.getActivity());
            callbackContext.success();
            return true;
        } else if (ACTION_OPEN_AUTOSTART_SETTINGS.equals(action)) {
            try {
                callbackContext.success(BatteryOemHelper.openAutoStartSettings(cordova.getActivity()));
            } catch (Exception e) {
                callbackContext.sendPluginResult(ErrorPluginResult.from("openAutoStartSettings failed", e, PluginException.SERVICE_ERROR));
            }
            return true;
        } else if (ACTION_GET_MANUFACTURER_HELP.equals(action)) {
            try {
                callbackContext.success(BatteryOemHelper.getManufacturerHelp());
            } catch (Exception e) {
                callbackContext.sendPluginResult(ErrorPluginResult.from("getManufacturerHelp failed", e, PluginException.SERVICE_ERROR));
            }
            return true;
        } else if (ACTION_TRIGGER_SOS.equals(action)) {
            try {
                JSONObject payload = data.optJSONObject(0);
                facade.triggerSOS(payload);
                callbackContext.success();
            } catch (Exception e) {
                callbackContext.sendPluginResult(ErrorPluginResult.from("triggerSOS failed", e, PluginException.SERVICE_ERROR));
            }
            return true;
        } else if (ACTION_REQUEST_BACKGROUND_PERMISSION.equals(action)) {
            // Android 10+ (API 29+). Returns {granted: bool}.
            return requestPermissionAction(callbackContext,
                    Build.VERSION.SDK_INT >= 29 ? android.Manifest.permission.ACCESS_BACKGROUND_LOCATION : android.Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (ACTION_REQUEST_ACTIVITY_PERMISSION.equals(action)) {
            // Android 10+ (API 29+) needs runtime grant for activity recognition.
            return requestPermissionAction(callbackContext,
                    Build.VERSION.SDK_INT >= 29 ? "android.permission.ACTIVITY_RECOGNITION" : null);
        } else if (ACTION_REQUEST_NOTIFICATION_PERMISSION.equals(action)) {
            // Android 13+ (API 33+) requires POST_NOTIFICATIONS at runtime.
            return requestPermissionAction(callbackContext,
                    Build.VERSION.SDK_INT >= 33 ? "android.permission.POST_NOTIFICATIONS" : null);
        }

        return false;
    }

    /** v4.5: shared helper — request a single runtime permission via PermissionManager.
     *  Returns {granted: true} if already granted (or unsupported on this OS version).
     *  Returns {granted: false, denied: [name]} if user denies.
     */
    private boolean requestPermissionAction(final CallbackContext cb, final String permission) {
        if (permission == null) {
            // OS version where this permission does not exist: act as already granted.
            try {
                JSONObject r = new JSONObject();
                r.put("granted", true);
                r.put("notRequired", true);
                cb.success(r);
            } catch (JSONException e) { cb.success(); }
            return true;
        }
        Context ctx = cordova.getActivity().getApplicationContext();
        if (hasPermission(ctx, permission)) {
            try { JSONObject r = new JSONObject(); r.put("granted", true); cb.success(r); }
            catch (JSONException e) { cb.success(); }
            return true;
        }
        com.intentfilter.androidpermissions.PermissionManager pm =
                com.intentfilter.androidpermissions.PermissionManager.getInstance(ctx);
        pm.checkPermissions(java.util.Arrays.asList(permission),
                new com.intentfilter.androidpermissions.PermissionManager.PermissionRequestListener() {
            @Override public void onPermissionGranted() {
                try { JSONObject r = new JSONObject(); r.put("granted", true); cb.success(r); }
                catch (JSONException e) { cb.success(); }
            }
            @Override public void onPermissionDenied(com.intentfilter.androidpermissions.models.DeniedPermissions deniedPermissions) {
                try {
                    JSONObject r = new JSONObject();
                    r.put("granted", false);
                    r.put("denied", new org.json.JSONArray().put(permission));
                    cb.success(r);
                } catch (JSONException e) { cb.success(); }
            }
        });
        return true;
    }

    /** v3.5 Phase 4: extended diagnostics. */
    private JSONObject buildDiagnostics() throws JSONException {
        JSONObject d = new JSONObject();
        Context ctx = cordova.getActivity().getApplicationContext();

        // Common
        d.put("isRunning", facade.isRunning());
        try {
            d.put("locationServicesEnabled", facade.locationServicesEnabled());
        } catch (PluginException e) {
            d.put("locationServicesEnabled", JSONObject.NULL);
        }

        try {
            Config cfg = facade.getConfig();
            if (cfg != null) {
                d.put("startOnBoot", cfg.getStartOnBoot());
            }
        } catch (Exception ignored) { /* config may not be persisted yet */ }

        try {
            d.put("pendingSyncCount", (int) Math.min(facade.getPendingSyncCount(), Integer.MAX_VALUE));
        } catch (Exception ignored) { /* DAO might not be ready */ }

        try {
            BackgroundLocation last = facade.getStationaryLocation();
            // Last *received* location is closer to lastBest; we expose stationary as a fallback signal.
            d.put("lastLocationAt", last != null ? last.getTime() : JSONObject.NULL);
        } catch (Exception ignored) {
            d.put("lastLocationAt", JSONObject.NULL);
        }

        // Permissions
        d.put("fineLocationGranted", hasPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION));
        d.put("coarseLocationGranted", hasPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            d.put("backgroundLocationGranted", hasPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION));
        } else {
            d.put("backgroundLocationGranted", true);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            d.put("notificationPermissionGranted", hasPermission(ctx, "android.permission.POST_NOTIFICATIONS"));
        } else {
            d.put("notificationPermissionGranted", true);
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            d.put("activityRecognitionGranted", hasPermission(ctx, "android.permission.ACTIVITY_RECOGNITION"));
        } else {
            d.put("activityRecognitionGranted", true);
        }

        // Battery / OEM
        d.put("batteryOptimizationIgnored", isIgnoringBatteryOptimizations(ctx));
        d.put("manufacturer", android.os.Build.MANUFACTURER != null ? android.os.Build.MANUFACTURER : "");

        // Foreground service type read from manifest (only meaningful on API 34+; reported as-is otherwise)
        d.put("foregroundServiceType", readForegroundServiceTypeFromManifest(ctx));

        return d;
    }

    private static boolean hasPermission(Context ctx, String permission) {
        try {
            return ctx.getPackageManager().checkPermission(permission, ctx.getPackageName())
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIgnoringBatteryOptimizations(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private static int readForegroundServiceTypeFromManifest(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < 34) return 0;
        try {
            android.content.ComponentName cn = new android.content.ComponentName(
                    ctx, com.marianhello.bgloc.service.LocationServiceImpl.class);
            android.content.pm.ServiceInfo si = ctx.getPackageManager().getServiceInfo(
                    cn, android.content.pm.PackageManager.ComponentInfoFlags.of(0));
            java.lang.reflect.Field f = android.content.pm.ServiceInfo.class.getField("foregroundServiceType");
            Object v = f.get(si);
            return (v instanceof Integer) ? (Integer) v : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
        logger.info("App will be paused multitasking={}", multitasking);
        facade.pause();
        sendEvent(BACKGROUND_EVENT);
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
        logger.info("App will be resumed multitasking={}", multitasking);
        facade.resume();
        sendEvent(FOREGROUND_EVENT);
    }

    /**
     * Called when the activity is becoming visible to the user.
     */
    public void onStart() {
        logger.info("App is visible");
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    public void onStop() {
        logger.info("App is no longer visible");
    }

    /**
     * The final call you receive before your activity is destroyed.
     * Checks to see if it should turn off
     */
    @Override
    public void onDestroy() {
        logger.info("Destroying plugin");
        facade.destroy();
        super.onDestroy();
    }

    public Activity getActivity() {
        return cordova.getActivity();
    }

    public Context getContext() {
        return getActivity().getApplicationContext();
    }

    protected Application getApplication() {
        return getActivity().getApplication();
    }

    private void sendEvent(String name) {
        if (callbackContext == null) {
            return;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            logger.error("Error sending event {}: {}", name, e.getMessage());
        }
    }

    private void sendEvent(String name, JSONObject payload) {
        if (callbackContext == null) {
            return;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("payload", payload);
            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            logger.error("Error sending event {}: {}", name, e.getMessage());
        }
    }

    private void sendEvent(String name, Integer payload) {
        if (callbackContext == null) {
            return;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("payload", payload);
            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            logger.error("Error sending event {}: {}", name, e.getMessage());
        }
    }

    private void sendError(PluginException e) {
        if (callbackContext == null) {
            return;
        }
        PluginResult result = ErrorPluginResult.from(e);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void runOnUiThread(Runnable runnable) {
        getActivity().runOnUiThread(runnable);
    }

    private void runOnWebViewThread(Runnable runnable) {
        cordova.getThreadPool().execute(runnable);
    }

    private JSONArray getAllLocations() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        Collection<BackgroundLocation> locations = facade.getLocations();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObjectWithId());
        }
        return jsonLocationsArray;
    }

    private JSONArray getValidLocations() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        Collection<BackgroundLocation> locations = facade.getValidLocations();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObjectWithId());
        }
        return jsonLocationsArray;
    }

    private JSONArray getValidLocationsAndDelete() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        Collection<BackgroundLocation> locations = facade.getValidLocationsAndDelete();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObjectWithId());
        }
        return jsonLocationsArray;
    }

    private JSONArray getSessionLocations() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        Collection<BackgroundLocation> locations = facade.getSessionLocations();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObjectWithId());
        }
        return jsonLocationsArray;
    }

    private JSONArray getLogs(Integer limit, int offset, String minLevel) throws Exception {
        JSONArray jsonLogsArray = new JSONArray();
        Collection<LogEntry> logEntries = facade.getLogEntries(limit, offset, minLevel);
        for (LogEntry logEntry : logEntries) {
            jsonLogsArray.put(logEntry.toJSONObject());
        }
        return jsonLogsArray;
    }

    private JSONObject checkStatus() throws JSONException, PluginException {
        JSONObject json = new JSONObject();
        json.put("isRunning", facade.isRunning());
        json.put("hasPermissions", facade.hasPermissions()); //@Deprecated
        json.put("locationServicesEnabled", facade.locationServicesEnabled());
        json.put("authorization", facade.getAuthorizationStatus());

        return json;
    }

    @Override
    public void onAuthorizationChanged(int authStatus) {
        sendEvent(AUTHORIZATION_EVENT, authStatus);
    }

    @Override
    public void onLocationChanged(BackgroundLocation location) {
        try {
            sendEvent(LOCATION_EVENT, location.toJSONObjectWithId());
        } catch (JSONException e) {
            logger.error("Error converting location to json: {}", e.getMessage());
            sendError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onStationaryChanged(BackgroundLocation location) {
        try {
            sendEvent(STATIONARY_EVENT, location.toJSONObjectWithId());
        } catch (JSONException e) {
            logger.error("Error converting location to json: {}", e.getMessage());
            sendError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onActivityChanged(BackgroundActivity activity) {
        try {
            sendEvent(ACTIVITY_EVENT, activity.toJSONObject());
        } catch (JSONException e) {
            logger.error("Error converting activity to json: {}", e.getMessage());
            sendError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onServiceStatusChanged(int status) {
        switch (status) {
            case BackgroundGeolocationFacade.SERVICE_STARTED:
                sendEvent(START_EVENT);
                return;
            case BackgroundGeolocationFacade.SERVICE_STOPPED:
                sendEvent(STOP_EVENT);
                return;
        }
    }

    @Override
    public void onAbortRequested() {
        sendEvent(ABORT_REQUESTED_EVENT, 0);
    }

    @Override
    public void onHttpAuthorization() {
        sendEvent(HTTP_AUTHORIZATION_EVENT);
    }

    @Override
    public void onError(PluginException e) {
        sendError(e);
    }

    // v3.5 Phase 4: sync queue events
    @Override
    public void onSyncStart() {
        sendEvent("syncStart");
    }

    @Override
    public void onSyncSuccess(int locationsSent) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sent", locationsSent);
            sendEvent("syncSuccess", payload);
        } catch (JSONException e) {
            sendEvent("syncSuccess");
        }
    }

    @Override
    public void onSyncError(int httpStatus, String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("httpStatus", httpStatus);
            payload.put("message", message != null ? message : "");
            sendEvent("syncError", payload);
        } catch (JSONException e) {
            sendEvent("syncError");
        }
    }

    @Override
    public void onSyncProgress(int progress) {
        sendEvent("syncProgress", Integer.valueOf(progress));
    }

    @Override
    public void onHeartbeat(BackgroundLocation location) {
        if (location == null) {
            sendEvent("heartbeat");
            return;
        }
        try {
            sendEvent("heartbeat", location.toJSONObjectWithId());
        } catch (JSONException e) {
            sendEvent("heartbeat");
        }
    }

    // v4.0 Phase 6 — driver-insight events
    @Override
    public void onTripStart(BackgroundLocation location) {
        sendLocationEvent("tripStart", location);
    }

    @Override
    public void onTripEnd(BackgroundLocation location, double distance, long durationMs) {
        try {
            JSONObject p = new JSONObject();
            p.put("location", location != null ? location.toJSONObjectWithId() : JSONObject.NULL);
            p.put("distance", distance);
            p.put("durationMs", durationMs);
            sendEvent("tripEnd", p);
        } catch (JSONException e) { sendEvent("tripEnd"); }
    }

    @Override
    public void onMoving(BackgroundLocation location) {
        sendLocationEvent("moving", location);
    }

    @Override
    public void onStopped(BackgroundLocation location) {
        sendLocationEvent("stopped", location);
    }

    @Override
    public void onSpeeding(BackgroundLocation location, double speedKmh, double limitKmh) {
        try {
            JSONObject p = new JSONObject();
            p.put("location", location != null ? location.toJSONObjectWithId() : JSONObject.NULL);
            p.put("speedKmh", speedKmh);
            p.put("limitKmh", limitKmh);
            sendEvent("speeding", p);
        } catch (JSONException e) { sendEvent("speeding"); }
    }

    @Override
    public void onProviderChange(String provider) {
        try {
            JSONObject p = new JSONObject();
            p.put("provider", provider != null ? provider : "");
            sendEvent("providerChange", p);
        } catch (JSONException e) { sendEvent("providerChange"); }
    }

    @Override
    public void onSOS(BackgroundLocation location, JSONObject userPayload) {
        try {
            JSONObject p = userPayload != null ? new JSONObject(userPayload.toString()) : new JSONObject();
            p.put("location", location != null ? location.toJSONObjectWithId() : JSONObject.NULL);
            sendEvent("sos", p);
        } catch (JSONException e) { sendEvent("sos"); }
    }

    private void sendLocationEvent(String name, BackgroundLocation location) {
        if (location == null) { sendEvent(name); return; }
        try { sendEvent(name, location.toJSONObjectWithId()); }
        catch (JSONException e) { sendEvent(name); }
    }

    // v4.1 GPS-derived sensor-like events
    @Override
    public void onHardBrake(BackgroundLocation location, double decelMps2) {
        sendDrivingEvent("hardBrake", location, decelMps2);
    }
    @Override
    public void onRapidAcceleration(BackgroundLocation location, double accelMps2) {
        sendDrivingEvent("rapidAcceleration", location, accelMps2);
    }
    @Override
    public void onSharpTurn(BackgroundLocation location, double degPerSec) {
        sendDrivingEvent("sharpTurn", location, degPerSec);
    }
    @Override
    public void onPossibleCrash(BackgroundLocation location, double velocityDropKmh) {
        sendDrivingEvent("possibleCrash", location, velocityDropKmh);
    }

    // v4.2 sensor fusion: enriched possibleCrash with `source` ("gps"|"sensor") and phone-usage event.
    @Override
    public void onPossibleCrash(BackgroundLocation location, double value, String source) {
        try {
            JSONObject p = new JSONObject();
            p.put("location", location != null ? location.toJSONObjectWithId() : JSONObject.NULL);
            p.put("value", value);
            p.put("source", source != null ? source : "gps");
            sendEvent("possibleCrash", p);
        } catch (JSONException e) {
            sendEvent("possibleCrash");
        }
    }
    @Override
    public void onPhoneUsageWhileDriving(BackgroundLocation location) {
        sendLocationEvent("phoneUsageWhileDriving", location);
    }

    private void sendDrivingEvent(String name, BackgroundLocation location, double value) {
        try {
            JSONObject p = new JSONObject();
            p.put("location", location != null ? location.toJSONObjectWithId() : JSONObject.NULL);
            p.put("value", value);
            sendEvent(name, p);
        } catch (JSONException e) {
            sendEvent(name);
        }
    }
}
