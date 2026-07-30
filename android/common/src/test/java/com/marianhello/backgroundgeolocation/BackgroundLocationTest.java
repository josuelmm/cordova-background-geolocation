package com.marianhello.backgroundgeolocation;

import androidx.test.filters.SmallTest;

import com.marianhello.bgloc.data.BackgroundLocation;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Created by finch on 10/08/16.
 *
 * NOTE: these tests used to force Build.VERSION.SDK_INT via the `Field.modifiers` reflection
 * hack, removed in JDK 12. The resulting exception was swallowed, so SDK_INT stayed at 0 and
 * isBetterLocation() took the pre-API-17 getTime() branch instead of the
 * getElapsedRealtimeNanos() branch these tests actually exercise. Running under Robolectric
 * gives a real (modern) SDK_INT, which is also the only thing minSdk 24 can produce.
 */
@SmallTest
@RunWith(RobolectricTestRunner.class)
public class BackgroundLocationTest {

    @Test public void testHasMockShouldBeFalse() {
        BackgroundLocation l = new BackgroundLocation();
        Assert.assertFalse(l.hasIsFromMockProvider());
        Assert.assertFalse(l.hasMockLocationsEnabled());
        Assert.assertEquals(0x0, l.getMockFlags());
    }

    @Test public void testHasMockShouldBeTrue() {
        BackgroundLocation l = new BackgroundLocation();
        l.setMockLocationsEnabled(true);
        l.setIsFromMockProvider(true);
        Assert.assertTrue(l.hasMockLocationsEnabled());
        Assert.assertTrue(l.areMockLocationsEnabled());
        Assert.assertEquals(0xF, l.getMockFlags());
    }

    @Test public void testIsFromMockProviderShouldBeTrue() {
        BackgroundLocation l = new BackgroundLocation();
        l.setIsFromMockProvider(true);
        Assert.assertTrue(l.hasIsFromMockProvider());
        Assert.assertTrue(l.isFromMockProvider());
        Assert.assertEquals(0x3, l.getMockFlags());
    }

    @Test public void testIsFromMockProviderShouldBeFalse() {
        BackgroundLocation l = new BackgroundLocation();
        l.setIsFromMockProvider(false);
        Assert.assertTrue(l.hasIsFromMockProvider());
        Assert.assertFalse(l.isFromMockProvider());
        Assert.assertEquals(0x2, l.getMockFlags());
    }

    @Test public void testAreMockLocationsEnabledShouldBeTrue() {
        BackgroundLocation l = new BackgroundLocation();
        l.setMockLocationsEnabled(true);
        Assert.assertTrue(l.hasMockLocationsEnabled());
        Assert.assertTrue(l.areMockLocationsEnabled());
        Assert.assertEquals(0xC, l.getMockFlags());
    }

    @Test public void testAreMockLocationsEnabledShouldBeFalse() {
        BackgroundLocation l = new BackgroundLocation();
        l.setMockLocationsEnabled(false);
        Assert.assertTrue(l.hasMockLocationsEnabled());
        Assert.assertFalse(l.areMockLocationsEnabled());
        Assert.assertEquals(0x8, l.getMockFlags());
    }

    @Test
    public void olderLocationShouldBeWorse() {

        BackgroundLocation netLocation = new BackgroundLocation();
        netLocation.setProvider("network");
        netLocation.setLatitude(49);
        netLocation.setLongitude(5);
        netLocation.setAccuracy(38);
        netLocation.setElapsedRealtimeNanos(1470776557324L * 1000000L);

        BackgroundLocation gpsLocation = new BackgroundLocation();
        gpsLocation.setProvider("gps");
        gpsLocation.setLatitude(49);
        gpsLocation.setLongitude(5);
        gpsLocation.setAccuracy(5);
        gpsLocation.setElapsedRealtimeNanos(1470773246000L * 1000000L);

        Assert.assertFalse(gpsLocation.isBetterLocationThan(netLocation));
    }

    @Test
    public void newerLocationShouldBeBetter() {

        BackgroundLocation netLocation = new BackgroundLocation();
        netLocation.setProvider("network");
        netLocation.setLatitude(49);
        netLocation.setLongitude(5);
        netLocation.setAccuracy(38);
        netLocation.setElapsedRealtimeNanos(0);

        BackgroundLocation gpsLocation = new BackgroundLocation();
        gpsLocation.setProvider("gps");
        gpsLocation.setLatitude(49);
        gpsLocation.setLongitude(5);
        gpsLocation.setAccuracy(105);
        gpsLocation.setElapsedRealtimeNanos(2000000000L * 60 * 2);

        Assert.assertFalse(netLocation.isBetterLocationThan(gpsLocation));
    }
}