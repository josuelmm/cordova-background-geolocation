# Cordova Background Geolocation
# Capacitor Background Geolocation
# Background Geolocation

[![npm](https://img.shields.io/npm/v/@josuelmm/cordova-background-geolocation?style=flat-square)](https://www.npmjs.com/package/@josuelmm/cordova-background-geolocation)
![npm downloads](https://img.shields.io/npm/dm/@josuelmm/cordova-background-geolocation?style=flat-square)

[![GitHub issues](https://img.shields.io/github/issues/josuelmm/cordova-background-geolocation?style=flat-square)](https://github.com/josuelmm/cordova-background-geolocation/issues)
[![GitHub stars](https://img.shields.io/github/stars/josuelmm/cordova-background-geolocation?style=flat-square)](https://github.com/josuelmm/cordova-background-geolocation/stargazers)
![GitHub last commit](https://img.shields.io/github/last-commit/josuelmm/cordova-background-geolocation?style=flat-square)

## What it does

This plugin provides **background and foreground geolocation** for **Cordova**, **Capacitor**, and **Ionic** apps. It is tested and works with current versions of Capacitor and Ionic. It is more battery- and data-efficient than the HTML5 Geolocation API and is designed to keep tracking location even when the app is in the background or the screen is off.

**Features:**

- **Circular region monitoring** and **stop detection** to reduce battery use
- **Activity-based provider** (e.g. walking, driving, still) for smarter updates
- **Configurable distance/interval** filtering and optional **HTTP posting** of locations to your server
- **Foreground service** (Android) with a persistent notification so the OS does not kill the tracker
- Works alongside other geolocation sources (e.g. `navigator.geolocation`)

**Self-contained:** This plugin works on its own. You install it, call `BackgroundGeolocation.configure()`, `start()`, etc. directly. TypeScript definitions (`.d.ts`) are included. You do **not** need any wrapper or extra package for Capacitor or Cordova.

**Capacitor & Ionic:** Use the plugin in a Capacitor app (with or without Ionic). Install the package, run `npx cap sync`, then use the same JavaScript API. The plugin is compatible with recent Capacitor (e.g. 6.x, 7.x) and Ionic (7.x, 8.x) versions.

---

## Installation

### npm (Capacitor or modern Cordova)

```bash
npm install @josuelmm/cordova-background-geolocation
```

For Capacitor, sync native projects after installing:

```bash
npx cap sync
```

### Cordova CLI

```bash
cordova plugin add @josuelmm/cordova-background-geolocation
```

Optional: set variables for Google Play Services and iOS permission strings:

```bash
cordova plugin add @josuelmm/cordova-background-geolocation \
  --variable GOOGLE_PLAY_SERVICES_VERSION=17+ \
  --variable ALWAYS_USAGE_DESCRIPTION="Your app needs location for ..." \
  --variable MOTION_USAGE_DESCRIPTION="Your app uses motion for ..."
```

**Notes:**

- **AndroidX:** Use plugin version 2.x. For non-AndroidX projects use 1.x.
- **Android 14+:** You may need to justify foreground location usage (e.g. in Play Console) for `FOREGROUND_SERVICE_LOCATION`.
- **Android 13+:** Request runtime `POST_NOTIFICATION` permission if you want the tracking notification to show.
- After changing plugin options, remove and reinstall the plugin for changes to take effect.

### Android: configuring your app (recommended)

If your app’s merged manifest ends up with a `foregroundServiceType` other than `location` (e.g. due to other libraries or templates), you may see *"foregroundServiceType 0x00000004 is not a subset of ..."* and the tracking notification may not show. To **force** the correct type and avoid starting the service at boot without permissions, add the following in **your** Android project (the app that consumes the plugin).

**1. AndroidManifest.xml** (inside `<application>`, and add `xmlns:tools="http://schemas.android.com/tools"` on the root `<manifest>` if not already present):

```xml
<!-- Background Location Service: force foregroundServiceType="location" in the merged manifest -->
<service
    android:name="com.marianhello.bgloc.service.LocationServiceImpl"
    android:exported="false"
    android:foregroundServiceType="location"
    tools:replace="android:foregroundServiceType" />

<!-- Optional: disable start on boot to avoid ForegroundServiceDidNotStartInTimeException when location permission is not granted -->
<receiver
    android:name="com.marianhello.bgloc.BootCompletedReceiver"
    android:enabled="false"
    tools:replace="android:enabled" />
```

`tools:replace="android:foregroundServiceType"` makes the merged manifest use your `location` value instead of whatever another dependency might declare, so the type matches what the plugin uses in `startForeground()`.

**2. res/values/strings.xml** (required by the plugin for the sync account; replace `site.seelight.client` with your app’s package or identifier if different):

```xml
<!-- Required by cordova-background-geolocation-plugin (sync account) -->
<string name="plugin_bgloc_account_name">Background location</string>
<string name="plugin_bgloc_account_type">site.seelight.client.bgloc.account</string>
<string name="plugin_bgloc_content_authority">site.seelight.client.bgloc</string>
```

**Notification labels (showTime / showDistance):** If you use `showTime: true` or `showDistance: true`, the notification shows a line for elapsed time and one for distance. **By default the labels are in English** ("Time" and "Distance"). If you want Spanish or another language, add these optional strings in your app so the plugin uses them; if you don’t define them, English is used.

Example — English (default, optional in `res/values/strings.xml`):

```xml
<string name="plugin_bgloc_notification_time_label">Time</string>
<string name="plugin_bgloc_notification_distance_label">Distance</string>
```

Example — Spanish: add in `res/values-es/strings.xml` (or your locale folder):

```xml
<string name="plugin_bgloc_notification_time_label">Tiempo</string>
<string name="plugin_bgloc_notification_distance_label">Distancia</string>
```

This makes your app enforce the correct foreground service type and defines the strings the plugin needs for the sync account.

---

## Usage (with or without Angular)

You can use the plugin in two ways:

- **Without Angular** — Use the global `BackgroundGeolocation` object (Cordova/Capacitor injects it after `deviceready`). Same in plain JS, React, Vue, or any framework.
- **With Angular (Ionic Angular)** — Import the Angular service and inject it; same API, better testability and no global. See [Angular (Ionic Angular)](#angular-ionic-angular) below.

The following steps use the global API. If you use Angular, call the same methods on the injected service instead.

### TypeScript imports

You can use either the **native** type names or the **Awesome Cordova Plugins–style** aliases.

**Option A — Named import (same style as @awesome-cordova-plugins):**

```ts
import {
  BackgroundGeolocation,
  BackgroundGeolocationConfig,
  BackgroundGeolocationEvents,
  BackgroundGeolocationResponse
} from '@josuelmm/cordova-background-geolocation';

// After deviceready (use the global object, not injection):
BackgroundGeolocation.configure({ distanceFilter: 50 } as BackgroundGeolocationConfig);
BackgroundGeolocation.on('location', (loc: BackgroundGeolocationResponse) => { ... });
```

**Angular/Ionic:** One import for service and types: use `BackgroundGeolocationService` (and types like `BackgroundGeolocationConfig`, `BackgroundGeolocationResponse`) from `@josuelmm/cordova-background-geolocation/angular`. See [Angular (Ionic Angular)](#angular-ionic-angular).

**Option B — Default export + native type names:**

```ts
import BackgroundGeolocation from '@josuelmm/cordova-background-geolocation';
import type {
  ConfigureOptions,
  Location,
  LocationOptions,
  ServiceStatus,
  Activity,
  BackgroundGeolocationError,
  LogEntry
} from '@josuelmm/cordova-background-geolocation';

// After deviceready:
BackgroundGeolocation.configure({ distanceFilter: 50 } as ConfigureOptions);
BackgroundGeolocation.on('location', (loc: Location) => { ... });
```

**Type aliases / compatibility:** `BackgroundGeolocationConfig` = `ConfigureOptions`, `BackgroundGeolocationResponse` = `Location`. `BackgroundGeolocationEvents` is an enum (e.g. `BackgroundGeolocationEvents.location`). Enums and types match [@awesome-cordova-plugins/background-geolocation](https://github.com/danielsogl/awesome-cordova-plugins/blob/master/src/%40awesome-cordova-plugins/plugins/background-geolocation/index.ts) where applicable; **accuracy values** in this plugin are `0, 100, 1000, 10000` (use `BackgroundGeolocation.HIGH_ACCURACY` etc. or the `BackgroundGeolocationAccuracy` enum from the types).

**Constants** (accuracy, provider, mode) are on the plugin object:

```ts
BackgroundGeolocation.HIGH_ACCURACY
BackgroundGeolocation.ACTIVITY_PROVIDER
BackgroundGeolocation.BACKGROUND_MODE
BackgroundGeolocation.FOREGROUND_MODE
```

### 1. Configure

Set your preferred provider, accuracy, intervals, and optional server URLs. All options are optional; you can reconfigure later with a subset (options are merged).

#### 1.1 `locationProvider` — which engine produces the fixes

This is the **most important choice**. It changes how often, how accurately, and how cheaply (battery-wise) the plugin gets locations. Three values, pick **one**:

| Value | Constant | Internals | When to use |
|---|---|---|---|
| `0` | `BackgroundGeolocation.DISTANCE_FILTER_PROVIDER` (default) | **Hybrid (v4.5.2+)**: `FusedLocationProviderClient` cuando Google Play Services está disponible, fallback automático a `LocationManager` cuando no (Huawei/HMS, AOSP, ChinaROMs). Implementa una **state machine de detección stationary**: mientras se mueve muestrea normal; al detenerse usa **polling lazy** (3 min) y **polling aggressive** (1 min cerca del borde) vía `AlarmManager`. **No usa geofences** (eliminados por decisión de producto); la salida del estado stationary se detecta solo por polling. | **Default para la mayoría de apps.** Tracking personal, couriers de delivery, cualquiera que necesite ubicación sin drenar batería al estar parado. **Funciona con o sin Google Play Services.** |
| `1` | `BackgroundGeolocation.ACTIVITY_PROVIDER` | Google Play Services `FusedLocationProviderClient` + **Activity Recognition** (walking / driving / still). When `STILL` is detected, the provider stops requesting fixes; when activity resumes, it restarts. Uses `LocationRequest.Builder` + `Priority.PRIORITY_HIGH_ACCURACY` (or balanced, per `desiredAccuracy`). | Apps where the **activity matters** (fitness, fleet, ride-hailing). Best battery profile when the user spends a lot of time still. **Requires Google Play Services** on the device and `ACTIVITY_RECOGNITION` runtime permission on Android 10+. |
| `2` | `BackgroundGeolocation.RAW_PROVIDER` | Direct `LocationManager` requests with **no stationary detection, no batching, no filtering**. Every fix the OS produces is forwarded. | Vehicle tracking with a **constant interval**, dashcams, anything where you want raw GPS without the plugin trying to be smart. **Highest battery cost.** |

```js
// Recommended for personal apps (Life360-style)
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.MEDIUM_ACCURACY,
  distanceFilter: 50,
  stationaryRadius: 50
});

// Recommended for fleet/vehicle tracking
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.ACTIVITY_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  interval: 15000,
  fastestInterval: 10000,
  activitiesInterval: 30000
});

// Raw — every fix, no smart filtering
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.RAW_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  interval: 5000
});
```

**iOS note:** all three constants exist for API parity, but iOS internally always uses `CLLocationManager`. The plugin maps `DISTANCE_FILTER_PROVIDER` and `RAW_PROVIDER` to standard location updates, and `ACTIVITY_PROVIDER` enables `CMMotionActivityManager` for activity detection on top.

#### 1.2 `startOnBoot` — survive a reboot (Android only)

When `true`, the plugin re-arms tracking automatically after the device reboots or after an app update — without the user opening the app.

**How it works internally:**

1. The plugin registers a `BootCompletedReceiver` that listens for `BOOT_COMPLETED`, `QUICKBOOT_POWERON` (HTC/Xiaomi), `com.htc.intent.action.QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` (Play Store updates).
2. On boot, the receiver loads the **persisted `Config`** from SQLite (since v4.4.1 / v4.5.0 the full config is stored as `config_json`, so `httpMethod`, `queryParams`, `drivingEvents`, etc. survive).
3. It checks `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, and on Android 10+ also `ACCESS_BACKGROUND_LOCATION`. If any is missing, it logs and gives up — it will **NOT** request permissions from a receiver (the OS forbids it).
4. If permissions are OK, it calls `context.startForegroundService(...)`. The plugin's notification appears and tracking resumes with the same config the app had configured before reboot.

**Required runtime permissions** (the app must request them at least once **while it is in foreground**, before relying on `startOnBoot`):

```ts
// Order matters — the OS dialog flow is strict on Android 11+.
await BackgroundGeolocation.requestNotificationPermission();        // Android 13+
// ACCESS_FINE_LOCATION via Cordova permission API or @capacitor/geolocation
await BackgroundGeolocation.requestBackgroundLocationPermission();  // Android 10+, user MUST pick "Allow all the time"
```

Without **"Allow all the time"** the receiver runs but the foreground service is killed shortly after, because the OS treats the boot-launched service as "background-started" and a background start without background permission is not allowed.

**iOS:** Apple does **not** allow third-party apps to auto-start at boot. `startOnBoot: true` is silently ignored on iOS. The closest equivalent is **`significantLocationChanges`** (already implemented), which wakes the app when the device moves to a new cell tower / region — but only if the app was running at least once after the reboot. Document this expectation to your iOS users.

**Common pitfalls:**

- **OEM kills (Xiaomi MIUI, Huawei EMUI, Oppo, Vivo, OnePlus, Asus)** disable auto-start by default for non-system apps. The user must enable the app in *"Autostart"* / *"Background activity"* settings of the OEM. Use `BackgroundGeolocation.openAutoStartSettings()` to take the user directly there, and `getManufacturerHelp()` for instructions.
- **Android 12+ `ForegroundServiceStartNotAllowedException`** — if the device is in *Doze* deep-idle when it reboots, the system may block the foreground service. The plugin catches the exception and logs it; the next user-triggered `start()` (when they open the app) will succeed.
- **Doze whitelist** — `isIgnoringBatteryOptimizations()` + `requestIgnoreBatteryOptimizations()` reduce the chance of the OS putting the app on the "restricted" list after a reboot.

```js
// Full recommended setup for "track this user across reboots"
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.MEDIUM_ACCURACY,
  distanceFilter: 50,
  startForeground: true,            // mandatory for Android 8+ background tracking
  notificationsEnabled: true,
  notificationTitle: 'Live tracking',
  notificationText: 'Your location is being shared',
  startOnBoot: true,                // survive reboots
  stopOnTerminate: false,           // keep tracking when user swipes the app away
  url: 'https://my.api/locations',
  syncUrl: 'https://my.api/sync'
});
```

#### 1.3 Other main options

**Main options:**

| Option | Description |
|--------|-------------|
| `locationProvider` | `ACTIVITY_PROVIDER`, `DISTANCE_FILTER_PROVIDER`, or `RAW_PROVIDER` |
| `desiredAccuracy` | `HIGH_ACCURACY`, `MEDIUM_ACCURACY`, `LOW_ACCURACY`, `PASSIVE_ACCURACY` |
| `distanceFilter` | Minimum metres the device must move before an update (e.g. 50) |
| `stationaryRadius` | Metres from “stationary” point before aggressive tracking (e.g. 50) |
| `interval` / `fastestInterval` / `activitiesInterval` | Android timing (ms) |
| `notificationTitle` / `notificationText` | Android foreground notification text |
| `url` | Server URL where **each** location is posted immediately (if post fails, it goes to sync queue) |
| `syncUrl` | Server URL where **pending** locations are sent in batch (when count reaches `syncThreshold` or on `forceSync()`) |
| `syncThreshold` | Number of pending locations before automatic batch sync (default 100) |
| `sync` | When `true` (default), automatic sync and `forceSync()` run. When `false`, sync is disabled (locations are still stored). |
| `httpHeaders` / `headers` | Headers for every request (e.g. `{ 'Content-Type': 'application/json', 'Authorization': 'Bearer TOKEN' }`). `headers` is the alias added in 3.3.0; both are supported. |
| `httpMethod` | **Since 3.3.0.** HTTP method for `url`. `POST` (default) `| GET | PUT | PATCH`. Use `GET` together with URL templating to deliver positions through the query string. |
| `syncHttpMethod` | **Since 3.3.0.** HTTP method for `syncUrl`. Same values as `httpMethod`. |
| `httpMode` / `syncMode` | **Since 3.3.0.** `batch` (default) or `single` (one HTTP request per location). `single` is required when the corresponding method is `GET`. |
| `queryParams` | **Since 3.3.0.** Static placeholder values for URL/body templating (e.g. `{ device_id: 'USER_123' }`). See [HTTP transport](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#http-transport-since-330). |
| `postTemplate` / `bodyTemplate` | Object or array of properties to send. Supports `@latitude`, `@longitude`, ... and the new `{latitude}`, `{lon}`, `{timestamp_iso}`, ... placeholders on string values. `bodyTemplate` is the alias added in 3.3.0. |
| `maxLocations` | Max locations kept in DB (default 10000). Should be &gt; `syncThreshold`. |

**Diagnostics / sync (3.5+):**

| Option | Description |
|--------|-------------|
| `heartbeatInterval` | ms between `heartbeat` events. `0` (default) disables. |
| `mockLocationPolicy` | `'allow'` (default) / `'flag'` / `'drop'` — what to do with mocked locations. |

**Driver insights (4.0+):**

| Option | Description |
|--------|-------------|
| `drivingEvents.enabled` | Master switch. Default `false`. |
| `drivingEvents.speedLimit` | km/h for `speeding` event. `0` disables. |
| `drivingEvents.minMovingSpeed` | m/s threshold for "moving" (default 1.0). |
| `drivingEvents.stoppedDuration` | ms of below-threshold needed for "stopped" (default 60 000). |
| `drivingEvents.minTripSpeed` | m/s threshold for `tripStart` (default 3.0). |
| `drivingEvents.minTripDuration` | ms sustained to fire `tripStart` (default 30 000). |
| `drivingEvents.hardBrakeMps2` | m/s² threshold for `hardBrake` (default 3.5; `0` disables). |
| `drivingEvents.rapidAccelMps2` | m/s² threshold for `rapidAcceleration` (default 3.5). |
| `drivingEvents.sharpTurnDegPerSec` | deg/s for `sharpTurn` at speed ≥ 5 m/s (default 30). |
| `drivingEvents.crashImpactKmh` | km/h drop within window for GPS `possibleCrash` (default 25). |
| `drivingEvents.crashWindowMs` | window for GPS crash detection (default 2 000). |
| `drivingEvents.sensorFusion` | **4.2+.** Enable accelerometer + gyroscope. Off by default. |
| `drivingEvents.crashImpactG` | g threshold for sensor `possibleCrash` (default 3.0). |
| `drivingEvents.sensorCrashCooldownMs` | ms cooldown between sensor crash detections (default 10 000). |
| `drivingEvents.phoneUsageWindowMs` | ms of sustained jitter to fire `phoneUsageWhileDriving` (default 4 000). |
| `drivingEvents.phoneUsageCooldownMs` | ms cooldown between phone-usage events (default 60 000). |

**Battery payload (4.4+):**

| Option | Description |
|--------|-------------|
| `includeBattery` | Stamp `battery` (0-100) and `isCharging` on every fix. Default `true`. |

**Battery saving (4.5.1):**

| Option | Description |
|--------|-------------|
| `wakeLockMode` | `'none' \| 'posting' \| 'always'`. Default `'posting'`. |
| `stationaryTimeout` | ms of no movement → declare stationary (Android `DISTANCE_FILTER_PROVIDER`). Default 300 000. |
| `stationaryPollInterval` | Lazy poll interval while stationary (ms). Default 180 000. |
| `stationaryPollFast` | Aggressive poll near the stationary boundary (ms). Default 60 000. |

**Provider hardening (4.5.2):**

| Option | Description |
|--------|-------------|
| `activityConfidenceThreshold` | 0-100. Ignore STILL/ACTIVE transitions below this confidence (prevents jittery GPS start/stop). iOS normalizes Low/Med/High → 20/40/80. Default 50. Applies to `ACTIVITY_PROVIDER`. |
| `maxAcceptedAccuracy` | Maximum accepted horizontal accuracy in meters. Fixes worse than this are dropped before persist/POST/JS emission. **Off by default (`null`)** to avoid regressing existing apps. Recommended values: `100` for vehicular tracking, `500` for tolerant scenarios. All providers. |

> **Caveats v4.5.2:**
> - `maxAcceptedAccuracy` está **apagado por defecto**. Si quieres descartar fixes ruidosos (típico en GPS vehicular o indoor), seteá explícito un valor (ej. `100`).
> - **ACTIVITY_PROVIDER sin `ACTIVITY_RECOGNITION`** (Android 10+): el provider emite `PERMISSION_DENIED_ERROR` pero **degrada a tracking continuo** (no puede detectar STILL/ACTIVE). Si querés ahorro real, pedí el permiso antes de iniciar o cambiá a `DISTANCE_FILTER_PROVIDER`.
> - **`enableWatchdog`** está **apagado por defecto**. Reinicia el provider si deja de llegar `location` por ~60s (solo si `tripActive`). Útil en apps vehiculares/críticas; aumenta consumo. Recomendado `false` salvo necesidad real.
> - **`postTemplate` reemplaza el payload completo** — no hay merge con el default. Si lo defines, debes incluir manualmente `@events`, `@battery`, `@isCharging` si los necesitas. Detalle en [docs/api.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#custom-post-template).
> - **`events` / `battery` / `isCharging` sobreviven la cola de sync** desde v4.5.0 (Android schema v22, iOS schema v7). Si un POST falla, esos campos persisten para `forceSync`/sync automático. Auto-migración desde versiones previas.
> - **`pendingDrivingEvents` son in-memory**: el detector almacena eventos (hardBrake, speeding, etc.) hasta anexarlos al próximo `location` (cap 20, TTL 60s). Si el proceso muere antes del próximo fix, esos eventos pueden perderse. La cola principal de ubicaciones no se afecta.
> - **`startOnBoot`** requiere: `stopOnTerminate: false` (o config persistida), `ACCESS_BACKGROUND_LOCATION` concedido (Android 10+), y que el fabricante no bloquee autostart/battery optimization. Xiaomi/Huawei/Oppo/Vivo suelen requerir desactivar manualmente la optimización de batería — usar `openBatterySettings()` / `openAutoStartSettings()` para guiar al usuario.
> - **Permisos por versión Android**: Android 10+ requiere `ACCESS_BACKGROUND_LOCATION` para tracking real en background. Android 10+ requiere `ACTIVITY_RECOGNITION` para `ACTIVITY_PROVIDER`. Android 13+ requiere `POST_NOTIFICATIONS` para la notificación foreground. Android 14+ requiere `FOREGROUND_SERVICE_LOCATION`. El plugin no fuerza permisos automáticamente; usar los helpers `requestBackgroundLocationPermission()`, `requestActivityRecognitionPermission()` y `requestNotificationPermission()`.

**Android-only:** `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, `notificationSyncFailedText` — texts shown in the notification while syncing (defaults in English; set for localization). `startForeground`, `notificationsEnabled`, `startOnBoot`, `stopOnTerminate`, `enableWatchdog`.

**iOS-only:** `activityType`, `pauseLocationUpdates`, `saveBatteryOnBackground`, `showsBackgroundLocationIndicator` (since 3.4.0; iOS 11+, shows the blue background indicator).

**Full reference** (every option, with types, defaults and platform/provider hints): [`docs/api.md`](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md).

Example (default POST + JSON):

```js
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.ACTIVITY_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  stationaryRadius: 50,
  distanceFilter: 50,
  notificationTitle: 'Background tracking',
  notificationText: 'enabled',
  debug: true,
  interval: 10000,
  fastestInterval: 5000,
  activitiesInterval: 10000,
  startOnBoot: true,
  stopOnTerminate: false,
  url: 'https://yourserver.com/location',
  syncUrl: 'https://yourserver.com/location',
  syncThreshold: 5,
  sync: true,
  httpHeaders: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer YOUR_TOKEN'
  },
  postTemplate: {
    lat: '@latitude',
    lon: '@longitude',
    timestamp: '@time'
  }
});
```

Example (since 3.3.0, GET with URL templating — works against Traccar OsmAnd, GPSWox, custom REST, etc.):

```js
BackgroundGeolocation.configure({
  url: 'https://your-backend/track?uid={uid}&lat={latitude}&lon={longitude}&t={timestamp_iso}&spd={speed}',
  httpMethod: 'GET',
  httpMode: 'single',
  queryParams: { uid: 'USER_DEVICE_123' },
  startOnBoot: true,
  stopOnTerminate: false
});
```

> **Android runtime permissions.** With `startOnBoot: true`, the app must request `ACCESS_FINE_LOCATION` first, then `POST_NOTIFICATIONS` (Android 13+), and finally `ACCESS_BACKGROUND_LOCATION` (Android 10+, the user must pick **Allow all the time**). Without the background permission the plugin will skip the start on boot. iOS does **not** allow auto-start on device boot (Apple restriction). See [Auto-start on Android boot](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#auto-start-on-android-boot-since-330) for the full flow.

### 2. Start tracking

```js
BackgroundGeolocation.start();
```

### 3. Listen for events

```js
BackgroundGeolocation.on('location', function (location) {
  console.log(location.latitude, location.longitude);
});

BackgroundGeolocation.on('error', function (error) {
  console.warn(error.code, error.message);
});
```

**HTTP headers (second way — dynamic):** When your server responds with **401 Unauthorized**, the plugin emits the `http_authorization` event. You can then refresh your token and reconfigure headers so the next request uses them (e.g. a new `Authorization` header):

```js
BackgroundGeolocation.on('http_authorization', function () {
  // e.g. get a new token from your auth API, then:
  BackgroundGeolocation.configure({
    httpHeaders: { 'Authorization': 'Bearer ' + newToken }
  });
});
```

### 4. Stop tracking

```js
BackgroundGeolocation.stop();
```

### 5. Sync queue (syncUrl): pending count, force sync, clear queue

When you use `syncUrl`, locations that fail to post to `url` (or that are only queued for sync) are sent in batch to `syncUrl`.

**How sync sends data (Content-Type):** It depends on the `Content-Type` you set in `httpHeaders`. Many people assume “one request per location”; that is only true for form encoding.

| Content-Type | Sync to `syncUrl` |
|--------------|-------------------|
| **`application/json`** (default) | **One POST** with a JSON **array** of all locations in the batch. |
| **`application/x-www-form-urlencoded`** | **One POST per location** (same flat `key=value&...` as real-time to `url`). Same endpoint can handle both. |

So: with **JSON** you get one request per batch (e.g. 100 locations in one body). With **form-urlencoded** you get one request per location (one record per POST). For headers, retries, `postTemplate` and full behaviour see [HTTP posting](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/http_posting.md) and [API](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md).

You can:

- **Get pending count** — `getPendingSyncCount()` returns how many locations are waiting to be synced.
- **Force sync now** — `forceSync()` sends all pending locations immediately (ignores `syncThreshold`). No-op if `sync: false`.
- **Clear queue** — `clearSync()` discards all pending locations (they will not be sent). Use for a “Clear queue” or “Discard” button.

```js
// Show "X locations pending" and let user sync or clear
BackgroundGeolocation.getPendingSyncCount()
  .then(function (count) {
    console.log('Pending to sync:', count);
    // e.g. show UI: "Sync (5)" or "Clear queue"
  });

// User taps "Sync now"
BackgroundGeolocation.forceSync().then(function () {
  console.log('Sync completed');
});

// User taps "Clear queue"
BackgroundGeolocation.clearSync().then(function () {
  console.log('Queue cleared');
});
```

More on sync (headers, retries, postTemplate): [HTTP posting](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/http_posting.md). Full options and methods: [API](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md).

### 6. Other methods (summary)

| Method | Since | Description |
|--------|-------|-------------|
| `getConfig(success, fail)` | 1.0 | Get current configuration (merged options). |
| `getLocations(success, fail)` | 1.0 | Get all stored locations. |
| `getValidLocations(success, fail)` | 1.0 | Get locations not yet posted (valid only). |
| `deleteLocation(id, success, fail)` | 1.0 | Delete one location by id. |
| `deleteAllLocations(success, fail)` | 1.0 | Delete all stored locations. |
| `getCurrentLocation(success, fail, options)` | 1.0 | One-shot location (e.g. timeout, maximumAge). |
| `getPluginVersion(success, fail)` | 1.0 | Plugin version string (e.g. `"4.5.1"`). |
| `checkStatus(success, fail)` | 1.0 | Service status (isRunning, authorization, etc.). |
| `showAppSettings()` / `openSettings()` | 1.0 | Open app settings. |
| `showLocationSettings()` | 1.0 | Open system location settings. |
| `getLogEntries(limit, fromId, minLevel, success, fail)` | 1.0 | Debug log entries. |
| `getPendingSyncCount(success, fail)` | 3.1 | Number of locations pending sync. |
| `forceSync(success, fail)` | 3.1 | Send pending locations immediately (ignores `syncThreshold`). |
| `clearSync(success, fail)` | 3.1 | Discard pending sync queue without sending. |
| `startSession(success, fail)` | 3.2 | Start session: clear session table and store new locations until `clearSession()`. |
| `getSessionLocations(success, fail)` | 3.2 | All locations in current session. |
| `clearSession(success, fail)` | 3.2 | Clear session table (call when route finished). |
| `getSessionLocationsCount(success, fail)` | 3.2 | Number of locations in current session. |
| `getDiagnostics(success, fail)` | 3.5 | Extended diagnostics: permissions, battery optimisation, OEM, last fix age, pending sync, iOS precise location, etc. |
| `isIgnoringBatteryOptimizations(success, fail)` | 3.6 | Android only. `true` if app is on the Doze whitelist. iOS always `true`. |
| `requestIgnoreBatteryOptimizations(success, fail)` | 3.6 | Android only. Opens the system dialog to add the app to the whitelist. |
| `openBatterySettings(success, fail)` | 3.6 | Android only. Opens the battery-related settings screen for this app. |
| `openAutoStartSettings(success, fail)` | 3.6 | Android only. Opens the OEM-specific auto-start screen (Xiaomi/Huawei/Oppo/Vivo/OnePlus/Asus). |
| `getManufacturerHelp(success, fail)` | 3.6 | Returns `{ manufacturer, steps[] }` with OEM-specific guidance text. |
| `triggerSOS(payload?, success, fail)` | 4.0 | Emit a single `sos` event with the latest location plus your payload. Host app handles actual SOS workflow. |
| `requestBackgroundLocationPermission(success, fail)` | 4.5 | Android 10+. Request `ACCESS_BACKGROUND_LOCATION`. iOS / older Android resolve `{ granted: true, notRequired: true }`. |
| `requestActivityRecognitionPermission(success, fail)` | 4.5 | Android 10+. Request `ACTIVITY_RECOGNITION`. |
| `requestNotificationPermission(success, fail)` | 4.5 | Android 13+. Request `POST_NOTIFICATIONS`. |
| `headlessTask(task)` | 1.0 | **Android only.** Run JS when app is terminated and `stopOnTerminate: false`. iOS no-op. |

All methods return a **Promise** if you omit the `success` / `fail` callbacks.

### 7. Events (summary)

Subscribe with `BackgroundGeolocation.on(eventName, callback)`. Unsubscribe with the returned object’s `remove()` or by calling `removeAllListeners(eventName)`.

#### Core (1.0+)

| Event | Payload | When |
|-------|---------|------|
| `location` | Location object | New location (foreground/background). |
| `stationary` | Location | Device stopped (activity provider). |
| `activity` | Activity type | Activity changed (walking, driving, still). |
| `error` | `{ code, message }` | Error (e.g. permission, timeout). |
| `start` | — | Tracking started. |
| `stop` | — | Tracking stopped. |
| `authorization` | status | Permission status changed. |
| `background` / `foreground` | — | App entered background/foreground. |
| `http_authorization` | — | Server returned 401; refresh token and reconfigure headers. |
| `abort_requested` | — | Server returned 285 (updates not required). |

#### Diagnostics + Sync (3.5+)

| Event | Payload | When |
|-------|---------|------|
| `heartbeat` | `Location` or `void` | Periodic heartbeat at `heartbeatInterval` ms. Disabled when `heartbeatInterval = 0`. |
| `syncStart` | — | Background sync POST started. |
| `syncProgress` | `number` (0-100) | Sync upload progress. |
| `syncSuccess` | `{ sent: number }` | Sync completed, N locations uploaded. |
| `syncError` | `{ httpStatus, message }` | Sync failed (network or server). |

#### Driver insights (4.0+)

| Event | Payload | When |
|-------|---------|------|
| `tripStart` | `Location` | Sustained speed ≥ `minTripSpeed` for `minTripDuration` ms. |
| `tripEnd` | `{ location, distance, durationMs }` | Stationary detected during active trip. |
| `moving` | `Location` | Speed crossed `minMovingSpeed` upward. |
| `stopped` | `Location` | `stoppedDuration` ms below `minMovingSpeed`. |
| `speeding` | `{ location, speedKmh, limitKmh }` | Above `drivingEvents.speedLimit` (rearms on drop). |
| `providerChange` | `{ provider }` | Location provider changed (gps/network/fused). |
| `sos` | `{ location, ...payload }` | `BackgroundGeolocation.triggerSOS(payload)` invoked. |

#### Driving events — GPS-derived (4.1+) / sensor fusion (4.2+)

| Event | Payload | When |
|-------|---------|------|
| `hardBrake` | `{ location, value }` | Δspeed/Δt ≤ −`hardBrakeMps2` during active trip. `value` is m/s² (negative). |
| `rapidAcceleration` | `{ location, value }` | Δspeed/Δt ≥ `rapidAccelMps2`. `value` is m/s² (positive). |
| `sharpTurn` | `{ location, value }` | Bearing change rate ≥ `sharpTurnDegPerSec` and speed ≥ 5 m/s. `value` is deg/s. |
| `possibleCrash` | `{ location, value, source }` | GPS heuristic (`source: 'gps'`, `value`: km/h drop) **or** sensor impact (`source: 'sensor'`, `value`: g). Always confirm with user before notifying contacts. |
| `phoneUsageWhileDriving` | `Location` | Sustained device interaction during active trip with screen on. Requires `drivingEvents.sensorFusion: true`. |

Full event payloads and options: [Events](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/events.md). Full API (all options, all methods): [API](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md).

### New in 4.5.1

Stability + battery optimization release. No new features — only fixes that make the plugin safe for long-term production.

- **`wakeLockMode: 'none' | 'posting' | 'always'`** (default `'posting'`). Replaces the previous always-on `PARTIAL_WAKE_LOCK`. `'posting'` acquires a bounded 30 s lock around each fix (SQLite + HTTP POST); `'none'` never acquires; `'always'` is the legacy behaviour for fleet/emergency apps.
- **Watchdog moving-only** — `enableWatchdog: true` no longer restarts the provider when the device is intentionally stationary. Saves real battery during parking/idle.
- **Stationary detection configurable** — `stationaryTimeout`, `stationaryPollInterval`, `stationaryPollFast` (Android `DISTANCE_FILTER_PROVIDER`). Defaults: 300 000 / 180 000 / 60 000 ms.
- **Critical persistence fixes**: `maxLocations` recycle no longer mixes old `events_json`/`battery_level`/`is_charging` into new rows; iOS sync no longer marks rows `Deleted` before the upload finishes (was losing the whole queue on network drop). iOS rescues `SyncPending` rows stale > 15 min after a crash.
- **iOS race condition fixed**: `MAURLocationMapper._location` was a file-scope global → real-time post + background sync running concurrently produced mixed fixes. Now per-instance ivar.
- **`locationTransform`**: events / battery / charging now survive transforms that return a new instance (both platforms).

### New in 4.5.0

- **Driving events, battery and isCharging now survive the sync queue.** Android DB v22 + iOS DB v7 add `events_json` / `battery_level` / `is_charging` columns on `location`. When a real-time POST fails and the location enters SQLite, those fields travel with it to the backend on the next sync.
- **iOS config persistence parity** — `MAURConfig` now serialises post-3.2 keys (`httpMethod`, `queryParams`, `drivingEvents`, `includeBattery`, etc.) to a `config_json` blob, matching Android v4.4.1.
- **Runtime permission helpers** (Android): `requestBackgroundLocationPermission()`, `requestActivityRecognitionPermission()`, `requestNotificationPermission()`. iOS / older Android resolve `{ granted: true, notRequired: true }`.
- **iOS background sync cleanup**: rows that were uploaded successfully are now removed from SQLite (the previous code re-uploaded them every cycle).

### New in 4.4.0

Cada location enviada al backend incluye `battery` (0-100) y `isCharging` automáticamente. Sin dependencias externas (no necesitas `cordova-plugin-battery-status`).

```json
{
  "latitude": 40.4168, "longitude": -3.7038, "speed": 8.2,
  "battery": 78, "isCharging": false
}
```

Default ON. Opt-out: `configure({ includeBattery: false })`. Con templates custom usa los placeholders `'@battery'` y `'@isCharging'`.

### New in 4.3.0

Driving events ahora se **anexan al location** que se envía al backend, en un atributo `events`:

```json
{
  "latitude": 40.4168,
  "longitude": -3.7038,
  "time": 1730000000000,
  "speed": 8.2,
  "events": [
    { "type": "hardBrake", "value": -4.1, "time": 1730000000000 }
  ]
}
```

Tipos: `moving`, `stopped`, `tripStart`, `tripEnd`, `speeding`, `providerChange`, `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`, `phoneUsageWhileDriving`. La emisión por JS sigue funcionando idéntica (no rompe Opción A).

Desde v4.5.0, `events`, `battery` e `isCharging` se persisten en la cola de sync (Android DB v22, iOS DB v7) y sobreviven a POST fallido → SQLite/Core Data → background sync.

Si usas `postTemplate`/`bodyTemplate` custom, añade el placeholder `'@events'` para incluirlos:

```js
postTemplate: {
  lat: '@latitude', lon: '@longitude', t: '@time',
  events: '@events'
}
```

### New in 4.2.2

- Build hotfixes — required when consuming the plugin via Capacitor (Gradle 8.x):
  - `PostLocationTask.postLocation`: cast `LocationTemplate.locationToJson` (returns `Object`) to the right `HttpPostService.postJSON` overload.
  - `BackgroundGeolocationPlugin.buildDiagnostics`: wrap `facade.locationServicesEnabled()` in `try/catch (PluginException)`.

### New in 4.2.0

- **Real sensor fusion (Phase 8).** Optional accelerometer + gyroscope pipeline (`drivingEvents.sensorFusion: true`). On Android uses `Sensor.TYPE_LINEAR_ACCELERATION` + `Sensor.TYPE_GYROSCOPE`; on iOS uses `CMMotionManager.startDeviceMotionUpdatesToQueue` at 50 Hz.
- **Sensor-driven `possibleCrash`.** When the pipeline detects `|a| ≥ crashImpactG` (default 3 g) during an active trip, emits `possibleCrash` with `source: "sensor"`. GPS-derived `possibleCrash` keeps emitting with `source: "gps"`. Detects parking-lot impacts that GPS alone misses.
- **New event `phoneUsageWhileDriving`.** Sustained device interaction (gyro/accel jitter + screen on) during an active trip.
- New thresholds in `drivingEvents`: `crashImpactG`, `sensorCrashCooldownMs`, `phoneUsageWindowMs`, `phoneUsageCooldownMs`.
- Hot-reload: changes to `drivingEvents.sensorFusion` via `configure()` (re)start the pipeline without a full service restart.
- Battery: pipeline only samples while a trip is active. Off by default — opt in with `sensorFusion: true`.

```javascript
BackgroundGeolocation.configure({
  drivingEvents: {
    enabled: true,
    sensorFusion: true,        // v4.2 — opt in
    crashImpactG: 3.0,         // |a| in g for sensor-driven possibleCrash (default 3.0)
    phoneUsageWindowMs: 4000,
    phoneUsageCooldownMs: 60000
  }
});

BackgroundGeolocation.on('possibleCrash', (d) => {
  // d.source === 'gps' (GPS heuristic) or 'sensor' (accelerometer impact, higher confidence)
});
BackgroundGeolocation.on('phoneUsageWhileDriving', (location) => {
  // Sustained device interaction during an active trip with the screen on.
});
```

Full reference (all thresholds, when to enable, iOS screen-on caveat): [docs/api.md → Driver insights](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#driver-insights-since-400).

### New in 4.1.0

- **GPS-derived driving events.** Four new events: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`. All derived from GPS speed and bearing — **no accelerometer/gyroscope required**. Configure thresholds via the extended `drivingEvents` config (`hardBrakeMps2`, `rapidAccelMps2`, `sharpTurnDegPerSec`, `crashImpactKmh`, `crashWindowMs`). Each event has a 4 s cooldown.
- `possibleCrash` is heuristic. **Always confirm with the user** before notifying contacts; false positives are expected (sudden tunnel exit, GPS glitches).
- TypeScript: 4 new typed `on()` overloads with `{ location, value }` payloads.
- v4.2 adds real sensor fusion on top of these events.

### New in 4.0.0

- **Driver insights (Phase 6).** New GPS-only state machine emits `tripStart`, `tripEnd`, `moving`, `stopped`, `speeding`, `providerChange`, `sos` events end-to-end (Android + iOS). Configure thresholds via the new `drivingEvents` option.
- New method **`triggerSOS(payload?)`** — fires a single `sos` event with the latest known location plus your payload. The host app handles the actual SOS workflow (notify contacts, push, alarm UI).
- The Angular service exposes `triggerSOS()`.
- ✅ Driving events (`hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`) ya están disponibles en v4.1.0 — derivados de GPS (sin sensores adicionales). Real sensor fusion (acelerómetro/giroscopio) se planea para v4.2.

### New in 3.6.0

- **Battery / OEM helpers (Phase 5).** Five new methods to guide the user through the steps required for reliable background tracking on aggressive OEMs:
  - `isIgnoringBatteryOptimizations()` — Android Doze whitelist state.
  - `requestIgnoreBatteryOptimizations()` — opens the system dialog.
  - `openBatterySettings()` — opens the per-app battery screen.
  - `openAutoStartSettings()` — opens the OEM-specific auto-start screen on Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, OnePlus, Asus (Samsung falls back to app-info).
  - `getManufacturerHelp()` — returns OEM-specific guidance text the app can render as a help screen.
- The Angular service exposes all 5 methods.
- Combine with `getDiagnostics()` to build a "Tracking is being killed by your phone — fix it here" flow.

### New in 3.5.0

- **`getDiagnostics()` (Phase 4).** New extended diagnostics method that helps you debug "tracking is not running" in production. Returns permissions (`fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted`, `notificationPermissionGranted`, `activityRecognitionGranted`), battery optimisation state (`batteryOptimizationIgnored`), OEM manufacturer (`manufacturer`), last-fix timestamp, pending sync count, declared `foregroundServiceType` (Android), and `preciseLocationEnabled`, `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus` (iOS). Use it to surface actionable hints to users like "Allow all the time" or "Disable battery optimisation". See [getDiagnostics](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#getdiagnosticssuccess-fail).
- **`mockLocationPolicy: 'allow' | 'flag' | 'drop'`** — policy for mocked locations. Detection (`isFromMockProvider` Android, `simulated` iOS) was already present; this option controls what to do with those samples. Recommended for anti-fraud: `'flag'` (keep them but tagged) or `'drop'` (discard).
- **`heartbeatInterval`** option plus events `heartbeat`, `syncStart`, `syncProgress`, `syncSuccess`, `syncError` are all wired end-to-end with native emission on Android and iOS. Subscribe via `BackgroundGeolocation.on('heartbeat', cb)` etc.
- The Angular service exposes `getDiagnostics()` directly.

### New in 3.4.0

- **Location API modernization (Phase 3).** The Android Activity provider now uses `LocationRequest.Builder` + `Priority.PRIORITY_*` (replaces the deprecated `LocationRequest.create()` / `setPriority` / `setInterval` / `LocationRequest.PRIORITY_*` family from `play-services-location 21.0.0+`). The Distance Filter and Raw providers no longer use the deprecated `Criteria` API; provider selection is now an explicit GPS-first / Network-fallback. iOS migrates `[NSURLConnection sendSynchronousRequest:]` (deprecated since iOS 9) to `NSURLSession + dispatch_semaphore`, and adds the iOS 14+ `locationManagerDidChangeAuthorization:` callback alongside the legacy one.
- **`showsBackgroundLocationIndicator`** (iOS 11+). Set to `true` to display the blue status indicator while the app uses location in the background.
- **Bug fixes:** iOS `activitiesInterval` is now correctly read from the dictionary (the previous `isNull` check was inverted, dropping user input). Android `Content-Length` is computed in UTF-8 byte length instead of `String.length()`, so multi-byte characters (`ñ`, `é`, emoji) no longer mismatch the body size.
- **Default `GOOGLE_PLAY_SERVICES_VERSION`** raised to `21.0.1`. Apps that override it via `--variable` keep their override.

### New in 3.3.0

- **Auto-start Android (Phase 1).** The plugin's boot receiver now also handles `QUICKBOOT_POWERON` (HTC, MIUI / Xiaomi), `com.htc.intent.action.QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` (the service relaunches after a Play Store update). Added `ACCESS_BACKGROUND_LOCATION` to the manifest with runtime validation on Android 10+. `ForegroundServiceStartNotAllowedException` (Android 12+) is caught with clear logging — WorkManager is **not** used as a fallback for tracking. The `foregroundServiceType` is now `"location"` only (dropped `dataSync`); the runtime type is read dynamically from the merged manifest. `<uses-library org.apache.http.legacy>` and `useLibrary 'org.apache.http.legacy'` are removed (the plugin uses `HttpURLConnection`). `jcenter()` → `mavenCentral()`.
- **Backend-agnostic HTTP transport (Phase 2).** New `httpMethod`, `syncHttpMethod`, `httpMode`, `syncMode`, `queryParams`, `headers` (alias of `httpHeaders`), `bodyTemplate` (alias of `postTemplate`). URL templating with `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}` and any keys from `queryParams`. The plugin no longer hardcodes `POST` on Android (`HttpPostService`) or iOS (`MAURPostLocationTask`, `MAURBackgroundSync`). Compatibility is fully preserved — apps that only set `url` + `httpHeaders` + `postTemplate` keep working unchanged. See [HTTP transport](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md#http-transport-since-330) for backend examples (REST, GET single, form-urlencoded, Firebase, n8n).
- **Engines:** `cordova-android >= 12.0.0` is now the minimum.

### New in 3.2.0

- **Session API for route/recording** — Store all locations for the *current route* in the plugin, independent of sync. When the user reopens the app without internet, you can restore the full track from the plugin (no need for `localStorage` for points).
  - **`startSession()`** — Call when the user starts a route. Clears the session table; from then on every location is also saved in the session table (and not removed when synced).
  - **`getSessionLocations()`** — Returns all session locations (same format as `Location`: latitude, longitude, time, speed, altitude, bearing, accuracy). Use to rebuild the track after reopening without internet.
  - **`clearSession()`** — Call when the route is finished and sync succeeded. Clears the session table.
  - **`getSessionLocationsCount()`** — Returns how many points are in the session (e.g. to show "X points" in the UI).
- Typical flow: **Start route** → `startSession()` then `start()`. **Reopen without internet** → `getSessionLocations()` and redraw the route. **Finish route and sync OK** → `clearSession()`.

### New in 3.1.1

- **Browser / `ng serve` builds** — The plugin can now be bundled by webpack without "Can't resolve 'cordova/exec'" or "Can't resolve 'cordova/channel'". The package ships stub modules and a `browser` field so `ng serve` and browser builds succeed; on device/emulator the stubs delegate to the real Cordova API. See [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md#build-ng-serve--browser).

### New in 3.1.0

- **`getPendingSyncCount()`** — Number of locations pending to be synced. Use with `forceSync()` for “X pending” UI.
- **`forceSync()`** — Sends all pending locations to `syncUrl` immediately. Promise now resolves correctly on Android.
- **`clearSync()`** — Clears the pending sync queue (discard without sending).
- **Config `sync`** (default `true`) — Set `sync: false` to disable automatic sync and `forceSync()`; locations are still stored.
- **Config `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, `notificationSyncFailedText`** (Android) — Customize or localize the notification shown while syncing.
- **Sync with `Content-Type: application/x-www-form-urlencoded`** — Batch sync now sends **one POST per location** (same flat format as real-time), so the same server endpoint works for both.

---

More (stationary, activity, headless task, Angular) is in the [documentation](https://josuelmm.github.io/cordova-background-geolocation/). For **Angular** (service, methods, events), see [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md).

---

## Angular (Ionic Angular)

The package includes an **Angular integration**: an injectable service and optional NgModule. You can use the plugin **without it** (global `BackgroundGeolocation`) or **with it** (inject the service). Both use the same native plugin.

### Install (same as above)

```bash
npm install @josuelmm/cordova-background-geolocation
npx cap sync
```

### How to import and use

**One import** — Service and the most used types are exported from the `/angular` entry, so you do **not** need a second import from the main package:

```ts
import {
  BackgroundGeolocationService,
  BackgroundGeolocationConfig,
  BackgroundGeolocationEvents,
  BackgroundGeolocationResponse
} from '@josuelmm/cordova-background-geolocation/angular';

@Injectable({ ... })
export class MyService {
  constructor(private bg: BackgroundGeolocationService) {}

  startTracking() {
    this.bg.configure({ distanceFilter: 50, url: 'https://...' } as BackgroundGeolocationConfig)
      .then(() => this.bg.start());
  }

  onLocation() {
    return this.bg.on('location', (loc: BackgroundGeolocationResponse) => console.log(loc));
    // subscription.unsubscribe() when done
  }
}
```

**You must import `BackgroundGeolocationModule`** in your `AppModule` (or feature module) so the service is provided and AOT builds work. Then inject `BackgroundGeolocationService` as in the example above. See [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md) for the full snippet.

**`ng serve` / browser:** From 3.1.1 the plugin includes browser stubs so `ng serve` and web builds complete without "Can't resolve 'cordova/exec'" — see [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md#build-ng-serve--browser).

**Lazy-loaded pages:** If you see **NG0202** or *"dependency at index N is invalid"* when opening a page that injects this service, use an app-defined token and inject by that token (the plugin token can be undefined in the lazy chunk). See [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md) (Lazy-loaded modules).

**Migrating from @awesome-cordova-plugins/background-geolocation:** there you inject a class named `BackgroundGeolocation`. In this package, `BackgroundGeolocation` is the **global plugin object**, not an injectable class. Use `BackgroundGeolocationService` instead (same API). See [docs/angular.md](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md) for details.

### Summary

| Use case              | What to do |
|-----------------------|------------|
| **Without Angular**   | Use global `BackgroundGeolocation` after `deviceready`. Types: main package or Awesome-style aliases (see [TypeScript imports](#typescript-imports) above). |
| **With Angular**      | Import from `@josuelmm/cordova-background-geolocation/angular`: add `BackgroundGeolocationModule` to your module `imports`, then inject `BackgroundGeolocationService`. Do **not** inject the global `BackgroundGeolocation`. |

No extra wrapper (e.g. Awesome Cordova Plugins) is required.

---

## Compatibility

| Plugin version | Cordova CLI | Cordova Android | Cordova iOS |
|----------------|-------------|-----------------|-------------|
| 1.x            | ≥ 8.0.0     | ≥ 8.0.0         | ≥ 6.0.0     |
| 2.x            | ≥ 10.0.0    | ≥ 10.0.0        | ≥ 6.0.0     |
| 3.0.x – 3.2.x  | ≥ 10.0.0    | ≥ 10.0.0        | ≥ 6.0.0     |
| 3.3.x – 4.1.x  | ≥ 10.0.0    | ≥ 12.0.0        | ≥ 6.2.0     |
| 4.2.x – 4.5.x  | ≥ 10.0.0    | ≥ 12.0.0        | ≥ 6.2.0     |

> 3.3.0+: `cordova-android >= 12` is required for `targetSdk 34+` and the modernised foreground-service handling. The new iOS APIs (`showsBackgroundLocationIndicator` iOS 11+, `locationManagerDidChangeAuthorization:` iOS 14+) are gated by runtime `@available` checks, so older iOS versions still link and run.
>
> 4.5.0+: SQLite schemas bumped (Android v22, iOS v7) to persist `events`, `battery`, `isCharging` across the sync queue. Upgrades from any earlier version auto-migrate.
>
> 4.5.2+ — **Google Play Services matrix (Android)**:
> - `RAW_PROVIDER` — no requiere Play Services (usa OS LocationManager). Funciona en Huawei/HMS, AOSP, China ROMs.
> - `DISTANCE_FILTER_PROVIDER` — **híbrido**: usa `FusedLocationProviderClient` cuando hay Play Services, fallback automático a `LocationManager` cuando no. Funciona en todos los Android.
> - `ACTIVITY_PROVIDER` — **requiere Play Services** (depende de `ActivityRecognitionClient`, sin equivalente OS). En dispositivos sin Play Services usar `DISTANCE_FILTER_PROVIDER` o `RAW_PROVIDER`.

---

## Documentation and changelog

This README is the main entry point. For more detail, edge cases and examples use the docs below (and the [online documentation](https://josuelmm.github.io/cordova-background-geolocation/)).

| Doc | What you'll find |
|-----|------------------|
| **[API reference](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/api.md)** | Every `configure` option, every method (`configure`, `start`, `stop`, `getPendingSyncCount`, `forceSync`, `clearSync`, `startSession`, `getSessionLocations`, `clearSession`, `getSessionLocationsCount`, `getConfig`, `getLocations`, etc.), TypeScript types. Also covers the **HTTP transport (3.3.0)** and **auto-start on Android boot (3.3.0)**. |
| **[HTTP transport (3.3.0)](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/http-transport.md)** | `httpMethod`, `httpMode`, URL templating, `bodyTemplate`, `queryParams`, examples for REST, GET single, form-urlencoded, Firebase, n8n, Traccar. |
| **[Auto-start on boot (3.3.0)](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/auto-start.md)** | Android receiver actions, `ACCESS_BACKGROUND_LOCATION` runtime flow, OEM caveats, iOS limitation. |
| **[Traccar integration example](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/traccar.md)** | Optional backend: how to deliver positions to Traccar (or Firebase, n8n, custom REST) via the HTTP transport. The plugin is backend-agnostic. |
| **[Location modernization (3.4.0)](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/location-modernization.md)** | What was modernised in Phase 3 (LocationRequest.Builder, NSURLSession, iOS 14+ auth callback) and what is still legacy. |
| **[Roadmap](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/ROADMAP.md)** | Phases 1–6, what's released, what's next. |
| **[HTTP posting](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/http_posting.md)** | `url` vs `syncUrl`, Content-Type (JSON = one POST with array; form-urlencoded = one POST per location), headers, retries, `postTemplate`, sync behaviour. |
| **[Events](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/events.md)** | All events (`location`, `error`, `stationary`, `activity`, `http_authorization`, etc.) and payloads. |
| **[Angular / Ionic](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/angular.md)** | Injectable service, module, lazy-loaded modules and token "must be defined", `ng serve` / browser build. |
| **[Example](https://github.com/josuelmm/cordova-background-geolocation/blob/main/docs/example.md)** | Full example with events and sync. |
| **[CHANGELOG](CHANGELOG.md)** | Version history. |

This project is based on [@mauron85/cordova-plugin-background-geolocation](https://github.com/mauron85/cordova-plugin-background-geolocation) and the original by [christocracy](https://github.com/christocracy). Maintained at [josuelmm/cordova-background-geolocation](https://github.com/josuelmm/cordova-background-geolocation). Issues and PRs welcome.

---

## Licence

[Apache License](http://www.apache.org/licenses/LICENSE-2.0)

Copyright (c) 2013 Christopher Scott, Transistor Software

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
