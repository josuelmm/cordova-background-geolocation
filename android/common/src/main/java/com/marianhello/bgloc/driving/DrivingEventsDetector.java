package com.marianhello.bgloc.driving;

import android.os.SystemClock;

import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * v4.0 Phase 6 — Driver insights state machine (GPS-only).
 *
 * Hosted by {@code LocationServiceImpl}, which feeds it every received location and surfaces
 * emitted events via the plugin {@code MSG_ON_*} broadcast pipeline.
 *
 * Clocks — three different ones, on purpose:
 *   - {@code loc.getTime()} (the fix timestamp) drives every time delta: speed deltas, bearing
 *     deltas, the moving/stopped/trip state machine and the crash window. Providers deliver
 *     batches in a tight loop (e.g. when leaving Doze), so the *arrival* clock reports 1-2 ms
 *     between fixes that are really seconds apart, which would turn any speed change into a
 *     hundreds-of-m/s² acceleration.
 *   - {@link SystemClock#elapsedRealtime()} drives the per-event cooldowns. It is monotonic, so
 *     an NTP step backwards cannot park a cooldown timestamp in the future and mute an event
 *     type forever.
 *   - Wall clock is not used at all (only as a fallback when a fix carries no timestamp).
 *
 * Heuristics:
 *   moving   = speed > minMovingSpeed
 *   stopped  = !moving for stoppedDuration ms
 *   tripStart = stopped → moving with speed >= minTripSpeed sustained for minTripDuration ms
 *   tripEnd  = moving → stopped (after a tripStart)
 *   speeding = crossing above speedLimit (km/h); rearms below speedLimit * REARM_FACTOR
 *
 * v5.0 — F4: the stoppedDuration timeout is also evaluated by {@link #tick(long)}, because a
 * parked vehicle stops producing fixes and onLocation() alone would never close the trip.
 *
 * Fixes without a speed value (NETWORK provider, first fix after reacquisition) are *not*
 * treated as speed 0: every speed-derived branch is skipped for them. Only the position is
 * consumed. Otherwise a single speed-less fix at cruising speed would look like a -25 m/s²
 * deceleration and raise a bogus crash alert.
 */
public class DrivingEventsDetector {

    public interface Listener {
        void onMoving(BackgroundLocation location);
        void onStopped(BackgroundLocation location);
        void onTripStart(BackgroundLocation location);
        /** distance in meters, durationMs in milliseconds. */
        void onTripEnd(BackgroundLocation location, double distance, long durationMs);
        void onSpeeding(BackgroundLocation location, double speedKmh, double limitKmh);
        void onProviderChange(String provider);
        // v4.1 GPS-derived driving events
        /** GPS-derived deceleration (m/s²). Negative number, more negative = harder brake. */
        void onHardBrake(BackgroundLocation location, double decelMps2);
        /** GPS-derived acceleration (m/s²). */
        void onRapidAcceleration(BackgroundLocation location, double accelMps2);
        /** Bearing change rate (deg/s). */
        void onSharpTurn(BackgroundLocation location, double degPerSec);
        /** Velocity drop in km/h within {@code crashWindowMs} while tripActive. */
        void onPossibleCrash(BackgroundLocation location, double velocityDropKmh);
    }

    public static class Config {
        public boolean enabled = false;
        public double speedLimitKmh = 0;        // 0 disables speeding
        public double minMovingSpeedMps = 1.0;  // ~3.6 km/h
        public long stoppedDurationMs = 60_000;
        public double minTripSpeedMps = 3.0;    // ~10.8 km/h
        public long minTripDurationMs = 30_000;
        // v4.1 GPS-derived driving events. 0 disables each one.
        public double hardBrakeMps2 = 3.5;      // -m/s² threshold (positive value)
        public double rapidAccelMps2 = 3.5;     // m/s² threshold
        public double sharpTurnDegPerSec = 30;  // deg/sec, requires speed > 5 m/s
        public double crashImpactKmh = 25;      // velocity drop within crashWindow
        public long   crashWindowMs = 2_000;    // window to evaluate the velocity drop
    }

    /**
     * Minimum spacing between two fixes for a derivative (dv/dt, dbearing/dt) to be meaningful.
     * Below this, GPS speed quantisation noise dominates and batched/duplicated fixes produce
     * absurd accelerations.
     */
    private static final long MIN_DELTA_MS = 500L;
    /** Above this the two fixes are unrelated and no derivative is computed. */
    private static final long MAX_DELTA_MS = 5_000L;

    /** Fixes worse than this are not trusted to accumulate trip distance. */
    private static final float MAX_DISTANCE_ACCURACY_M = 50f;
    /** Floor for a segment to count, on top of the "displacement must beat the accuracy" rule. */
    private static final double MIN_SEGMENT_METERS = 5.0;
    /** A gap longer than this (tunnel, Doze) means the straight line between fixes is fiction. */
    private static final long MAX_SEGMENT_GAP_MS = 120_000L;

    /** Speeding rearms only below limit * this, so cruising exactly at the limit is quiet. */
    private static final double SPEEDING_REARM_FACTOR = 0.95;

    /** Cooldown so we don't refire the same event on every fix in a sustained brake. */
    private static final long DRIVING_EVENT_COOLDOWN_MS = 4_000L;
    /** "Never fired". Far enough from any elapsedRealtime() value that the first check passes. */
    private static final long NEVER = Long.MIN_VALUE / 4;

    /** Ring buffer capacity for the crash sliding window. */
    private static final int SPEED_HISTORY = 32;

    private final Listener listener;
    private Config cfg = new Config();

    // State
    private boolean isMoving = false;
    private boolean tripActive = false;
    private long tripStartedAt = 0;
    private double tripDistanceMeters = 0;

    private long aboveTripSpeedSince = 0;   // first sample with speed >= minTripSpeed
    private long belowMovingSinceMs = 0;    // first sample with speed < minMovingSpeed

    private boolean wasSpeeding = false;
    private String lastProvider;

    private double prevLat, prevLon;
    private long prevPosAt = 0L;
    private boolean prevPosAccurate = false;
    private boolean hasPrev = false;

    /**
     * v5.0 — F4: last location handed to {@link #onLocation}, used as the payload for the
     * transitions {@link #tick(long)} fires when no fix is arriving any more.
     */
    private BackgroundLocation lastLocation;

    // v4.1 GPS-derived deltas (all timestamps are fix times)
    private double prevSpeedMps = 0.0;
    private long   prevSpeedAt = 0L;
    private double prevBearingDeg = 0.0;
    private boolean hasPrevBearing = false;
    private long   prevBearingAt = 0L;

    // Crash sliding window: (fix time, speed) ring buffer.
    private final long[] histAt = new long[SPEED_HISTORY];
    private final double[] histSpeed = new double[SPEED_HISTORY];
    private int histHead = 0;   // next write slot
    private int histCount = 0;

    // Cooldowns — elapsedRealtime() based.
    private long lastHardBrakeAt = NEVER, lastRapidAccelAt = NEVER,
                 lastSharpTurnAt = NEVER, lastCrashAt = NEVER, lastSpeedingAt = NEVER;

    public DrivingEventsDetector(Listener listener) {
        this.listener = listener;
    }

    public synchronized void setConfig(Config c) {
        if (c != null) this.cfg = c;
    }

    /** Reset internal state. Called when service stops. */
    public synchronized void reset() {
        prevSpeedMps = 0.0;
        prevSpeedAt = 0L;
        prevBearingDeg = 0.0;
        hasPrevBearing = false;
        prevBearingAt = 0L;
        lastHardBrakeAt = lastRapidAccelAt = lastSharpTurnAt = lastCrashAt = lastSpeedingAt = NEVER;
        isMoving = false;
        tripActive = false;
        tripStartedAt = 0;
        tripDistanceMeters = 0;
        aboveTripSpeedSince = 0;
        belowMovingSinceMs = 0;
        wasSpeeding = false;
        lastProvider = null;
        hasPrev = false;
        prevPosAt = 0L;
        prevPosAccurate = false;
        lastLocation = null;
        histHead = 0;
        histCount = 0;
    }

    /**
     * v5.0 — F4: evaluates the purely time-based transitions with no new fix.
     *
     * <p>The state machine only advanced inside {@link #onLocation}. When the vehicle parks the
     * provider stops delivering fixes, so the {@code stoppedDurationMs} timeout was never
     * evaluated: {@code stopped} and {@code tripEnd} never fired, the trip stayed open forever
     * and the service watchdog kept restarting the provider for a vehicle that was not moving.
     *
     * <p>{@code nowMs} must be wall clock — the same clock {@code loc.getTime()} reports — so it
     * is directly comparable with {@code belowMovingSinceMs}. The listener gets the last known
     * location, which may be null; the {@code LocationServiceImpl} listener tolerates that.
     */
    public synchronized void tick(long nowMs) {
        if (!cfg.enabled) return;
        if (!isMoving || belowMovingSinceMs == 0) return;
        if ((nowMs - belowMovingSinceMs) < cfg.stoppedDurationMs) return;

        // Same transition as the equivalent branch in onLocation().
        final BackgroundLocation loc = lastLocation;
        isMoving = false;
        if (listener != null) listener.onStopped(loc);
        if (tripActive) {
            // The trip really ended when the vehicle stopped moving, not
            // stoppedDurationMs later — otherwise every trip carries a fake tail.
            long durMs = belowMovingSinceMs - tripStartedAt;
            if (durMs < 0) durMs = 0;
            double dist = tripDistanceMeters;
            tripActive = false;
            if (listener != null) listener.onTripEnd(loc, dist, durMs);
        }
    }

    public synchronized void onLocation(BackgroundLocation loc) {
        if (!cfg.enabled || loc == null) return;
        lastLocation = loc;

        // Fix timestamp drives every delta. Fall back to arrival only if the fix has none.
        long tMs = loc.getTime();
        if (tMs <= 0) tMs = System.currentTimeMillis();
        // Monotonic clock, cooldowns only.
        final long mono = SystemClock.elapsedRealtime();

        final boolean hasSpeed = loc.hasSpeed();
        final double speed = hasSpeed ? loc.getSpeed() : 0.0;

        // Provider change
        String provider = loc.getProvider();
        if (provider != null && !provider.equals(lastProvider)) {
            lastProvider = provider;
            if (listener != null) listener.onProviderChange(provider);
        }

        // ---- Distance accumulator -------------------------------------------------------
        // Only trustworthy segments count: both endpoints reasonably accurate, displacement
        // bigger than the accuracy circle (otherwise it is jitter while parked at a light) and
        // no huge gap (post-tunnel reacquisition draws a straight line through buildings).
        double curLat = loc.getLatitude();
        double curLon = loc.getLongitude();
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : -1f;
        boolean accurate = acc < 0 || acc <= MAX_DISTANCE_ACCURACY_M;
        if (hasPrev && tripActive && accurate && prevPosAccurate) {
            long segDt = tMs - prevPosAt;
            if (segDt >= 0 && segDt <= MAX_SEGMENT_GAP_MS) {
                double seg = haversineMeters(prevLat, prevLon, curLat, curLon);
                double minDisp = Math.max(MIN_SEGMENT_METERS, acc > 0 ? acc : 0);
                if (seg >= minDisp) {
                    tripDistanceMeters += seg;
                }
            }
        }
        prevLat = curLat;
        prevLon = curLon;
        prevPosAt = tMs;
        prevPosAccurate = accurate;
        hasPrev = true;

        // ---- Bearing / sharp turn -------------------------------------------------------
        // Handled outside the speed block so a speed-less fix still refreshes (or invalidates)
        // the bearing anchor; the emission itself still requires a real speed reading.
        if (!loc.hasBearing()) {
            hasPrevBearing = false;
            prevBearingAt = 0L;
        } else {
            double bearing = loc.getBearing();
            if (hasPrevBearing) {
                long dtMs = tMs - prevBearingAt;
                if (dtMs > MAX_DELTA_MS) {
                    hasPrevBearing = false; // stale anchor, re-armed below with this fix
                } else if (dtMs >= MIN_DELTA_MS
                        && cfg.sharpTurnDegPerSec > 0
                        && hasSpeed && speed >= 5.0) {
                    double diff = Math.abs(bearing - prevBearingDeg);
                    if (diff > 180) diff = 360 - diff;
                    double rate = diff * 1000.0 / dtMs;
                    if (rate >= cfg.sharpTurnDegPerSec
                            && (mono - lastSharpTurnAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                        lastSharpTurnAt = mono;
                        if (listener != null) listener.onSharpTurn(loc, rate);
                    }
                }
            }
            prevBearingDeg = bearing;
            prevBearingAt = tMs;
            hasPrevBearing = true;
        }

        // ---- Everything below is speed-derived ------------------------------------------
        if (!hasSpeed) return;

        // Moving / stopped state
        boolean nowMoving = speed >= cfg.minMovingSpeedMps;
        if (nowMoving) {
            belowMovingSinceMs = 0;
            if (!isMoving) {
                isMoving = true;
                if (listener != null) listener.onMoving(loc);
            }
            // Trip start arming
            if (!tripActive) {
                if (speed >= cfg.minTripSpeedMps) {
                    if (aboveTripSpeedSince == 0) aboveTripSpeedSince = tMs;
                    if (tMs - aboveTripSpeedSince >= cfg.minTripDurationMs) {
                        tripActive = true;
                        tripStartedAt = tMs;
                        tripDistanceMeters = 0;
                        if (listener != null) listener.onTripStart(loc);
                    }
                } else {
                    aboveTripSpeedSince = 0;
                }
            }
        } else {
            aboveTripSpeedSince = 0;
            if (belowMovingSinceMs == 0) belowMovingSinceMs = tMs;
            if (isMoving && (tMs - belowMovingSinceMs) >= cfg.stoppedDurationMs) {
                isMoving = false;
                if (listener != null) listener.onStopped(loc);
                if (tripActive) {
                    // The trip really ended when the vehicle stopped moving, not
                    // stoppedDurationMs later — otherwise every trip carries a fake tail.
                    long durMs = belowMovingSinceMs - tripStartedAt;
                    if (durMs < 0) durMs = 0;
                    double dist = tripDistanceMeters;
                    tripActive = false;
                    if (listener != null) listener.onTripEnd(loc, dist, durMs);
                }
            }
        }

        // Speeding (km/h), with dead band + cooldown so cruising at the limit is not a burst.
        if (cfg.speedLimitKmh > 0) {
            double kmh = speed * 3.6;
            if (!wasSpeeding) {
                if (kmh > cfg.speedLimitKmh
                        && (mono - lastSpeedingAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    wasSpeeding = true;
                    lastSpeedingAt = mono;
                    if (listener != null) listener.onSpeeding(loc, kmh, cfg.speedLimitKmh);
                }
            } else if (kmh < cfg.speedLimitKmh * SPEEDING_REARM_FACTOR) {
                wasSpeeding = false;
            }
        }

        // v4.1 GPS-derived driving events (only meaningful during an active trip)
        if (tripActive && prevSpeedAt > 0) {
            long dtMs = tMs - prevSpeedAt;
            if (dtMs >= MIN_DELTA_MS && dtMs <= MAX_DELTA_MS) {
                double dt = dtMs / 1000.0;
                double dv = speed - prevSpeedMps; // m/s
                double accel = dv / dt;            // m/s²

                if (cfg.hardBrakeMps2 > 0
                        && accel <= -cfg.hardBrakeMps2
                        && (mono - lastHardBrakeAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    lastHardBrakeAt = mono;
                    if (listener != null) listener.onHardBrake(loc, accel);
                }
                if (cfg.rapidAccelMps2 > 0
                        && accel >= cfg.rapidAccelMps2
                        && (mono - lastRapidAccelAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    lastRapidAccelAt = mono;
                    if (listener != null) listener.onRapidAcceleration(loc, accel);
                }
            }

            // Possible crash: real sliding window. Compare the current speed against the peak
            // recorded in the last crashWindowMs, instead of requiring two *consecutive* fixes
            // to fall inside the window (which, at fleet intervals of 10-60 s, never happened).
            if (cfg.crashImpactKmh > 0 && cfg.crashWindowMs > 0) {
                double peak = maxSpeedWithin(tMs, cfg.crashWindowMs,
                        Math.min(MIN_DELTA_MS, cfg.crashWindowMs));
                if (peak >= 0) {
                    double dropKmh = (peak - speed) * 3.6; // positive when slowing down
                    if (dropKmh >= cfg.crashImpactKmh
                            && speed < 1.5 // ended near stop
                            && peak * 3.6 >= cfg.crashImpactKmh
                            && (mono - lastCrashAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                        lastCrashAt = mono;
                        if (listener != null) listener.onPossibleCrash(loc, dropKmh);
                    }
                }
            }
        }

        pushSpeedSample(tMs, speed);
        prevSpeedMps = speed;
        prevSpeedAt = tMs;
    }

    /**
     * Highest speed recorded before {@code nowMs}, at least {@code minAgeMs} old and no more
     * than {@code windowMs} old. The minimum age keeps duplicated/batched fixes carrying the
     * same or near-same timestamp from being read as an instantaneous velocity collapse.
     */
    private double maxSpeedWithin(long nowMs, long windowMs, long minAgeMs) {
        double max = -1;
        for (int i = 0; i < histCount; i++) {
            int idx = (histHead - 1 - i + SPEED_HISTORY * 2) % SPEED_HISTORY;
            long age = nowMs - histAt[idx];
            if (age < minAgeMs || age > windowMs) continue;
            if (histSpeed[idx] > max) max = histSpeed[idx];
        }
        return max;
    }

    private void pushSpeedSample(long tMs, double speed) {
        histAt[histHead] = tMs;
        histSpeed[histHead] = speed;
        histHead = (histHead + 1) % SPEED_HISTORY;
        if (histCount < SPEED_HISTORY) histCount++;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
