# Releases 3.3.0 → 4.2.0 — resumen ejecutivo

Este documento resume todo lo entregado entre `3.2.0` y `4.2.0`. Para detalle por release ver `CHANGELOG.md`.

---

## v3.3.0 — Auto-start Android + HTTP transport (Fases 1-2)

### Phase 1 — Auto-start Android
- `BootCompletedReceiver`: ahora escucha `BOOT_COMPLETED`, `QUICKBOOT_POWERON` (HTC, MIUI), `com.htc.intent.action.QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED`.
- Validación de `ACCESS_BACKGROUND_LOCATION` en Android 10+ con flujo runtime requerido en la app host.
- Try/catch de `ForegroundServiceStartNotAllowedException` (Android 12+) con log explícito. **NO** se usa WorkManager para tracking.
- `LocationServiceImpl`: corregido hardcode `0x8`; lectura dinámica del manifest via `getManifestForegroundServiceType()`. Constante muerta `FOREGROUND_SERVICE_TYPE_LOCATION = 4` eliminada (valor real es 8).
- `LocationServiceProxy`: ya no cae a `startService` silencioso si falta permiso.
- `plugin.xml`: `foregroundServiceType="location"` (sin `dataSync`); permiso `FOREGROUND_SERVICE_DATA_SYNC` removido; `<uses-library org.apache.http.legacy>` removido (sin imports en el código).
- `dependencies.gradle`: `useLibrary 'org.apache.http.legacy'` removido.
- `build.gradle`: `jcenter()` → `mavenCentral()`. `compileSdk` 28 → 35.
- Engines: `cordova >= 10.0.0`, `cordova-android >= 12.0.0`.
- iOS: documentación de la limitación (Apple no permite auto-start al encender).

### Phase 2 — HTTP transport personalizable (backend-agnóstico)
- Nuevas opciones: `httpMethod`, `syncHttpMethod` (`POST | GET | PUT | PATCH`), `httpMode`, `syncMode` (`batch | single`), `headers` (alias `httpHeaders`), `bodyTemplate` (alias `postTemplate`), `queryParams`.
- URL templating con placeholders: `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`, `{is_moving}` y claves de `queryParams`.
- Eliminado hardcode `POST` en Android `HttpPostService` e iOS `MAURPostLocationTask`/`MAURBackgroundSync`.
- Helpers nuevos: `com.marianhello.bgloc.http.UrlTemplateResolver` (Android) y `MAURUrlTemplateResolver` (iOS).
- Modo `single` requerido cuando `httpMethod = 'GET'`. Habilita Traccar Osmand + cualquier backend custom **sin lógica hardcodeada**.
- Compatibilidad: apps con solo `url + httpHeaders + postTemplate` siguen funcionando.

### Bugs corregidos
- iOS `MAURConfig.fromDictionary`: `isNull(activitiesInterval)` → `isNotNull` (descartaba el valor enviado).
- Android `HttpPostService.postJSONString`: `Content-Length` con bytes UTF-8 (`getBytes(UTF_8).length`) en lugar de `String.length()`.

---

## v3.4.0 — Modernización de location APIs (Phase 3)

### Android
- `ActivityRecognitionLocationProvider`: `LocationRequest.Builder(priority, intervalMillis)` + `Priority.PRIORITY_*`. Reemplaza la API deprecated en `play-services-location 21.0.0+`.
- `RawLocationProvider`: `Criteria` API removida. Selección GPS-first / Network-fallback explícita.
- `DistanceFilterLocationProvider`: `Criteria` API totalmente removida. `getBestProvider(criteria, true)` → `pickProvider()`. `requestSingleUpdate(criteria, ...)` → `requestSingleUpdate(provider, PendingIntent)` con try/catch ampliado.
- `plugin.xml`: `GOOGLE_PLAY_SERVICES_VERSION` default `17+` → `21.0.1`.

### iOS
- `MAURPostLocationTask`: `[NSURLConnection sendSynchronousRequest:returningResponse:error:]` (deprecated iOS 9) → `NSURLSession dataTaskWithRequest:completionHandler:` con `dispatch_semaphore`.
- `MAURDistanceFilterLocationProvider`: callback `locationManagerDidChangeAuthorization:` (iOS 14+) + `accuracyAuthorization` con fallback al legacy.
- Nueva opción `showsBackgroundLocationIndicator` (iOS 11+).

### Conservados (con justificación)
- `LocationManager.getLastKnownLocation()` se conserva (NO está deprecated).
- `LocationManager.requestSingleUpdate(String, PendingIntent)` se conserva — única vía con `PendingIntent` para wake-up desde background.
- `AlarmManager.setInexactRepeating` se conserva — Doze lo difiere a 9-15 min en background prolongado pero sigue funcional. Reemplazo planificado.

---

## v3.5.0 — Diagnóstico, sync events, mockLocationPolicy, heartbeat (Phase 4)

### `getDiagnostics()` — explicación de "el tracking no corre"
- Android: `isRunning`, `locationServicesEnabled`, `fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted`, `notificationPermissionGranted`, `activityRecognitionGranted`, `batteryOptimizationIgnored`, `manufacturer`, `lastLocationAt`, `pendingSyncCount`, `startOnBoot`, `foregroundServiceType`.
- iOS: `preciseLocationEnabled` (iOS 14+), `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus`, `authorizationStatusText`.

### Sync events (nativos, end-to-end)
- `syncStart`, `syncProgress`, `syncSuccess` (con `sent`), `syncError` (con `httpStatus`, `message`).
- Android: `MSG_ON_SYNC_START/SUCCESS/ERROR/PROGRESS` (108-111) → `PluginDelegate` (default no-op) → `BackgroundGeolocationPlugin` JS event.
- iOS: `MAURBackgroundSyncDid{Start,Succeed,Fail,Progress}Notification` posteadas por `MAURBackgroundSync`, observadas por `CDVBackgroundGeolocation`.

### Heartbeat (nativo, end-to-end)
- Config: `heartbeatInterval` (ms; 0 disabled).
- Android: `ScheduledExecutorService` en `LocationServiceImpl`. Tick broadcasts `MSG_ON_HEARTBEAT` (112) con la última `BackgroundLocation`.
- iOS: `NSTimer` en `MAURBackgroundGeolocationFacade`. Tick postea `MAURHeartbeatNotification` con la última `MAURLocation`.
- En las primeras ticks antes del primer fix la app recibe el evento sin payload; el tipo TS lo refleja con `(location?: Location)`.

### `mockLocationPolicy: 'allow' | 'flag' | 'drop'`
- Aplica en Android `PostLocationTask` (línea inicial de `add()`) e iOS `MAURPostLocationTask` (callback de `add:`).
- `'drop'` descarta antes de persistir/postear; `'flag'` deja la flag `isFromMockProvider` / `simulated` intacta para que el server filtre; `'allow'` (default) preserva comportamiento.

### Bugs corregidos
- iOS `getPluginVersion` retornaba "3.2.0" hardcoded → "3.5.0".
- iOS `MAURBackgroundSync.tasks` nunca se inicializaba (`addObject:`/`cancel`/`status` no-op'd sobre nil) → `tasks = [[NSMutableArray alloc] init]` en `init`.
- TS `Event` union ampliado para cubrir los 5 nuevos eventos como string literales.
- TS overloads de `on()` añadidos para `'heartbeat'`, `'syncStart'`, `'syncProgress'`, `'syncSuccess'`, `'syncError'`.

---

## v3.6.0 — Battery / OEM helpers (Phase 5)

### 5 métodos nuevos
- `isIgnoringBatteryOptimizations()` — Android `PowerManager.isIgnoringBatteryOptimizations(packageName)`. iOS resuelve `true`.
- `requestIgnoreBatteryOptimizations()` — Android `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. iOS no-op.
- `openBatterySettings()` — Android `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` con fallback a app-info. iOS abre `UIApplicationOpenSettingsURLString`.
- `openAutoStartSettings()` — Android: tabla `ComponentName` por OEM (Xiaomi MIUI, Huawei EMUI/Honor, Oppo ColorOS, Vivo FunTouch, OnePlus, Asus). Samsung cae a app-info por falta de componente estable. Retorna `{ opened, manufacturer, screen }`.
- `getManufacturerHelp()` — pasos OEM-específicos para mostrar en pantalla de ayuda. Cubre Xiaomi/Huawei/Oppo/Vivo/Samsung/OnePlus/Asus + fallback genérico Android + Apple-specific iOS.

### Implementación
- Nueva clase `com.marianhello.bgloc.oem.BatteryOemHelper` (registrada en `plugin.xml`).
- 5 selectores en `CDVBackgroundGeolocation.m` con `UIApplicationOpenSettingsURLString` y `manufacturer: 'apple'`.
- Angular service re-exporta los 5 métodos con tipos fuertes.

---

## v4.0.0 — Driver insights (Phase 6, GPS-only)

### 7 eventos nuevos (nativos, end-to-end)
- `tripStart` — speed sostenida ≥ `minTripSpeed` durante `minTripDuration`. Payload: location.
- `tripEnd` — pasa a "stopped" durante un trip activo. Payload: `{ location, distance (m), durationMs }`.
- `moving` — speed cruzó `minMovingSpeed`. Payload: location.
- `stopped` — speed bajo umbral durante `stoppedDuration`. Payload: location.
- `speeding` — speed cruzó `speedLimit` (km/h). Re-arma al bajar. Payload: `{ location, speedKmh, limitKmh }`.
- `providerChange` — provider nativo cambió (gps/network/fused/passive). Payload: `{ provider }`.
- `sos` — emitido por `triggerSOS(payload?)`. Payload: dict de usuario + `location` (si hay fix).

### Método nuevo
- `triggerSOS(payload?, success?, fail?)` — JS API + Angular. Plugin emite un único evento `sos` con la última location conocida + el payload del usuario.

### Config nueva
```ts
drivingEvents?: {
  enabled?: boolean;
  speedLimit?: number;       // km/h, 0 disables
  minMovingSpeed?: number;   // m/s, default 1.0
  stoppedDuration?: number;  // ms, default 60000
  minTripSpeed?: number;     // m/s, default 3.0
  minTripDuration?: number;  // ms, default 30000
};
```

### Implementación
- Android: `com.marianhello.bgloc.driving.DrivingEventsDetector` (pure-Java, state machine GPS-only). Hosted por `LocationServiceImpl.onLocation`. MSG codes 113-119. Registrado en `plugin.xml`.
- iOS: detector inline en `MAURBackgroundGeolocationFacade` (mismo state machine en Objective-C). 7 nuevas `NSNotification`s observadas por `CDVBackgroundGeolocation`.
- `LocationServiceImpl.getLastReceivedLocation()` static accessor para que `BackgroundGeolocationFacade.triggerSOS()` adjunte la última location.
- TS: 7 nuevas firmas de `on()`, nuevo método `triggerSOS()`, nueva config `drivingEvents`.

---

## v4.1.0 — Driving events GPS-derived (Phase 6.1)

### 4 eventos nuevos (todos con cooldown 4s, payload `{ location, value }`)
- `hardBrake` — `value` = aceleración m/s² (negativa). Solo durante trip activo. Trigger: `Δspeed/Δt ≤ -hardBrakeMps2`.
- `rapidAcceleration` — `value` = aceleración m/s² (positiva). Solo durante trip activo. Trigger: `Δspeed/Δt ≥ rapidAccelMps2`.
- `sharpTurn` — `value` = tasa cambio de bearing deg/s. Requiere speed ≥ 5 m/s. Trigger: `|Δbearing|/Δt ≥ sharpTurnDegPerSec`.
- `possibleCrash` — heurístico. Trigger: caída ≥ `crashImpactKmh` en `crashWindowMs` durante trip, terminando cerca de 0. **Always confirm with user**, falsos positivos posibles.

### 5 nuevos thresholds en `drivingEvents`
- `hardBrakeMps2` (default 3.5)
- `rapidAccelMps2` (default 3.5)
- `sharpTurnDegPerSec` (default 30)
- `crashImpactKmh` (default 25)
- `crashWindowMs` (default 2000)

Cualquier valor a `0` deshabilita ese evento específico.

### Implementación
- Android `DrivingEventsDetector`: state machine extendido con `prevSpeed`/`prevBearing`/cooldown.
- Android `Config.DrivingEventsOptions`: 5 nuevos campos parceled (5 doubles + 1 long extra) — orden simétrico read/write.
- Android `LocationServiceImpl`: 4 nuevos `MSG_ON_*` codes (120-123).
- iOS `MAURBackgroundGeolocationFacade.drivingDetectorFeed:`: mismas heurísticas en Objective-C, 4 nuevas `NSNotification`s.
- iOS `CDVBackgroundGeolocation`: 4 observers + helper `sendDrivingEventN:note:`.
- TypeScript: 4 eventos en `Event` union y `BackgroundGeolocationEvents` enum; 4 typed `on()` overloads con payload `{ location, value }`.

### Hot-reload de configuración (también v4.1)
- Android `LocationServiceImpl.configure()`: re-evalúa `scheduleHeartbeat()` si cambió `heartbeatInterval`; rebuild `configureDrivingDetector()` si cambió `drivingEvents` (helper `equalsDrivingEvents()` para evitar rebuilds innecesarios).
- iOS `MAURBackgroundGeolocationFacade.configure:`: `scheduleHeartbeat` si cambió `heartbeatInterval`; `drivingDetectorReset` si cambió `drivingEvents` para aplicar nuevos thresholds limpios.

### Diferido a v4.2 — entregado
- Ver sección siguiente.

---

## v4.2.0 — Sensor fusion real (Phase 8)

Acelerómetro + giroscopio reales para refinar `possibleCrash` y nuevo `phoneUsageWhileDriving`. **Off por defecto** (`drivingEvents.sensorFusion = true` para activar).

### Nuevos archivos
- Android: `android/common/src/main/java/com/marianhello/bgloc/sensor/SensorFusionDetector.java` — `SensorEventListener`, `Sensor.TYPE_LINEAR_ACCELERATION` (gravity-removed) + `Sensor.TYPE_GYROSCOPE` a `SENSOR_DELAY_GAME` (~50 Hz). Sólo muestrea si `tripActive == true`.
- iOS: `ios/common/BackgroundGeolocation/MAURSensorFusionDetector.{h,m}` — `CMMotionManager.startDeviceMotionUpdatesToQueue` 50 Hz; `userAcceleration` (g) + `rotationRate` (rad/s).

### Nueva config `drivingEvents.*`
| Clave | Tipo | Default | Descripción |
|---|---|---|---|
| `sensorFusion` | `bool` | `false` | Master switch del pipeline de sensores. |
| `crashImpactG` | `number` (g) | `3.0` | Umbral de impacto para `possibleCrash` (sensor). |
| `sensorCrashCooldownMs` | `number` | `10000` | Cooldown entre crashes detectados por sensor. |
| `phoneUsageWindowMs` | `number` | `4000` | Ventana sostenida de jitter para emitir `phoneUsageWhileDriving`. |
| `phoneUsageCooldownMs` | `number` | `60000` | Cooldown entre eventos de uso de teléfono. |

### Nuevos eventos
- `phoneUsageWhileDriving` — emitido cuando hay interacción sostenida del usuario durante un trip activo y la pantalla está encendida. Heurística conservadora: jitter combinado de gyro (≥0.7 rad/s) + accel (≥0.5 m/s²) durante `phoneUsageWindowMs`.
- `possibleCrash` ahora incluye `source: "gps" | "sensor"` en el payload — el motor GPS sigue emitiendo (`source: "gps"`) y el motor de sensor añade `source: "sensor"` cuando `|a| ≥ crashImpactG`. Permite detectar choques a baja velocidad (parking-lot) que GPS no captura.

### Wiring
- Android: `LocationServiceImpl.configureSensorFusion()` instancia/destruye el detector; `onLocation()` propaga la última ubicación; `tripStart/tripEnd` propagan `tripActive`. Nuevo MSG `MSG_ON_PHONE_USAGE_WHILE_DRIVING = 124`.
- iOS: facade conforma a `MAURSensorFusionListener`; nueva notificación `MAURPhoneUsageWhileDrivingNotification`; `CDVBackgroundGeolocation` la observa y emite el evento JS.
- Hot-reload: `configure()` reevalúa `drivingEvents.sensorFusion` en ambos lados; reinicia el muestreo cuando se activa.

### Notas de diseño
- Sólo muestrea durante `tripActive == true` para acotar coste de batería.
- Android: `TYPE_LINEAR_ACCELERATION` puede no estar disponible en algunos OEMs (Honor, Lenovo); `isAvailable()` lo señala y la emisión queda silenciosa.
- iOS: la heurística "screen on" usa `applicationState == Active` (foreground) — descarta uso del pasajero con la app en background.

---

## Compatibilidad

| Plugin | Cordova CLI | Cordova Android | Cordova iOS |
|---|---|---|---|
| 3.2.x y anteriores | ≥ 8 | ≥ 8 | ≥ 6.0 |
| **3.3.x – 4.0.x** | ≥ 10 | ≥ 12 | ≥ 6.2 |

`compileSdk` recomendado: 35 (necesario para `getCurrentLocation`, `getServiceInfo(ComponentInfoFlags)`, `Priority.*` y otros APIs modernos).

---

## Eventos y métodos por release

| Release | Eventos añadidos | Métodos añadidos |
|---|---|---|
| 3.3.0 | — | — (HTTP es config) |
| 3.4.0 | — | — |
| 3.5.0 | `heartbeat`, `syncStart`, `syncProgress`, `syncSuccess`, `syncError` | `getDiagnostics()` |
| 3.6.0 | — | `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`, `openAutoStartSettings`, `getManufacturerHelp` |
| 4.0.0 | `tripStart`, `tripEnd`, `moving`, `stopped`, `speeding`, `providerChange`, `sos` | `triggerSOS(payload?)` |

---

## Validación

Cada release fue verificado vía:
- Inspección por archivo (no `grep` de bash workspace, que tiene caché stale).
- Coherencia de versiones: `package.json`, `plugin.xml`, `BackgroundGeolocationPlugin.PLUGIN_VERSION` Android, `CDVBackgroundGeolocation.getPluginVersion` iOS.
- Imports: añadidos para nuevos símbolos (`Locale`, `JSONObject`, `OutputStream`, `ScheduledExecutorService`, `CoreMotion`, `objc/runtime`).
- Parcelable read/write order: simétrico para todos los nuevos campos en `Config`.
- Default methods en `PluginDelegate`: usados para evitar romper otros consumers (testPluginDelegate y futuros).

## Pendiente para producción estricta

Antes del primer release público recomiendo:

1. **Probar en dispositivo real** Android (Xiaomi + Samsung + Pixel) y iOS (14+, Low Power Mode).
2. Verificar que `LocationServiceProxy.startForegroundService` no rompe apps que iniciaban tracking sin background-location previo (caso típico al pulsar Start con app en foreground — no requiere background, solo fine).
3. Añadir tests unitarios para `DrivingEventsDetector` (state machine cubre 6 transiciones).
4. Documentar en `docs/api.md` las claves del payload de cada evento nuevo (parcialmente hecho).
5. Considerar emitir todos los driver-insight eventos también via la session API si está activa (para reconstruir el viaje offline).
