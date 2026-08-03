package com.marianhello.bgloc.cordova;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.ArrayListLocationTemplate;
import com.marianhello.bgloc.data.HashMapLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by finch on 29.11.2017.
 */

public class ConfigMapper {
    private static final org.slf4j.Logger logger =
            com.marianhello.logging.LoggerManager.getLogger(ConfigMapper.class);

    public static Config fromJSONObject (JSONObject jObject) throws JSONException {
        Config config = new Config();

        if (jObject.has("stationaryRadius")) {
            config.setStationaryRadius(jObject.getDouble("stationaryRadius"));
        }
        if (jObject.has("distanceFilter")) {
            config.setDistanceFilter(jObject.getInt("distanceFilter"));
        }
        if (jObject.has("desiredAccuracy")) {
            config.setDesiredAccuracy(jObject.getInt("desiredAccuracy"));
        }
        if (jObject.has("debug")) {
            config.setDebugging(jObject.getBoolean("debug"));
        }
        if (jObject.has("notificationsEnabled")) {
            config.setNotificationsEnabled(jObject.getBoolean("notificationsEnabled"));
        }
        if (jObject.has("notificationTitle")) {
            config.setNotificationTitle(!jObject.isNull("notificationTitle") ? jObject.getString("notificationTitle") : Config.NullString);
        }
        if (jObject.has("notificationText")) {
            config.setNotificationText(!jObject.isNull("notificationText") ? jObject.getString("notificationText") : Config.NullString);
        }
        if (jObject.has("notificationSyncTitle")) {
            config.setNotificationSyncTitle(jObject.isNull("notificationSyncTitle") ? null : jObject.getString("notificationSyncTitle"));
        }
        if (jObject.has("notificationSyncText")) {
            config.setNotificationSyncText(jObject.isNull("notificationSyncText") ? null : jObject.getString("notificationSyncText"));
        }
        if (jObject.has("notificationSyncCompletedText")) {
            config.setNotificationSyncCompletedText(jObject.isNull("notificationSyncCompletedText") ? null : jObject.getString("notificationSyncCompletedText"));
        }
        if (jObject.has("notificationSyncFailedText")) {
            config.setNotificationSyncFailedText(jObject.isNull("notificationSyncFailedText") ? null : jObject.getString("notificationSyncFailedText"));
        }
        if (jObject.has("stopOnTerminate")) {
            config.setStopOnTerminate(jObject.getBoolean("stopOnTerminate"));
        }
        if (jObject.has("startOnBoot")) {
            config.setStartOnBoot(jObject.getBoolean("startOnBoot"));
        }
        if (jObject.has("locationProvider")) {
            config.setLocationProvider(jObject.getInt("locationProvider"));
        }
        if (jObject.has("interval")) {
            config.setInterval(jObject.getInt("interval"));
        }
        if (jObject.has("fastestInterval")) {
            config.setFastestInterval(jObject.getInt("fastestInterval"));
        }
        if (jObject.has("activitiesInterval")) {
            config.setActivitiesInterval(jObject.getInt("activitiesInterval"));
        }
        if (jObject.has("notificationIconColor")) {
            config.setNotificationIconColor(!jObject.isNull("notificationIconColor") ? jObject.getString("notificationIconColor") : Config.NullString);
        }
        if (jObject.has("notificationIconLarge")) {
            config.setLargeNotificationIcon(!jObject.isNull("notificationIconLarge") ? jObject.getString("notificationIconLarge") : Config.NullString);
        }
        if (jObject.has("notificationIconSmall")) {
            config.setSmallNotificationIcon(!jObject.isNull("notificationIconSmall") ? jObject.getString("notificationIconSmall") : Config.NullString);
        }
        if (jObject.has("startForeground")) {
            config.setStartForeground(jObject.getBoolean("startForeground"));
        }
        if (jObject.has("stopOnStillActivity")) {
            config.setStopOnStillActivity(jObject.getBoolean("stopOnStillActivity"));
        }
        if (jObject.has("url")) {
            config.setUrl(!jObject.isNull("url") ? jObject.getString("url") : Config.NullString);
        }
        if (jObject.has("syncUrl")) {
            config.setSyncUrl(!jObject.isNull("syncUrl") ? jObject.getString("syncUrl") : Config.NullString);
        }
        if (jObject.has("syncThreshold")) {
            config.setSyncThreshold(jObject.getInt("syncThreshold"));
        }
        if (jObject.has("sync")) {
            config.setSyncEnabled(jObject.getBoolean("sync"));
        }
        // headers (alias of httpHeaders)
        if (jObject.has("httpHeaders")) {
            config.setHttpHeaders(jObject.getJSONObject("httpHeaders"));
        }
        if (jObject.has("headers")) {
            config.setHttpHeaders(jObject.getJSONObject("headers"));
        }
        if (jObject.has("maxLocations")) {
            config.setMaxLocations(jObject.getInt("maxLocations"));
        }
        // bodyTemplate (alias of postTemplate)
        if (jObject.has("postTemplate")) {
            if (jObject.isNull("postTemplate")) {
                config.setTemplate(LocationTemplateFactory.getDefault());
            } else {
                Object postTemplate = jObject.get("postTemplate");
                config.setTemplate(LocationTemplateFactory.fromJSON(postTemplate));
            }
        }
        if (jObject.has("bodyTemplate")) {
            if (jObject.isNull("bodyTemplate")) {
                config.setTemplate(LocationTemplateFactory.getDefault());
            } else {
                Object bodyTemplate = jObject.get("bodyTemplate");
                config.setTemplate(LocationTemplateFactory.fromJSON(bodyTemplate));
            }
        }
        // v3.3 Phase 2: HTTP transport
        if (jObject.has("httpMethod") && !jObject.isNull("httpMethod")) {
            config.setHttpMethod(jObject.getString("httpMethod"));
        }
        if (jObject.has("syncHttpMethod") && !jObject.isNull("syncHttpMethod")) {
            config.setSyncHttpMethod(jObject.getString("syncHttpMethod"));
        }
        if (jObject.has("httpMode") && !jObject.isNull("httpMode")) {
            config.setHttpMode(jObject.getString("httpMode"));
        }
        if (jObject.has("syncMode") && !jObject.isNull("syncMode")) {
            config.setSyncMode(jObject.getString("syncMode"));
        }
        if (jObject.has("queryParams") && !jObject.isNull("queryParams")) {
            config.setQueryParams(jObject.getJSONObject("queryParams"));
        }
        if (jObject.has("heartbeatInterval") && !jObject.isNull("heartbeatInterval")) {
            config.setHeartbeatInterval(jObject.getInt("heartbeatInterval"));
        }
        if (jObject.has("mockLocationPolicy") && !jObject.isNull("mockLocationPolicy")) {
            config.setMockLocationPolicy(jObject.getString("mockLocationPolicy"));
        }
        // v4.0 Phase 6: drivingEvents
        if (jObject.has("drivingEvents") && !jObject.isNull("drivingEvents")) {
            JSONObject de = jObject.getJSONObject("drivingEvents");
            Config.DrivingEventsOptions opts = new Config.DrivingEventsOptions();
            if (de.has("enabled"))          opts.enabled            = de.getBoolean("enabled");
            if (de.has("speedLimit"))       opts.speedLimitKmh      = de.getDouble("speedLimit");
            if (de.has("minMovingSpeed"))   opts.minMovingSpeedMps  = de.getDouble("minMovingSpeed");
            if (de.has("stoppedDuration"))  opts.stoppedDurationMs  = de.getLong("stoppedDuration");
            if (de.has("minTripSpeed"))     opts.minTripSpeedMps    = de.getDouble("minTripSpeed");
            if (de.has("minTripDuration"))  opts.minTripDurationMs  = de.getLong("minTripDuration");
            // v4.1
            if (de.has("hardBrakeMps2"))    opts.hardBrakeMps2      = de.getDouble("hardBrakeMps2");
            if (de.has("rapidAccelMps2"))   opts.rapidAccelMps2     = de.getDouble("rapidAccelMps2");
            if (de.has("sharpTurnDegPerSec")) opts.sharpTurnDegPerSec = de.getDouble("sharpTurnDegPerSec");
            if (de.has("crashImpactKmh"))   opts.crashImpactKmh     = de.getDouble("crashImpactKmh");
            if (de.has("crashWindowMs"))    opts.crashWindowMs      = de.getLong("crashWindowMs");
            // v4.2 sensor fusion
            if (de.has("sensorFusion"))         opts.sensorFusion           = de.getBoolean("sensorFusion");
            if (de.has("crashImpactG"))         opts.crashImpactG           = de.getDouble("crashImpactG");
            if (de.has("sensorCrashCooldownMs"))opts.sensorCrashCooldownMs  = de.getLong("sensorCrashCooldownMs");
            if (de.has("phoneUsageWindowMs"))   opts.phoneUsageWindowMs     = de.getLong("phoneUsageWindowMs");
            if (de.has("phoneUsageCooldownMs")) opts.phoneUsageCooldownMs   = de.getLong("phoneUsageCooldownMs");
            config.setDrivingEvents(opts);
        }
        if (jObject.has("enableWatchdog")) {
            config.setEnableWatchdog(jObject.getBoolean("enableWatchdog"));
        }
        if (jObject.has("showTime")) {
            config.setShowTime(jObject.getBoolean("showTime"));
        }
        if (jObject.has("showDistance")) {
            config.setShowDistance(jObject.getBoolean("showDistance"));
        }
        // v4.4: opt-out for battery snapshot in payload.
        if (jObject.has("includeBattery")) {
            config.setIncludeBattery(jObject.getBoolean("includeBattery"));
        }
        // v4.5.1: battery-saving knobs.
        if (jObject.has("wakeLockMode") && !jObject.isNull("wakeLockMode")) {
            config.setWakeLockMode(jObject.getString("wakeLockMode"));
        }
        if (jObject.has("stationaryTimeout") && !jObject.isNull("stationaryTimeout")) {
            config.setStationaryTimeout(jObject.getInt("stationaryTimeout"));
        }
        if (jObject.has("stationaryPollInterval") && !jObject.isNull("stationaryPollInterval")) {
            config.setStationaryPollInterval(jObject.getInt("stationaryPollInterval"));
        }
        if (jObject.has("stationaryPollFast") && !jObject.isNull("stationaryPollFast")) {
            config.setStationaryPollFast(jObject.getInt("stationaryPollFast"));
        }
        // v4.5.4: provider hardening
        if (jObject.has("activityConfidenceThreshold") && !jObject.isNull("activityConfidenceThreshold")) {
            config.setActivityConfidenceThreshold(jObject.getInt("activityConfidenceThreshold"));
        }
        // v5.0.1 — paridad con iOS (MAURConfig.resetMaxAcceptedAccuracy). Un `null` explícito
        // significa "quita el filtro", y aquí se descartaba: quien configuraba
        // maxAcceptedAccuracy y luego intentaba desactivarlo (túnel, urbano denso) seguía
        // perdiendo TODOS los fixes, sin forma de recuperarse salvo reinstalando.
        if (jObject.has("maxAcceptedAccuracy")) {
            if (jObject.isNull("maxAcceptedAccuracy")) {
                config.setResetMaxAcceptedAccuracy(true);
            } else {
                config.setMaxAcceptedAccuracy((float) jObject.getDouble("maxAcceptedAccuracy"));
            }
        }

        validate(config);
        return config;
    }

    /**
     * Corrige, con log, los valores que la capa nativa no puede honrar.
     *
     * <p><b>NO LANZA.</b> v5.0.0 introdujo esta validación lanzando {@code JSONException}, y el
     * llamante ({@code BackgroundGeolocationPlugin}) la convierte en {@code CONFIGURE_ERROR}: la
     * llamada a {@code configure()} entera se rechaza, no se persiste nada y {@code start()} no
     * llega a arrancar. Es decir, un solo campo con un valor inesperado deja la app SIN TRACKING.
     *
     * <p>Eso rompe despliegues que funcionaban: hasta 5.0.0 la propia {@code docs/traccar.md}
     * recomendaba {@code syncHttpMethod: 'GET'}, así que cualquier flota que copió el ejemplo
     * oficial dejaría de trackear al actualizar. Lo mismo con {@code httpMode: 'batched'} (un
     * typo), {@code locationProvider: 3} o un {@code wakeLockMode} desconocido: v4 los aceptaba.
     *
     * <p>Contrato actual: valor inválido → se registra un ERROR con el nombre del campo y el valor
     * recibido, y se cae al valor por defecto. Se conserva el objetivo de v5.0.0 (que un valor malo
     * no llegue en silencio al servicio) sin el modo de fallo de v5.0.0 (matar la configuración).
     */
    private static void validate(Config config) {
        Config defaults = Config.getDefault();

        if (!requireOneOf("locationProvider", config.getLocationProvider(), 0, 1, 2)) {
            config.setLocationProvider(defaults.getLocationProvider());
        }
        if (!requireNonNegative("interval", config.getInterval())) {
            config.setInterval(defaults.getInterval());
        }
        if (!requireNonNegative("fastestInterval", config.getFastestInterval())) {
            config.setFastestInterval(defaults.getFastestInterval());
        }
        if (!requireNonNegative("activitiesInterval", config.getActivitiesInterval())) {
            config.setActivitiesInterval(defaults.getActivitiesInterval());
        }
        if (!requireNonNegative("heartbeatInterval", config.getHeartbeatInterval())) {
            config.setHeartbeatInterval(defaults.getHeartbeatInterval());
        }
        if (!requireNonNegative("distanceFilter", config.getDistanceFilter())) {
            config.setDistanceFilter(defaults.getDistanceFilter());
        }
        // v4 aceptaba 0 con semantica propia ("sincroniza en cada posicion") — sigue siendo valido.
        if (!requireNonNegative("syncThreshold", config.getSyncThreshold())) {
            config.setSyncThreshold(defaults.getSyncThreshold());
        }
        // v4 aceptaba 0 ("no persistir") — sigue siendo valido.
        if (!requireNonNegative("maxLocations", config.getMaxLocations())) {
            config.setMaxLocations(defaults.getMaxLocations());
        }
        if (!requireRange("activityConfidenceThreshold", config.getActivityConfidenceThreshold(), 0, 100)) {
            config.setActivityConfidenceThreshold(defaults.getActivityConfidenceThreshold());
        }

        if (config.getStationaryRadius() != null && config.getStationaryRadius() < 0) {
            logger.error("Config invalida: stationaryRadius debe ser >= 0, llego {}. Se usa el valor por defecto.",
                    config.getStationaryRadius());
            config.setStationaryRadius(defaults.getStationaryRadius());
        }
        if (config.getMaxAcceptedAccuracy() != null && config.getMaxAcceptedAccuracy() < 0) {
            logger.error("Config invalida: maxAcceptedAccuracy debe ser >= 0, llego {}. Se desactiva el filtro.",
                    config.getMaxAcceptedAccuracy());
            config.setMaxAcceptedAccuracy(null);
        }

        if (!requireOneOfString("httpMethod", config.getHttpMethod(), "POST", "GET", "PUT", "PATCH")) {
            config.setHttpMethod(defaults.getHttpMethod());
        }
        // R14: GET fuera del sync. La URL del lote se resuelve con location = null, asi que ningun
        // placeholder por posicion se sustituye: saldria un GET sin datos y un 200 borraria el lote.
        // Se cae a POST (con log) en vez de rechazar la configuracion entera: hasta 5.0.0 la propia
        // docs/traccar.md recomendaba syncHttpMethod:'GET', asi que rechazar dejaba sin tracking a
        // quien copio el ejemplo oficial.
        if (!requireOneOfString("syncHttpMethod", config.getSyncHttpMethod(), "POST", "PUT", "PATCH")) {
            config.setSyncHttpMethod(defaults.getSyncHttpMethod());
        }
        if (!requireOneOfString("httpMode", config.getHttpMode(), "batch", "single")) {
            config.setHttpMode(defaults.getHttpMode());
        }
        if (!requireOneOfString("syncMode", config.getSyncMode(), "batch", "single")) {
            config.setSyncMode(defaults.getSyncMode());
        }
        if (!requireOneOfString("mockLocationPolicy", config.getMockLocationPolicy(), "allow", "flag", "drop")) {
            config.setMockLocationPolicy(defaults.getMockLocationPolicy());
        }
        if (!requireOneOfString("wakeLockMode", config.getWakeLockMode(), "none", "posting", "always")) {
            config.setWakeLockMode(defaults.getWakeLockMode());
        }
    }

    /** true si el valor es válido; si no, loguea y devuelve false para que el llamante lo reponga. */
    private static boolean requireOneOf(String name, Integer value, int... allowed) {
        if (value == null) return true;
        for (int a : allowed) {
            if (value == a) return true;
        }
        logger.error("Config invalida: {} debe ser uno de {}, llego {}. Se usa el valor por defecto.",
                name, java.util.Arrays.toString(allowed), value);
        return false;
    }

    private static boolean requireOneOfString(String name, String value, String... allowed) {
        if (value == null) return true;
        for (String a : allowed) {
            if (a.equalsIgnoreCase(value)) return true;
        }
        logger.error("Config invalida: {} debe ser uno de {}, llego '{}'. Se usa el valor por defecto.",
                name, java.util.Arrays.toString(allowed), value);
        return false;
    }

    private static boolean requireNonNegative(String name, Integer value) {
        if (value == null || value >= 0) return true;
        logger.error("Config invalida: {} debe ser >= 0, llego {}. Se usa el valor por defecto.", name, value);
        return false;
    }

    private static boolean requireRange(String name, Integer value, int min, int max) {
        if (value == null || (value >= min && value <= max)) return true;
        logger.error("Config invalida: {} debe estar entre {} y {}, llego {}. Se usa el valor por defecto.",
                name, min, max, value);
        return false;
    }

    public static JSONObject toJSONObject(Config config) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("stationaryRadius", config.getStationaryRadius());
        json.put("distanceFilter", config.getDistanceFilter());
        json.put("desiredAccuracy", config.getDesiredAccuracy());
        json.put("debug", config.isDebugging());
        json.put("notificationsEnabled", config.getNotificationsEnabled());
        json.put("notificationTitle", !Config.isNullString(config.getNotificationTitle()) ? config.getNotificationTitle() : JSONObject.NULL);
        json.put("notificationText", !Config.isNullString(config.getNotificationText()) ? config.getNotificationText() : JSONObject.NULL);
        json.put("notificationSyncTitle", config.getNotificationSyncTitle());
        json.put("notificationSyncText", config.getNotificationSyncText());
        json.put("notificationSyncCompletedText", config.getNotificationSyncCompletedText());
        json.put("notificationSyncFailedText", config.getNotificationSyncFailedText());
        json.put("notificationIconLarge", !Config.isNullString(config.getLargeNotificationIcon()) ? config.getLargeNotificationIcon() : JSONObject.NULL);
        json.put("notificationIconSmall", !Config.isNullString(config.getSmallNotificationIcon()) ? config.getSmallNotificationIcon() : JSONObject.NULL);
        json.put("notificationIconColor", !Config.isNullString(config.getNotificationIconColor()) ? config.getNotificationIconColor() : JSONObject.NULL);
        json.put("stopOnTerminate", config.getStopOnTerminate());
        json.put("startOnBoot", config.getStartOnBoot());
        json.put("startForeground", config.getStartForeground());
        json.put("locationProvider", config.getLocationProvider());
        json.put("interval", config.getInterval());
        json.put("fastestInterval", config.getFastestInterval());
        json.put("activitiesInterval", config.getActivitiesInterval());
        json.put("stopOnStillActivity", config.getStopOnStillActivity());
        json.put("url", !Config.isNullString(config.getUrl()) ? config.getUrl() : JSONObject.NULL);
        json.put("syncUrl", !Config.isNullString(config.getSyncUrl())  ? config.getSyncUrl() : JSONObject.NULL);
        json.put("syncThreshold", config.getSyncThreshold());
        json.put("sync", config.getSyncEnabled());
        json.put("httpHeaders", new JSONObject(config.getHttpHeaders()));
        json.put("maxLocations", config.getMaxLocations());
        json.put("enableWatchdog", Boolean.TRUE.equals(config.getEnableWatchdog()));
        json.put("showTime", Boolean.TRUE.equals(config.getShowTime()));
        json.put("showDistance", Boolean.TRUE.equals(config.getShowDistance()));
        LocationTemplate tpl = config.getTemplate();
        Object template = JSONObject.NULL;
        if (tpl instanceof HashMapLocationTemplate) {
            Map map = ((HashMapLocationTemplate)tpl).toMap();
            if (map != null) {
                template = new JSONObject(map);
            }
        } else if (tpl instanceof ArrayListLocationTemplate) {
            Object[] keys = ((ArrayListLocationTemplate)tpl).toArray();
            if (keys != null) {
                template = new JSONArray(Arrays.asList(keys));
            }
        }

        json.put("postTemplate", template);

        json.put("httpMethod", config.getHttpMethod());
        json.put("syncHttpMethod", config.getSyncHttpMethod());
        json.put("httpMode", config.getHttpMode());
        json.put("syncMode", config.getSyncMode());
        json.put("queryParams", config.getQueryParams() != null ? new JSONObject(config.getQueryParams()) : JSONObject.NULL);
        json.put("heartbeatInterval", config.getHeartbeatInterval());
        json.put("mockLocationPolicy", config.getMockLocationPolicy());

        Config.DrivingEventsOptions de = config.getDrivingEvents();
        if (de != null) {
            JSONObject deJson = new JSONObject();
            deJson.put("enabled", de.enabled);
            deJson.put("speedLimit", de.speedLimitKmh);
            deJson.put("minMovingSpeed", de.minMovingSpeedMps);
            deJson.put("stoppedDuration", de.stoppedDurationMs);
            deJson.put("minTripSpeed", de.minTripSpeedMps);
            deJson.put("minTripDuration", de.minTripDurationMs);
            deJson.put("hardBrakeMps2", de.hardBrakeMps2);
            deJson.put("rapidAccelMps2", de.rapidAccelMps2);
            deJson.put("sharpTurnDegPerSec", de.sharpTurnDegPerSec);
            deJson.put("crashImpactKmh", de.crashImpactKmh);
            deJson.put("crashWindowMs", de.crashWindowMs);
            // v4.2 sensor fusion
            deJson.put("sensorFusion", de.sensorFusion);
            deJson.put("crashImpactG", de.crashImpactG);
            deJson.put("sensorCrashCooldownMs", de.sensorCrashCooldownMs);
            deJson.put("phoneUsageWindowMs", de.phoneUsageWindowMs);
            deJson.put("phoneUsageCooldownMs", de.phoneUsageCooldownMs);
            json.put("drivingEvents", deJson);
        }
        // v4.4 battery
        json.put("includeBattery", config.getIncludeBattery() != null ? config.getIncludeBattery() : true);
        // v4.5.1 battery-saving knobs
        json.put("wakeLockMode", config.getWakeLockMode() != null ? config.getWakeLockMode() : "posting");
        json.put("stationaryTimeout", config.getStationaryTimeout());
        json.put("stationaryPollInterval", config.getStationaryPollInterval());
        json.put("stationaryPollFast", config.getStationaryPollFast());
        // v4.5.4 provider hardening
        json.put("activityConfidenceThreshold", config.getActivityConfidenceThreshold());
        json.put("maxAcceptedAccuracy", config.getMaxAcceptedAccuracy());

        return json;
    }
}
