package com.marianhello.bgloc.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;

import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * v4.2 Phase 8 — Real sensor fusion detector.
 *
 * Samples linear acceleration (TYPE_LINEAR_ACCELERATION, gravity removed) at 20 Hz and
 * gyroscope (TYPE_GYROSCOPE) at SENSOR_DELAY_UI (~15 Hz), so ~35 callbacks/s combined.
 * Those callbacks run on a dedicated {@link HandlerThread}, never on the main looper.
 * See {@link #ACCEL_SAMPLING_PERIOD_US} for why the two channels differ.
 *
 * Sensors are only registered while a trip is active ({@link #setTripActive}), since
 * every event this class emits is gated on {@code tripActive} anyway. Parked, the
 * detector costs nothing.
 *
 * Crash detection: |a| (m/s²) above {@code crashImpactG} g during a tripActive window,
 * <em>corroborated by GPS</em>, emits {@link Listener#onSensorCrash}. A phone falling off
 * its mount easily exceeds 3 g, so a bare accelerometer spike is not enough: the last known
 * location must show a low speed (the vehicle actually stopped) or a significant recent speed
 * drop. Combined with the GPS-derived heuristic in
 * {@link com.marianhello.bgloc.driving.DrivingEventsDetector}, this gives a higher-confidence
 * signal at low speeds (parking-lot impact) where GPS alone misses.
 *
 * phoneUsageWhileDriving: while {@code tripActive} is true, if the screen is on and the device
 * shows sustained hand-shaped jitter — rotation <em>and</em> translation together — for a whole
 * window, fires {@link Listener#onPhoneUsageWhileDriving}. Conservative — designed to avoid
 * false-positives from road vibration, passenger usage and normal vehicle dynamics.
 */
public class SensorFusionDetector implements SensorEventListener {

    public interface Listener {
        /** Triggered when |a| exceeds crashImpactG while tripActive and GPS corroborates. */
        void onSensorCrash(BackgroundLocation lastLocation, double impactG);
        /** Screen on + sustained device interaction during tripActive. */
        void onPhoneUsageWhileDriving(BackgroundLocation lastLocation);
    }

    public static class Config {
        public boolean enabled = false;
        /** Crash threshold in g. 1g = 9.81 m/s². Default 3g. */
        public double crashImpactG = 3.0;
        /** Cooldown between repeated crash detections. */
        public long   crashCooldownMs = 10_000;
        /** Min sustained gyro+accel jitter window for phoneUsage. */
        public long   phoneUsageWindowMs = 4_000;
        /** Cooldown between repeated phone-usage events. */
        public long   phoneUsageCooldownMs = 60_000;
    }

    private static final float G = 9.80665f;

    private final Context appContext;
    private final Listener listener;
    private final SensorManager sensorManager;
    private final Sensor linearAccel;
    private final Sensor gyroscope;
    private final PowerManager powerManager;

    private Config cfg = new Config();
    private boolean started = false;
    private boolean registered = false;
    private boolean tripActive = false;
    private BackgroundLocation lastLocation;

    private HandlerThread sensorThread;
    private Handler sensorHandler;

    /** "Never fired" — elapsedRealtime() is 0 at boot, so 0 is not a usable sentinel. */
    private static final long NEVER = Long.MIN_VALUE / 4;

    // Written from the sensor thread, read from callers of stop()/setTripActive().
    private volatile long lastCrashAt = NEVER;
    private volatile long lastPhoneUsageAt = NEVER;
    private volatile long jitterAboveSince = 0L;
    /** Latest sample of each channel, so the AND below can look at both at once. */
    private volatile double lastAccelMag = -1;
    private volatile double lastGyroMag = -1;

    // GPS corroboration for crash detection.
    private volatile double lastSpeedMps = -1;
    private volatile double recentPeakSpeedMps = -1;
    private volatile long recentPeakAt = 0L;

    // phoneUsage thresholds.
    private static final double JITTER_GYRO_RAD_S = 0.7;     // ~40 deg/s of rotation
    /**
     * 2.0 m/s² of linear (gravity-removed) acceleration. The old 0.5 m/s² sat *below* the
     * acceleration of a car pulling away from a light (~1-2 m/s²), so with the old OR against
     * the gyroscope this fired once every cooldown for the whole trip with nobody touching the
     * phone. Requiring rotation AND translation together, both sustained across the window,
     * is what distinguishes a hand holding the device from the vehicle's own motion: driving
     * produces translation with almost no device rotation, road vibration produces neither
     * sustained.
     */
    private static final double JITTER_ACCEL_MPS2 = 2.0;
    /** How long a sensor sample stays valid for the AND correlation. */
    private static final long JITTER_SAMPLE_TTL_MS = 500L;
    /** Speed below which an impact is considered corroborated (vehicle stopped). */
    private static final double CRASH_CORROBORATION_SPEED_MPS = 3.0;
    /** Or: a speed drop of at least this much within the corroboration window. */
    private static final double CRASH_CORROBORATION_DROP_MPS = 5.0;
    private static final long   CRASH_CORROBORATION_WINDOW_MS = 15_000L;

    private volatile long lastAccelAt = 0L;
    private volatile long lastGyroAt = 0L;

    /**
     * v5.0 — A14: 50_000 us = 20 Hz for the accelerometer, down from SENSOR_DELAY_GAME (~50 Hz).
     *
     * <p>Not SENSOR_DELAY_UI (~15 Hz) like the gyroscope: this channel feeds the crash detector,
     * which compares the *instantaneous* magnitude of a single sample against crashImpactG. An
     * impact pulse lasts on the order of 100 ms, so at 66 ms per sample it can be sampled off its
     * peak and read below threshold. 50 ms keeps at least two samples inside a pulse while still
     * halving the callback rate.
     */
    private static final int ACCEL_SAMPLING_PERIOD_US = 50_000;

    public SensorFusionDetector(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        this.linearAccel = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) : null;
        this.gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        this.powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
    }

    public synchronized void setConfig(Config c) {
        if (c != null) this.cfg = c;
    }

    public synchronized boolean isAvailable() {
        return sensorManager != null && linearAccel != null;
    }

    /**
     * Arm the detector. Idempotent. Sensors are not registered here — that happens in
     * {@link #setTripActive(boolean)}, so a parked vehicle costs no sampling.
     */
    public synchronized void start() {
        if (started || !cfg.enabled || sensorManager == null) return;
        started = true;
        if (tripActive) registerSensors();
    }

    /** Stop sampling and tear down the sampling thread. Idempotent. */
    public synchronized void stop() {
        if (!started) return;
        unregisterSensors();
        started = false;
        jitterAboveSince = 0L;
        lastAccelMag = lastGyroMag = -1;
        lastAccelAt = lastGyroAt = 0L;
    }

    /** Called by detector host whenever the GPS layer marks tripActive on/off. */
    public synchronized void setTripActive(boolean active) {
        this.tripActive = active;
        if (!active) {
            jitterAboveSince = 0L;
            lastAccelMag = lastGyroMag = -1;
            lastAccelAt = lastGyroAt = 0L;
            unregisterSensors();
        } else if (started) {
            registerSensors();
        }
    }

    /** Last known location for event payload and for GPS corroboration of impacts. */
    public synchronized void setLastLocation(BackgroundLocation loc) {
        this.lastLocation = loc;
        if (loc == null || !loc.hasSpeed()) return;
        double s = loc.getSpeed();
        long now = SystemClock.elapsedRealtime();
        if (recentPeakSpeedMps < 0
                || s >= recentPeakSpeedMps
                || (now - recentPeakAt) > CRASH_CORROBORATION_WINDOW_MS) {
            recentPeakSpeedMps = s;
            recentPeakAt = now;
        }
        lastSpeedMps = s;
    }

    // -- sensor registration ------------------------------------------------------------

    private void registerSensors() {
        if (registered || sensorManager == null || !cfg.enabled) return;
        if (sensorThread == null) {
            sensorThread = new HandlerThread("bgloc-sensor-fusion");
            sensorThread.start();
            sensorHandler = new Handler(sensorThread.getLooper());
        }
        if (linearAccel != null) {
            sensorManager.registerListener(this, linearAccel, ACCEL_SAMPLING_PERIOD_US, sensorHandler);
        }
        if (gyroscope != null) {
            // v5.0 — A14: the gyroscope only feeds evaluatePhoneUsage(), which needs the channel
            // to be *fresh* within JITTER_SAMPLE_TTL_MS (500 ms) and sustained across
            // phoneUsageWindowMs (4 s). SENSOR_DELAY_UI (~66 ms) has a 7x margin on both.
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI, sensorHandler);
        }
        registered = true;
    }

    private void unregisterSensors() {
        if (sensorManager != null && registered) sensorManager.unregisterListener(this);
        registered = false;
        if (sensorThread != null) {
            sensorThread.quitSafely();
            sensorThread = null;
            sensorHandler = null;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* ignore */ }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Listener l;
        Config c;
        boolean tripActiveNow;
        BackgroundLocation loc;
        synchronized (this) {
            l = this.listener;
            c = this.cfg;
            tripActiveNow = this.tripActive;
            loc = this.lastLocation;
        }
        if (l == null || c == null || !c.enabled) return;

        long now = SystemClock.elapsedRealtime();

        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float ax = event.values[0], ay = event.values[1], az = event.values[2];
            double mag = Math.sqrt(ax * ax + ay * ay + az * az);
            double gMag = mag / G;
            lastAccelMag = mag;
            lastAccelAt = now;

            // Crash: high impact during a trip, corroborated by GPS.
            if (tripActiveNow && c.crashImpactG > 0 && gMag >= c.crashImpactG
                    && (now - lastCrashAt) >= c.crashCooldownMs
                    && gpsCorroboratesImpact(now)) {
                lastCrashAt = now;
                l.onSensorCrash(loc, gMag);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float gx = event.values[0], gy = event.values[1], gz = event.values[2];
            lastGyroMag = Math.sqrt(gx * gx + gy * gy + gz * gz);
            lastGyroAt = now;
        } else {
            return;
        }

        evaluatePhoneUsage(now, c, l, tripActiveNow, loc);
    }

    /**
     * A 3 g spike alone is not a crash — a phone dropping off its mount clears that easily.
     * Require the GPS layer to agree: either the vehicle is (nearly) stopped right now, or it
     * shed a significant amount of speed inside the corroboration window.
     */
    private boolean gpsCorroboratesImpact(long now) {
        double speed = lastSpeedMps;
        if (speed < 0) return false; // no GPS speed at all → not corroborated
        if (speed <= CRASH_CORROBORATION_SPEED_MPS) return true;
        double peak = recentPeakSpeedMps;
        return peak >= 0
                && (now - recentPeakAt) <= CRASH_CORROBORATION_WINDOW_MS
                && (peak - speed) >= CRASH_CORROBORATION_DROP_MPS;
    }

    private void evaluatePhoneUsage(long now,
                                    Config c,
                                    Listener l,
                                    boolean tripActiveNow,
                                    BackgroundLocation loc) {
        if (!tripActiveNow) { jitterAboveSince = 0L; return; }
        if (powerManager == null || !isScreenOn()) { jitterAboveSince = 0L; return; }

        // AND, not OR: hand manipulation shows up on BOTH channels at once. Vehicle
        // acceleration moves the accelerometer without rotating the device; road vibration
        // spikes neither for a sustained window.
        boolean accelFresh = lastAccelAt > 0 && (now - lastAccelAt) <= JITTER_SAMPLE_TTL_MS;
        boolean gyroFresh = lastGyroAt > 0 && (now - lastGyroAt) <= JITTER_SAMPLE_TTL_MS;
        boolean above = accelFresh && gyroFresh
                && lastAccelMag >= JITTER_ACCEL_MPS2
                && lastGyroMag >= JITTER_GYRO_RAD_S;

        if (above) {
            if (jitterAboveSince == 0L) jitterAboveSince = now;
            if ((now - jitterAboveSince) >= c.phoneUsageWindowMs
                    && (now - lastPhoneUsageAt) >= c.phoneUsageCooldownMs) {
                lastPhoneUsageAt = now;
                jitterAboveSince = 0L;
                l.onPhoneUsageWhileDriving(loc);
            }
        } else {
            jitterAboveSince = 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isScreenOn() {
        if (powerManager == null) return false;
        try {
            // isInteractive added API 20; we target >= 21 elsewhere, but guard anyway.
            return powerManager.isInteractive();
        } catch (Throwable t) {
            return powerManager.isScreenOn();
        }
    }
}
