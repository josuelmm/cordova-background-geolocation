package com.marianhello.backgroundgeolocation;

import org.json.JSONObject;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TestHelper {

    /**
     * Compares two JSON objects by content instead of by serialized string.
     *
     * Asserting on `JSONObject.toString()` couples the test to map iteration order, which is
     * unspecified. Key order is not part of the JSON contract, so compare semantically.
     */
    static void assertJsonEquals(String expected, String actual) throws Exception {
        org.junit.Assert.assertEquals(canonicalize(new JSONObject(expected)),
                canonicalize(new JSONObject(actual)));
    }

    private static String canonicalize(JSONObject obj) throws Exception {
        List<String> keys = new ArrayList<String>();
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            keys.add(it.next());
        }
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object value = obj.get(key);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(JSONObject.quote(key)).append(':');
            sb.append(value instanceof JSONObject ? canonicalize((JSONObject) value) : String.valueOf(value));
        }
        return sb.append('}').toString();
    }
    /**
     * Sets a static (possibly final) field.
     *
     * NOTE: this used to clear Modifier.FINAL through the `Field.modifiers` reflection hack,
     * which JDK 12 removed — every test using it failed with NoSuchFieldException once the
     * build moved off JDK 8. Robolectric's ReflectionHelpers does the same job supported.
     */
    static void setFinalStatic(Field field, Object newValue) throws Exception {
        ReflectionHelpers.setStaticField(field.getDeclaringClass(), field.getName(), newValue);
    }
}
