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
| `httpHeaders` | Headers for every POST (e.g. `{ 'Content-Type': 'application/json', 'Authorization': 'Bearer TOKEN' }`) |
| `postTemplate` | Object or array of properties to send (use `@latitude`, `@longitude`, etc.). See [Custom post template](docs/api.md#custom-post-template). |
| `maxLocations` | Max locations kept in DB (default 10000). Should be &gt; `syncThreshold`. |

**Android-only:** `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, `notificationSyncFailedText` — texts shown in the notification while syncing (defaults in English; set for localization). `startForeground`, `notificationsEnabled`, `startOnBoot`, `stopOnTerminate`, `enableWatchdog`.

**iOS-only:** `activityType`, `pauseLocationUpdates`, `saveBatteryOnBackground`.

Example:

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

So: with **JSON** you get one request per batch (e.g. 100 locations in one body). With **form-urlencoded** you get one request per location (one record per POST). For headers, retries, `postTemplate` and full behaviour see [HTTP posting](docs/http_posting.md) and [API](docs/api.md).

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

More on sync (headers, retries, postTemplate): [HTTP posting](docs/http_posting.md). Full options and methods: [API](docs/api.md).

### 6. Other methods (summary)

| Method | Description |
|--------|-------------|
| `getConfig(success, fail)` | Get current configuration (merged options). |
| `getLocations(success, fail)` | Get all stored locations. |
| `getValidLocations(success, fail)` | Get locations not yet posted (valid only). |
| `deleteLocation(id, success, fail)` | Delete one location by id. |
| `deleteAllLocations(success, fail)` | Delete all stored locations. |
| `getCurrentLocation(success, fail, options)` | One-shot location (e.g. timeout, maximumAge). |
| `getPluginVersion(success, fail)` | Plugin version string (e.g. "3.1.1"). |
| `checkStatus(success, fail)` | Service status (isRunning, authorization, etc.). |
| `showAppSettings()` / `openSettings()` | Open app settings. |
| `showLocationSettings()` | Open system location settings. |
| `getLogEntries(limit, fromId, minLevel, success, fail)` | Debug log entries. |

All methods return a **Promise** if you omit the `success` / `fail` callbacks.

### 7. Events (summary)

Subscribe with `BackgroundGeolocation.on(eventName, callback)`. Unsubscribe with the returned object’s `remove()` or by calling `removeAllListeners(eventName)`.

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

Full event payloads and options: [Events](docs/events.md). Full API (all options, all methods): [API](docs/api.md).

### New in 3.1.1

- **Browser / `ng serve` builds** — The plugin can now be bundled by webpack without "Can't resolve 'cordova/exec'" or "Can't resolve 'cordova/channel'". The package ships stub modules and a `browser` field so `ng serve` and browser builds succeed; on device/emulator the stubs delegate to the real Cordova API. See [docs/angular.md](docs/angular.md#build-ng-serve--browser).

### New in 3.1.0

- **`getPendingSyncCount()`** — Number of locations pending to be synced. Use with `forceSync()` for “X pending” UI.
- **`forceSync()`** — Sends all pending locations to `syncUrl` immediately. Promise now resolves correctly on Android.
- **`clearSync()`** — Clears the pending sync queue (discard without sending).
- **Config `sync`** (default `true`) — Set `sync: false` to disable automatic sync and `forceSync()`; locations are still stored.
- **Config `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, `notificationSyncFailedText`** (Android) — Customize or localize the notification shown while syncing.
- **Sync with `Content-Type: application/x-www-form-urlencoded`** — Batch sync now sends **one POST per location** (same flat format as real-time), so the same server endpoint works for both.

---

More (stationary, activity, headless task, Angular) is in the [documentation](https://josuelmm.github.io/cordova-background-geolocation/). For **Angular** (service, methods, events), see [docs/angular.md](docs/angular.md).

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

**You must import `BackgroundGeolocationModule`** in your `AppModule` (or feature module) so the service is provided and AOT builds work. Then inject `BackgroundGeolocationService` as in the example above. See [docs/angular.md](docs/angular.md) for the full snippet.

**`ng serve` / browser:** From 3.1.1 the plugin includes browser stubs so `ng serve` and web builds complete without "Can't resolve 'cordova/exec'" — see [docs/angular.md](docs/angular.md#build-ng-serve--browser).

**Lazy-loaded pages:** If you see **NG0202** or *"dependency at index N is invalid"* when opening a page that injects this service, use an app-defined token and inject by that token (the plugin token can be undefined in the lazy chunk). See [docs/angular.md](docs/angular.md) (Lazy-loaded modules).

**Migrating from @awesome-cordova-plugins/background-geolocation:** there you inject a class named `BackgroundGeolocation`. In this package, `BackgroundGeolocation` is the **global plugin object**, not an injectable class. Use `BackgroundGeolocationService` instead (same API). See [docs/angular.md](docs/angular.md) for details.

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

---

## Documentation and changelog

This README is the main entry point. For more detail, edge cases and examples use the docs below (and the [online documentation](https://josuelmm.github.io/cordova-background-geolocation/)).

| Doc | What you’ll find |
|-----|------------------|
| **[API reference](docs/api.md)** | Every `configure` option, every method (`configure`, `start`, `stop`, `getPendingSyncCount`, `forceSync`, `clearSync`, `getConfig`, `getLocations`, etc.), TypeScript types. |
| **[HTTP posting](docs/http_posting.md)** | `url` vs `syncUrl`, Content-Type (JSON = one POST with array; form-urlencoded = one POST per location), headers, retries, `postTemplate`, sync behaviour. |
| **[Events](docs/events.md)** | All events (`location`, `error`, `stationary`, `activity`, `http_authorization`, etc.) and payloads. |
| **[Angular / Ionic](docs/angular.md)** | Injectable service, module, lazy-loaded modules and token “must be defined”, `ng serve` / browser build. |
| **[Example](docs/example.md)** | Full example with events and sync. |
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
