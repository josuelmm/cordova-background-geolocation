package com.marianhello.backgroundgeolocation;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.HashMapLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;

/**
 * Created by finch on 9.12.2017.
 */

public class HashMapLocationTemplateTest {
    @Test
    public void testObjectTemplateToString() throws Exception {
        HashMap props = new HashMap();
        props.put("foo", "bar");
        props.put("pretzels", 123);
        HashMapLocationTemplate tpl = new HashMapLocationTemplate(props);

        TestHelper.assertJsonEquals("{\"foo\":\"bar\",\"pretzels\":123}", tpl.toString());
    }

    @Test
    public void testLocationToJSONObject() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setLocationId(11L);
        location.setProvider("test");
        location.setElapsedRealtimeNanos(2000000000L * 60 * 2);
        location.setAltitude(100);
        location.setLatitude(49);
        location.setLongitude(5);
        location.setLocationProvider(1);
        location.setAccuracy(105);
        location.setSpeed(50);
        location.setBearing(1);

        HashMap map = new HashMap<String, String>();
        map.put("Id", "@id");
        map.put("Provider", "@provider");
        map.put("Time", "@time");
        map.put("Altitude", "@altitude");
        map.put("Latitude", "@latitude");
        map.put("Longitude", "@longitude");
        map.put("Foo", "bar");
        map.put("LocationProvider", "@locationProvider");
        map.put("Accuracy", "@accuracy");
        map.put("Speed", "@speed");
        map.put("Bearing", "@bearing");
        map.put("Pretzels", 123);

        LocationTemplate tpl = new HashMapLocationTemplate(map);
        JSONObject expected = (JSONObject) tpl.locationToJson(location);

        Assert.assertEquals(expected.get("Id"), location.getLocationId());
        Assert.assertEquals(expected.get("Provider"), location.getProvider());
        Assert.assertEquals(expected.get("Time"), location.getTime());
        Assert.assertEquals(expected.get("Altitude"), location.getAltitude());
        Assert.assertEquals(expected.get("Latitude"), location.getLatitude());
        Assert.assertEquals(expected.get("Longitude"), location.getLongitude());
        Assert.assertEquals(expected.get("Foo"), "bar");
        Assert.assertEquals(expected.get("LocationProvider"), location.getLocationProvider());
        Assert.assertEquals(expected.get("Accuracy"), location.getAccuracy());
        Assert.assertEquals(expected.get("Speed"), location.getSpeed());
        Assert.assertEquals(expected.get("Bearing"), location.getBearing());
        Assert.assertEquals(expected.get("Pretzels"), 123);
    }

    @Test
    public void testLocationToJSONObjectFactory() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setLocationId(11L);
        location.setProvider("test");
        location.setElapsedRealtimeNanos(2000000000L * 60 * 2);
        location.setAltitude(100);
        location.setLatitude(49);
        location.setLongitude(5);
        location.setLocationProvider(1);
        location.setAccuracy(105);
        location.setSpeed(50);
        location.setBearing(1);

        HashMap map = new HashMap();
        map.put("Id", "@id");
        map.put("Provider", "@provider");
        map.put("Time", "@time");
        map.put("Altitude", "@altitude");
        map.put("Latitude", "@latitude");
        map.put("Longitude", "@longitude");
        map.put("Foo", "bar");
        map.put("LocationProvider", "@locationProvider");
        map.put("Accuracy", "@accuracy");
        map.put("Speed", "@speed");
        map.put("Bearing", "@bearing");
        map.put("Pretzels", 123);

        JSONObject json = new JSONObject(map);
        LocationTemplate tpl = LocationTemplateFactory.fromJSON(json);

        JSONObject expected = (JSONObject) tpl.locationToJson(location);

        Assert.assertEquals(expected.get("Id"), location.getLocationId());
        Assert.assertEquals(expected.get("Provider"), location.getProvider());
        Assert.assertEquals(expected.get("Time"), location.getTime());
        Assert.assertEquals(expected.get("Altitude"), location.getAltitude());
        Assert.assertEquals(expected.get("Latitude"), location.getLatitude());
        Assert.assertEquals(expected.get("Longitude"), location.getLongitude());
        Assert.assertEquals(expected.get("Foo"), "bar");
        Assert.assertEquals(expected.get("LocationProvider"), location.getLocationProvider());
        Assert.assertEquals(expected.get("Accuracy"), location.getAccuracy());
        Assert.assertEquals(expected.get("Speed"), location.getSpeed());
        Assert.assertEquals(expected.get("Bearing"), location.getBearing());
    }

    @Test
    public void testNullToString() {
        HashMapLocationTemplate tpl = new HashMapLocationTemplate((HashMap)null);
        Assert.assertEquals("null", tpl.toString());
    }

    @Test
    public void testNestedObjectTemplate() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setLocationId(11L);
        location.setProvider("test");
        location.setElapsedRealtimeNanos(2000000000L * 60 * 2);
        location.setAltitude(100);
        location.setLatitude(49);
        location.setLongitude(5);
        location.setLocationProvider(1);
        location.setAccuracy(105);
        location.setSpeed(50);
        location.setBearing(1);

        JSONObject template = new JSONObject(
            "{\"data\":{\"Id\":\"@id\"," +
                    "\"Provider\":\"@provider\"," +
                    "\"Time\":\"@time\"," +
                    "\"Altitude\":\"@altitude\"," +
                    "\"Latitude\":\"@latitude\"," +
                    "\"Longitude\":\"@longitude\"," +
                    "\"Foo\":\"bar\"," +
                    "\"LocationProvider\":\"@locationProvider\"," +
                    "\"Accuracy\":\"@accuracy\"," +
                    "\"Speed\":\"@speed\"," +
                    "\"Bearing\":\"@bearing\"}" +
                    "}"
        );
        LocationTemplate tpl = LocationTemplateFactory.fromJSON(template);

        JSONObject json = ((JSONObject) tpl.locationToJson(location));
        JSONObject expected = json.getJSONObject("data");

        Assert.assertEquals(expected.get("Id"), location.getLocationId());
        Assert.assertEquals(expected.get("Provider"), location.getProvider());
        Assert.assertEquals(expected.get("Time"), location.getTime());
        Assert.assertEquals(expected.get("Altitude"), location.getAltitude());
        Assert.assertEquals(expected.get("Latitude"), location.getLatitude());
        Assert.assertEquals(expected.get("Longitude"), location.getLongitude());
        Assert.assertEquals(expected.get("Foo"), "bar");
        Assert.assertEquals(expected.get("LocationProvider"), location.getLocationProvider());
        Assert.assertEquals(expected.get("Accuracy"), location.getAccuracy());
        Assert.assertEquals(expected.get("Speed"), location.getSpeed());
        Assert.assertEquals(expected.get("Bearing"), location.getBearing());
    }


    @Test
    public void testNestedArrayTemplate() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setLocationId(11L);
        location.setProvider("test");
        location.setElapsedRealtimeNanos(2000000000L * 60 * 2);
        location.setAltitude(100);
        location.setLatitude(49);
        location.setLongitude(5);
        location.setLocationProvider(1);
        location.setAccuracy(105);
        location.setSpeed(50);
        location.setBearing(1);

        JSONObject template = new JSONObject(
                "{\"data\":[\"@id\",\"@provider\",\"@time\",\"@altitude\",\"@latitude\"," +
                "\"@longitude\",\"foo\",\"@locationProvider\",\"@accuracy\",\"@speed\"," +
                "\"@bearing\",123]}"
        );
        LocationTemplate tpl = LocationTemplateFactory.fromJSON(template);

        JSONObject json = ((JSONObject) tpl.locationToJson(location));
        JSONArray expected = json.getJSONArray("data");

        Assert.assertEquals(expected.get(0), location.getLocationId());
        Assert.assertEquals(expected.get(1), location.getProvider());
        Assert.assertEquals(expected.get(2), location.getTime());
        Assert.assertEquals(expected.get(3), location.getAltitude());
        Assert.assertEquals(expected.get(4), location.getLatitude());
        Assert.assertEquals(expected.get(5), location.getLongitude());
        Assert.assertEquals(expected.get(6), "foo");
        Assert.assertEquals(expected.get(7), location.getLocationProvider());
        Assert.assertEquals(expected.get(8), location.getAccuracy());
        Assert.assertEquals(expected.get(9), location.getSpeed());
        Assert.assertEquals(expected.get(10), location.getBearing());
        Assert.assertEquals(expected.get(11), 123);

    }

    /**
     * v5.0.1 — un placeholder que la posición no puede resolver debe salir como JSON null, no
     * como el literal "@heading". La ruta de sync (BatchManager) e iOS ya lo hacían así y es lo
     * que documenta docs/api.md; en el POST en tiempo real salía el literal, y Traccar/OsmAnd
     * responden 400 (NumberFormatException) ante "speed=@speed".
     */
    @Test
    public void testUnresolvedPlaceholderBecomesNull() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setLatitude(40.21);
        location.setLongitude(-3.7);

        JSONObject template = new JSONObject(
                "{\"lat\":\"@latitude\",\"nope\":\"@thisKeyDoesNotExist\",\"lit\":\"plain\"}"
        );
        LocationTemplate tpl = LocationTemplateFactory.fromJSON(template);
        JSONObject json = (JSONObject) tpl.locationToJson(location);

        Assert.assertEquals(location.getLatitude(), json.get("lat"));
        Assert.assertTrue("el placeholder sin valor debe ser null", json.isNull("nope"));
        Assert.assertEquals("una cadena normal del template se conserva", "plain", json.get("lit"));
    }

    /** v5.0.1 — `@timestamp_iso` estaba documentado pero no implementado en ninguna plataforma. */
    @Test
    public void testTimestampIsoPlaceholder() throws JSONException {
        BackgroundLocation location = new BackgroundLocation();
        location.setTime(1000000000000L); // 2001-09-09T01:46:40Z

        JSONObject template = new JSONObject("{\"t\":\"@timestamp_iso\",\"s\":\"@time_seconds\"}");
        LocationTemplate tpl = LocationTemplateFactory.fromJSON(template);
        JSONObject json = (JSONObject) tpl.locationToJson(location);

        Assert.assertEquals("2001-09-09T01:46:40Z", json.get("t"));
        Assert.assertEquals(1000000000L, json.get("s"));
    }
}
