# Roadmap

Estado actual: **v3.2.0** (2026-02-28).

> **Principio de diseño.** Este es un plugin **global y backend-agnóstico**. No incorpora lógica de Traccar, GPSWox, OsmAnd ni de ninguna API propietaria. La compatibilidad con cualquier backend se logra mediante **transporte HTTP personalizable** (URL templating + body templating + métodos HTTP genéricos). Traccar y similares solo se documentan como **ejemplos de integración**, nunca como modo interno.

Orden de implementación (de mayor a menor impacto):

1. Auto-start Android
2. HTTP transport personalizable
3. Diagnóstico
4. Battery / OEM helpers
5. Driving / crash / SOS

---

## v3.3 — Auto-start Android (Fase 1)

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

## v3.4 — HTTP transport personalizable (Fase 2)

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

## v3.5 — Diagnóstico (Fase 3)

- `getDiagnostics()` extendido:
  - Android: `isRunning`, `locationServicesEnabled`, `fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted`, `notificationPermissionGranted`, `activityRecognitionGranted`, `batteryOptimizationIgnored`, `manufacturer`, `lastLocationAt`, `pendingSyncCount`, `startOnBoot`, `foregroundServiceType`.
  - iOS: `preciseLocationEnabled`, `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus`, `authorizationStatusText`.
- Evento `heartbeat` (intervalo configurable).
- Eventos sync: `syncStart`, `syncProgress`, `syncSuccess`, `syncError`.
- `mockLocationPolicy: 'allow' | 'flag' | 'drop'`. La **detección ya existe** (`isFromMockProvider` Android, `simulated` iOS); aquí solo se añade la política.

---

## v3.6 — Battery / OEM helpers (Fase 4)

- Helpers batería: `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`.
- `openAutoStartSettings()` por fabricante (Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, Samsung One UI).
- `getManufacturerHelp()` con instrucciones por OEM.
- Manufacturer detection helper.

---

## v4.0 — Driver insights (Fase 5)

Detección dentro del plugin usando GPS + acelerómetro + giroscopio + activity recognition.

- Eventos viaje: `tripStart`, `tripEnd`, `moving`, `stopped`, `providerChange`.
- Driving events: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `speeding`, `phoneUsageWhileDriving`.
- `possibleCrash` (sensor fusion).
- SOS helper local.

Detalle: ver [driving-events.md](driving-events.md).

---

## Backend layer (opcional, fuera del plugin)

Si la app necesita geocercas, eventos espaciales, historial server-side, viajes/paradas o reportes, esa lógica vive en una capa backend, **no** en el plugin. Traccar es una opción razonable; ver [traccar.md](traccar.md) como ejemplo de integración HTTP. El plugin se conecta a Traccar (o a cualquier otra API) usando únicamente el HTTP transport personalizable de v3.4.
