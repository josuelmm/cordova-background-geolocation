# Changelog

## [4.2.3](https://github.com/josuelmm/cordova-background-geolocation/tree/4.2.3) (2026-05-09)

### Fixed
- `PostLocationTask`: log de debug pasaba 3 args para 4 placeholders `{}` (faltaba `mode`). Cosmético, sin impacto funcional.

### Audited (no changes required)
- HTTP transport (real-time + sync queue) intacto: URL templating, `httpMethod`/`httpMode`/`syncMode`, headers, `Content-Length` UTF-8.
- Hot-reload `configure()` de v4.2 no toca `clearQueue` — no se pierden ubicaciones pendientes.
- Sensor fusion: imports, protocolo iOS, MSG codes, parcels simétricos, listeners enlazados — sin regresiones.

## [4.2.2](https://github.com/josuelmm/cordova-background-geolocation/tree/4.2.2) (2026-05-09)

### Fixed
- `PostLocationTask.postLocation`: cast del retorno de `LocationTemplate.locationToJson` (que es `Object` — `JSONObject` para `HashMapLocationTemplate`, `JSONArray` para `ArrayListLocationTemplate`) a la sobrecarga correcta de `HttpPostService.postJSON`. Bug latente que rompía la compilación con consumidores Capacitor (Gradle 8.x).
- `BackgroundGeolocationPlugin.buildDiagnostics`: envolver `facade.locationServicesEnabled()` en `try/catch (PluginException)`. El método declara `throws JSONException` pero no `PluginException`, lo que rompía el build cuando se invoca desde `getDiagnostics`.

## [4.2.0](https://github.com/josuelmm/cordova-background-geolocation/tree/4.2.0) (2026-05-08)

### Phase 8 — Real sensor fusion (accelerometer + gyroscope)

#### Added
- Android `SensorFusionDetector` (`Sensor.TYPE_LINEAR_ACCELERATION` + `Sensor.TYPE_GYROSCOPE`, `SENSOR_DELAY_GAME`).
- iOS `MAURSensorFusionDetector` (`CMMotionManager.startDeviceMotionUpdatesToQueue`, 50 Hz).
- `drivingEvents.sensorFusion` flag (off by default) plus `crashImpactG`, `sensorCrashCooldownMs`, `phoneUsageWindowMs`, `phoneUsageCooldownMs` thresholds.
- `possibleCrash` event payload now carries `source: "gps" | "sensor"` so consumers can distinguish the GPS heuristic from the accelerometer impact.
- New event `phoneUsageWhileDriving`: detects sustained device interaction (gyro/accel jitter) while a trip is active and the screen is on.
- Hot-reload: `configure()` reevaluates and (re)starts sensor pipeline on both platforms.

#### Notes
- Sensor pipeline only samples while `tripActive` is true → modest battery cost.
- Android requires `TYPE_LINEAR_ACCELERATION` (gravity-removed); fallback path skips silently if unavailable.
- iOS uses foreground-active heuristic for screen-on (background = passenger usage assumed).

## [4.1.0](https://github.com/josuelmm/cordova-background-geolocation/tree/4.1.0) (2026-05-09)

### Phase 6.1 — GPS-derived sensor-like driving events

#### Added

- **`hardBrake`** — emitted when GPS-derived deceleration `Δspeed/Δt` ≤ `-drivingEvents.hardBrakeMps2` during an active trip. Payload `{ location, value }` where `value` is the negative m/s².
- **`rapidAcceleration`** — emitted when GPS-derived acceleration ≥ `drivingEvents.rapidAccelMps2` during an active trip.
- **`sharpTurn`** — emitted when |Δbearing|/Δt ≥ `drivingEvents.sharpTurnDegPerSec` and speed ≥ 5 m/s (filters GPS jitter at low speeds).
- **`possibleCrash`** — heuristic. Fires when speed drops by ≥ `drivingEvents.crashImpactKmh` within `crashWindowMs` and ends near 0 during an active trip. Payload `{ location, value }` where `value` is the velocity drop in km/h. **Apps must always confirm with the user before notifying contacts** — false positives are expected (sudden tunnel exit, GPS glitches).
- All 4 events have a 4-second cooldown to avoid refiring during sustained events.

#### Added — Config

- Extended `drivingEvents` with: `hardBrakeMps2` (default 3.5), `rapidAccelMps2` (default 3.5), `sharpTurnDegPerSec` (default 30), `crashImpactKmh` (default 25), `crashWindowMs` (default 2000). Set any to `0` to disable that specific event.

#### Implementation

- Android `DrivingEventsDetector`: state machine extended with `prevSpeed`/`prevBearing` deltas, per-event cooldown, and the 4 new listener callbacks.
- Android `Config.DrivingEventsOptions`: 5 new fields parceled with the existing primitives pattern.
- Android `LocationServiceImpl`: 4 new MSG codes (120-123) routed through `BackgroundGeolocationFacade` to the new default-no-op methods on `PluginDelegate`.
- iOS `MAURBackgroundGeolocationFacade.drivingDetectorFeed:`: same state machine in Objective-C, posting 4 new `NSNotification` names.
- iOS `CDVBackgroundGeolocation`: 4 new observers + `sendDrivingEventN:note:` helper.
- TypeScript: 4 new event names in `Event` union and `BackgroundGeolocationEvents` enum; 4 new typed `on()` overloads.
- Plugin version bumped to `4.1.0` in all 4 sync points.

#### Sensor-fusion (deferred)

- Real accelerometer + gyroscope sampling (`Sensor.TYPE_LINEAR_ACCELERATION`, `CMMotionManager.deviceMotion`) deferred. The current heuristics work surprisingly well for `hardBrake` and `rapidAcceleration` because GPS already reports speed; `sharpTurn` works above 5 m/s; `possibleCrash` is intentionally conservative. v4.2 will add a separate `SensorFusionDetector` for finer-grained event detection during slow driving and parking-lot crashes.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/4.0.0...4.1.0)

---

## [4.0.0](https://github.com/josuelmm/cordova-background-geolocation/tree/4.0.0) (2026-05-08)

### Phase 6 — Driver insights (Roadmap v4.0)

#### Added — GPS-based driver-insight events (no extra sensors required)

- **`tripStart`** — emitted when the user moves continuously above `drivingEvents.minTripSpeed` for `minTripDuration`. Payload: latest location.
- **`tripEnd`** — emitted when the user becomes "stopped" while a trip was active. Payload: `{ location, distance, durationMs }`.
- **`moving`** — speed crossed above `minMovingSpeed`.
- **`stopped`** — speed below the threshold for `stoppedDuration`.
- **`speeding`** — speed crossed above `drivingEvents.speedLimit` (km/h). Rearms when speed drops below.
- **`providerChange`** — native location provider changed (GPS / network / fused). Payload: `{ provider }`.
- **`sos`** — emitted by `BackgroundGeolocation.triggerSOS(payload?)`. Payload: user dictionary plus `location` (the latest known fix; absent if no fix yet).

#### Added — Methods

- **`triggerSOS(payload?)`** — JS API + Angular service + Android `BackgroundGeolocationFacade.triggerSOS(JSONObject)` + iOS `MAURBackgroundGeolocationFacade triggerSOS:`.

#### Added — Config

- **`drivingEvents`** option:
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

#### Implementation

- New shared GPS state machine: Android `com.marianhello.bgloc.driving.DrivingEventsDetector` (pure-Java, hosted by `LocationServiceImpl.onLocation` and broadcasting via the `MSG_ON_*` pipeline); iOS inline detector inside `MAURBackgroundGeolocationFacade` posting `NSNotification`s.
- New Android MSG codes: `MSG_ON_TRIP_START` (113), `MSG_ON_TRIP_END` (114), `MSG_ON_MOVING` (115), `MSG_ON_STOPPED` (116), `MSG_ON_SPEEDING` (117), `MSG_ON_PROVIDER_CHANGE` (118), `MSG_ON_SOS` (119). Routed by `BackgroundGeolocationFacade` to the new default-no-op methods on `PluginDelegate`.
- iOS notification names: `MAURTripStartNotification`, `MAURTripEndNotification`, `MAURMovingNotification`, `MAURStoppedNotification`, `MAURSpeedingNotification`, `MAURProviderChangeNotification`, `MAURSOSNotification`. Observed by `CDVBackgroundGeolocation.pluginInitialize`.
- TypeScript: 7 new event names in the `Event` union and `BackgroundGeolocationEvents` enum; 7 new typed `on()` overloads with full payload typings; new `triggerSOS()` method declaration.
- Plugin version bumped to `4.0.0` in `package.json`, `plugin.xml`, Android `PLUGIN_VERSION` and iOS `getPluginVersion`.

#### Sensor-fusion events deferred to v4.1

- `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash` require accelerometer + gyroscope sampling and a separate detector class. They are intentionally not part of v4.0.0 to keep this release focused on GPS-only signals that work reliably on every device. The API surface for `drivingEvents` is intentionally extensible so v4.1 can add `hardBrakeMps2`, `sharpTurnDegPerSec`, `crashImpactKmh` thresholds without breaking changes.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.6.0...4.0.0)

---

## [3.6.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.6.0) (2026-05-08)

### Phase 5 — Battery / OEM helpers (Roadmap v3.6)

#### Added

- **`isIgnoringBatteryOptimizations()`** — Android: returns `true` if the app is on the system battery-optimisation whitelist (Settings → Battery → "Don't optimise"). iOS: resolves `true` (concept does not apply).
- **`requestIgnoreBatteryOptimizations()`** — Android: opens the system dialog to add the app to the whitelist; the user must accept. Falls back to the battery settings screen if the system dialog is unavailable. iOS: no-op resolves `true`.
- **`openBatterySettings()`** — Android: opens the per-app battery-optimisation settings screen (with fallback to app-info). iOS: opens the app's Settings entry via `UIApplicationOpenSettingsURLString`.
- **`openAutoStartSettings()`** — Android: opens the OEM-specific "auto-start" / "background activity" screen on Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, OnePlus, Asus and Samsung (Samsung falls back to app-info because there is no stable component). Resolves `{ opened, manufacturer, screen }`. iOS: opens the app's Settings entry and reports `manufacturer: 'apple'`.
- **`getManufacturerHelp()`** — returns OEM-specific guidance steps (`{ manufacturer, steps: string[] }`) so the app can render an actionable help screen when the FGS is being killed by the OEM. Coverage: Xiaomi (MIUI/Redmi/Poco), Huawei/Honor (EMUI), Oppo (ColorOS), Vivo (FunTouch), Samsung (One UI), OnePlus, Asus, generic Android, iOS.

#### Implementation

- New helper class `com.marianhello.bgloc.oem.BatteryOemHelper` (Android) wraps the `PowerManager.isIgnoringBatteryOptimizations(...)` call, the `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog, and a per-OEM `ComponentName` table for the auto-start screens. Components verified against AOSP / OEM forks.
- iOS: 5 best-effort selectors in `CDVBackgroundGeolocation.m` that use `UIApplicationOpenSettingsURLString` and report `manufacturer: 'apple'` so the JS layer can branch with `getManufacturerHelp()`.
- TypeScript surface: 5 new methods on the `BackgroundGeolocationPlugin` interface with strong types for the result objects (`isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`, `openAutoStartSettings`, `getManufacturerHelp`).
- Angular service re-exports all 5 methods.
- `plugin.xml`: registers `android/common/.../oem/BatteryOemHelper.java` as a `<source-file>`.
- Plugin version bumped to `3.6.0` in `package.json`, `plugin.xml`, Android `PLUGIN_VERSION`, and iOS `getPluginVersion`.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.5.0...3.6.0)

---

## [3.5.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.5.0) (2026-05-08)

### Phase 4 — Diagnostics (Roadmap v3.5)

#### Added

- **`getDiagnostics()`** — extended diagnostics method that explains *why* tracking may not be running in production. Returns a typed `Diagnostics` object covering, where applicable to the platform: `isRunning`, `locationServicesEnabled`, `startOnBoot`, `pendingSyncCount`, `lastLocationAt`, plus permissions (`fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted`, `notificationPermissionGranted`, `activityRecognitionGranted`), `batteryOptimizationIgnored`, `manufacturer`, `foregroundServiceType` (Android), and `preciseLocationEnabled`, `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus`, `authorizationStatusText` (iOS).
- **`Diagnostics` TypeScript interface** in `www/BackgroundGeolocation.d.ts`.
- **Angular service:** `BackgroundGeolocationService.getDiagnostics(success?, fail?)`.

#### Implementation

- Android `BackgroundGeolocationPlugin`: new action `getDiagnostics` + helpers `hasPermission`, `isIgnoringBatteryOptimizations`, `readForegroundServiceTypeFromManifest`. Plugin version bumped to `3.5.0`.
- iOS `CDVBackgroundGeolocation`: new selector `getDiagnostics:` reads `CLLocationManager.authorizationStatus`, `accuracyAuthorization` (iOS 14+), `UIBackgroundRefreshStatus`, `NSProcessInfo.lowPowerModeEnabled` (iOS 9+), `CMMotionActivityManager.authorizationStatus` (iOS 11+), and `MAURSQLiteLocationDAO.getLocationsForSyncCount`.
- New imports added to iOS plugin: `CoreMotion/CoreMotion.h`, `UIKit/UIKit.h`, `MAURSQLiteLocationDAO.h`.

### Phase 4 — extra (still in 3.5.0)

- **`mockLocationPolicy: 'allow' | 'flag' | 'drop'`** — applied in Android `PostLocationTask` and iOS `MAURPostLocationTask`. With `'drop'`, a mocked location is discarded before persisting/posting; with `'flag'` it is delivered but the existing `isFromMockProvider` (Android) / `simulated` (iOS) field marks it; `'allow'` (default) preserves current behaviour.
- **`heartbeatInterval`** config option added to `Config` / `MAURConfig` and `.d.ts`. Native timer-based emission of `heartbeat` is **still deferred** to 3.5.1+ (the surface — config + event name — is registered).
- **Sync events emission (real, native).**
  - Android: `MSG_ON_SYNC_START`, `MSG_ON_SYNC_SUCCESS`, `MSG_ON_SYNC_ERROR` codes added to `LocationServiceImpl`. `PluginDelegate` extended with default-no-op `onSyncStart()`, `onSyncSuccess(int)`, `onSyncError(int, String)`. `BackgroundGeolocationFacade.serviceBroadcastReceiver` routes them to the delegate. `SyncAdapter.uploadLocations` broadcasts `MSG_ON_SYNC_START` before the upload, then `MSG_ON_SYNC_SUCCESS` (with `sent` count) on 2xx or `MSG_ON_SYNC_ERROR` (with `httpStatus` + message) on non-2xx / IOException. `BackgroundGeolocationPlugin` overrides the new delegate methods and emits the events to JS.
  - iOS: new notification names `MAURBackgroundSyncDidStartNotification`, `MAURBackgroundSyncDidSucceedNotification`, `MAURBackgroundSyncDidFailNotification` posted from `MAURBackgroundSync`. `CDVBackgroundGeolocation` registers observers in `pluginInitialize` and forwards them as `syncStart` / `syncSuccess` / `syncError` JS events. Payload includes `sent` (success) and `httpStatus` + `message` (error). `MAURBackgroundSyncDelegate` also exposes the new optional methods for in-process listeners.
  - TypeScript: `Event` union extended with `'heartbeat' | 'syncStart' | 'syncProgress' | 'syncSuccess' | 'syncError'` so `removeAllListeners('syncSuccess')` etc. type-check.

### Bug fixes

- **iOS `getPluginVersion`:** returned the hardcoded string `"3.2.0"`; now returns `"3.5.0"` matching `plugin.xml` and the Android `PLUGIN_VERSION`. Production traceability restored.

### Phase 4 — additional fixes (still in 3.5.0)

- **`syncProgress` now emits natively.** Android: `MSG_ON_SYNC_PROGRESS = 111` added to `LocationServiceImpl`; `SyncAdapter.onProgress(int)` broadcasts the percentage; `BackgroundGeolocationFacade` routes it to the new `PluginDelegate.onSyncProgress(int)` (default no-op); `BackgroundGeolocationPlugin` emits the JS event with the integer payload. iOS: `NSURLSessionTaskDelegate didSendBodyData:totalBytesSent:totalBytesExpectedToSend:` computes 0..100 and posts `MAURBackgroundSyncDidProgressNotification`; `CDVBackgroundGeolocation` forwards it as `syncProgress` JS event with numeric payload.
- **TypeScript `on()` overloads** for the new events: `'heartbeat'`, `'syncStart'`, `'syncProgress'`, `'syncSuccess'`, `'syncError'`. Apps can now write `BackgroundGeolocation.on('syncSuccess', ({ sent }) => ...)` with full type safety. The catch-all `BackgroundGeolocationEvents` enum overload was widened to cover the new payload shapes.
- **Bug: `MAURBackgroundSync` `tasks` was never allocated** — `addObject:`, `removeObject:`, `cancel` and `status` silently no-op'd on a `nil` array (Objective-C messages-to-nil semantics), so background uploads couldn't be tracked or cancelled. `tasks` is now `[[NSMutableArray alloc] init]` in `init`.

### Phase 4 — heartbeat native emission (still in 3.5.0)

- **Heartbeat now emits natively.**
  - Android: `LocationServiceImpl` adds `MSG_ON_HEARTBEAT = 112`, a `mLastReceivedLocation` field updated on every `onLocation()`, and a `ScheduledExecutorService`-based scheduler started after `MSG_ON_SERVICE_STARTED` and cancelled on stop. Each tick broadcasts the latest `BackgroundLocation` as the `payload` parcelable. `BackgroundGeolocationFacade` routes `MSG_ON_HEARTBEAT` to the new `PluginDelegate.onHeartbeat(BackgroundLocation)` (default no-op). `BackgroundGeolocationPlugin` forwards it to JS as `sendEvent("heartbeat", location.toJSONObjectWithId())`.
  - iOS: `MAURBackgroundGeolocationFacade` declares `MAURHeartbeatNotification`, caches `lastReceivedLocation` on every `onLocationChanged:`, schedules an `NSTimer` (main run loop) when `start:` succeeds and invalidates it in `stop:`. Each tick posts `MAURHeartbeatNotification` with `userInfo[@"location"]`. `CDVBackgroundGeolocation` observes and emits a `heartbeat` JS event with the location dictionary (or empty when no fix yet).

### Status

- All Phase 4 deliverables for 3.5.0 are now native and end-to-end:
  - `getDiagnostics()` ✅
  - `mockLocationPolicy` ✅
  - `syncStart` / `syncProgress` / `syncSuccess` / `syncError` ✅
  - `heartbeat` ✅

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.4.0...3.5.0)

---

## [3.4.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.4.0) (2026-05-08)

### Phase 3 — Location API modernization (Roadmap v3.4)

#### Android

- **`ActivityRecognitionLocationProvider.java`:** migrated to `new LocationRequest.Builder(priority, intervalMillis)` + `Priority.PRIORITY_*` (from `com.google.android.gms.location.Priority`). Replaces `LocationRequest.create()`, `setPriority()`, `setInterval()`, `setFastestInterval()` and `LocationRequest.PRIORITY_*` — all deprecated since `play-services-location 21.0.0`. Adds `setMinUpdateIntervalMillis` (replaces `fastestInterval`) and `setWaitForAccurateLocation(false)`.
- **`RawLocationProvider.java`:** removed `Criteria` API and `LocationManager.getBestProvider(criteria, true)`. Provider selection is now explicit GPS-first / Network-fallback. Drops the historical "boost stationary" Criteria block; behaviour matches GPS-enabled path on modern Android.
- **`plugin.xml`:** bumped `GOOGLE_PLAY_SERVICES_VERSION` default from `17+` to `21.0.1`. Apps overriding the variable continue to work.

#### iOS

- **`MAURPostLocationTask.m`:** migrated `[NSURLConnection sendSynchronousRequest:returningResponse:error:]` (deprecated since iOS 9) to `NSURLSession dataTaskWithRequest:completionHandler:` synchronised via `dispatch_semaphore`. Caller still receives `error` and `statusCode` with the same semantics; the call is safe because it runs on a background queue.
- **`MAURDistanceFilterLocationProvider.m`:** added `locationManagerDidChangeAuthorization:` (iOS 14+) which is the canonical replacement for the deprecated `locationManager:didChangeAuthorizationStatus:`. Both callbacks share a private helper `handleAuthorizationStatusChange:`. The legacy callback is short-circuited on iOS 14+ to avoid double-notifying delegates.
- **`MAURDistanceFilterLocationProvider.m` + `MAURConfig`:** new `showsBackgroundLocationIndicator` config option (iOS 11+). When `true`, iOS shows the blue status indicator while the app uses location in the background.

#### TypeScript

- `www/BackgroundGeolocation.d.ts`: added `showsBackgroundLocationIndicator?: boolean` (iOS) with documentation.

#### Versions

- `cordovaDependencies."3.4.0"`: requires `cordova-ios >= 6.2.0`. The new APIs use runtime availability checks (`@available(iOS 11.0, *)` for `showsBackgroundLocationIndicator`, `API_AVAILABLE(ios(14.0))` for `locationManagerDidChangeAuthorization:`), so older iOS versions still link and run.

### Bug fixes

- **iOS `MAURConfig.fromDictionary`:** corrected `isNull(config[@"activitiesInterval"])` → `isNotNull(...)`. The inverted check meant a value sent by the app was assigned only when the dictionary entry was missing/null, effectively dropping user input. Pre-existing bug.
- **Android `HttpPostService.postJSONString`:** `Content-Length` now uses UTF-8 byte length instead of `String.length()`. Multi-byte characters (`ñ`, `é`, emoji, ...) now match the body sent. Pre-existing bug.

### Phase 3 also closed

- **Android `DistanceFilterLocationProvider`:** the legacy `Criteria` API is fully removed. The `criteria` field, its init in `onCreate()`, and the `translateDesiredAccuracy(...)` helper are gone. `LocationManager.getBestProvider(criteria, true)` is replaced by an explicit `pickProvider()` (GPS-first, Network-fallback). `requestSingleUpdate(criteria, ...)` in `StationaryLocationMonitorReceiver` is replaced by the provider-string overload plus a wider `try/catch` that also handles `IllegalArgumentException`.

### Notes (still legacy but functional)

- `LocationManager.getLastKnownLocation(provider)` is **not** deprecated; only `getBestProvider(Criteria, ...)` is. Kept as-is in `getLastBestLocation()`.
- `LocationManager.requestSingleUpdate(String, PendingIntent)` is deprecated since API 30 but is the only PendingIntent-based path; the modern `getCurrentLocation` does not accept a `PendingIntent` and would require redesigning the receiver pattern that wakes the app from background.
- `AlarmManager.setInexactRepeating` retained for stationary polling. Doze defers it to ≥9-15 min windows; an FGS-driven `LocationCallback` interval is the planned replacement.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.3.0...3.4.0)

---

## [3.3.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.3.0) (2026-05-07)

### Phase 2 — Backend-agnostic HTTP transport (Roadmap v3.3)

#### Added

- **`httpMethod`** and **`syncHttpMethod`** config options. Values: `POST` (default), `GET`, `PUT`, `PATCH`. Removes the previously hardcoded `POST` in Android (`HttpPostService.java`) and iOS (`MAURPostLocationTask.m`, `MAURBackgroundSync.m`).
- **`httpMode`** and **`syncMode`** config options. Values: `batch` (default) or `single` (one HTTP request per location). `single` is required when `httpMethod === 'GET'`.
- **URL templating.** The plugin now substitutes placeholders in `url`, `syncUrl` (and string values inside `bodyTemplate`/`postTemplate`) using the current location plus any `queryParams`. Built-in placeholders: `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`. Any extra `queryParams` keys are also available (e.g. `{device_id}`, `{token}`).
- **`queryParams`** config option. Static dictionary used to fill placeholders not derived from a location.
- **`headers`** config option as alias of `httpHeaders` (if both are present, `headers` wins).
- **`bodyTemplate`** config option as alias of `postTemplate`.
- New helpers: Android `com.marianhello.bgloc.http.UrlTemplateResolver` and iOS `MAURUrlTemplateResolver`.

#### Changed

- **`Config` / `MAURConfig`** extended with the new transport options. Parcelable read/write, copy constructor, defaults, `merge`, `toString`, `toDictionary` and `ConfigMapper` updated accordingly. All existing apps using only `url` + `httpHeaders` + `postTemplate` continue to work unchanged.
- **`HttpPostService`** Android: `setRequestMethod` is now driven by the configured method. GET requests skip the body entirely. New static helpers `postJSON(url, json, headers, method)` and `postJSONFile(url, file, headers, listener, method)`.
- **`MAURPostLocationTask` / `MAURBackgroundSync`** iOS: `setHTTPMethod` is parameterised. URL templates are resolved before each request. `single` mode posts one location per request.

### Phase 1 — Auto-start Android (Roadmap v3.3)

#### Added

- **Android `ACCESS_BACKGROUND_LOCATION` permission** declared in `plugin.xml`. Required for Android 10+ when starting the foreground location service from background (boot, app update). Apps must request it at runtime with the proper flow (foreground → explanation → "Allow all the time").
- **Boot/replace receiver expanded.** `BootCompletedReceiver` now also handles:
  - `android.intent.action.QUICKBOOT_POWERON` (HTC, MIUI / Xiaomi).
  - `com.htc.intent.action.QUICKBOOT_POWERON`.
  - `android.intent.action.MY_PACKAGE_REPLACED` — service is restarted after the app is updated via Play Store, with no need for the user to re-open the app.
- **Background location permission validated** in `BootCompletedReceiver` (Android 10+ only) and in `LocationServiceProxy.startForegroundService()`.
- **`ForegroundServiceStartNotAllowedException` handled** (Android 12+). Both `BootCompletedReceiver` and `LocationServiceProxy.startForegroundService()` now wrap `startForegroundService()` in try/catch with clear logging. WorkManager is **not** used as a fallback for tracking — it is only suitable for deferred sync, not for continuous GPS.

#### Changed

- **`foregroundServiceType` simplified to `"location"`** (was `"location|dataSync"`). The service does not perform `dataSync`; declaring it added unnecessary scrutiny in Play Console.
- **`LocationServiceImpl.startForeground()`** now reads `foregroundServiceType` dynamically from the merged manifest via `getManifestForegroundServiceType()` instead of using a hardcoded `0x8`. If the manifest type cannot be read (returns 0), the service refuses to start in foreground and logs an error rather than calling `startForeground` with an invalid type.
- **`LocationServiceProxy.startForegroundService()`** no longer falls back to a non-foreground `startService()` when the location permission is missing. It now logs and exits, avoiding zombie services.
- **`engines`** raised in both `package.json` and `plugin.xml`: `cordova >= 10.0.0`, `cordova-android >= 12.0.0`. Required for `targetSdk 34+` and modern foreground service handling.

#### Removed

- **`FOREGROUND_SERVICE_DATA_SYNC` permission** removed from `plugin.xml`. Pair of the `dataSync` foreground type cleanup above.
- **`<uses-library android:name="org.apache.http.legacy" />`** removed from `plugin.xml`. The plugin's HTTP client uses `HttpURLConnection`; no `org.apache.http.*` imports exist in the codebase.
- **`useLibrary 'org.apache.http.legacy'`** removed from `android/dependencies.gradle`. Same reason.
- **Dead constant `FOREGROUND_SERVICE_TYPE_LOCATION = 4`** removed from `LocationServiceImpl.java`. Its value was incorrect (real value is `0x00000008 = 8`; `4` is `PHONE_CALL`) and the constant was never referenced.

#### Build

- **`jcenter()` replaced with `mavenCentral()`** in `android/build.gradle` (both `buildscript` and `allprojects` blocks). JCenter has been deprecated since 2022.

#### Documentation

- **`docs/auto-start.md`** — full guide for boot/restart behavior (Android current state, gaps, v3.3 plan with concrete diffs; iOS limitations).
- **`docs/http-transport.md`** — backend-agnostic HTTP transport (Phase 2, v3.3.0).
- **`docs/traccar.md`** — Traccar as an optional backend example (geofences, events, history); plugin remains backend-agnostic.
- **`docs/driving-events.md`** — driving events plan (Phase 6, v4.0).
- **`docs/location-modernization.md`** — location API modernization plan (Phase 3, v3.4).
- **`docs/ROADMAP.md`** — phases 1→6.
- **`REVIEW_3.2.0.md`** — full audit and rationale for the roadmap.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.2.0...3.3.0)

---

## [3.2.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.2.0) (2026-02-28)

### Added

- **Session API for route/recording** — Lets the app keep a full copy of all locations for the *current recording session* in the plugin, independent of the sync queue. When the user reopens the app without internet, the app can restore the entire route from the plugin (no need to rely on `localStorage` for points).
  - **`startSession(success?, fail?)`** — Call when the user starts a route (e.g. "Start" button). Clears the session table and from then on every new location is also stored in the session table. Session data is **not** removed when locations are synced to the server.
  - **`getSessionLocations(success?, fail?)`** — Returns all locations currently in the session table, ordered by time. Same format as `Location` (latitude, longitude, time, speed, altitude, bearing, accuracy, etc.). Use when reopening without internet to rebuild the track.
  - **`clearSession(success?, fail?)`** — Call when the route is finished and sync has succeeded. Clears the session table so the next `startSession()` starts clean.
  - **`getSessionLocationsCount(success?, fail?)`** — Returns the number of locations in the session (e.g. to show "X points" without loading all).
- **Android:** New table `location_session` (DB version 20), `SessionLocationDAO`, and persistence from `PostLocationTask` when session is active. Session state stored in `SharedPreferences`.
- **iOS:** New table `location_session` (DB version 5), `MAURSessionLocationDAO` (singleton), and persistence from `MAURPostLocationTask` when session is active. Session state stored in `NSUserDefaults`.
- **JS / TypeScript:** The four session methods are available on the global plugin and in the `.d.ts`. **Angular:** `BackgroundGeolocationService` exposes `startSession`, `getSessionLocations`, `clearSession`, `getSessionLocationsCount`.

### Documentation

- **README.md** — New section *"New in 3.2.0"* describing the session API and typical flow (start route → startSession; reopen without internet → getSessionLocations; finish route → clearSession).
- **docs/api.md** — Quick reference and full sections for `startSession`, `getSessionLocations`, `clearSession`, `getSessionLocationsCount`.
- **docs/angular.md** — Session methods added to the methods table.
- **docs/index.md** — Mention of session/route restore without internet.
- **CHANGELOG.md** — This entry. **HISTORY.md** — 3.2.0 session methods. **RELEASE.MD** — Version example updated.

### Changed

- Version bump to 3.2.0.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.1.1...3.2.0)

---

## [3.1.1](https://github.com/josuelmm/cordova-background-geolocation/tree/3.1.1) (2026-02-27)

### Added

- **Browser / `ng serve` support** — The plugin uses `cordova/exec` and `cordova/channel`, which only exist in the Cordova runtime. To allow Angular (and other webpack-based) builds to succeed when running `ng serve` or building for browser, the package now ships:
  - **Stub modules:** `www/cordova-exec-stub.js` and `www/cordova-channel-stub.js`. When running in the browser they avoid crashes; when running inside Cordova they delegate to the real `cordova.exec` and `cordova/channel`.
  - **`browser` field in `package.json`** so bundlers (e.g. webpack) resolve `cordova/exec` and `cordova/channel` to these stubs. No app-side webpack config is required in normal setups.

### Fixed

- **Angular types on Windows** — The emitted `.d.ts` in `angular/dist/` used `from '../www/BackgroundGeolocation'`, which resolves (relative to `dist/`) to `angular/www/`, a folder that does not exist in the published package. Some apps worked around this by creating a junction `angular/www` → `www`. The build now runs a post-step (`scripts/fix-angular-dts-paths.js`) that rewrites these paths to `../../www/BackgroundGeolocation` in the emitted declarations, so types resolve to the package root `www/` and the junction is no longer needed.

### Documentation

- **docs/angular.md** — New section *"Build (ng serve / browser)"*: explains why the stubs exist, that webpack uses the `browser` field, and how to add resolve aliases in the app if a bundler does not respect it (e.g. "Can't resolve 'cordova/exec'"). Note that from 3.1.1 the junction workaround for types is unnecessary.

- **README.md** — Angular section and "New in" updated to mention 3.1.1 and browser/ng serve compatibility.

### Changed

- Version bump to 3.1.1.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.1.0...3.1.1)

---

## [3.1.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.1.0) (2026-02-21)

### Added

- **`getPendingSyncCount(success?, fail?)`** — Returns the number of locations pending to be synced (not yet sent to `syncUrl`). Use with `forceSync()` to show "X locations pending" and let the user trigger sync on demand. (Android, iOS)
- **`clearSync(success?, fail?)`** — Clears the pending sync queue: discards all locations waiting to be sent to `syncUrl` (they will not be synced). Use when the user wants to discard pending locations (e.g. "Clear queue" button). (Android, iOS)
- **Config option `sync`** (default `true`) — When `false`, automatic sync and `forceSync()` do not run; locations are still stored and can be synced later by setting `sync: true`. (Android, iOS)
- **Config options for sync notification texts (Android):** `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, `notificationSyncFailedText` — Customize or localize the notification shown while locations are syncing to the server.

### Fixed

- **Android:** `forceSync()` now correctly calls `callbackContext.success()` so the JS Promise resolves.
- **Android SyncAdapter:** Use `currentSyncConfig` (not out-of-scope `config`) when building sync notifications.
- **Android & iOS:** When `sync` was never set in config, DB column `sync_enabled` could be NULL; the plugin now treats NULL as “not set” (sync enabled by default) instead of “sync disabled”, so `forceSync()` and automatic sync work when the user did not pass `sync: false`.
- **Android sync with `Content-Type: application/x-www-form-urlencoded`:** Batch sync was sending one POST with `locations=<url-encoded-json-array>`, which many servers reject (400). Sync now sends **one POST per location** with the same flat `key=value&key=value` format as real-time posting, so the same endpoint accepts both.

### Changed

- Version bump to 3.1.0.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.0.2...3.1.0)

---

## [3.0.2](https://github.com/josuelmm/cordova-background-geolocation/tree/3.0.2) (2026-02-21)

### Fixed

- **Android (API 34+):** `getServiceInfo()` was returning an incomplete `ServiceInfo` (foregroundServiceType 0x0), causing "Cannot startForeground: manifest foregroundServiceType unknown/0". Fix:
  - Use **`PackageManager.ComponentInfoFlags.of(0)`** (not `GET_META_DATA`) when calling `getServiceInfo()` on API 33+, so the system returns a complete `ServiceInfo` with the real `foregroundServiceType`.
  - Renamed to **`getManifestForegroundServiceType()`**; never invent a fallback (no 0x4 when unknown). If the type is 0, return 0 and do not call `startForeground()` — log an error and return so the app must fix its manifest (e.g. `tools:replace="android:foregroundServiceType"` with `location`).
  - Use `LocationServiceImpl.class` for `ComponentName`. Read `foregroundServiceType` from `ServiceInfo` (reflection only for the field so the plugin compiles with compileSdk 33).

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.0.1...3.0.2)

---

## [3.0.1](https://github.com/josuelmm/cordova-background-geolocation/tree/3.0.1) (2026-02-21)

### Fixed

- **Android (API 34+):** Foreground service type mismatch crash (`IllegalArgumentException: foregroundServiceType 0x00000004 is not a subset of ...`). The type passed to `startForeground()` now matches the merged app manifest:
  - Read `foregroundServiceType` from the running service’s manifest at runtime and pass that value to `startForeground()` (so it works whether the merged manifest is `location`, `location|dataSync`, or overridden by the app to another type).
  - On API 33+, use `getServiceInfo(ComponentName, ComponentInfoFlags)` (via reflection) instead of the deprecated `getServiceInfo(component, int)`, which could return an incomplete `ServiceInfo` and force an incorrect fallback to 4 (location) while the system validated against the real manifest (e.g. 0x9).
  - Use `getClass()` for `ComponentName` so the manifest read corresponds to the actual running service instance.
  - Debug log before `startForeground`: `startForeground serviceType=0x…` in logcat to confirm the type used.
- **Android:** Avoid starting the foreground service when location permission is not granted: check in `LocationServiceImpl.startForeground()`, and in `BootCompletedReceiver` / `LocationServiceProxy` before calling `startForegroundService()` to prevent `SecurityException` and `ForegroundServiceDidNotStartInTimeException` when the app has no location permission at boot or when starting from background.

### Changed

- **Android:** Plugin manifests now declare the service with `foregroundServiceType`: `plugin.xml` uses `location|dataSync`; `android/common/AndroidManifest.xml` uses `location`.
- **Angular:** Wrapper is built with **ng-packagr** (Ivy/AOT) so the service works in production without requiring the JIT compiler (fixes “The injectable 'BackgroundGeolocationService' needs to be compiled using the JIT compiler” when `@angular/compiler` is not available). Entry point is `angular/public-api.ts`; package exports point to `angular/dist/`.
- **Angular:** Removed redundant files: `angular/index.ts`, `angular/tsconfig.lib.json`, and legacy tsc output (root-level `*.js` / `*.d.ts` in `angular/`). Only the ng-packagr build in `angular/dist/` is used.
- **README:** New section *“Android: configuring your app (recommended)”*: how to force `foregroundServiceType="location"` in the app’s AndroidManifest with `tools:replace`, optionally disable `BootCompletedReceiver` at boot, and required `strings.xml` entries for the plugin’s sync account (`plugin_bgloc_account_name`, `plugin_bgloc_account_type`, `plugin_bgloc_content_authority`).
- **package-lock.json:** Kept in sync with `package.json` (e.g. zone.js) so `npm ci` succeeds in CI.

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/3.0.0...3.0.1)

---

## [3.0.0](https://github.com/josuelmm/cordova-background-geolocation/tree/3.0.0) (2026-02-20)

### Added
- Android: Wake lock (PARTIAL_WAKE_LOCK) during tracking
- Android: Optional location watchdog (`enableWatchdog`) to restart provider if no update in ~60s
- `openSettings()` — convenience alias for `showAppSettings()`
- `getPluginVersion()` — returns plugin version from native
- iOS: `simulated` field in location payload (iOS 15+, from `sourceInformation.isSimulatedBySoftware`)
- Config option `enableWatchdog` (Android, default `false`)
- Angular: full documentation in docs/angular.md

### Changed
- Version bump to 3.0.0

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/2.3.4...3.0.0)

## [2.3.4](https://github.com/josuelmm/cordova-background-geolocation/tree/2.3.4) (2026-02-20)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v2.0.4...2.3.4)

## [v2.0.4](https://github.com/josuelmm/cordova-background-geolocation/tree/v2.0.4) (2022-03-16)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v2.0.3...v2.0.4)

## [v2.0.3](https://github.com/josuelmm/cordova-background-geolocation/tree/v2.0.3) (2022-02-27)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v2.0.2...v2.0.3)

## [v2.0.2](https://github.com/josuelmm/cordova-background-geolocation/tree/v2.0.2) (2022-01-20)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v2.0.1...v2.0.2)

## [v2.0.1](https://github.com/josuelmm/cordova-background-geolocation/tree/v2.0.1) (2021-10-01)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v2.0.0...v2.0.1)

## [v2.0.0](https://github.com/josuelmm/cordova-background-geolocation/tree/v2.0.0) (2021-10-01)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v1.1.0...v2.0.0)

## [v1.1.0](https://github.com/josuelmm/cordova-background-geolocation/tree/v1.1.0) (2021-06-27)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v1.0.0...v1.1.0)

## [v1.0.0](https://github.com/josuelmm/cordova-background-geolocation/tree/v1.0.0) (2021-05-04)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/v1.0.0-rc.5...v1.0.0)

## [v1.0.0-rc.5](https://github.com/josuelmm/cordova-background-geolocation/tree/v1.0.0-rc.5) (2021-05-03)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/1.0.0-rc.4...v1.0.0-rc.5)

## [1.0.0-rc.4](https://github.com/josuelmm/cordova-background-geolocation/tree/1.0.0-rc.4) (2021-05-02)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/1.0.0-rc.3...1.0.0-rc.4)

## [1.0.0-rc.3](https://github.com/josuelmm/cordova-background-geolocation/tree/1.0.0-rc.3) (2021-05-02)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/1.0.0-rc.2...1.0.0-rc.3)

## [1.0.0-rc.2](https://github.com/josuelmm/cordova-background-geolocation/tree/1.0.0-rc.2) (2021-05-01)

[Full Changelog](https://github.com/josuelmm/cordova-background-geolocation/compare/1.0.0-rc.1...1.0.0-rc.2)

# Historical Changelog

**for cordova-plugin-background-geolocation**

## [3.1.0] - 2019-09-24

### Fixed

- fix package scope
- Android fix RejectedExecutionException
- Android add stop guard

## Changed

- adopt headless task changes in common module

### [3.0.7] - 2019-09-17

### Fixed

- Android Foreground service permission is required since Android 28 - @IsraelHikingMap

### [3.0.6] - 2019-08-27

### Fixed

- Android allow to start service from background on API >=26

### [3.0.5] - 2019-08-13

### Fixed

- Android fix tone generator crash
- Fixed XML config to use to install plugin (PR #575) - @globules-io
- Fixed typo in README - @diegogurpegui

Many thanks to all contributors

### [3.0.1] - 2019-03-28

### Added

- iOS implement config.stopOnTerminate using startMonitoringSignificantLocationChanges

### Fixed

- Android fix don't start service on app visibility change events
fixes: #552, #551

### [3.0.0] - 2019-03-25

### Fixed

- Android fix don't start service on configure
fixes: #552, #551

### [3.0.0-alpha.XY] - unreleased

#### Added

- checkStatus if service is running
- events [start, stop, authorization, background, foreground]
- implement all methods for both platforms
- new RAW_LOCATION_PROVIDER

Since alpha.8:

- onError event signature = { code, message }
- post/sync attributes customization via postTemplate config prop
- enable partial plugin reconfiguration
- Android on "activity" event
- iOS configuration persistence

Since alpha.12:

- iOS ACTIVITY_PROVIDER (experimental)

Since alpha.15:

- checkStatus returns status of location services (locationServicesEnabled)
- iOS RAW_LOCATION_PROVIDER continue to run on app terminate

Since alpha.19:

- Android Headless Task

Since alpha.20:

- Android location parameters isFromMockProvider and mockLocationsEnabled

Since alpha.24:

- Android Oreo support

Since alpha.25:

- method forceSync
- option to get logs by offset and filter by log level
- log uncaught exceptions

Since alpha.30:

- method getCurrentLocation

Since alpha.41:

- notificationsEnabled config option (by [@danielgindi](https://github.com/danielgindi/))
More info: <https://github.com/mauron85/react-native-background-geolocation/pull/269>
- Allow stopping location updates on status "285 Updates Not Required" (by [@danielgindi](https://github.com/danielgindi/))
More info: <https://github.com/mauron85/react-native-background-geolocation/pull/271>

Since alpha.45:

- Listen for 401 Unauthorized status codes received from http server (by [@FeNoMeNa](https://github.com/FeNoMeNa/))
More info: <https://github.com/mauron85/react-native-background-geolocation/pull/308/files>

Since alpha.46:

- typescript definitions

Since alpha.47:

- allow nested location props in postTemplate

#### Changed

- start and stop methods doesn't accept callback (use event listeners instead)
- for background syncing syncUrl option is required
- on Android DISTANCE_FILTER_PROVIDER now accept arbitrary values (before only 10, 100, 1000)
- all plugin constants are in directly BackgroundGeolocation namespace. (check index.js)
- plugin can be started without executing configure (stored settings or defaults will be used)
- location property locationId renamed to just id
- iOS pauseLocationUpdates now default to false (becuase iOS docs now states that you need to restart manually if you set it to true)
- iOS finish method replaced with startTask and endTask

Since alpha.8:

- Android bind to service on facade construct

Since alpha.14:

- iOS saveBatteryOnBackground defaults to false

Since alpha.15:

- shared code base with react-native

Since alpha.25:

- Android common error format
- Android remove sync delay when conditions are met
- Android consider HTTP 201 response code as succesful post
- Android obey system sync setting

Since alpha.28:

- Android remove wake locks
<https://github.com/mauron85/background-geolocation-android/pull/4> by @grassick

Since alpha.29:

- Android show service notification only when in background
- Android remove config option startForeground (related to above)

Since alpha.32:

- Android bring back startForeground config option (BREAKING CHANGE!)

startForeground has slightly different meaning.

If false (default) then service will create notification and promotes
itself to foreground service, when client unbinds from service.
This typically happens when application is moving to background.
If app is moving back to foreground (becoming visible to user)
service destroys notification and also stop being foreground service.

If true service will create notification and will stay in foreground at all times.

Since alpha.33:

- Android internal changes (permission handling)

Since alpha.40:

- Android disable notification sound and vibration on oreo
(PR: [#9](https://github.com/mauron85/background-geolocation-android/pull/9)
by [@danielgindi](https://github.com/danielgindi/))

Since alpha.48:

- removeAllListeners - remove all event listeners when calling without parameter

Since alpha.50:

- export BackgroundGeolocationPlugin interface for ionic users (fixes #515)

### Fixed

Since alpha.13:

- iOS open location settings on iOS 10 and later (PR #158) by @asafron

Since alpha.15:

- checkStatus authorization
- Android fix for #362 Build Failed: cannot find symbol (PR #378)

Since alpha.18:

- Android fix #276 - NullPointerException: onTaskRemoved
- Android fix #380 - allow to override android support library

Since alpha.19:

- Android fix event listeners not triggering after app is restarted and service was running

Since alpha.23:

- iOS fix #394 - App Store Rejection - Prefs Non-Public URL Scheme
- iOS reset connectivity status on stop

Since alpha.24:

- Android fix service accidently started with default or stored config

Since alpha.25:

- Android add guards to prevent some race conditions
- Android config null handling

Since alpha.31:

- iOS fix error message format
- iOS fix missing getLogEntries arguments

Since alpha.32:

- iOS display debug notifications in foreground on iOS >= 10
- iOS missing activity provider stationary event
- Android getCurrentLocation request permission prompt

Since alpha.35:

- Android fix issue #431 - "dependencies.gradle" not found

Since alpha.38:

- iOS Fix crash on delete all location ([7392e39](https://github.com/mauron85/background-geolocation-ios/commit/7392e391c3de3ff0d6f5ef2ef19c34aba612bf9b) by [@acerbetti](https://github.com/acerbetti/))

Since alpha.39:

- Android Defer start and configure until service is ready
(PR: [#7](https://github.com/mauron85/background-geolocation-android/pull/7)
Commit: [00e1314](https://github.com/mauron85/background-geolocation-android/commit/00e131478ad4e37576eb85581bb663b65302a4e0) by [@danielgindi](https://github.com/danielgindi/),
fixes #201, #181, #172)

Since alpha.40:

- iOS Avoid taking control of UNUserNotificationCenter
(PR: [#268](https://github.com/mauron85/react-native-background-geolocation/pull/268))

Since alpha.42:

- Android fix locationService treating success as errors
(PR: [#13](https://github.com/mauron85/background-geolocation-android/pull/13)
by [@hoisel](https://github.com/hoisel/))

Since alpha.43:

- Android make sure mService exists when we call start or stop
(PR: [#17](https://github.com/mauron85/background-geolocation-android/pull/17)
by [@ivosabev](https://github.com/ivosabev/))

Since alpha.46:

- Android fix service crash on boot for Android 8 when startOnBoot option is used

Since alpha.48:

- fix typescript definitions (fixes #514)
- Android prefix resource strings to prevent collision with other plugins

Since alpha.49:

- Android fix App Crashes when entering / leaving Background
- Android fix crash on permission when started from background

### [2.3.6] - 2018-09-11

### Fixed

- Android remove non public URL

### [2.3.5] - 2018-03-29

### Fixed

- Android fix #384

### [2.3.3] - 2017-11-17

### Added

- Android allow override google play services version

### [2.3.2] - 2017-11-06

### Fix

- iOS support for iOS 11 (#PR 330)

### [2.3.1] - 2017-10-31

### Fix

- iOS httpHeaders values are not sent with syncUrl on iOS PR #325

### [2.3.0] - 2017-10-31

### Added

- Android Make account name configurable PR #334 by unixmonkey

### [2.2.5] - 2016-11-13

### Fixed

- Android fixing issue #195 PR204

### [2.2.4] - 2016-09-24

### Fixed

- iOS extremely stupid config bug from 2.2.3

### [2.2.3] - 2016-09-23

### Fixed

- Android issue #173 - allow stop service and prevent crash on destroy

### [2.2.2] - 2016-09-22

### Added

- Android android.hardware.location permission

### Fixed

- iOS onStationary null location
- iOS fix potential issue sending outdated location
- iOS handle null config options

### [2.2.1] - 2016-09-15

### Added

- iOS suppress minor error messages on first app run

### [2.2.0] - 2016-09-14

### Added

- iOS option pauseLocationUpdates PR #156

### [2.2.0-alpha.8] - 2016-09-02

### Fixed

- iOS compilation errors

### [2.2.0-alpha.7] - 2016-09-01

#### Removed

- Android location filtering

### Changed

- Android db logging instead of file
- iOS location prop heading renamed to bearing

### [2.2.0-alpha.6] - 2016-08-10

### Fixed

- Android don't try sync when locations count is lower then threshold

### [2.2.0-alpha.5] - 2016-08-10

### Fixed

- Android issue #130 - sync complete notification stays visible
- Android don't try sync when locations count is zero

### [2.2.0-alpha.4] - 2016-08-10

### Fixed

- Android issue #137 - fix only for API LEVEL >= 17

### [2.2.0-alpha.3] - 2016-08-10

### Fixed

- Android issue #139 - Starting backgroundGeolocation just after configure failed

### [2.2.0-alpha.2] - 2016-08-10

### Fixed

- iOS issue #132 use Library as DB path

### [2.2.0-alpha.1] - 2016-08-01

### Added

- Android, iOS limit maximum number of locations in db (maxLocations)
- Android showAppSettings
- Android, iOS database logging (getLogEntries)
- Android, iOS autosync locations to server with configurable threshold
- Android, iOS method getValidLocations
- iOS watchLocationMode and stopWatchingLocationMode
- iOS configurable NSLocationAlwaysUsageDescription

### Changed

- Locations stored into db at all times
- iOS persist locations also when url option is not used
- iOS dropping support for iOS < 4

### Fixed

- Android fix crash on permission change
- Android permission error code: 2
- Android on start err callback instead configure err callback
- Android overall background service reliability
- iOS do not block js thread when posting locations

### [2.1.2] - 2016-06-23

### Fixed

- iOS database not created

### [2.1.1] - private release

### Fixed

- iOS switching mode

### [2.1.0] - private release

### Added

- iOS option saveBatteryOnBackground
- iOS time validation rule for location

### [2.0.0] - 2016-06-17

### Fixed

- iOS prevent unintentional start when in background
- Android Destroy Existing Provider Before Creating New One (#94)

### [2.0.0-rc.3] - 2016-06-13

#### Fixed

- iOS memory leak

### [2.0.0-rc.1] - 2016-06-13

#### Changed

- Android notificationIcon option split into small and large!!!
- Android stopOnTerminate defaults to true
- Android option locationService renamed to locationProvider
- Android providers renamed (see README.md)
- Android bugfixing
- SampleApp moved into separate repo
- deprecated backgroundGeoLocation
- iOS split cordova specific code to allow code sharing with react-native-background-geolocation
- desiredAccuracy map any number
- Android locationTimeout option renamed to interval
- iOS switchMode (formerly setPace)

#### Added

- Android startOnBoot option
- Android startForeground option
- iOS, Android http posting of locations (options url and httpHeaders)
- iOS showLocationSettings
- iOS showAppSettings
- iOS isLocationEnabled
- iOS getLocations
- iOS deleteLocation
- iOS deleteAllLocations
- iOS foreground mode

#### Removed

- WP8 platform
- Android deprecated window.plugins.backgroundGeoLocation

### [1.0.2] - 2016-06-09

#### Fixed

- iOS queued locations are send FIFO (before fix LIFO)

### [1.0.1] - 2016-06-03

#### Fixed

- iOS7 crash on start
- iOS attempt to fix #46 and #39

### [1.0.0] - 2016-06-01

#### Added

- Android ANDROID_FUSED_LOCATION stopOnStillActivity (enhancement #69)

### [0.9.6] - 2016-04-07

#### Fixed

- Android ANDROID_FUSED_LOCATION fixing crash on start
- Android ANDROID_FUSED_LOCATION unregisterReceiver on destroy

### [0.9.5] - 2016-04-05

#### Fixed

- Android ANDROID_FUSED_LOCATION startTracking when STILL after app has started

### [0.9.4] - 2016-01-31

#### Fixed

- Android 6.0 permissions issue #21

### [0.9.3] - 2016-01-29

#### Fixed

- iOS cordova 6 compilation error
- iOS fix for iOS 9

#### Changes

- iOS removing cordova-plugin-geolocation dependency
- iOS user prompt for using location services
- iOS error callback when location services are disabled
- iOS error callback when user denied location tracking
- iOS adding error callbacks to SampleApp

### [0.9.2] - 2016-01-29

#### Fixed

- iOS temporarily using cordova-plugin-geolocation-ios9-fix to fix issues with iOS9
- iOS fixing SampleApp indexedDB issues

### [0.9.1] - 2015-12-18

#### Fixed

- Android ANDROID_FUSED_LOCATION fix config setActivitiesInterval

### [0.9.0] - 2015-12-18

#### Changed

- Android ANDROID_FUSED_LOCATION using ActivityRecognition (saving battery)

### [0.8.3] - 2015-12-18

#### Fixed

- Android fixing crash on exit

### [0.8.2] - 2015-12-18

#### Fixed

- Android fixing #9 - immediate bg service crash

### [0.8.1] - 2015-12-15

#### Fixed

- Android fixing #9

### [0.8.0] - 2015-12-15 (Merry XMas Edition :-)

#### Fixed

- Android persist location when main activity was killed

#### Changed

- Android persisting position when debug is on

### [0.7.3] - 2015-11-06

#### Fixed

- Android issue #11

### [0.7.2] - 2015-10-21

#### Fixed

- iOS fixing plugin dependencies (build)
- iOS related fixes for SampleApp

### [0.7.1] - 2015-10-21

#### Changed

- Android ANDROID_FUSED_LOCATION ditching setSmallestDisplacement(stationaryRadius) (seems buggy)

### [0.7.0] - 2015-10-21

#### Changed

- Android deprecating config option.interval
- Android allow run in background for FusedLocationService (wakeLock)
- Android will try to persist locations when main activity is killed
- Android new methods: (getLocations, deleteLocation, deleteAllLocations)
- Android stop exporting implicit intents (security)
- SampleApp updates

### [0.6.0] - 2015-10-17

#### Changed

- deprecating window.plugins clobber
- SampleApp updates

#### Added

- Android showLocationSettings and watchLocationMode

### [0.5.4] - 2015-10-13

#### Changed

- Android only cosmetic changes, but we need stable base

### [0.5.3] - 2015-10-12

#### Changed

- Android not setting any default notificationIcon and notificationIconColor.
- Android refactoring
- Android updated SampleApp

### [0.5.2] - 2015-10-12

#### Fixed

- Android fixing FusedLocationService start and crash on stop

### [0.5.1] - 2015-10-12

#### Fixed

- Android fix return types
- Android fix #3 NotificationBuilder.setColor method not present in API Level <21

#### Changed

- Android replacing Notication.Builder for NotificationCompat.Builder
- SampleApp can send position to server.
- SampleApp offline mode (IndexedDB)

#### Removed

- Android unnecessary plugins
- Docs: removing instructions to enable cordova geolocation in foreground
 and user accept location services

### [0.5.0] - 2015-10-10

#### Changed

- Android FusedLocationService
- Android package names reverted
- Android configuration refactored
- WP8 merged improvements

#### Removed

- Android unused classes
- All removing deprecated url, params, headers

### [0.4.3] - 2015-10-09

#### Added

- Android Add icon color parameter

#### Changed

- Changed the plugin.xml dependencies to the new NPM-based plugin syntax
- updated SampleApp

### [0.4.2] - 2015-09-30

#### Added

- Android open activity when notification clicked [69989e79a8a67485fc88463eec8d69bb713c2dbe](https://github.com/erikkemperman/cordova-plugin-background-geolocation/commit/69989e79a8a67485fc88463eec8d69bb713c2dbe)

#### Fixed

- Android duplicate desiredAccuracy extra
- Android [compilation error](https://github.com/coletivoEITA/cordova-plugin-background-geolocation/commit/813f1695144823d2a61f9733ced5b9fdedf15ff3)

### [0.4.1] - 2015-09-21

- maintenance version

### [0.4.0] - 2015-03-08

#### Added

- Android using callbacks same as iOS

#### Removed

- Android storing position into sqlite


\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
