# Review del plugin (v3.2.0)

Fecha: 2026-05-07
Repo: `@josuelmm/cordova-background-geolocation`
Plugin: `@josuelmm/cordova-background-geolocation` — global y backend-agnóstico. Casos como Life360 con backend Traccar son solo ejemplos referenciales; cualquier backend se conecta vía el HTTP transport personalizable.

> **Nota post-v3.3.0:** Las secciones **1–7** conservan el snapshot de auditoría sobre **v3.2.0** (gaps y “estado actual” de entonces). Parte del contenido quedó obsoleto tras **v3.3.0** (Fases 1–2: auto-start Android + HTTP transport). Para el estado real del código y del roadmap usar **`CHANGELOG.md`**, **`docs/ROADMAP.md`** y las secciones **8–9** de este documento.

---

## 1. Estado actual (lo que ya cumple)

- Versión sincronizada en `package.json`, `plugin.xml`, `CHANGELOG.md`.
- Cordova Android (≥8) e iOS (≥6). Compatible con Capacitor vía wrapper Cordova.
- Build Angular con `ng-packagr` 18, exporta `./angular`. Stubs `cordova/exec` y `cordova/channel` para `ng serve`. Fix `.d.ts` Windows.
- API GPS base: `configure`, `start`, `stop`, `getCurrentLocation`, `checkStatus`.
- Providers: `DISTANCE_FILTER_PROVIDER`, `ACTIVITY_PROVIDER`, `RAW_PROVIDER`.
- Cola offline: `getPendingSyncCount`, `forceSync`, `clearSync`, `getValidLocations`, `getValidLocationsAndDelete`.
- Session API (3.2.0): `startSession`, `getSessionLocations`, `clearSession`, `getSessionLocationsCount`.
- Android moderno: `foregroundServiceType="location|dataSync"`, permisos `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`.
- iOS: `NSLocationAlwaysUsageDescription`, `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription`, `NSMotionUsageDescription`, `UIBackgroundModes: location`.
- Watchdog Android para reinicio del provider.

---

## 2. Auto-start tras reinicio del dispositivo

### 2.1 Android — qué existe

Archivo: `android/common/src/main/java/com/marianhello/bgloc/BootCompletedReceiver.java`.
Manifest: `plugin.xml` líneas 165-172 + permiso `RECEIVE_BOOT_COMPLETED` (línea 180).

Flujo actual:
1. Recibe `android.intent.action.BOOT_COMPLETED`.
2. Lee la última `Config` persistida en SQLite vía `ConfigurationDAO`.
3. Si `config.getStartOnBoot() == true` y hay `ACCESS_FINE_LOCATION` o `ACCESS_COARSE_LOCATION`:
   - SDK O+ (Android 8+) → `startForegroundService(LocationServiceImpl)`.
   - Inferior → `startService`.

`Config` se persiste en `configure()` y la flag `startOnBoot: boolean` ya está expuesta en `www/BackgroundGeolocation.d.ts` línea 127.

### 2.2 Android — gaps detectados

| Gap | Riesgo | Acción |
|---|---|---|
| Solo escucha `BOOT_COMPLETED`. NO escucha `QUICKBOOT_POWERON` | HTC, MIUI (Xiaomi/Redmi) NO disparan `BOOT_COMPLETED`; sí `QUICKBOOT_POWERON` y `com.htc.intent.action.QUICKBOOT_POWERON` | Añadir actions en intent-filter |
| `LOCKED_BOOT_COMPLETED` (Direct Boot, Android 7+ FBE) NO se incluye en v3.3 | El receiver no se dispara hasta el primer desbloqueo del usuario | Fuera de scope v3.3: la `Config` vive en SQLite credential-encrypted; soportarlo requiere migrar `ConfigurationDAO` a Device Protected Storage |
| NO escucha `MY_PACKAGE_REPLACED` | Tras actualizar la app (Play Store), el servicio no se relanza hasta que el usuario abre la app | Añadir action |
| NO valida `ACCESS_BACKGROUND_LOCATION` | Android 10+: si el servicio se inicia desde background sin este permiso, FGS de tipo location falla silencioso | Validar antes de `startForegroundService` y loggear |
| NO maneja excepción `ForegroundServiceStartNotAllowedException` (Android 12+) | Si el sistema bloquea el inicio, crash o silencio | Try/catch + log + evento `serviceStartBlocked`. WorkManager solo para sync diferido, **no** para tracking continuo. |
| Receiver `exported="true"` correcto, pero NO valida que el broadcast venga del sistema | Spoofing posible | Verificar `intent.getAction()` y firma |
| Race: `LocationServiceImpl` debe llamar `startForeground()` en <5s tras `startForegroundService` (Android 8+) | ANR / ForegroundServiceDidNotStartInTimeException | Confirmar en `LocationServiceImpl.onStartCommand` |
| OEM con DOZE / restrictive battery (Xiaomi, Huawei, Oppo, Vivo, Samsung One UI) matan el FGS aunque arranque | Servicio se reinicia y muere | Helpers de batería + AutoStart settings (ver §6) |

### 2.3 Android — receiver propuesto (intent-filter)

```xml
<receiver android:name="com.marianhello.bgloc.BootCompletedReceiver"
          android:enabled="true"
          android:exported="true"
          android:directBootAware="false">
    <intent-filter android:priority="999">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.REBOOT" />
    </intent-filter>
</receiver>
```

### 2.4 iOS — limitaciones reales

iOS NO permite auto-iniciar al encender el dispositivo. Apple bloquea código de la app hasta primera apertura post-boot.

Mitigaciones (ya soportadas parcialmente):
- `stopOnTerminate: false` + `UIBackgroundModes: location` → si la app se mueve a background, iOS puede relanzar.
- `Significant Location Changes` (CLLocationManager) → relanza la app si hay movimiento ≥500m. **Verificar si el plugin lo registra explícitamente al `start()`**.
- Region monitoring (geofence "wake-up") → relanza al cruzar regiones.
- `Background App Refresh` activado por el usuario.

Recomendación: documentar en `docs/api.md` que en iOS `startOnBoot` no aplica y describir alternativa "wake on motion".

---

## 3. HTTP / envío de datos — adaptable a cualquier plataforma

### 3.1 Estado actual

| Aspecto | Estado |
|---|---|
| Método HTTP | **Hardcoded POST** (Android `HttpPostService.java` L112,L261; iOS `MAURPostLocationTask.m` L141, `MAURBackgroundSync.m` L80) |
| `httpHeaders` | Soportado |
| `Content-Type` flexible | Sí: `application/json` (default) y `application/x-www-form-urlencoded` (auto-conversión en Android) |
| `postTemplate` (formato body custom) | Soportado |
| URL templating (`{lat}`, `{lon}`) | NO existe |
| GET / PUT / PATCH | NO soportado |
| Modo single-location vs batch | Solo batch (array) |
| Query string dinámico | NO existe |
| Response parsing custom | NO (solo lee status code) |

### 3.2 Objetivo: HTTP genérico para cualquier backend

El plugin debe enviar a **cualquier endpoint** (Traccar, Firebase, REST custom, GraphQL, n8n, Make, etc.) sin código de plataforma específico.

API propuesta (NO modo Traccar dedicado):

```ts
configure({
  url: string,
  syncUrl?: string,

  // Nuevo en v3.3
  httpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH',   // default 'POST'
  syncHttpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH',
  httpHeaders?: { [k: string]: string },
  postTemplate?: object | array,                    // ya existe

  // URL templating: aplica tanto a `url` como a `syncUrl`
  // Placeholders: {id}, {lat}, {lon}, {time}, {timestamp_iso},
  //               {speed}, {altitude}, {bearing}, {accuracy},
  //               {provider}, {is_moving}, {activity}, {device_id},
  //               y cualquier clave de httpExtras
  queryParams?: { [k: string]: string | number },  // valores estáticos extra

  // Modo de envío
  httpMode?: 'batch' | 'single',                    // default 'batch'
  // 'single': hace una request por cada location (ideal Osmand/Traccar via GET)
  // 'batch': agrupa según syncThreshold (comportamiento actual)
})
```

Ejemplos cubiertos sin código específico:

**Traccar (Osmand) vía GET single:**
```ts
configure({
  url: 'https://gps.midominio.com/?id={device_id}&lat={lat}&lon={lon}&timestamp={timestamp_iso}&speed={speed}&altitude={altitude}&bearing={bearing}&accuracy={accuracy}',
  httpMethod: 'GET',
  httpMode: 'single',
  queryParams: { device_id: 'USER_DEVICE_123' }
})
```

**REST JSON batch (comportamiento actual):**
```ts
configure({
  url: 'https://api.miapp.com/locations',
  httpMethod: 'POST',
  httpHeaders: { 'Authorization': 'Bearer ...', 'Content-Type': 'application/json' },
  postTemplate: { lat: '@latitude', lon: '@longitude', t: '@time' }
})
```

**Form-urlencoded:**
```ts
configure({
  url: 'https://legacy.midominio.com/track.php',
  httpMethod: 'POST',
  httpHeaders: { 'Content-Type': 'application/x-www-form-urlencoded' },
  postTemplate: { lat: '@latitude', lng: '@longitude' }
})
```

### 3.3 Cambios técnicos necesarios

- `Config.java` / `Config.swift`: añadir `httpMethod`, `syncHttpMethod`, `httpMode`, `queryParams`, `bodyTemplate` (alias retrocompatibles `httpHeaders`/`postTemplate`).
- `HttpPostService.java`:
  - Aceptar `method` parámetro (no hardcode `setRequestMethod("POST")`).
  - Resolver placeholders en URL antes de abrir conexión.
  - Si `httpMode == 'single'`, iterar y enviar cada location por separado.
  - Si método es GET → no enviar body (todo en query string vía URL templating). POST/PUT/PATCH envían body si aplica.
- `MAURPostLocationTask.m` + `MAURBackgroundSync.m`: idem.
- `PostLocationTask.java`: respetar `httpMode`.
- TypeScript `.d.ts`: añadir tipos.
- Angular service: re-exportar.

---

## 4. Eventos de conducción (driving events) — diferido a v4.0

Detección **dentro del propio plugin** (sin servicio externo):

### 4.1 Sensores requeridos

- GPS (ya disponible).
- Acelerómetro (`Sensor.TYPE_LINEAR_ACCELERATION` Android / `CMMotionManager` iOS).
- Giroscopio (`Sensor.TYPE_GYROSCOPE` / `CMMotionManager`).
- Activity Recognition (ya integrado).

### 4.2 Eventos a detectar

| Evento | Algoritmo base |
|---|---|
| `tripStart` | `activity == IN_VEHICLE` confianza ≥75% durante ≥30s + speed ≥10 km/h |
| `tripEnd` | `activity != IN_VEHICLE` durante ≥3 min, o speed ≈0 durante ≥5 min |
| `hardBrake` | Δspeed GPS ≥ 8 m/s en ≤2s + lin.accel < -3.5 m/s² |
| `rapidAcceleration` | Δspeed GPS ≥ 7 m/s en ≤2s + lin.accel > +3.5 m/s² |
| `sharpTurn` | gyro Z > 0.6 rad/s + bearing change ≥45° en ≤2s |
| `speeding` | speed > `speedLimit` (configurable global o por geofence) durante ≥5s |
| `phoneUsageWhileDriving` | `IN_VEHICLE` + screen on + interaction events (heurístico) |
| `possibleCrash` | impact > 3g + Δspeed > 25 km/h en <1s + activity == IN_VEHICLE → confirmación user |

### 4.3 Configuración

```ts
configure({
  drivingEvents: {
    enabled: true,
    speedLimit: 80,                    // km/h
    hardBrakeThreshold: 3.5,           // m/s²
    rapidAccelThreshold: 3.5,
    sharpTurnGyroZ: 0.6,               // rad/s
    crashImpactG: 3.0,
    minTripSpeed: 10,                  // km/h
    minTripDuration: 30                // s
  }
})

BackgroundGeolocation.on('drivingEvent', (event) => {
  // event.type, event.value, event.location, event.confidence, event.timestamp
});
```

### 4.4 Versionado

- v3.3 (Fase 1, entregada): Auto-start Android.
- v3.3 (Fase 2, entregada): HTTP transport personalizable.
- v3.4 (Fase 3, próxima): Modernización location APIs Android/iOS.
- v3.5 (Fase 4): Diagnóstico, heartbeat, sync events, mockLocationPolicy.
- v3.6 (Fase 5): Battery / OEM helpers.
- v4.0 (Fase 6): Driving events + crash detection + sensor fusion + SOS helper.

---

## 5. Lo que cubre Traccar (NO desarrollar en plugin)

Si la app usa Traccar como backend, estos features **NO** se implementan en el plugin:

| Feature | Cubierto por | Detalle |
|---|---|---|
| Persistencia historial de rutas | Traccar Reports | Tabla `tc_positions`, retención configurable |
| Geofences (alta/baja/listado) | Traccar Geofences module | Polígono, círculo, polilínea. API REST `/api/geofences` |
| Eventos `geofenceEnter` / `geofenceExit` | Traccar Notifications | Server evalúa al recibir cada posición |
| Reglas de notificación (overspeed, idle, geofence, deviceMoving, deviceStopped) | Traccar Notifications | Configurables por device/grupo, dispara push/email/webhook |
| Eventos de viaje (Trips) | Traccar Reports → Trips | Calcula start/end automáticamente desde positions |
| Stops / paradas | Traccar Reports → Stops | Idem |
| Resumen diario (Summary) | Traccar Reports → Summary | Distancia, tiempo en movimiento, speed promedio/máx |
| Compartir posición temporal | Traccar Sharing | URL pública con expiración |
| Históricos por rango | Traccar Web/API | `/api/positions?from=...&to=...` |
| Dashboard / mapa multi-device | Traccar Web | Manager / Modern UI |
| Alertas push a familia | Traccar Notifications + webhook → FCM | Server-side |
| Audit log | Traccar | `tc_log` |

**El plugin solo debe garantizar que las posiciones lleguen a Traccar de forma fiable** (offline queue, retry, formato Osmand vía GET con URL templating).

---

## 6. Riesgos de versiones / dependencias

- `androidx.core:core:1.1.0` y `androidx.appcompat:appcompat:1.1.0` → 2019. Subir a 1.13.x.
- `play-services-location` default `17+` (2020) → estable actual 21.x.
- `cordova-android >=8` permite versiones sin `targetSdk 34`. Para Play Store 2026 exigir `cordova-android >=12`.
- Peer Angular `>=12.0.0` muy laxo; build en 18 → mínimo recomendable `>=16`.

---

## 7. Faltantes confirmados en código (verificado por grep)

| Faltante | Verificación |
|---|---|
| `ACCESS_BACKGROUND_LOCATION` (Android) | NO presente en `plugin.xml` |
| `httpMethod` GET/PUT | NO existe (POST hardcoded en Android `HttpPostService.java` L112,L261; iOS `MAURPostLocationTask.m` L141, `MAURBackgroundSync.m` L80) |
| URL templating | NO existe |
| `httpMode: 'single' / 'batch'` | NO existe (solo batch) |
| `getDiagnostics()` completo | NO existe (solo `checkStatus` básico) |
| Eventos `tripStart` / `tripEnd` | NO existen |
| Evento `heartbeat` | NO existe |
| Eventos `syncStart` / `syncProgress` / `syncSuccess` / `syncError` | NO existen |
| Eventos de conducción | NO existen |
| Crash detection | NO existe |
| Helpers batería Android | NO existen |
| Diagnóstico iOS extendido (precise, background refresh, low power, motion permission) | NO expuesto |
| Política mock location (`mockLocationPolicy`) | NO existe (la **detección** sí existe: ver §7.1) |
| Boot receiver: `QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED` | NO declarados (`LOCKED_BOOT_COMPLETED` queda fuera de scope v3.3, ver §8) |
| Plugin Capacitor nativo | NO existe (solo vía Cordova wrapper) |

### 7.1 Lo que SÍ existe (correcciones a revisiones previas)

- **Mock detection ya implementada:**
  - Android: `BackgroundLocation.isFromMockProvider` (línea 161 de `BackgroundLocation.java`).
  - iOS: `MAURLocation.simulated` con `sourceInformation.isSimulatedBySoftware` (iOS 15+).
  - Falta: política de qué hacer con esas posiciones (`allow` / `flag` / `drop`).
- **iOS Significant Location Changes ya implementado:**
  - `MAURDistanceFilterLocationProvider.m` L191, L427, L431-433 → `startMonitoringSignificantLocationChanges`.
  - `INTULocationManager.m` L546.
  - `allowsBackgroundLocationUpdates = YES` en `MAURDistanceFilterLocationProvider.m` L73 e `INTULocationManager.m` L140, L1011.

### 7.2 Inconsistencias / código a limpiar

- **`LocationServiceImpl.java` L586:** llama `super.startForeground(NOTIFICATION_ID, notification, 0x8)` (solo `LOCATION`), pero el manifest declara `foregroundServiceType="location|dataSync"` = 0x9. La función `getManifestForegroundServiceType()` (L528) ya existe para leer el manifest pero **no se usa** en el call. Hardcode.
- **`LocationServiceImpl.java` L519-520:** comentario y constante dicen `FOREGROUND_SERVICE_TYPE_LOCATION = 4`. Es **incorrecto**: el valor real es `0x00000008 = 8`. La constante 4 es `PHONE_CALL`. La constante L520 está declarada pero no se usa.
- **`plugin.xml` L163:** `foregroundServiceType="location|dataSync"`. `dataSync` no aporta (el código solo usa LOCATION). Quitar reduce escrutinio en Play Console.
- **`plugin.xml` L190:** `FOREGROUND_SERVICE_DATA_SYNC` permission sobrante por lo mismo.
- **`plugin.xml` L173:** `<uses-library android:name="org.apache.http.legacy" />`. `grep` no encuentra imports `org.apache.http.*` en `android/common/src/main/java` ni en `android/CDVBackgroundGeolocation`. **No se usa**: el código usa `HttpURLConnection`. Quitar.
- **`android/dependencies.gradle` L12:** `useLibrary 'org.apache.http.legacy'`. Idem, eliminar.
- **`android/build.gradle` L9, L25:** `jcenter()`. Repositorio descontinuado desde 2022. Migrar a `mavenCentral()`.

---

## 8. Roadmap definitivo

> **Principio.** Plugin global y backend-agnóstico. Sin lógica hardcodeada de Traccar / GPSWox / OsmAnd. Cualquier backend se conecta vía el HTTP transport personalizable.

Orden:

1. Auto-start Android
2. HTTP transport personalizable
3. Modernización location APIs Android/iOS
4. Diagnóstico
5. Battery / OEM helpers
6. Driving / crash / SOS

### v3.3 — Auto-start Android (Fase 1, ✅ entregada)

- `plugin.xml`:
  - Añadir `ACCESS_BACKGROUND_LOCATION`.
  - `foregroundServiceType="location"` (quitar `dataSync`).
  - Quitar permiso `FOREGROUND_SERVICE_DATA_SYNC`.
  - Receiver con: `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `com.htc.intent.action.QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED`. NO `LOCKED_BOOT_COMPLETED` ni `directBootAware` (la `Config` no está en device-protected storage).
  - Quitar `<uses-library org.apache.http.legacy>`.
- `BootCompletedReceiver.java`:
  - Validar `ACCESS_BACKGROUND_LOCATION` solo en Android 10+.
  - Try/catch en `startForegroundService` → log + futuro evento `serviceStartBlocked`.
- `LocationServiceImpl.java`:
  - Reemplazar `0x8` hardcode por `getManifestForegroundServiceType()` real.
  - Eliminar constante muerta L520 + corregir comentario L519.
- `LocationServiceProxy.java`:
  - Si falta permiso al arrancar desde background/boot: no iniciar foreground service, log y salir.
- `dependencies.gradle`: quitar `useLibrary 'org.apache.http.legacy'`.
- `build.gradle`: `jcenter()` → `mavenCentral()`. `compileSdk` 28 → 35.
- iOS: documentar limitación. `Significant Location Changes` y `allowsBackgroundLocationUpdates` ya implementados.
- Versiones: `cordova-android >=12`, `androidx.core` 1.13.x, `androidx.appcompat` 1.7.x, `play-services-location` 21.x, peer Angular `>=16`.

### v3.3 — HTTP transport personalizable (Fase 2, ✅ entregada en 3.3.0)

API agnóstica de backend:

```ts
configure({
  url: string,
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH',
  httpMode?: 'single' | 'batch',
  headers?: { [k: string]: string },                // alias retrocompatible: httpHeaders
  bodyTemplate?: object,                            // alias retrocompatible: postTemplate
  queryParams?: { [k: string]: string | number },
  syncUrl?: string,
  syncHttpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH',
  syncMode?: 'single' | 'batch',
});
```

Placeholders en URL y `bodyTemplate`: `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`, `{is_moving}`, `{activity}`, `{battery}` + claves de `queryParams`.

Cambios:

- Android `HttpPostService.java`: parametrizar método HTTP, resolver placeholders, GET sin body, soporte `single` / `batch`.
- iOS `MAURPostLocationTask.m` + `MAURBackgroundSync.m`: idem.
- `Config.java` / `MAURConfig`: nuevas propiedades + alias retrocompatibles.
- `ConfigMapper.java`: mapear nuevas keys.
- `PostLocationTask.java` / `SyncAdapter.java`: respetar `httpMode` / `syncMode`.
- `www/BackgroundGeolocation.d.ts` + Angular service: tipos.

### v3.4 — Modernización location APIs (Fase 3)

Antes del diagnóstico, sanea las APIs nativas: ya hay deprecations Google/Apple que pueden romper el plugin con cualquier release mayor.

- **Android:**
  - Migrar `LocationRequest.create()` + `setPriority/setInterval/setFastestInterval` → `LocationRequest.Builder(priority, intervalMillis)` + `Priority.PRIORITY_*` (deprecated en `play-services-location 21.0.0`).
  - Eliminar `Criteria` API + `getBestProvider` en `DistanceFilterLocationProvider.java` (deprecated API 31).
  - Reemplazar `getLastKnownLocation(provider)` por `getCurrentLocation(...)` (API 30+).
  - Reemplazar `requestLocationUpdates(String, long, float, LocationListener)` por la sobrecarga con `LocationRequest.Builder` (API 31+) con fallback al método clásico.
  - Stationary detection: retirar `AlarmManager.setInexactRepeating` (Doze lo difiere a 9-15 min). Mantener FGS con `LocationCallback` interval grande.
- **iOS:**
  - Migrar `[NSURLConnection sendSynchronousRequest:returningResponse:error:]` (deprecated iOS 9+) a `NSURLSession dataTaskWithRequest:completionHandler:` con `dispatch_semaphore` en `MAURPostLocationTask.m`.
  - Exponer `showsBackgroundLocationIndicator` en `MAURConfig` y `.d.ts`.
  - Implementar `locationManagerDidChangeAuthorization:` (iOS 14+) con fallback al legacy.
  - Persistir `accuracyAuthorization` para exponerlo en Fase 4.
- **Build:** bump `play-services-location` default a 21.x; `compileSdk` 34+ obligatorio.

Detalle por archivo y línea: ver [docs/location-modernization.md](docs/location-modernization.md).

### v3.5 — Diagnóstico (Fase 4)

- `getDiagnostics()` extendido (Android e iOS).
- Evento `heartbeat`.
- Eventos sync: `syncStart`, `syncProgress`, `syncSuccess`, `syncError`.
- `mockLocationPolicy: 'allow' | 'flag' | 'drop'` (detección ya existe).

### v3.6 — Battery / OEM helpers (Fase 5)

- `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`.
- `openAutoStartSettings()` por fabricante (Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, Samsung One UI).
- `getManufacturerHelp()` con instrucciones por OEM.

### v4.0 — Driver insights (Fase 6)

- Eventos `tripStart` / `tripEnd` / `moving` / `stopped` / `providerChange`.
- Driving events: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `speeding`, `phoneUsageWhileDriving`.
- `possibleCrash` (sensor fusion).
- SOS helper local.

---

## 9. Veredicto

- Cumple como **motor GPS background base** Android/iOS con cola offline, HTTP posting y session API.
- El plugin se mantiene **global y backend-agnóstico**. No se incorpora lógica de Traccar / GPSWox / OsmAnd / etc.; cualquier backend se conecta vía el HTTP transport personalizable (entregado en v3.3).
- Fases 1 y 2 (auto-start + HTTP) entregadas en v3.3.0.
- Prioridad inmediata: **Modernización location APIs** (Fase 3, v3.4) — sanea deprecations Google/Apple antes de añadir el diagnóstico.
- Geocercas, eventos espaciales, historial server-side, viajes, paradas y reportes son responsabilidad del **backend** (Traccar es solo un ejemplo posible). Ver [docs/traccar.md](docs/traccar.md).
- `mockLocationPolicy` debe ir en cliente (los backends no detectan mocks).
- Driving events / crash / SOS se difieren a v4.0 (requiere sensores adicionales y filtros).
- WorkManager NO es solución para tracking GPS continuo. Solo aplica para sync diferido de la cola offline.
