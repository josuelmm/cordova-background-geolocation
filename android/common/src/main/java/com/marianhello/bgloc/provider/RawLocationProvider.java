package com.marianhello.bgloc.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.marianhello.bgloc.Config;

import java.util.ArrayList;
import java.util.List;

public class RawLocationProvider extends AbstractLocationProvider implements LocationListener {
    private LocationManager locationManager;
    private boolean isStarted = false;
    // v4.5.4: providers we actively subscribed to (so we can unsubscribe cleanly).
    private final List<String> activeProviders = new ArrayList<>(2);

    // v5.0 — A8: whether providersChangedReceiver is currently registered. Unregistering a
    // receiver twice throws IllegalArgumentException, so registration must be idempotent.
    private boolean providersChangedRegistered = false;

    /**
     * v5.0 — A8: fires when the user toggles GPS / Network in system settings.
     *
     * <p>Needed because {@link #onProviderEnabled(String)} is a {@link LocationListener}
     * callback: the OS only delivers it to listeners that are actually registered with
     * {@link LocationManager}. When location was off at {@code onStart()} we never got to
     * {@code requestLocationUpdates(...)}, so nothing was registered and that callback could
     * never arrive — the service reported itself started and then never asked for a single fix,
     * even after the user turned location back on. This receiver is the only signal that reaches
     * us in that state.
     */
    private final BroadcastReceiver providersChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!activeProviders.isEmpty()) {
                return; // already subscribed; nothing to recover
            }
            logger.info("PROVIDERS_CHANGED received with no active provider, retrying subscription");
            subscribeToProviders();
        }
    };

    public RawLocationProvider(Context context) {
        super(context);
        PROVIDER_ID = Config.RAW_PROVIDER;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onStart() {
        if (isStarted) {
            return;
        }
        if (locationManager == null) {
            logger.error("LocationManager is null");
            return;
        }
        if (mConfig == null) {
            logger.warn("RawLocationProvider started without config");
            return;
        }
        subscribeToProviders();
    }

    /**
     * v4.5.4: honor desiredAccuracy and subscribe to all suitable providers simultaneously
     * (GPS + Network when available). Previously RAW only used GPS-or-Network and ignored
     * desiredAccuracy.
     *
     * <p>v5.0 — A8: extracted from {@code onStart()} so {@link #providersChangedReceiver} can
     * retry it. Calling {@code onStop(); onStart();} from the receiver would not work: with
     * {@code isStarted == false} (which is exactly the state we are recovering from) onStop()
     * early-returns and does nothing.
     */
    private void subscribeToProviders() {
        if (locationManager == null || mConfig == null) {
            return;
        }
        List<String> providers = pickProviders();
        if (providers.isEmpty()) {
            // Surface it instead of failing silently, and arm the system-settings receiver so we
            // recover when the user turns location back on. isStarted stays false.
            String msg = "No location provider available (GPS and Network disabled)";
            logger.warn(msg);
            registerProvidersChangedReceiver();
            handleServiceError(msg);
            return;
        }
        activeProviders.clear();
        for (String provider : providers) {
            try {
                logger.info("Requesting location updates from provider {}", provider);
                locationManager.requestLocationUpdates(provider, mConfig.getInterval(), mConfig.getDistanceFilter(), this);
                activeProviders.add(provider);
            } catch (SecurityException e) {
                logger.error("Security exception requesting {} updates: {}", provider, e.getMessage());
                this.handleSecurityException(e);
            } catch (IllegalArgumentException e) {
                logger.warn("requestLocationUpdates({}) failed: {}", provider, e.getMessage());
            }
        }
        isStarted = !activeProviders.isEmpty();
        if (isStarted) {
            // Subscribed: onProviderEnabled/onProviderDisabled now reach us, so the receiver is
            // redundant. Keep it armed while we have nothing, drop it as soon as we do.
            unregisterProvidersChangedReceiver();
        } else {
            registerProvidersChangedReceiver();
        }
    }

    /** v5.0 — A8: idempotent. Registration goes through the service's registerReceiver override,
     *  which adds RECEIVER_NOT_EXPORTED on API 33+ and the service HandlerThread. */
    private void registerProvidersChangedReceiver() {
        if (providersChangedRegistered) {
            return;
        }
        try {
            registerReceiver(providersChangedReceiver,
                    new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
            providersChangedRegistered = true;
            logger.debug("Armed PROVIDERS_CHANGED receiver (no active location provider)");
        } catch (Exception e) {
            logger.warn("Could not register PROVIDERS_CHANGED receiver: {}", e.getMessage());
        }
    }

    /** v5.0 — A8: idempotent counterpart. */
    private void unregisterProvidersChangedReceiver() {
        if (!providersChangedRegistered) {
            return;
        }
        providersChangedRegistered = false;
        try {
            unregisterReceiver(providersChangedReceiver);
        } catch (Exception ignored) {
            // already gone; never let teardown throw
        }
    }

    /**
     * v4.5.4: choose providers based on desiredAccuracy.
     * <ul>
     *   <li>&lt; 1000 m → include GPS when enabled (HIGH / BALANCED)</li>
     *   <li>≥ 10 m → include Network when enabled (covers indoor and quick fixes)</li>
     *   <li>≥ 1000 m → Network-only (LOW_POWER)</li>
     * </ul>
     * Falls back to whatever is enabled if the preferred set is empty.
     */
    private List<String> pickProviders() {
        List<String> result = new ArrayList<>(2);
        if (locationManager == null) return result;

        Integer da = mConfig != null ? mConfig.getDesiredAccuracy() : null;
        int desired = (da != null) ? da : 100; // default BALANCED

        boolean wantGps = desired < 1000;
        boolean wantNet = desired >= 10;

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean netEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (wantGps && gpsEnabled) result.add(LocationManager.GPS_PROVIDER);
        if (wantNet && netEnabled) result.add(LocationManager.NETWORK_PROVIDER);

        // Fallback: at least one of the available providers if our preferred set was empty.
        if (result.isEmpty()) {
            if (gpsEnabled) result.add(LocationManager.GPS_PROVIDER);
            else if (netEnabled) result.add(LocationManager.NETWORK_PROVIDER);
        }
        return result;
    }

    /** Backwards-compatible single-provider picker used by onProviderDisabled to check fallback. */
    private String pickProvider() {
        List<String> ps = pickProviders();
        return ps.isEmpty() ? null : ps.get(0);
    }

    @Override
    public void onStop() {
        // v5.0 — A8: drop the recovery receiver even when we never managed to subscribe
        // (isStarted == false is precisely the state that armed it). Before the early return,
        // otherwise stop() would leak a registered receiver for the life of the process.
        unregisterProvidersChangedReceiver();
        if (!isStarted) {
            return;
        }
        try {
            // v4.5.4: removeUpdates(this) detaches us from every provider we
            // subscribed to via the same LocationListener.
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        } finally {
            activeProviders.clear();
            isStarted = false;
        }
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        if (isStarted) {
            onStop();
            onStart();
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void onLocationChanged(Location location) {
        logger.debug("Location change: {}", location.toString());

        showDebugToast("acy:" + location.getAccuracy() + ",v:" + location.getSpeed());
        handleLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle bundle) {
        logger.debug("Provider {} status changed: {}", provider, status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        logger.debug("Provider {} was enabled", provider);
        // Re-subscribe if we came up with no providers at all (location was off at onStart).
        // Without this the service reported itself started, published MSG_ON_SERVICE_STARTED,
        // and then never requested a single fix — even after the user turned location back on.
        if (activeProviders.isEmpty()) {
            logger.info("Provider {} became available, re-subscribing", provider);
            // v5.0 — A8: subscribeToProviders() instead of onStop()+onStart(): with isStarted
            // false onStop() early-returns and onStart() would just repeat the same work.
            subscribeToProviders();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        logger.warn("Provider {} was disabled", provider);
        // v4.5.4: emit SERVICE error when no fallback provider is available so
        // the JS layer can re-prompt the user. Matches DISTANCE_FILTER provider
        // behavior.
        if (locationManager != null && pickProvider() == null) {
            handleServiceError("Location provider '" + provider + "' disabled and no fallback available.");
        }
    }

    @Override
    public void onDestroy() {
        logger.debug("Destroying RawLocationProvider");
        this.onStop();
        super.onDestroy();
    }
}
