package com.marianhello.backgroundgeolocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.driving.DrivingEventsDetector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests for {@link DrivingEventsDetector}.
 *
 * Runs under Robolectric only because the class touches {@code android.os.SystemClock}
 * for its (monotonic) cooldowns; the logic itself is plain Java.
 */
@RunWith(RobolectricTestRunner.class)
public class DrivingEventsDetectorTest {

    /** Records every callback so tests can assert on counts and payloads. */
    private static class Recorder implements DrivingEventsDetector.Listener {
        final List<String> events = new ArrayList<String>();
        final List<Double> hardBrakes = new ArrayList<Double>();
        final List<Double> rapidAccels = new ArrayList<Double>();
        final List<Double> crashes = new ArrayList<Double>();
        final List<Double> tripDistances = new ArrayList<Double>();
        final List<Long> tripDurations = new ArrayList<Long>();

        @Override public void onMoving(BackgroundLocation l) { events.add("moving"); }
        @Override public void onStopped(BackgroundLocation l) { events.add("stopped"); }
        @Override public void onTripStart(BackgroundLocation l) { events.add("tripStart"); }
        @Override public void onTripEnd(BackgroundLocation l, double distance, long durationMs) {
            events.add("tripEnd");
            tripDistances.add(distance);
            tripDurations.add(durationMs);
        }
        @Override public void onSpeeding(BackgroundLocation l, double kmh, double limit) { events.add("speeding"); }
        @Override public void onProviderChange(String provider) { events.add("providerChange"); }
        @Override public void onHardBrake(BackgroundLocation l, double decel) {
            events.add("hardBrake"); hardBrakes.add(decel);
        }
        @Override public void onRapidAcceleration(BackgroundLocation l, double accel) {
            events.add("rapidAcceleration"); rapidAccels.add(accel);
        }
        @Override public void onSharpTurn(BackgroundLocation l, double degPerSec) { events.add("sharpTurn"); }
        @Override public void onPossibleCrash(BackgroundLocation l, double dropKmh) {
            events.add("possibleCrash"); crashes.add(dropKmh);
        }

        int count(String type) {
            int n = 0;
            for (String e : events) if (e.equals(type)) n++;
            return n;
        }
    }

    private static final long T0 = 1_700_000_000_000L;
    /** ~0.0001 deg of latitude is ~11.1 m; used to build a straight northbound track. */
    private static final double BASE_LAT = 40.0;
    private static final double BASE_LON = -3.0;

    private Recorder rec;
    private DrivingEventsDetector det;

    @Before
    public void setUp() {
        rec = new Recorder();
        det = new DrivingEventsDetector(rec);
        det.setConfig(defaultConfig());
    }

    private static DrivingEventsDetector.Config defaultConfig() {
        DrivingEventsDetector.Config c = new DrivingEventsDetector.Config();
        c.enabled = true;
        c.speedLimitKmh = 0;          // speeding off unless a test enables it
        c.minMovingSpeedMps = 1.0;
        c.stoppedDurationMs = 60_000;
        c.minTripSpeedMps = 3.0;
        c.minTripDurationMs = 30_000;
        c.hardBrakeMps2 = 3.5;
        c.rapidAccelMps2 = 3.5;
        c.sharpTurnDegPerSec = 30;
        c.crashImpactKmh = 25;
        c.crashWindowMs = 2_000;
        return c;
    }

    /** Location with a fix timestamp, a speed and a position. */
    private static BackgroundLocation fix(long timeMs, double speedMps, double lat, double lon) {
        BackgroundLocation l = new BackgroundLocation();
        l.setProvider("gps");
        l.setTime(timeMs);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(5f);
        l.setSpeed((float) speedMps);
        return l;
    }

    /** Same, but the provider gave no speed at all (NETWORK fix / first fix after reacquire). */
    private static BackgroundLocation fixNoSpeed(long timeMs, double lat, double lon) {
        BackgroundLocation l = new BackgroundLocation();
        l.setProvider("gps");
        l.setTime(timeMs);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(5f);
        return l;
    }

    private static double latAfter(double meters) {
        return BASE_LAT + meters / 111_320.0;
    }

    /** Drives the detector until a trip is active. Returns the timestamp of the LAST fix fed. */
    private long startTrip(double speedMps) {
        long t = T0;
        double travelled = 0;
        long last = t;
        // minTripDuration is 30 s; feed 40 s of steady driving in 5 s steps.
        for (int i = 0; i <= 8; i++) {
            det.onLocation(fix(t, speedMps, latAfter(travelled), BASE_LON));
            last = t;
            t += 5_000;
            travelled += speedMps * 5.0;
        }
        return last;
    }

    // -----------------------------------------------------------------------------------
    // 1. Batched delivery must not synthesise accelerations.
    // -----------------------------------------------------------------------------------

    @Test
    public void batchedFixesOneMillisecondApartProduceNoAccelerationEvents() {
        long t = startTrip(20.0);
        assertEquals("trip should be active for this scenario", 1, rec.count("tripStart"));
        rec.events.clear();
        rec.hardBrakes.clear();
        rec.rapidAccels.clear();

        // Provider flushes a batch in a tight loop: 5 fixes, timestamps 1 ms apart.
        double[] speeds = {20.0, 20.5, 21.0, 20.2, 20.8};
        for (int i = 0; i < speeds.length; i++) {
            det.onLocation(fix(t + i, speeds[i], latAfter(1000 + i), BASE_LON));
        }

        assertEquals("batched fixes must not produce hard brakes", 0, rec.count("hardBrake"));
        assertEquals("batched fixes must not produce rapid accelerations", 0, rec.count("rapidAcceleration"));
        assertEquals("batched fixes must not produce crashes", 0, rec.count("possibleCrash"));
    }

    @Test
    public void batchedFixesWithLargeSpeedSwingStillProduceNoEvents() {
        long t = startTrip(25.0);
        rec.events.clear();

        // Worst case: a full stop reported 1 ms after 25 m/s. Arrival-clock maths would call
        // this -25000 m/s² and a crash; fix-time maths rejects the delta as too short.
        det.onLocation(fix(t + 1, 0.0, latAfter(2000), BASE_LON));

        assertEquals(0, rec.count("hardBrake"));
        assertEquals(0, rec.count("rapidAcceleration"));
        assertEquals(0, rec.count("possibleCrash"));
    }

    @Test
    public void realHardBrakeOverAValidDeltaStillFires() {
        long t = startTrip(20.0);
        rec.events.clear();

        // 20 -> 12 m/s in 2 s = -4 m/s², past the 3.5 threshold.
        det.onLocation(fix(t + 2_000, 12.0, latAfter(1000), BASE_LON));

        assertEquals("a genuine brake over a 2 s delta must still fire", 1, rec.count("hardBrake"));
        assertTrue(rec.hardBrakes.get(0) <= -3.5);
    }

    // -----------------------------------------------------------------------------------
    // 2. A fix without speed is not speed 0.
    // -----------------------------------------------------------------------------------

    @Test
    public void fixWithoutSpeedDoesNotFakeADecelerationOrCrash() {
        long t = startTrip(25.0);
        assertEquals(1, rec.count("tripStart"));
        rec.events.clear();

        // 1.5 s later a NETWORK fix arrives with no speed at all.
        det.onLocation(fixNoSpeed(t + 1_500, latAfter(2000), BASE_LON));

        assertEquals("speed-less fix must not look like a brake", 0, rec.count("hardBrake"));
        assertEquals("speed-less fix must not raise a crash alert", 0, rec.count("possibleCrash"));
        assertEquals("speed-less fix must not report a stop", 0, rec.count("stopped"));
        assertEquals("speed-less fix must not end the trip", 0, rec.count("tripEnd"));
    }

    @Test
    public void speedLessFixDoesNotPoisonTheNextRealDelta() {
        long t = startTrip(25.0);
        rec.events.clear();

        det.onLocation(fixNoSpeed(t + 1_500, latAfter(2000), BASE_LON));
        // Next real fix, 1.5 s after the speed-less one, still cruising.
        det.onLocation(fix(t + 3_000, 25.0, latAfter(2040), BASE_LON));

        assertEquals(0, rec.count("hardBrake"));
        assertEquals(0, rec.count("rapidAcceleration"));
        assertEquals(0, rec.count("possibleCrash"));
    }

    // -----------------------------------------------------------------------------------
    // 3. Trip lifecycle.
    // -----------------------------------------------------------------------------------

    @Test
    public void fullTripCycleEmitsExactlyOneStartAndOneEnd() {
        long t = T0;
        double travelled = 0;

        // Parked.
        det.onLocation(fix(t, 0.0, latAfter(travelled), BASE_LON));
        t += 5_000;

        // Accelerate and hold 20 m/s for 60 s (well past minTripDuration).
        for (int i = 0; i < 12; i++) {
            det.onLocation(fix(t, 20.0, latAfter(travelled), BASE_LON));
            travelled += 100;
            t += 5_000;
        }
        assertEquals("exactly one tripStart", 1, rec.count("tripStart"));
        assertEquals("exactly one moving", 1, rec.count("moving"));

        // Stop and stay stopped past stoppedDuration (60 s).
        for (int i = 0; i < 14; i++) {
            det.onLocation(fix(t, 0.0, latAfter(travelled), BASE_LON));
            t += 5_000;
        }

        assertEquals("exactly one tripStart over the whole cycle", 1, rec.count("tripStart"));
        assertEquals("exactly one tripEnd over the whole cycle", 1, rec.count("tripEnd"));
        assertEquals("exactly one stopped", 1, rec.count("stopped"));
        assertTrue("trip must have accumulated distance", rec.tripDistances.get(0) > 0);

        // Duration must exclude the trailing stoppedDuration tail: the vehicle drove for
        // roughly 55 s, so anything approaching 60 s of extra tail is a bug.
        long dur = rec.tripDurations.get(0);
        assertTrue("duration must be positive", dur > 0);
        assertTrue("duration must not include the stopped tail, was " + dur, dur <= 60_000);
    }

    @Test
    public void secondTripDoesNotInheritDistanceFromTheFirst() {
        long t = T0;
        double travelled = 0;

        // ---- trip 1: 12 x 100 m at 20 m/s ----
        det.onLocation(fix(t, 0.0, latAfter(travelled), BASE_LON));
        t += 5_000;
        for (int i = 0; i < 12; i++) {
            det.onLocation(fix(t, 20.0, latAfter(travelled), BASE_LON));
            travelled += 100;
            t += 5_000;
        }
        for (int i = 0; i < 14; i++) {
            det.onLocation(fix(t, 0.0, latAfter(travelled), BASE_LON));
            t += 5_000;
        }
        assertEquals(1, rec.count("tripEnd"));
        double firstDistance = rec.tripDistances.get(0);
        assertTrue("first trip distance should be several hundred metres, was " + firstDistance,
                firstDistance > 300);

        // ---- trip 2: much shorter, 3 x 100 m of accumulation after tripStart ----
        for (int i = 0; i < 8; i++) {   // 40 s to arm the trip
            det.onLocation(fix(t, 20.0, latAfter(travelled), BASE_LON));
            travelled += 100;
            t += 5_000;
        }
        for (int i = 0; i < 14; i++) {
            det.onLocation(fix(t, 0.0, latAfter(travelled), BASE_LON));
            t += 5_000;
        }

        assertEquals("two trip starts overall", 2, rec.count("tripStart"));
        assertEquals("two trip ends overall", 2, rec.count("tripEnd"));
        double secondDistance = rec.tripDistances.get(1);
        assertTrue("second trip distance must not carry the first one, was " + secondDistance,
                secondDistance < firstDistance);
        assertTrue("second trip distance must be plausible, was " + secondDistance,
                secondDistance > 0 && secondDistance < 500);
    }

    // -----------------------------------------------------------------------------------
    // v5.0 — F4: tick() closes a trip when the provider stops delivering fixes.
    // -----------------------------------------------------------------------------------

    /**
     * Drives a trip, then a single slow fix, then silence — exactly what a parked vehicle
     * produces. Only tick() can fire stopped/tripEnd from there.
     */
    @Test
    public void tickPastStoppedDurationEndsTheTripWithNoFurtherLocations() {
        long t = startTrip(20.0);
        assertEquals("trip should be active for this scenario", 1, rec.count("tripStart"));

        // Vehicle comes to a halt: one fix below minMovingSpeed arms the stopped timer.
        long parkedAt = t + 5_000;
        det.onLocation(fix(parkedAt, 0.0, latAfter(1000), BASE_LON));
        assertEquals("the halting fix alone must not end the trip", 0, rec.count("tripEnd"));
        rec.events.clear();

        // GPS goes quiet. 61 s later only the periodic tick can advance the state machine.
        det.tick(parkedAt + 61_000);

        assertEquals("tick must fire stopped", 1, rec.count("stopped"));
        assertEquals("tick must close the trip", 1, rec.count("tripEnd"));
        assertTrue("trip duration must be positive", rec.tripDurations.get(0) > 0);
        assertTrue("duration must not include the stopped tail, was " + rec.tripDurations.get(0),
                rec.tripDurations.get(0) <= 60_000);

        // Idempotent: further ticks must not re-fire the same transition.
        det.tick(parkedAt + 240_000);
        assertEquals(1, rec.count("stopped"));
        assertEquals(1, rec.count("tripEnd"));
    }

    @Test
    public void tickBeforeStoppedDurationFiresNothing() {
        long t = startTrip(20.0);
        long parkedAt = t + 5_000;
        det.onLocation(fix(parkedAt, 0.0, latAfter(1000), BASE_LON));
        rec.events.clear();

        // Half of stoppedDurationMs (60 s): still nothing to report.
        det.tick(parkedAt + 30_000);

        assertEquals("stopped must not fire before the timeout", 0, rec.count("stopped"));
        assertEquals("tripEnd must not fire before the timeout", 0, rec.count("tripEnd"));

        // And a tick while still moving (no below-moving fix seen) does nothing either.
        det.reset();
        det.setConfig(defaultConfig());
        startTrip(20.0);
        rec.events.clear();
        det.tick(T0 + 10_000_000L);
        assertEquals(0, rec.count("stopped"));
        assertEquals(0, rec.count("tripEnd"));
    }

    // -----------------------------------------------------------------------------------
    // Distance hygiene.
    // -----------------------------------------------------------------------------------

    @Test
    public void gpsJitterWhileParkedDoesNotAccumulateDistance() {
        long t = T0;
        // Arm a trip without any real displacement in the coordinates.
        for (int i = 0; i <= 8; i++) {
            det.onLocation(fix(t, 5.0, BASE_LAT, BASE_LON));
            t += 5_000;
        }
        assertEquals(1, rec.count("tripStart"));

        // Now sit at a light: the same nominal position +- ~2 m, reported with accuracy 50 m.
        // Speed stays above minMovingSpeed so the trip does not end here.
        for (int i = 0; i < 20; i++) {
            BackgroundLocation l = fix(t, 1.2, BASE_LAT + (i % 2 == 0 ? 0.00002 : -0.00002), BASE_LON);
            l.setAccuracy(50f);
            det.onLocation(l);
            t += 5_000;
        }
        // End the trip and read the distance.
        for (int i = 0; i < 14; i++) {
            det.onLocation(fix(t, 0.0, BASE_LAT, BASE_LON));
            t += 5_000;
        }
        assertEquals(1, rec.count("tripEnd"));
        double dist = rec.tripDistances.get(0);
        assertTrue("jitter under a 50 m accuracy circle must not add distance, got " + dist,
                dist < 25);
    }

    // -----------------------------------------------------------------------------------
    // Crash sliding window.
    // -----------------------------------------------------------------------------------

    @Test
    public void crashUsesASlidingWindowNotTwoConsecutiveFixes() {
        DrivingEventsDetector.Config c = defaultConfig();
        c.crashWindowMs = 10_000;   // fleet-ish window
        c.hardBrakeMps2 = 0;        // isolate the crash path
        c.rapidAccelMps2 = 0;
        det.setConfig(c);

        long t = startTrip(20.0);   // 72 km/h
        rec.events.clear();

        // Impact: 20 m/s -> 0 across two 3 s steps, both inside the 10 s window even though no
        // single consecutive pair spans it in the old sense.
        det.onLocation(fix(t + 3_000, 9.0, latAfter(3000), BASE_LON));
        det.onLocation(fix(t + 6_000, 0.2, latAfter(3010), BASE_LON));

        assertEquals("sliding window must catch the drop", 1, rec.count("possibleCrash"));
        assertTrue(rec.crashes.get(0) >= 25);
    }

    // -----------------------------------------------------------------------------------
    // Speeding hysteresis.
    // -----------------------------------------------------------------------------------

    @Test
    public void cruisingAtTheLimitDoesNotBurstSpeedingEvents() {
        DrivingEventsDetector.Config c = defaultConfig();
        c.speedLimitKmh = 90;
        det.setConfig(c);

        long t = T0;
        double travelled = 0;
        // 25 m/s = 90 km/h exactly; oscillate a hair above and below the limit.
        double[] speeds = {25.05, 24.98, 25.06, 24.99, 25.07, 24.97, 25.08, 24.96,
                           25.05, 24.99, 25.06, 24.98, 25.07, 24.99, 25.05, 24.98};
        for (double s : speeds) {
            det.onLocation(fix(t, s, latAfter(travelled), BASE_LON));
            travelled += s * 5;
            t += 5_000;
        }

        assertTrue("dead band must collapse the burst, got " + rec.count("speeding"),
                rec.count("speeding") <= 1);
    }
}
