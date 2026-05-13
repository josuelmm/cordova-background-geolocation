# Historical Changelog

**for cordova-plugin-background-geolocation**

## [4.5.3] - 2026-05-13

### Fixed (blocker — sync HTTP 400 with Traccar-style servers)
- Form-urlencoded body no longer sends `speed=null`, `events=null`, etc. when placeholders resolve to no value. Traccar's `OsmAndProtocolDecoder.parseDouble("null")` was throwing `NumberFormatException` → HTTP 400. Fix omits `JSONObject.NULL` / `NSNull` / literal `"null"` from the request body. Applies to real-time POST (foreground + background), `forceSync()`, automatic sync, both `httpMode='single'` and `httpMode='batch'`.

### Plugin version: `4.5.3`.

## [4.5.2] - 2026-05-10

### Added (Provider Hardening)
- `activityConfidenceThreshold` (0-100, default 50) — ignore low-confidence STILL/ACTIVE transitions in ACTIVITY_PROVIDER. iOS normalizes CMMotionActivityConfidence (Low/Medium/High → 20/40/80).
- `maxAcceptedAccuracy` (m, optional) — global filter that drops fixes worse than this before persist/POST/JS emission. All providers.

### Fixed (ACTIVITY_PROVIDER blockers)
- Android: missing `setMinUpdateDistanceMeters(distanceFilter)` in `LocationRequest.Builder`.
- Android: Google Play Services availability check in `onCreate`; emits `SERVICE_ERROR` if missing.
- Android: `ACTIVITY_RECOGNITION` permission check on Android 10+; emits `PERMISSION_DENIED_ERROR` once when denied (was silent → tracking ran continuously).
- Android: `onConfigure` only restarts tracking when a relevant field changes (was always stop+start).
- iOS: `onLocationsChanged` returns after stationary emission (was emitting both stationary and regular onLocationChanged during STILL).
- iOS: SOMotionDetector confidence normalized to 0-100 to match the threshold scale.
- iOS: ACTIVITY/RAW/DISTANCE providers release `delegate` slot in `onDestroy`/`dealloc` (singletons were leaking callbacks to destroyed instances).

### Fixed (Provider Errors)
- Android `DISTANCE_FILTER` + `RAW`: `onProviderDisabled` now emits `SERVICE_ERROR` when no fallback provider is available.
- iOS `MAURLocationManager`: added iOS 14+ `locationManagerDidChangeAuthorization:` callback (legacy guard prevents double-notification on iOS 14+).
- Android `RAW`: now picks providers based on `desiredAccuracy` (HIGH → GPS only, BALANCED → GPS+Network, LOW → Network only). Subscribes to both when useful.
- Warning logged when `stopOnStillActivity: false` is combined with `ACTIVITY_PROVIDER`.

### Refactor (internal, no JS API change, no device-coverage loss)
- iOS `ACTIVITY_PROVIDER` migrated to `CMMotionActivityManager` directly. `SOMotionDetector` removed entirely (sources + plugin.xml entries).
- Android `DISTANCE_FILTER_PROVIDER` is now **hybrid**: chooses backend at runtime — `FusedLocationProviderClient` when Google Play Services is available, `LocationManager` fallback when not (Huawei/HMS, AOSP, ChinaROMs). API unchanged.
- `addProximityAlert` path removed in both routes (no geofencing per product decision); stationary exit detected purely by polling.

### Plugin version: `4.5.2`.

## [4.5.1] - 2026-05-09

### Fixed (blockers)
- Android compile: missing `import android.os.Build;` in BackgroundGeolocationPlugin.
- Android `SQLiteLocationDAO` UPDATE on `maxLocations` now writes `events_json`, `battery_level`, `is_charging` (with NULL when absent).
- iOS `MAURSQLiteLocationDAO` UPDATE on `persistLocation:limitRows:` now writes the same 3 columns.

### Added (battery optimization)
- `wakeLockMode: 'none' | 'posting' | 'always'`. Default `'posting'`. Replaces previous always-on wake lock.
- Watchdog only restarts provider when `tripActive` — no needless GPS wake-ups while stationary.
- Stationary detection knobs now configurable: `stationaryTimeout`, `stationaryPollInterval`, `stationaryPollFast`.

### Fixed (other)
- Internal Android manifest cleaned (`uses-feature` instead of bogus `uses-permission` for `android.hardware.location`).
- README and `.d.ts` no longer claim that `events` is lost in sync queue (false since 4.5.0).
- `.npmignore` no longer ships `CLAUDE.md`, internal tests, scripts.

### Plugin version: `4.5.1`.

## [4.5.0] - 2026-05-09

### Added
- Persistir `events`, `battery`, `isCharging` en SQLite (Android v22 + iOS v6) — sobreviven cola de sync.
- iOS `config_json` (DB v7) — paridad con Android, persiste todas las keys post-3.2.0.
- Helpers JS de permisos runtime: `requestBackgroundLocationPermission`, `requestActivityRecognitionPermission`, `requestNotificationPermission`. iOS/Android viejos resuelven `notRequired: true`.

### Fixed
- iOS `MAURBackgroundSync`: borrar SQLite pendientes tras success (antes re-subía los mismos rows).
- Plugin version: `4.5.0`.

## [4.4.1] - 2026-05-09

### Fixed (stability)
- Android: persistencia de config completa via `config_json` TEXT (DB v21 migration). Soluciona pérdida de `httpMethod`, `queryParams`, `drivingEvents`, `includeBattery`, etc. tras reboot + startOnBoot.
- Android: `Config.merge()` ahora copia `includeBattery` (faltaba — `configure({includeBattery: false})` era ignorado).
- Android: `attachBatterySnapshot` usa `getApplicationContext().registerReceiver()` para evitar el override interno.
- Android: `<uses-permission android.hardware.location>` → `<uses-feature ... required="false">`.
- iOS: `MAURPostLocationTask` guard `outError == NULL`.
- Android+iOS: `pendingDrivingEvents` capped a 20 entradas, TTL 60s al drenar.
- JS: removido comentario huérfano `isLocationEnabled` (no existe método).
- Plugin version: `4.4.1`.

### Diseño
- Nueva `ConfigJsonMapper` en `common/` — serialización JSON reusable por DAO (common) y ConfigMapper (cordova) sin crear dependencia común→cordova.

## [4.4.0] - 2026-05-09

### Added
- Battery snapshot stamped on every location: `battery` (0-100) + `isCharging` (boolean).
- Default ON. Opt-out: `includeBattery: false`. Placeholders for templates: `'@battery'`, `'@isCharging'`.
- Android via `BatteryManager` sticky broadcast; iOS via `UIDevice.batteryLevel`. No extra permissions.
- Plugin version: `4.4.0`.

## [4.3.0] - 2026-05-09

### Added
- Driving events anexados al payload de location como `events: [{type, time, ...payload}]`. Se incluyen en el POST real-time al `url` configurado. La emisión vía `on(...)` JS sigue funcionando idéntico.
- Android: campo `drivingEvents` (transient) en `BackgroundLocation`; iOS: property en `MAURLocation`. Buffer de pending events para los que firan sin fix simultáneo.
- Caveat: si la location entra a cola de sync (SQLite), `events` no sobrevive. Los eventos siguen llegando por JS.
- Plugin version: `4.3.0`.

## [4.2.4] - 2026-05-09

### Fixed (CRITICAL)
- Foreground service silently aborted on Android 14+ when manifest reflection returned `0`. The plugin now falls back to `FOREGROUND_SERVICE_TYPE_LOCATION` (`0x8`) and retries without type if the typed call throws. Restores notification + background tracking on devices where the manifest merge did not pick up `foregroundServiceType="location"`.
- Plugin version: `4.2.4`.

## [4.2.3] - 2026-05-09

### Fixed
- `PostLocationTask` debug log: missing `mode` argument for the 4-placeholder format string. Cosmetic.
- Plugin version: `4.2.3`.

## [4.2.2] - 2026-05-09

### Fixed
- `PostLocationTask`: handle `LocationTemplate.locationToJson` returning `Object` (JSONObject vs JSONArray) — fixed compile failure under Capacitor / Gradle 8.x.
- `BackgroundGeolocationPlugin.buildDiagnostics`: wrap `facade.locationServicesEnabled()` in `try/catch (PluginException)` — fixed unreported-checked-exception build error.
- Plugin version: `4.2.2`.

## [4.2.0] - 2026-05-08

### Phase 8 — Real sensor fusion (accelerometer + gyroscope)

- New Android `com.marianhello.bgloc.sensor.SensorFusionDetector` using `Sensor.TYPE_LINEAR_ACCELERATION` + `Sensor.TYPE_GYROSCOPE` at `SENSOR_DELAY_GAME`.
- New iOS `MAURSensorFusionDetector` using `CMMotionManager.startDeviceMotionUpdatesToQueue` (50 Hz).
- New `drivingEvents.sensorFusion` toggle (off by default) plus thresholds: `crashImpactG` (default 3.0), `sensorCrashCooldownMs`, `phoneUsageWindowMs`, `phoneUsageCooldownMs`.
- `possibleCrash` event payload extended with `source: "gps" | "sensor"`.
- New `phoneUsageWhileDriving` event (jitter + screen-on during active trip).
- Hot-reload of sensor pipeline on `configure()`.
- Plugin version: `4.2.0`.

## [4.1.0] - 2026-05-09

### Phase 6.1 — GPS-derived sensor-like driving events

- New events: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash` (all GPS-derived, no accelerometer/gyroscope yet).
- New config thresholds: `hardBrakeMps2`, `rapidAccelMps2`, `sharpTurnDegPerSec`, `crashImpactKmh`, `crashWindowMs`. Set any to 0 to disable.
- 4-second cooldown per event to avoid refiring on sustained conditions.
- Android `DrivingEventsDetector` extended; iOS detector inline in `MAURBackgroundGeolocationFacade`. Same heuristics on both platforms.
- 4 new MSG codes (120-123) and 4 new NSNotifications wired end-to-end to JS.
- Plugin version: `4.1.0`.

Sensor fusion (real accelerometer + gyroscope) deferred to v4.2 as a separate `SensorFusionDetector` class.

## [4.0.0] - 2026-05-08

### Phase 6 — Driver insights (GPS-only)

- New events: `tripStart`, `tripEnd` (with `distance` + `durationMs`), `moving`, `stopped`, `speeding`, `providerChange`, `sos`.
- New method: `triggerSOS(payload?)`. New config: `drivingEvents` with thresholds for moving / trip / speed limit.
- Shared state machine: Android `DrivingEventsDetector.java` (pure-Java); iOS inline in `MAURBackgroundGeolocationFacade`. Android uses MSG codes 113-119 routed via the existing `PluginDelegate`; iOS uses 7 new `NSNotification`s observed in `CDVBackgroundGeolocation`.
- TypeScript: 7 new event overloads, new method declaration, new config option.
- Plugin version: `4.0.0`.

### Sensor-fusion events deferred to v4.1
- `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash` require accelerometer + gyroscope. Excluded on purpose to keep v4.0.0 GPS-only and reliable.

## [3.6.0] - 2026-05-08

### Phase 5 — Battery / OEM helpers

- Added `isIgnoringBatteryOptimizations()`, `requestIgnoreBatteryOptimizations()`, `openBatterySettings()`, `openAutoStartSettings()`, `getManufacturerHelp()` (Android + iOS).
- Android helper class `com.marianhello.bgloc.oem.BatteryOemHelper` with per-OEM `ComponentName` table for auto-start screens (Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch, OnePlus, Asus; Samsung falls back to app-info).
- iOS uses `UIApplicationOpenSettingsURLString` as best-effort destination and reports `manufacturer: 'apple'`.
- Angular service re-exports the 5 new helpers with strong types.

## [3.5.0] - 2026-05-08

### Phase 4 — Diagnostics

- Added `getDiagnostics()` (Android + iOS) returning permissions, battery whitelist state, OEM manufacturer, last fix age, pending sync count, foreground service type (Android) and precise location / background refresh / low power / motion permission (iOS).
- Added `Diagnostics` TypeScript interface in `www/BackgroundGeolocation.d.ts`.
- Angular service exposes `getDiagnostics()`.
- Android plugin version bumped to `3.5.0`.

- Added `mockLocationPolicy: 'allow' | 'flag' | 'drop'` config (Android `Config` + iOS `MAURConfig`). Applied in `PostLocationTask` (Android) and `MAURPostLocationTask` (iOS): mocked samples are dropped before persistence when policy is `'drop'`.
- Added `heartbeatInterval` config option (typed in `.d.ts`, persisted in `Config` / `MAURConfig`).
- Registered events `heartbeat`, `syncStart`, `syncProgress`, `syncSuccess`, `syncError` in `www/BackgroundGeolocation.js` and both `Event` union and `BackgroundGeolocationEvents` enum in `.d.ts`.
- **Sync events emit natively now**: Android via `MSG_ON_SYNC_*` broadcasts from `SyncAdapter` → `BackgroundGeolocationFacade` → `PluginDelegate` → `BackgroundGeolocationPlugin.sendEvent`; iOS via `NSNotificationCenter` from `MAURBackgroundSync` → `CDVBackgroundGeolocation` observers. `syncSuccess` payload includes `sent`; `syncError` payload includes `httpStatus` + `message`.
- Bug: iOS `getPluginVersion` returned hardcoded `"3.2.0"`; now `"3.5.0"`.

- `syncProgress` emits natively now: Android via `MSG_ON_SYNC_PROGRESS` from `SyncAdapter.onProgress`; iOS via `URLSession:task:didSendBodyData:totalBytesSent:totalBytesExpectedToSend:` posting `MAURBackgroundSyncDidProgressNotification`. Both are forwarded as `syncProgress` JS events.
- TypeScript `on()` overloads added for `'heartbeat'`, `'syncStart'`, `'syncProgress'`, `'syncSuccess'`, `'syncError'` so string-literal subscriptions type-check.
- Bug: iOS `MAURBackgroundSync.tasks` was never allocated. Fixed in `init`.

- `heartbeat` emits natively now: Android `LocationServiceImpl` schedules a `ScheduledExecutorService` task on service start using `Config.heartbeatInterval`, broadcasts `MSG_ON_HEARTBEAT` with the latest `BackgroundLocation`, and `BackgroundGeolocationPlugin` forwards it to JS. iOS `MAURBackgroundGeolocationFacade` schedules an `NSTimer` (main run loop), posts `MAURHeartbeatNotification`, and `CDVBackgroundGeolocation` forwards it as a `heartbeat` JS event with the latest location.

Phase 4 status: all four deliverables (`getDiagnostics`, `mockLocationPolicy`, sync events, heartbeat) are now native and end-to-end.

## [3.4.0] - 2026-05-08

### Phase 3 — Location API modernization

- Android `ActivityRecognitionLocationProvider`: `LocationRequest.Builder` + `Priority.PRIORITY_*` (replaces deprecated `LocationRequest.create()`, `setPriority/setInterval/setFastestInterval`, `LocationRequest.PRIORITY_*`).
- Android `RawLocationProvider`: removed `Criteria` and `getBestProvider`; explicit GPS-first / Network-fallback selection.
- `plugin.xml`: `GOOGLE_PLAY_SERVICES_VERSION` default raised to `21.0.1`.
- iOS `MAURPostLocationTask`: `NSURLConnection` (deprecated iOS 9) replaced with `NSURLSession dataTaskWithRequest:` + `dispatch_semaphore` for the synchronous post path.
- iOS `MAURDistanceFilterLocationProvider`: added `locationManagerDidChangeAuthorization:` (iOS 14+) alongside the legacy callback; short-circuit legacy on iOS 14+ to avoid double notifications.
- iOS `MAURConfig` + `MAURDistanceFilterLocationProvider`: new `showsBackgroundLocationIndicator` config (iOS 11+).
- TypeScript: `showsBackgroundLocationIndicator?: boolean` added to `ConfigureOptions`.
- `cordova-ios >= 6.2.0` required for `3.4.0` (runtime `@available` checks gate iOS 11/14 APIs).

- Android `DistanceFilterLocationProvider`: `Criteria` API fully removed; `getBestProvider(criteria, true)` replaced by explicit `pickProvider()`; `requestSingleUpdate(criteria, ...)` replaced by the provider-string overload.
- Bug: iOS `activitiesInterval` parsing in `MAURConfig.fromDictionary` had inverted `isNull` check; fixed.
- Bug: Android `Content-Length` used `String.length()` (chars) instead of UTF-8 byte length; fixed in `HttpPostService.postJSONString`.

Notes (still legacy but functional):
- `LocationManager.getLastKnownLocation` kept (not deprecated).
- `LocationManager.requestSingleUpdate(String, PendingIntent)` kept (modern `getCurrentLocation` does not accept `PendingIntent`).
- `AlarmManager.setInexactRepeating` kept for stationary polling; planned FGS-driven replacement.

## [3.3.0] - 2026-05-07

### Phase 2 — Backend-agnostic HTTP transport

- Added: `httpMethod`, `syncHttpMethod` (`POST` default, also `GET`, `PUT`, `PATCH`).
- Added: `httpMode`, `syncMode` (`batch` default, `single` for one request per location). `single` is required with `GET`.
- Added: URL templating with placeholders `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}` plus any keys from `queryParams`.
- Added: `queryParams`, `headers` (alias of `httpHeaders`), `bodyTemplate` (alias of `postTemplate`).
- Added: helpers `com.marianhello.bgloc.http.UrlTemplateResolver` (Android) and `MAURUrlTemplateResolver` (iOS).
- Changed: `HttpPostService` (Android), `MAURPostLocationTask` and `MAURBackgroundSync` (iOS) no longer hardcode POST.
- Compatibility: existing apps using only `url` + `httpHeaders` + `postTemplate` keep working.

### Phase 1 — Auto-start Android

- Added: `ACCESS_BACKGROUND_LOCATION` permission, validated at runtime on Android 10+ before starting the foreground service.
- Added: boot receiver also handles `QUICKBOOT_POWERON` (HTC, MIUI), `com.htc.intent.action.QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` (service is relaunched after app updates).
- Added: `ForegroundServiceStartNotAllowedException` (Android 12+) is now caught with clear logging in `BootCompletedReceiver` and `LocationServiceProxy`. WorkManager is not used as a tracking fallback (only for deferred sync).
- Changed: `foregroundServiceType` simplified to `"location"`; `LocationServiceImpl.startForeground()` reads the type dynamically from the manifest instead of hardcoding `0x8`.
- Changed: `LocationServiceProxy.startForegroundService()` no longer falls back to `startService()` when location permission is missing — it logs and exits.
- Changed: `engines` raised to `cordova >= 10.0.0` and `cordova-android >= 12.0.0`.
- Removed: `FOREGROUND_SERVICE_DATA_SYNC` permission and `dataSync` from `foregroundServiceType`.
- Removed: `<uses-library org.apache.http.legacy>` and `useLibrary 'org.apache.http.legacy'` (no longer used; the plugin uses `HttpURLConnection`).
- Removed: dead constant `FOREGROUND_SERVICE_TYPE_LOCATION = 4` in `LocationServiceImpl.java` (incorrect value; never referenced).
- Build: `jcenter()` → `mavenCentral()` in `android/build.gradle`.
- Docs: planning docs `docs/auto-start.md`, `docs/http-transport.md`, `docs/traccar.md`, `docs/driving-events.md`, `docs/ROADMAP.md`, `docs/location-modernization.md` (Fase 3 / v3.4). Audit in `REVIEW_3.2.0.md` (§8–§9 alineados al roadmap vigente).

## [3.2.0] - 2026-02-28

### Added

- **Session API (route/recording):** `startSession()`, `getSessionLocations()`, `clearSession()`, `getSessionLocationsCount()`. Separate session table (Android DB v20, iOS DB v5) so the app can restore the full route when reopening without internet. Session is cleared on `startSession()` and `clearSession()`; not cleared when sync succeeds.

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
