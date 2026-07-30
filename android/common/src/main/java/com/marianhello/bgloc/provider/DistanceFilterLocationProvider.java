package com.marianhello.bgloc.provider;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.marianhello.bgloc.Config;
import com.marianhello.utils.ToneGenerator.Tone;

import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.round;


/**
 * v4.5.4 — Distance-filter provider with a runtime-chosen backend:
 * <ul>
 *   <li><b>Fused path</b> (Play Services available): {@link FusedLocationProviderClient}
 *       + {@link LocationCallback}. Better fused GPS+Network blending and battery.</li>
 *   <li><b>Legacy path</b> (Play Services missing — Huawei/HMS, AOSP, China ROMs):
 *       {@link android.location.LocationManager} + {@link LocationListener}. Preserves the
 *       original DISTANCE_FILTER behavior so the plugin works on every Android device.</li>
 * </ul>
 *
 * Stationary detection in both paths relies on the existing alarm-driven polling
 * (no geofencing / proximity alerts), per product decision.
 */
public class DistanceFilterLocationProvider extends AbstractLocationProvider implements LocationListener {

    private static final String TAG = DistanceFilterLocationProvider.class.getSimpleName();
    private static final String P_NAME = "com.marianhello.bgloc";

    private static final String STATIONARY_ALARM_ACTION             = P_NAME + ".STATIONARY_ALARM_ACTION";
    private static final String SINGLE_LOCATION_UPDATE_ACTION       = P_NAME + ".SINGLE_LOCATION_UPDATE_ACTION";
    private static final String STATIONARY_LOCATION_MONITOR_ACTION  = P_NAME + ".STATIONARY_LOCATION_MONITOR_ACTION";

    // v4.5.1: defaults — overridable per-config via config.stationaryTimeout / stationaryPollInterval / stationaryPollFast
    private static final long DEFAULT_STATIONARY_TIMEOUT                                = 5 * 1000 * 60;
    private static final long DEFAULT_STATIONARY_LOCATION_POLLING_INTERVAL_LAZY         = 3 * 1000 * 60;
    private static final long DEFAULT_STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE   = 1 * 1000 * 60;
    private static final int MAX_STATIONARY_ACQUISITION_ATTEMPTS = 5;
    private static final int MAX_SPEED_ACQUISITION_ATTEMPTS = 3;

    // v4.5.4 — Aggressive interval used while acquiring stationary location or speed (FLP path).
    private static final long ACQUISITION_INTERVAL_MS = 1000L;

    private volatile Boolean isMoving = false;
    // volatile: the stationary alarm / polling receivers are delivered on the service's
    // HandlerThread (LocationServiceImpl.registerReceiver passes mServiceHandler), while
    // onStart/onStop/onConfigure/handleNewLocation run on the main thread. Without a memory
    // barrier the two threads could see different halves of this state machine — e.g. stay in
    // acquisition mode forever, or subscribe to the FLP twice.
    private volatile Boolean isAcquiringStationaryLocation = false;
    private volatile Boolean isAcquiringSpeed = false;
    private volatile Integer locationAcquisitionAttempts = 0;

    private volatile Location lastLocation;
    private volatile Location stationaryLocation;
    private volatile float stationaryRadius;
    private PendingIntent stationaryAlarmPI;
    private PendingIntent stationaryLocationPollingPI;
    private PendingIntent singleUpdatePI;          // legacy path only
    private volatile long stationaryLocationPollingInterval;

    private volatile Integer scaledDistanceFilter;

    /**
     * v5.0 — A16: {@code volatile} above only buys visibility. Every transition here is a
     * read-modify-write over several fields at once, and they run on different threads: the
     * stationary alarm / polling receivers are delivered on the service's HandlerThread
     * (see {@code LocationServiceImpl.registerReceiver}), while {@code onStart}/{@code onStop}/
     * {@code onCommand}/{@code onConfigure} and the location callbacks run on the main thread.
     * {@code StationaryAlarmReceiver} → {@code setPace(false)} interleaving with
     * {@code setPace(true)} from {@code onCommand} could leave {@code isMoving == true} AND
     * {@code isAcquiringStationaryLocation == true}, or two live location subscriptions.
     *
     * <p>A dedicated lock, not {@code this}: the service and the receivers both hold references
     * to this provider, so an external {@code synchronized (provider)} would join this lock's
     * ordering and could deadlock.
     *
     * <p><b>Invariant:</b> never call out to the delegate while holding it —
     * {@code handleLocation}, {@code handleStationary}, {@code handleServiceError} and
     * {@code handleSecurityException} reach {@code LocationServiceImpl}, whose {@code stop()} /
     * {@code configure()} are {@code synchronized} on the service and call back into the
     * provider. Holding both in opposite orders is a textbook inversion. Compute under the lock,
     * release, then report. {@code playDebugTone}/{@code showDebugToast} never touch the
     * delegate, so they may stay inside.
     */
    private final Object mStateLock = new Object();

    private FusedLocationProviderClient fusedClient;  // null on the legacy path
    private LocationManager locationManager;           // always non-null after onCreate
    private AlarmManager alarmManager;
    private boolean usingFused = false;

    private volatile boolean isStarted = false;

    /** v4.5.1: read overrides from {@link com.marianhello.bgloc.Config}; fall back to defaults. */
    private long getStationaryTimeout() {
        Integer v = mConfig != null ? mConfig.getStationaryTimeout() : null;
        return v != null ? v.longValue() : DEFAULT_STATIONARY_TIMEOUT;
    }
    private long getStationaryPollLazy() {
        Integer v = mConfig != null ? mConfig.getStationaryPollInterval() : null;
        return v != null ? v.longValue() : DEFAULT_STATIONARY_LOCATION_POLLING_INTERVAL_LAZY;
    }
    private long getStationaryPollFast() {
        Integer v = mConfig != null ? mConfig.getStationaryPollFast() : null;
        return v != null ? v.longValue() : DEFAULT_STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE;
    }

    public DistanceFilterLocationProvider(Context context) {
        super(context);
        PROVIDER_ID = Config.DISTANCE_FILTER_PROVIDER;
    }

    // ====== FLP path callbacks ======

    private final LocationCallback fusedCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (result == null) return;
            List<Location> locations = result.getLocations();
            if (locations == null) return;
            for (Location location : locations) {
                handleNewLocation(location);
            }
        }
    };

    /** FLP one-shot callback used by the stationary polling alarm. */
    private final LocationCallback fusedPollCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (result == null || fusedClient == null) return;
            try {
                fusedClient.removeLocationUpdates(this);
            } catch (Exception ignored) { /* fire-and-forget */ }
            Location loc = result.getLastLocation();
            if (loc != null) {
                logger.debug("Stationary monitor single update: {}", loc);
                onPollStationaryLocation(loc);
            }
        }
    };

    // ====== Lifecycle ======

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        // v4.5.4 — pick the location backend at runtime. Play Services missing
        // (Huawei/HMS, AOSP, China ROMs) → use the OS LocationManager so the
        // provider still works.
        int gps = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
        usingFused = (gps == ConnectionResult.SUCCESS);
        if (usingFused) {
            fusedClient = LocationServices.getFusedLocationProviderClient(mContext);
            logger.info("DISTANCE_FILTER_PROVIDER using FusedLocationProviderClient (Play Services available).");
        } else {
            logger.info("DISTANCE_FILTER_PROVIDER falling back to LocationManager (Play Services unavailable, code={}).", gps);
        }

        int updateCurrentFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        // v4.5.4: singleUpdatePI must be MUTABLE on API 31+ because
        // LocationManager.requestSingleUpdate() fills the resulting Location
        // into the intent's extras at delivery time. FLAG_IMMUTABLE blocks that
        // population, so the receiver would never see the fix.
        int singleUpdateFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE
                : PendingIntent.FLAG_CANCEL_CURRENT;

        // IMPORTANT: these intents must stay *implicit* (action-only, restricted to our own
        // package). The receivers below are context-registered inner classes that are not, and
        // must not be, declared in the manifest. ActivityManager only matches context-registered
        // receivers when the broadcast carries no explicit component, so targeting the receiver
        // class here (`new Intent(mContext, XReceiver.class)`) made Android drop every one of
        // these broadcasts silently: the stationary alarm never fired, the provider stopped
        // requesting locations after entering the stationary state, and tracking died for good.
        // setPackage() keeps the broadcast internal, which is also what API 34+ requires to
        // build a PendingIntent from an implicit intent.
        String pkg = mContext.getPackageName();

        Intent stationaryAlarmIntent = new Intent(STATIONARY_ALARM_ACTION).setPackage(pkg);
        stationaryAlarmPI = PendingIntent.getBroadcast(mContext, 9000, stationaryAlarmIntent, updateCurrentFlag);
        registerReceiver(stationaryAlarmReceiver, new IntentFilter(STATIONARY_ALARM_ACTION));

        Intent stationaryLocationMonitorIntent = new Intent(STATIONARY_LOCATION_MONITOR_ACTION).setPackage(pkg);
        stationaryLocationPollingPI = PendingIntent.getBroadcast(mContext, 9002, stationaryLocationMonitorIntent, updateCurrentFlag);
        registerReceiver(stationaryLocationMonitorReceiver, new IntentFilter(STATIONARY_LOCATION_MONITOR_ACTION));

        // Legacy single-update PI + receiver (only used when usingFused == false).
        if (!usingFused) {
            Intent singleLocationUpdateIntent = new Intent(SINGLE_LOCATION_UPDATE_ACTION).setPackage(pkg);
            singleUpdatePI = PendingIntent.getBroadcast(mContext, 9003, singleLocationUpdateIntent, singleUpdateFlag);
            registerReceiver(singleUpdateReceiver, new IntentFilter(SINGLE_LOCATION_UPDATE_ACTION));
        }
    }

    @Override
    public void onStart() {
        // v5.0 — A16: serialized against the receivers' setPace() (see mStateLock).
        synchronized (mStateLock) {
            if (isStarted) {
                return;
            }
            if (locationManager == null) {
                logger.error("LocationManager is null");
                return;
            }
            if (alarmManager == null) {
                logger.error("AlarmManager is null");
                return;
            }
            if (mConfig == null) {
                logger.warn("DistanceFilterLocationProvider started without config");
                return;
            }

            logger.info("Start recording (path={})", usingFused ? "fused" : "legacy");
            scaledDistanceFilter = mConfig.getDistanceFilter();
            isStarted = true;
        }
        // setPace() takes the lock itself and reports errors after releasing it.
        setPace(false);
    }

    @Override
    public void onStop() {
        // v5.0 — A16: serialized; otherwise a stationary alarm arriving mid-stop could
        // re-subscribe to the FLP right after unsubscribeLocationUpdates().
        synchronized (mStateLock) {
            if (!isStarted) {
                return;
            }
            try {
                unsubscribeLocationUpdates();
                if (alarmManager != null) {
                    if (stationaryAlarmPI != null) alarmManager.cancel(stationaryAlarmPI);
                    if (stationaryLocationPollingPI != null) alarmManager.cancel(stationaryLocationPollingPI);
                }
            } catch (SecurityException ignored) {
            } finally {
                isStarted = false;
            }
        }
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        switch(commandId) {
            case CMD_SWITCH_MODE:
                setPace(arg1 == BACKGROUND_MODE ? false : true);
                return;
        }
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        // v5.0 — A16: deliberately NOT holding mStateLock here. onStop() and onStart() are each
        // atomic on their own, and onStart() ends in setPace(), which reports errors to the
        // delegate — wrapping the pair would hold the lock across that call-out and reopen the
        // lock-order inversion documented on mStateLock.
        if (isStarted) {
            onStop();
            onStart();
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /** GPS first, fall back to Network — used only by the legacy LocationManager path. */
    private String pickProvider() {
        if (locationManager == null) return null;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        return null;
    }

    /** Cheap check used to decide whether to emit SERVICE_ERROR when subscribing. */
    private boolean anyProviderEnabled() {
        if (locationManager == null) return true;
        boolean gpsOn = false, netOn = false;
        try { gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) { /* may throw on devices with no GPS hardware */ }
        try { netOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
        catch (Exception ignored) { }
        return gpsOn || netOn;
    }

    /** Translate the plugin's desiredAccuracy buckets into FLP priorities (FLP path only). */
    private int translatePriority(Integer accuracy) {
        if (accuracy == null) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        if (accuracy >= 10000) {
            return Priority.PRIORITY_PASSIVE;
        }
        if (accuracy >= 1000) {
            return Priority.PRIORITY_LOW_POWER;
        }
        if (accuracy >= 100) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        return Priority.PRIORITY_HIGH_ACCURACY;
    }

    private void unsubscribeLocationUpdates() {
        try {
            if (usingFused && fusedClient != null) {
                fusedClient.removeLocationUpdates(fusedCallback);
                fusedClient.removeLocationUpdates(fusedPollCallback);
            }
            if (locationManager != null) {
                locationManager.removeUpdates(this);
            }
        } catch (SecurityException ignored) {
        }
    }

    /**
     * @param value true → aggressive moving tracking, false → stationary monitoring
     */
    private void setPace(Boolean value) {
        // v5.0 — A16: the whole transition is one atomic unit. Two concurrent setPace() calls
        // (receiver thread vs main thread) used to interleave between the field writes and the
        // unsubscribe/subscribe pair, which could leave isMoving == true together with
        // isAcquiringStationaryLocation == true, or two live subscriptions.
        // Delegate call-outs are collected here and performed after the lock is released.
        boolean noProviderAvailable = false;
        SecurityException pendingSecurityException = null;

        synchronized (mStateLock) {
            if (!isStarted) {
                return;
            }
            if (mConfig == null) {
                return;
            }

            logger.info("Setting pace: {}", value);

            Boolean wasMoving                = isMoving;
            isMoving                         = value;
            isAcquiringStationaryLocation    = false;
            isAcquiringSpeed                 = false;
            stationaryLocation               = null;

            try {
                unsubscribeLocationUpdates();

                if (isMoving) {
                    if (!wasMoving) {
                        isAcquiringSpeed = true;
                    }
                } else {
                    isAcquiringStationaryLocation = true;
                }

                noProviderAvailable = !anyProviderEnabled();

                if (usingFused) {
                    subscribeFused();
                } else {
                    subscribeLegacy();
                }
            } catch (SecurityException e) {
                logger.error("Security exception: {}", e.getMessage());
                pendingSecurityException = e;
            }
        }

        if (noProviderAvailable) {
            handleServiceError("No location provider available (GPS and Network disabled).");
        }
        if (pendingSecurityException != null) {
            this.handleSecurityException(pendingSecurityException);
        }
    }

    private void subscribeFused() {
        if (fusedClient == null) return;
        LocationRequest request;
        if (isAcquiringSpeed || isAcquiringStationaryLocation) {
            locationAcquisitionAttempts = 0;
            request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ACQUISITION_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(ACQUISITION_INTERVAL_MS)
                    .setWaitForAccurateLocation(false)
                    .build();
        } else {
            int priority = translatePriority(mConfig.getDesiredAccuracy());
            long interval = mConfig.getInterval();
            LocationRequest.Builder b = new LocationRequest.Builder(priority, interval)
                    .setMinUpdateIntervalMillis(Math.min(interval, 1000L))
                    .setWaitForAccurateLocation(false);
            if (scaledDistanceFilter != null && scaledDistanceFilter > 0) {
                b.setMinUpdateDistanceMeters(scaledDistanceFilter.floatValue());
            }
            request = b.build();
        }
        // v5.0 — A16: let SecurityException propagate to setPace(), which reports it to the
        // delegate AFTER releasing mStateLock. Reporting from here would do it under the lock.
        fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper());
    }

    private void subscribeLegacy() {
        if (locationManager == null) return;
        // v5.0 — A16: SecurityException propagates to setPace(), which reports it to the delegate
        // after releasing mStateLock (see the note on that field).
        {
            if (isAcquiringSpeed || isAcquiringStationaryLocation) {
                locationAcquisitionAttempts = 0;
                // Burst: subscribe to every non-passive provider for fastest lock.
                List<String> matchingProviders = locationManager.getAllProviders();
                for (String provider : matchingProviders) {
                    if (!LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                        logger.info("Requesting location updates from provider {}", provider);
                        locationManager.requestLocationUpdates(provider, 0, 0, this);
                    }
                }
            } else {
                // v4.5.4 — subscribe to GPS AND Network simultaneously when both
                // are available. The previous version only used GPS-or-Network
                // (excluyente), which on cheap/vehicular Androids could leave the
                // app waiting for a GPS fix while a quick Network fix was available.
                boolean gpsOn = false, netOn = false;
                try { gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
                catch (Exception ignored) { }
                try { netOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
                catch (Exception ignored) { }
                if (!gpsOn && !netOn) {
                    logger.warn("No location provider available (GPS and Network disabled)");
                    return;
                }
                long interval = mConfig.getInterval();
                int distance = scaledDistanceFilter != null ? scaledDistanceFilter : 0;
                if (gpsOn) {
                    logger.info("Requesting location updates from GPS");
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, distance, this);
                }
                if (netOn) {
                    logger.info("Requesting location updates from Network");
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, interval, distance, this);
                }
            }
        }
    }

    // ====== LocationListener (legacy path) ======

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        logger.debug("Provider {} status changed: {}", provider, status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        logger.debug("Provider {} was enabled", provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        logger.warn("Provider {} was disabled", provider);
        // v4.5.4: surface as an error so JS layer can prompt the user to enable
        // location services. Only when no fallback provider is left.
        if (locationManager != null && pickProvider() == null) {
            handleServiceError("Location provider '" + provider + "' disabled and no fallback available.");
        }
    }

    // ====== State machine (path-agnostic) ======

    /**
     * Same logic as the legacy {@code onLocationChanged}: handles the moving / stationary /
     * acquisition state machine. Called from both the FLP callback and the LocationListener.
     */
    private void handleNewLocation(Location location) {
        if (location == null) return;

        // v5.0 — A16: the state machine runs under mStateLock so it cannot interleave with a
        // stationary alarm arriving on the service HandlerThread. The two delegate call-outs
        // (handleStationary / handleLocation) are deferred to after the lock is released; see the
        // note on mStateLock for why holding it across them would risk a deadlock.
        Location stationaryToReport = null;
        float stationaryRadiusToReport = 0f;
        Location locationToReport = null;
        SecurityException pendingSecurityException = null;

        synchronized (mStateLock) {
            logger.debug("Location change: {} isMoving={}", location.toString(), isMoving);

            if (!isMoving && !isAcquiringStationaryLocation && stationaryLocation==null) {
                // Perhaps our GPS signal was interrupted, re-acquire a stationary location now.
                // Reentrant: same thread, same lock.
                setPace(false);
            }

            showDebugToast("mv:" + isMoving + ",acy:" + location.getAccuracy() + ",v:" + location.getSpeed() + ",df:" + scaledDistanceFilter);

            // do/while(false): preserves the original method's early `return`s as `break`s so the
            // deferred call-outs below still run. Behaviour is unchanged.
            do {
                if (isAcquiringStationaryLocation) {
                    if (stationaryLocation == null || stationaryLocation.getAccuracy() > location.getAccuracy()) {
                        stationaryLocation = location;
                    }
                    if (++locationAcquisitionAttempts == MAX_STATIONARY_ACQUISITION_ATTEMPTS) {
                        isAcquiringStationaryLocation = false;
                        pendingSecurityException = enterStationary(stationaryLocation);
                        stationaryToReport = stationaryLocation;
                        stationaryRadiusToReport = stationaryRadius;
                    } else {
                        playDebugTone(Tone.BEEP);
                    }
                    break;
                } else if (isAcquiringSpeed) {
                    if (++locationAcquisitionAttempts == MAX_SPEED_ACQUISITION_ATTEMPTS) {
                        playDebugTone(Tone.DOODLY_DOO);
                        isAcquiringSpeed = false;
                        scaledDistanceFilter = calculateDistanceFilter(location.getSpeed());
                        setPace(true);
                    } else {
                        playDebugTone(Tone.BEEP);
                        break;
                    }
                } else if (isMoving) {
                    playDebugTone(Tone.BEEP);

                    if ((location.getSpeed() >= 1) && (location.getAccuracy() <= mConfig.getStationaryRadius())) {
                        resetStationaryAlarm();
                    }
                    Integer newDistanceFilter = calculateDistanceFilter(location.getSpeed());
                    if (newDistanceFilter != scaledDistanceFilter.intValue()) {
                        logger.info("Updating distanceFilter: new={} old={}", newDistanceFilter, scaledDistanceFilter);
                        scaledDistanceFilter = newDistanceFilter;
                        setPace(true);
                    }
                    if (lastLocation != null && location.distanceTo(lastLocation) < mConfig.getDistanceFilter()) {
                        break;
                    }
                } else if (stationaryLocation != null) {
                    break;
                }
                lastLocation = location;
                locationToReport = location;
            } while (false);
        }

        if (stationaryToReport != null) {
            handleStationary(stationaryToReport, stationaryRadiusToReport);
        }
        if (locationToReport != null) {
            handleLocation(locationToReport);
        }
        if (pendingSecurityException != null) {
            this.handleSecurityException(pendingSecurityException);
        }
    }

    public void resetStationaryAlarm() {
        if (alarmManager == null || stationaryAlarmPI == null) return;
        alarmManager.cancel(stationaryAlarmPI);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + getStationaryTimeout(), stationaryAlarmPI);
    }

    private Integer calculateDistanceFilter(Float speed) {
        Double newDistanceFilter = (double) mConfig.getDistanceFilter();
        if (speed < 100) {
            float roundedDistanceFilter = (round(speed / 5) * 5);
            newDistanceFilter = pow(roundedDistanceFilter, 2) + (double) mConfig.getDistanceFilter();
        }
        return (newDistanceFilter.intValue() < 1000) ? newDistanceFilter.intValue() : 1000;
    }

    /**
     * v4.5.4 — Stop active updates and start the polling-based stationary monitor.
     * The previous version also called {@code addProximityAlert} (geofence); that
     * path has been removed per product decision (no geofencing).
     */
    private SecurityException enterStationary(Location location) {
        if (location == null || mConfig == null) return null;
        try {
            unsubscribeLocationUpdates();

            float radius = mConfig.getStationaryRadius();
            float proximityRadius = (location.getAccuracy() < radius) ? radius : location.getAccuracy();
            stationaryLocation = location;
            this.stationaryRadius = proximityRadius;

            logger.info("enterStationary: lat={} lon={} acy={} radius={}", location.getLatitude(), location.getLongitude(), location.getAccuracy(), proximityRadius);

            startPollingStationaryLocation(getStationaryPollLazy());
        } catch (SecurityException e) {
            // v5.0 — A16: the caller (handleNewLocation) holds mStateLock; return the exception
            // so it is reported to the delegate only after the lock is released.
            logger.error("Security exception: {}", e.getMessage());
            return e;
        }
        return null;
    }

    /**
     * Engage aggressive geolocation after stationary exit.
     *
     * <p>v5.0 — A16: deliberately NOT holding {@link #mStateLock}. It only cancels an alarm and
     * delegates the whole transition to {@code setPace(true)}, which is atomic on its own and
     * reports errors after releasing the lock. Wrapping this method would hold the lock across
     * that call-out.
     */
    public void onExitStationaryRegion(Location location) {
        if (location == null || alarmManager == null) return;

        playDebugTone(Tone.BEEP_BEEP_BEEP);

        logger.info("Exited stationary: lat={} long={} acy={}", location.getLatitude(), location.getLongitude(), location.getAccuracy());

        try {
            alarmManager.cancel(stationaryLocationPollingPI);
            this.setPace(true);
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    public void startPollingStationaryLocation(long interval) {
        if (alarmManager == null || stationaryLocationPollingPI == null) return;
        stationaryLocationPollingInterval = interval;
        alarmManager.cancel(stationaryLocationPollingPI);
        long start = System.currentTimeMillis() + 60_000;
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, start, interval, stationaryLocationPollingPI);
    }

    public void onPollStationaryLocation(Location location) {
        if (location == null || mConfig == null) return;

        float radius = mConfig.getStationaryRadius();
        if (isMoving) {
            return;
        }
        playDebugTone(Tone.BEEP);

        float distance = 0.0f;
        if (stationaryLocation != null) {
            distance = abs(location.distanceTo(stationaryLocation) - stationaryLocation.getAccuracy() - location.getAccuracy());
        }

        showDebugToast("Stationary exit in " + (radius - distance) + "m");

        logger.info("Distance from stationary location: {}", distance);
        if (distance > radius) {
            onExitStationaryRegion(location);
        } else if (distance > 0) {
            startPollingStationaryLocation(getStationaryPollFast());
        } else if (stationaryLocationPollingInterval != getStationaryPollLazy()) {
            startPollingStationaryLocation(getStationaryPollLazy());
        }
    }

    // ====== Receivers ======

    private class StationaryAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.info("stationaryAlarm fired");
            setPace(false);
        }
    }
    private BroadcastReceiver stationaryAlarmReceiver = new StationaryAlarmReceiver();

    /**
     * Triggered by the inexact repeating alarm to poll a single fresh location while
     * inside the stationary region. Uses FLP one-shot when available, otherwise falls
     * back to {@link LocationManager#requestSingleUpdate(String, PendingIntent)}.
     */
    private class StationaryLocationMonitorReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.info("Stationary location monitor fired");
            playDebugTone(Tone.DIALTONE);

            if (usingFused && fusedClient != null) {
                LocationRequest oneShot = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
                        .setMaxUpdates(1)
                        .setWaitForAccurateLocation(false)
                        .build();
                try {
                    fusedClient.requestLocationUpdates(oneShot, fusedPollCallback, Looper.getMainLooper());
                } catch (SecurityException e) {
                    logger.error("Security exception (FLP one-shot): {}", e.getMessage());
                } catch (IllegalArgumentException e) {
                    logger.warn("FLP one-shot failed: {}", e.getMessage());
                }
            } else if (locationManager != null && singleUpdatePI != null) {
                String provider = pickProvider();
                if (provider == null) {
                    logger.warn("Stationary monitor: no provider available");
                    return;
                }
                try {
                    locationManager.requestSingleUpdate(provider, singleUpdatePI);
                } catch (SecurityException e) {
                    logger.error("Security exception (single update): {}", e.getMessage());
                } catch (IllegalArgumentException e) {
                    logger.warn("requestSingleUpdate failed: {}", e.getMessage());
                }
            }
        }
    }
    private BroadcastReceiver stationaryLocationMonitorReceiver = new StationaryLocationMonitorReceiver();

    /** Legacy single-update receiver — feeds {@link #onPollStationaryLocation(Location)}. */
    private class SingleUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            Location location = extras.getParcelable(LocationManager.KEY_LOCATION_CHANGED);
            if (location != null) {
                logger.debug("Single location update: {}", location);
                onPollStationaryLocation(location);
            }
        }
    }
    private BroadcastReceiver singleUpdateReceiver = new SingleUpdateReceiver();

    @Override
    public void onDestroy() {
        logger.info("Destroying DistanceFilterLocationProvider");

        this.onStop();
        if (alarmManager != null) {
            if (stationaryAlarmPI != null) alarmManager.cancel(stationaryAlarmPI);
            if (stationaryLocationPollingPI != null) alarmManager.cancel(stationaryLocationPollingPI);
        }

        try { unregisterReceiver(stationaryAlarmReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(stationaryLocationMonitorReceiver); } catch (Exception ignored) { }
        if (!usingFused) {
            try { unregisterReceiver(singleUpdateReceiver); } catch (Exception ignored) { }
        }

        super.onDestroy();
    }
}
