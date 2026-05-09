# Roadmap

Estado actual: **v4.2.3** (Fases 1-8 completadas; 4.2.x = hotfixes de compilación + log).

> **Principio de diseño.** Este es un plugin **global y backend-agnóstico**. No incorpora lógica de Traccar, GPSWox, OsmAnd ni de ninguna API propietaria. La compatibilidad con cualquier backend se logra mediante **transporte HTTP personalizable** (URL templating + body templating + métodos HTTP genéricos). Traccar y similares solo se documentan como **ejemplos de integración**, nunca como modo interno.

Orden de implementación (de mayor a menor impacto):

1. ✅ Auto-start Android (Fase 1, v3.3)
2. ✅ HTTP transport personalizable (Fase 2, v3.3)
3. ✅ Modernización location APIs Android/iOS (Fase 3, v3.4)
4. ✅ Diagnóstico, sync events, heartbeat, mockLocationPolicy (Fase 4, v3.5)
5. ✅ Battery / OEM helpers (Fase 5, v3.6)
6. ✅ Driver insights GPS-only (Fase 6, v4.0)
7. ✅ Driving events GPS-derived: hardBrake / rapidAcceleration / sharpTurn / possibleCrash (Fase 6.1, v4.1)
8. ✅ Sensor fusion real con acelerómetro + giroscopio (Fase 8, v4.2)

---

## v3.3 (Fase 1, ya entregada en 3.3.0) — Auto-start Android

Prioridad: alta. Sin auto-start fiable, el resto da igual.

- `plugin.xml`:
  - Añadir `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />`.
  - `foregroundServiceType="location"` (quitar `dataSync`).
  - Quitar permiso `FOREGROUND_SERVICE_DATA_SYNC`.
  - Receiver con actions: `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `com.htc.intent.action.QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED`. **NO** se incluye `LOCKED_BOOT_COMPLETED` ni `directBootAware="true"` en esta fase: la `Config` vive en SQLite normal, que no está disponible antes del primer desbloqueo (requiere migración a Direct Boot Storage, fuera de scope v3.3).
  - Quitar `<uses-library android:name="org.apache.http.legacy" />` (sin imports en el código).
- `BootCompletedReceiver.java`:
  - Validar `ACCESS_FINE_LOCATION` o `ACCESS_COARSE_LOCATION` (ya).
  - **Validar `ACCESS_BACKGROUND_LOCATION`** solo si `Build.VERSION.SDK_INT >= 29` (Android 10+).
  - Envolver `context.startForegroundService(intent)` en try/catch para `ForegroundServiceStartNotAllowedException` (Android 12+) y cualquier `Exception`. Log claro. Más adelante, emitir evento interno `serviceStartBlocked`.
- `LocationServiceImpl.java`:
  - Reemplazar hardcode `super.startForeground(NOTIFICATION_ID, notification, 0x8)` (L586) por uso real de `getManifestForegroundServiceType()` (ya definida en L528). Si retorna 0, log de error y abortar (no llamar `startForeground` con tipo inválido).
  - Eliminar la constante muerta `private static final int FOREGROUND_SERVICE_TYPE_LOCATION = 4` (L520) y corregir comentario L519: el valor real de `LOCATION` es `0x00000008 = 8`, no 4 (4 es `PHONE_CALL`).
- `LocationServiceProxy.java`:
  - Si falta permiso de ubicación al arrancar desde background/boot: NO iniciar foreground service, log y salir (en lugar de caer a `startService` silencioso).
- `dependencies.gradle`:
  - Eliminar `useLibrary 'org.apache.http.legacy'`.
- `build.gradle`:
  - `jcenter()` → `mavenCentral()`.
  - `compileSdk` 28 → 35.
- iOS:
  - Apple no permite auto-start al encender. Documentar la limitación. `Significant Location Changes` y `allowsBackgroundLocationUpdates = YES` ya están implementados (no requiere desarrollo).
- Versiones:
  - `cordova-android` mínimo `>=12`.
  - `androidx.core` 1.13.x, `androidx.appcompat` 1.7.x.
  - `play-services-location` default 21.x.
  - Peer Angular `>=16`.

Detalle: ver [auto-start.md](auto-start.md).

---

## v3.3 (Fase 2, ya entregada en 3.3.0) — HTTP transport personalizable

Prioridad: alta. Habilita integración con **cualquier backend** sin modo dedicado por proveedor.

API propuesta:

```ts
configure({
  // Endpoint principal
  url: string,                                        // soporta URL templating
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH',     // default 'POST'
  httpMode?: 'single' | 'batch',                     // default 'batch'
  headers?: { [key: string]: string },               // alias de httpHeaders, dinámico
  queryParams?: { [key: string]: string | number },  // valores estáticos para placeholders en URL
  bodyTemplate?: object,                             // alias de postTemplate, plantilla de body
  // Endpoint de cola de fallidos
  syncUrl?: string,
  syncHttpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH',
  syncMode?: 'single' | 'batch',
});
```

Placeholders disponibles en `url`, `syncUrl` y `bodyTemplate`:
`{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`, `{is_moving}`, `{activity}`, `{battery}`, más cualquier clave de `queryParams`.

Cambios técnicos:

- Android `HttpPostService.java`:
  - Eliminar hardcode `setRequestMethod("POST")` (L112, L261).
  - Aceptar `method` parámetro.
  - Resolver placeholders en URL antes de abrir conexión.
  - GET → no enviar body. POST/PUT/PATCH envían body si aplica.
  - Si `httpMode == 'single'`: iterar locations y emitir una request por cada una.
- iOS `MAURPostLocationTask.m` (L141), `MAURBackgroundSync.m` (L80): parametrizar `setHTTPMethod`, idem URL templating y single/batch.
- `Config.java` / `MAURConfig`: añadir `httpMethod`, `syncHttpMethod`, `httpMode`, `syncMode`, `queryParams`, `bodyTemplate`.
- `ConfigMapper.java`: mapear nuevas keys.
- `PostLocationTask.java` y `SyncAdapter.java`: respetar `httpMode` / `syncMode`.
- `www/BackgroundGeolocation.d.ts` y Angular service: nuevos tipos.
- Mantener `httpHeaders` y `postTemplate` como alias retrocompatibles.

Detalle y ejemplos: ver [http-transport.md](http-transport.md).

---

## v3.4 — Modernización location APIs (Fase 3)

Prioridad: alta. Antes del diagnóstico, sanea las APIs nativas para que el diagnóstico no reporte sobre stack obsoleto y para evitar que Google/Apple rompan el plugin con cualquier release mayor.

### Android

- `ActivityRecognitionLocationProvider.java`: migrar `LocationRequest.create()` → `new LocationRequest.Builder(priority, intervalMillis)` y `Priority.PRIORITY_*` (sustituye `LocationRequest.PRIORITY_*` deprecated en `play-services-location 21.0.0`).
- `DistanceFilterLocationProvider.java`:
  - Eliminar `Criteria` (deprecated API 31+).
  - Reemplazar `LocationManager.getBestProvider(criteria, true)` por selección explícita GPS/Network.
  - Reemplazar `LocationManager.getLastKnownLocation(provider)` por `LocationManager.getCurrentLocation(provider, null, executor, consumer)`.
  - Reemplazar `requestLocationUpdates(String, long, float, LocationListener)` por la sobrecarga con `LocationRequest.Builder` (API 31+) con fallback al método clásico.
- `RawLocationProvider.java`: misma migración.
- Stationary detection: retirar `AlarmManager.setInexactRepeating` (Doze lo difiere a 9-15 min). El FGS ya está vivo; mantener `LocationCallback` con interval grande durante stationary en lugar del scheduler externo.
- Bump `play-services-location` default a 21.x.

### iOS

- `MAURPostLocationTask.m`: migrar `[NSURLConnection sendSynchronousRequest:returningResponse:error:]` (deprecated iOS 9+) a `NSURLSession dataTaskWithRequest:completionHandler:` con `dispatch_semaphore` para mantener sincronía en thread de background.
- `MAURDistanceFilterLocationProvider.m` / `MAURConfig`:
  - Exponer `showsBackgroundLocationIndicator` en config.
  - Documentar `pausesLocationUpdatesAutomatically` y `activityType` en `.d.ts`.
- Implementar `locationManagerDidChangeAuthorization:` (iOS 14+) manteniendo el legacy.
- Persistir `accuracyAuthorization` (Precise vs Reduced) para exponerlo en Fase 4.

### TypeScript

- `www/BackgroundGeolocation.d.ts`: añadir `showsBackgroundLocationIndicator?: boolean` y documentar valores válidos de `activityType`.

### Compatibilidad

- Backward-compatible: `desiredAccuracy` (0/10/100/1000) sigue funcionando, solo cambia el mapeo interno a `Priority.*`.
- Min Android: `getCurrentLocation` requiere API 30+; las APIs deprecated se conservan tras `Build.VERSION.SDK_INT` checks como fallback.
- Min iOS: `NSURLSession` (iOS 7+) ya disponible; `locationManagerDidChangeAuthorization:` requiere iOS 14+ con fallback al legacy.

Detalle y diff por archivo: ver [location-modernization.md](location-modernization.md).

---

## v3.5 — Diagnóstico (Fase 4)

- `getDiagnostics()` extendido:
  - Android: `isRunning`, `locationServicesEnabled`, `fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted`, `notificationPermissionGranted`, `activityRecognitionGranted`, `batteryOptimizationIgnored`, `manufacturer`, `lastLocationAt`, `pendingSyncCount`, `startOnBoot`, `foregroundServiceType`.
  - iOS: `preciseLocationEnabled`, `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus`, `authorizationStatusText`.
- Evento `heartbeat` (intervalo configurable).
- Eventos sync: `syncStart`, `syncProgress`, `syncSuccess`, `syncError`.
- `mockLocationPolicy: 'allow' | 'flag' | 'drop'`. La **detección ya existe** (`isFromMockProvider` Android, `simulated` iOS); aquí solo se añade la política.

---

## v3.6 — Battery / OEM helpers (Fase 5)

- Helpers batería: `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`.
- `openAutoStartSettings()` por fabricante (Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, Samsung One UI).
- `getManufacturerHelp()` con instrucciones por OEM.
- Manufacturer detection helper.

---

## v4.0 — Driver insights (Fase 6)

Detección dentro del plugin usando GPS + acelerómetro + giroscopio + activity recognition.

- Eventos viaje: `tripStart`, `tripEnd`, `moving`, `stopped`, `providerChange`.
- Driving events: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `speeding`, `phoneUsageWhileDriving`.
- `possibleCrash` (sensor fusion).
- SOS helper local.

Detalle: ver [driving-events.md](driving-events.md).

---

## v4.2 — Sensor fusion real (Fase 8)

Acelerómetro + giroscopio reales para refinar `possibleCrash` y nuevo `phoneUsageWhileDriving`.

- Android: `Sensor.TYPE_LINEAR_ACCELERATION` + `Sensor.TYPE_GYROSCOPE` a `SENSOR_DELAY_GAME` (~50 Hz) vía `SensorFusionDetector`.
- iOS: `CMMotionManager.startDeviceMotionUpdatesToQueue` (50 Hz) vía `MAURSensorFusionDetector`.
- Activación: `drivingEvents.sensorFusion = true` (off por defecto; coste batería moderado).
- `possibleCrash` ahora trae `source: "gps" | "sensor"`. Sensor dispara con `|a| ≥ crashImpactG` (default 3 g) durante un trip activo — detecta impactos a baja velocidad (parking).
- `phoneUsageWhileDriving`: jitter de acelerómetro/giroscopio sostenido durante un trip activo y con la pantalla activa.
- Hot-reload: `configure()` reevalúa el pipeline de sensores en Android e iOS.

---

## Backend layer (opcional, fuera del plugin)

Si la app necesita geocercas, eventos espaciales, historial server-side, viajes/paradas o reportes, esa lógica vive en una capa backend, **no** en el plugin. Traccar es una opción razonable; ver [traccar.md](traccar.md) como ejemplo de integración HTTP. El plugin se conecta a Traccar (o a cualquier otra API) usando únicamente el HTTP transport personalizable entregado en v3.3.
