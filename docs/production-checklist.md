---
layout: default
title: Production Checklist
nav_order: 11
---

# Production Checklist

Antes de publicar una app que use este plugin, verificar cada punto.

## Configuración base

- [ ] `locationProvider` elegido conscientemente. Default seguro: `DISTANCE_FILTER_PROVIDER`.
- [ ] `desiredAccuracy` ajustado al caso de uso (HIGH para vehicular, MEDIUM para personal).
- [ ] `distanceFilter` y `stationaryRadius` calibrados con datos reales (no defaults ciegos).
- [ ] `url` y `syncUrl` apuntan a producción (no localhost / staging).
- [ ] `httpHeaders` incluye autenticación (`Authorization`, token, device id, etc.).
- [ ] `postTemplate` (si custom) incluye **todos** los campos requeridos por el backend, incluyendo `@events`, `@battery`, `@isCharging` si se usan.
- [ ] `sync: true` y `syncThreshold` razonable (default 100).

## Permisos

### Android

- [ ] `ACCESS_FINE_LOCATION` solicitado antes de `start()`.
- [ ] `ACCESS_BACKGROUND_LOCATION` solicitado tras FINE (Android 10+). Usar `requestBackgroundLocationPermission()`.
- [ ] `ACTIVITY_RECOGNITION` solicitado si se usa `ACTIVITY_PROVIDER` (Android 10+).
- [ ] `POST_NOTIFICATIONS` solicitado si `startForeground: true` (Android 13+).
- [ ] Battery optimization whitelist gestionado: `isIgnoringBatteryOptimizations()` + `requestIgnoreBatteryOptimizations()`.
- [ ] OEM autostart: guía al usuario via `openAutoStartSettings()` / `getManufacturerHelp()` para Xiaomi/Huawei/Oppo/Vivo.

### iOS

- [ ] `Info.plist` incluye `NSLocationWhenInUseUsageDescription` con texto claro y específico.
- [ ] `Info.plist` incluye `NSLocationAlwaysAndWhenInUseUsageDescription` si se usa background.
- [ ] `Info.plist` incluye `NSMotionUsageDescription` si se usa `ACTIVITY_PROVIDER`.
- [ ] Xcode → Background Modes → **Location updates** habilitado.
- [ ] Texto de usage justifica claramente el uso a App Store reviewers.

## Versión Android

- [ ] `targetSdkVersion >= 34` para Play Store (requerido 2024+).
- [ ] `cordova-android >= 12` (compatible con targetSdk 34+).
- [ ] `foregroundServiceType="location"` en el manifest (ya incluido por `plugin.xml`).
- [ ] `compileSdkVersion >= 34`.
- [ ] Android 14: `start()` invocado desde Activity visible o user gesture (no desde receiver background).

## Versión iOS

- [ ] Deployment target `>= 12.0` recomendado (`>= 14.0` para callbacks modernos de auth).
- [ ] CocoaPods actualizado: `pod repo update && pod install`.
- [ ] Tested en device real (Simulator no produce GPS realista ni CoreMotion).

## Plugin / SDK

- [ ] `@josuelmm/cordova-background-geolocation` version `4.5.4` o superior (post-hardening).
- [ ] `npm ci` (no `npm install`) en CI para builds reproducibles.
- [ ] `npm pack --dry-run` sin warnings/errors. Verifica tamaño del paquete (debe excluir tests, .git, node_modules).
- [ ] `npm run build:angular` (si usás el wrapper Angular).
- [ ] `npm publish --dry-run` previo al release real.

## Testing manual

### Android

- [ ] **Con Google Play Services** — `ACTIVITY_PROVIDER` y `DISTANCE_FILTER` funcionan.
- [ ] **Sin Google Play Services** (Huawei/HMS, emulator AOSP) — `DISTANCE_FILTER` y `RAW` funcionan, `ACTIVITY` falla con `SERVICE_ERROR` claro.
- [ ] **Android 10/11** — background tracking pide permiso "Allow all the time" correctamente.
- [ ] **Android 13** — notificación foreground visible (con `POST_NOTIFICATIONS`).
- [ ] **Android 14** — `start()` funciona desde Activity; `BootCompletedReceiver` no rompe con `FOREGROUND_SERVICE_LOCATION`.
- [ ] **Xiaomi/Huawei real** — tracking sobrevive cerrar app por más de 30 min.
- [ ] **startOnBoot** — tras reboot, servicio reaparece dentro de 1 min.

### iOS

- [ ] **Foreground tracking** — locations llegan al mover el dispositivo.
- [ ] **Background tracking** — locations siguen llegando con la app cerrada (Home button / locked screen).
- [ ] **ACTIVITY iniciando quieto** — abre app sin moverse, debe emitir `onStationaryChanged` dentro de ~30s.
- [ ] **ACTIVITY caminando/conduciendo** — debe emitir `onActivityChanged` con type correcto.
- [ ] **Auth status changes** — apagar/encender location services dispara `onAuthorizationChanged`.

### Sync y persistencia

- [ ] **POST default sin postTemplate** — backend recibe `latitude`, `longitude`, `time`, `accuracy`, `events`, `battery`, `isCharging`.
- [ ] **POST custom postTemplate** — solo los campos del template llegan; el resto se ignora.
- [ ] **Sync fallido + forceSync** — desconectar red, generar locations, reconectar, `forceSync()` envía la cola completa.
- [ ] **`maxLocations` recycling** — generar > maxLocations fixes; verificar que el oldest se descarta sin mezclar events viejos con location nueva.
- [ ] **`events` / `battery` / `isCharging` sobreviven sync** — locations con events, desconectar, reconectar, verificar que el server recibe events después del forceSync.

### Driver insights (si aplica)

- [ ] **hardBrake / rapidAcceleration / sharpTurn** — generados durante un trip activo.
- [ ] **possibleCrash** — generado con `source: 'gps'`; si `sensorFusion: true`, también con `source: 'sensor'`.
- [ ] **tripStart / tripEnd / moving / stopped** — dispara secuencia esperada en una salida de prueba.

## Configuración por defecto recomendada para producción

```js
BackgroundGeolocation.configure({
  // provider universal
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.MEDIUM_ACCURACY,

  // calibración estándar
  distanceFilter: 50,
  stationaryRadius: 50,
  interval: 60000,
  fastestInterval: 5000,
  activitiesInterval: 10000,

  // ahorro batería
  wakeLockMode: 'posting',
  enableWatchdog: false,
  saveBatteryOnBackground: true,  // iOS
  pauseLocationUpdates: false,     // iOS

  // filtros calidad
  maxAcceptedAccuracy: 200,        // descarta fixes peores que 200 m
  activityConfidenceThreshold: 50, // default; subir si hay jitter

  // persistencia
  startOnBoot: true,
  stopOnTerminate: false,
  notificationsEnabled: true,
  startForeground: true,
  notificationTitle: 'Tracking activo',
  notificationText: 'Compartiendo ubicación con el servidor',
  notificationIconColor: '#4CAF50',

  // backend
  url: 'https://backend.example.com/api/locations',
  syncUrl: 'https://backend.example.com/api/locations/sync',
  syncThreshold: 50,
  sync: true,
  httpMethod: 'POST',
  syncHttpMethod: 'POST',
  httpMode: 'batch',
  syncMode: 'batch',
  httpHeaders: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ${USER_TOKEN}'
  },
  maxLocations: 10000,
  includeBattery: true
});
```

## Soporte al usuario en producción

Implementar pantalla de diagnóstico que muestre:

- `getDiagnostics()` completo.
- `getPendingSyncCount()`.
- Última error capturada del listener `'error'`.
- Botón "Reintentar sync ahora" → `forceSync()`.
- Botón "Abrir ajustes de batería" → `openBatterySettings()`.
- Botón "Abrir ajustes de autostart (OEM)" → `openAutoStartSettings()`.
- Indicación si el dispositivo está en una OEM agresiva (`getManufacturerHelp()`).

Sin esto, los usuarios atascados en Xiaomi/Huawei reportarán "no funciona" sin que tengas datos para investigar.

---

Ver también: [`providers.md`](providers.md), [`battery.md`](battery.md), [`permissions.md`](permissions.md), [`troubleshooting.md`](troubleshooting.md).
