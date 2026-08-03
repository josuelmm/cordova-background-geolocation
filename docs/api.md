---
layout: default
nav_order: 2
title: API
---

# API

Note that all methods now return a `Promise` when the `success` and `fail` callbacks are omitted, so you can use `async/await`.

**Quick reference — main methods:** `configure`, `start`, `stop`, `getConfig`, `getCurrentLocation`, `checkStatus`, `getLocations`, `getValidLocations`, `deleteLocation`, `deleteAllLocations`, **`getPendingSyncCount`**, **`forceSync`**, **`clearSync`**, **`startSession`**, **`getSessionLocations`**, **`clearSession`**, **`getSessionLocationsCount`**, `getPluginVersion`, `showAppSettings`, `openSettings`, `showLocationSettings`, `getLogEntries`, `switchMode` (iOS), `startTask` / `endTask` (iOS), `removeAllListeners`, `on`. For sync queue (`syncUrl`), see [forceSync](#forcesync), [clearSync](#clearsyncsuccess-fail), [getPendingSyncCount](#getpendingsynccountsuccess-fail). For route/session (restore track without internet), see [startSession](#startsessionsuccess-fail), [getSessionLocations](#getsessionlocationssuccess-fail), [clearSession](#clearsessionsuccess-fail), [getSessionLocationsCount](#getsessionlocationscountsuccess-fail). For HTTP posting see [HTTP Location Posting](http_posting).

## TypeScript

Type definitions are in `www/BackgroundGeolocation.d.ts`. You can use:

- **Native names:** `ConfigureOptions`, `Location`, `LocationOptions`, `ServiceStatus`, `LogEntry`, `Event`, etc.
- **Awesome-style aliases / enums** (same names as [@awesome-cordova-plugins/background-geolocation](https://github.com/danielsogl/awesome-cordova-plugins/blob/master/src/%40awesome-cordova-plugins/plugins/background-geolocation/index.ts)): `BackgroundGeolocationConfig` (= `ConfigureOptions`), `BackgroundGeolocationResponse` (= `Location`), `BackgroundGeolocationEvents` (enum, e.g. `BackgroundGeolocationEvents.location`), `BackgroundGeolocationAccuracy`, `BackgroundGeolocationMode`, `BackgroundGeolocationLogEntry`, etc.

**Accuracy values** in this plugin are `0`, `100`, `1000`, `10000` (not 10, 100, 1000 like in Awesome). Use the constants on the plugin object (`BackgroundGeolocation.HIGH_ACCURACY`, `MEDIUM_ACCURACY`, `LOW_ACCURACY`, `PASSIVE_ACCURACY`) or the `BackgroundGeolocationAccuracy` enum from the types.

**Angular/Ionic:** Use a single import from `@josuelmm/cordova-background-geolocation/angular` for the service and common types; do not inject the global `BackgroundGeolocation`. See [Angular](angular).

## configure(options, success, fail)

Configure options:

| Parameter                 | Type              | Platform     | Description                                                                                                                                                                                                                                                                                                                                        | Provider*   | Default                    |
|---------------------------|-------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|----------------------------|
| `locationProvider`        | `Number`          | all          | Set location provider **@see** [PROVIDERS](providers)                                                                                                                                                                                                                                                                                          | N/A         | DISTANCE\_FILTER\_PROVIDER |
| `desiredAccuracy`         | `Number`          | all          | Desired accuracy in meters. Possible values [HIGH_ACCURACY, MEDIUM_ACCURACY, LOW_ACCURACY, PASSIVE_ACCURACY]. Accuracy has direct effect on power drain. Lower accuracy = lower power drain.                                                                                                                                                       | all         | MEDIUM\_ACCURACY           |
| `stationaryRadius`        | `Number`          | all          | Stationary radius in meters. When stopped, the minimum distance the device must move beyond the stationary location for aggressive background-tracking to engage.                                                                                                                                                                                  | DIS         | 50                         |
| `debug`                   | `Boolean`         | all          | When enabled, the plugin will emit sounds for life-cycle events of background-geolocation! See debugging sounds table.                                                                                                                                                                                                                             | all         | false                      |
| `distanceFilter`          | `Number`          | all          | The minimum distance (measured in meters) a device must move horizontally before an update event is generated. **@see** [Apple docs](https://developer.apple.com/library/ios/documentation/CoreLocation/Reference/CLLocationManager_Class/CLLocationManager/CLLocationManager.html#//apple_ref/occ/instp/CLLocationManager/distanceFilter).        | DIS,RAW     | 500                        |
| `stopOnTerminate`         | `Boolean`         | all          | Enable this in order to force a stop() when the application terminated (e.g. on iOS, double-tap home button, swipe away the app).                                                                                                                                                                                                                  | all         | true                       |
| `startOnBoot`             | `Boolean`         | Android      | Start background service on device boot.                                                                                                                                                                                                                                                                                                           | all         | false                      |
| `interval`                | `Number`          | Android      | The minimum time interval between location updates in milliseconds. **@see** [Android docs](http://developer.android.com/reference/android/location/LocationManager.html#requestLocationUpdates(long,%20float,%20android.location.Criteria,%20android.app.PendingIntent)) for more information.                                                    | all         | 60000                      |
| `fastestInterval`         | `Number`          | Android      | Fastest rate in milliseconds at which your app can handle location updates. **@see** [Android  docs](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.html#getFastestInterval()).                                                                                                                   | ACT         | 120000                     |
| `activitiesInterval`      | `Number`          | Android      | Rate in milliseconds at which activity recognition occurs. Larger values will result in fewer activity detections while improving battery life.                                                                                                                                                                                                    | ACT         | 10000                      |
| `stopOnStillActivity`     | `Boolean`         | Android      | @deprecated stop location updates, when the STILL activity is detected                                                                                                                                                                                                                                                                             | ACT         | true                       |
| `notificationsEnabled`    | `Boolean`         | Android      | Enable/disable local notifications when tracking and syncing locations                                                                                                                                                                                                                                                                             | all         | true                       |
| `startForeground`         | `Boolean`         | Android      | Allow location sync service to run in foreground state. Foreground state also requires a notification to be presented to the user.                                                                                                                                                                                                                 | all         | false                      |
| `notificationTitle`       | `String` optional | Android      | Custom notification title in the drawer. (goes with `startForeground`)                                                                                                                                                                                                                                                                             | all         | "Background tracking"      |
| `notificationText`        | `String` optional | Android      | Custom notification text in the drawer. (goes with `startForeground`)                                                                                                                                                                                                                                                                              | all         | "ENABLED"                  |
| `showTime`                | `Boolean`         | Android      | When true, the foreground notification shows a live elapsed time (HH:mm:ss) since the session started. Requires `startForeground`. Updates every second. **Labels default to English** ("Time"); for Spanish or other languages, define the optional string `plugin_bgloc_notification_time_label` in your app (see README).                                                                                           | all         | false                      |
| `showDistance`            | `Boolean`         | Android      | When true, the foreground notification shows accumulated distance (km) since the session started. Requires `startForeground`. Updates when each new location is received. **Labels default to English** ("Distance"); for Spanish or other languages, define `plugin_bgloc_notification_distance_label` in your app (see README).                                                                                                            | all         | false                      |
| `notificationSyncTitle`   | `String` optional | Android      | Title of the notification shown while syncing locations to the server. Use to localize (e.g. "Sincronizando ubicaciones").                                                                                                                                                                                                                         | all         | "Syncing locations"       |
| `notificationSyncText`   | `String` optional | Android      | Text shown while sync is in progress (e.g. "Sync in progress" / "Sincronizando…").                                                                                                                                                                                                                                                                 | all         | "Sync in progress"         |
| `notificationSyncCompletedText` | `String` optional | Android | Text when sync completes successfully.                                                                                                                                                                                                                                                                    | all         | "Sync completed"           |
| `notificationSyncFailedText`   | `String` optional | Android | Text when sync fails (prefix before " (HTTP …)" or ": error").                                                                                                                                                                                                                                            | all         | "Sync failed"              |
| `notificationIconColor`   | `String` optional | Android      | The accent color to use for notification. Eg. **#4CAF50**. (goes with `startForeground`)                                                                                                                                                                                                                                                           | all         |                            |
| `notificationIconLarge`   | `String` optional | Android      | The filename of a custom notification icon. **@see** Android quirks. (goes with `startForeground`)                                                                                                                                                                                                                                                 | all         |                            |
| `notificationIconSmall`   | `String` optional | Android      | The filename of a custom notification icon. **@see** Android quirks. (goes with `startForeground`)                                                                                                                                                                                                                                                 | all         |                            |
| `activityType`            | `String`          | iOS          | [AutomotiveNavigation, OtherNavigation, Fitness, Other] Presumably, this affects iOS GPS algorithm. **@see** [Apple docs](https://developer.apple.com/library/ios/documentation/CoreLocation/Reference/CLLocationManager_Class/CLLocationManager/CLLocationManager.html#//apple_ref/occ/instp/CLLocationManager/activityType) for more information | all         | "OtherNavigation"          |
| `pauseLocationUpdates`    | `Boolean`         | iOS          | Pauses location updates when app is paused. **@see** [Apple docs](https://developer.apple.com/documentation/corelocation/cllocationmanager/1620553-pauseslocationupdatesautomatical?language=objc)                                                                                                                                                  | all         | false                      |
| `saveBatteryOnBackground` | `Boolean`         | iOS          | Switch to less accurate significant changes and region monitory when in background                                                                                                                                                                                                                                                                 | all         | false                      |
| `url`                     | `String`          | all          | Server url where to send HTTP POST with recorded locations **@see** [HTTP locations posting](#http-locations-posting)                                                                                                                                                                                                                              | all         |                            |
| `syncUrl`                 | `String`          | all          | Server url where to send fail to post locations **@see** [HTTP locations posting](#http-locations-posting)                                                                                                                                                                                                                                         | all         |                            |
| `syncThreshold`           | `Number`          | all          | Specifies how many previously failed locations will be sent to server at once                                                                                                                                                                                                                                                                      | all         | 100                        |
| `sync`                    | `Boolean`         | all          | When true, automatic sync and forceSync() send locations to syncUrl. When false, sync is disabled (locations are still stored; set sync: true later to sync).                                                                                                                                                                                       | all         | true                       |
| `httpHeaders`             | `Object`          | all          | Headers for POST/sync. Two ways: static here, or dynamic on 401 via `http_authorization`. Content-Type: `application/json` (default) or `application/x-www-form-urlencoded`. **@see** [HTTP posting](http_posting#http-headers-two-ways).                                                                                                                                                                                       | all         |                            |
| `maxLocations`            | `Number`          | all          | Limit maximum number of locations stored into db                                                                                                                                                                                                                                                                                                   | all         | 10000                      |
| `enableWatchdog`          | `Boolean`         | Android      | If true, when no location update is received for ~60s the provider is restarted (helps on some devices).                                                                                                                                                                                                                                          | all         | false                      |
| `postTemplate`            | `Object\|Array`   | all          | Customization post template **@see** [Custom post template](#custom-post-template)                                                                                                                                                                                                                                                                 | all         |                            |
| `httpMethod`              | `String`          | Android, iOS | **Since 3.3.0.** HTTP method for `url`. One of `POST`, `GET`, `PUT`, `PATCH`. Use `GET` together with URL templating to deliver positions through the query string. **@see** [HTTP transport](#http-transport).                                                                                                                                       | all         | `POST`                     |
| `syncHttpMethod`          | `String`          | Android, iOS | **Since 3.3.0.** HTTP method for `syncUrl` (the offline queue). `POST`, `PUT` or `PATCH`. **`GET` is coerced to `POST` since 5.0.1** (logged, `configure()` still succeeds): the batch URL is resolved once with no location, so per-location placeholders stay unsubstituted and a 200 would delete the batch with zero real data.                                                                                                                                                                                                                                                       | all         | `POST`                     |
| `httpMode`                | `String`          | Android, iOS | **Since 3.3.0.** `single` (one request per location, body `{...}`) or `batch` (body `[{...}]`). Real-time always posts exactly one location, so `batch` only makes sense if your backend asks for an array. **Default changed to `single` in 5.0.1** to restore the v4 payload shape: v5.0.0 defaulted to `batch`, so REST backends that had received an object for years started getting `[{...}]` and answering 400 on every fix. Required to be `single` when `httpMethod` is `GET`. | all         | `single`                   |
| `syncMode`                | `String`          | Android, iOS | **Since 3.3.0.** Same values as `httpMode` but for the sync queue.                                                                                                                                                                                                                                                                                 | all         | `batch`                    |
| `headers`                 | `Object`          | Android, iOS | **Since 3.3.0.** Alias of `httpHeaders`. If both are present, `headers` takes precedence.                                                                                                                                                                                                                                                          | all         |                            |
| `bodyTemplate`            | `Object\|Array`   | Android, iOS | **Since 3.3.0.** Alias of `postTemplate`. Same syntax (`@latitude`, `@longitude`, ...) and the new placeholder syntax (`{latitude}`, `{lon}`, ...) is supported on string values.                                                                                                                                                                  | all         |                            |
| `queryParams`             | `Object`          | Android, iOS | **Since 3.3.0.** Static placeholder values used by URL/body templating. Built-in placeholders resolved from each location: `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`, `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`. Any extra keys here are also available (e.g. `{device_id}`). | all         |                            |
| `showsBackgroundLocationIndicator` | `Boolean` | iOS         | **Since 3.4.0.** iOS 11+. When `true`, iOS shows the blue status bar / pill while the app uses location in the background. Apple recommends this for transparency in apps that track continuously.                                                                                                                                                | all         | `false`                    |
| `heartbeatInterval`       | `Number`          | Android, iOS | **Since 3.5.0.** Interval (ms) at which the plugin emits a `heartbeat` event with the latest known location. `0` (default) disables the heartbeat. Native emission is wired end-to-end (Android `ScheduledExecutorService`, iOS `NSTimer`). On the first ticks before any GPS fix is received the event arrives without a location payload. | all         | `0`                        |
| `mockLocationPolicy`      | `String`          | Android, iOS | **Since 3.5.0.** Policy for mocked locations (`isFromMockProvider` Android / `simulated` iOS). One of `allow` (default, keep), `flag` (deliver with the existing mocked flag set so the app/server can filter) or `drop` (discard before persisting/posting).                                                                                       | all         | `allow`                    |
| `drivingEvents`           | `Object`          | Android, iOS | **Since 4.0.0.** Driver-insights configuration (GPS state machine + sensor fusion in 4.2+). See [Driver insights](#driver-insights-since-400).                                                                                                                                                                                                       | all         | `{ enabled: false }`       |
| `includeBattery`          | `Boolean`         | Android, iOS | **Since 4.4.0.** Stamp `battery` (0-100) and `isCharging` on every fix sent to the backend. Set `false` to opt out.                                                                                                                                                                                                                                 | all         | `true`                     |
| `wakeLockMode`            | `String`          | Android      | **Since 4.5.1.** WakeLock policy. `'none'` never; `'posting'` only during 30 s after each fix (default); `'always'` permanent while service runs (legacy).                                                                                                                                                                                          | all         | `'posting'`                |
| `stationaryTimeout`       | `Number`          | Android      | **Since 4.5.1.** ms of no movement before declaring stationary. Was hardcoded 5*60_000.                                                                                                                                                                                                                                                              | DIS         | `300000`                   |
| `stationaryPollInterval`  | `Number`          | Android      | **Since 4.5.1.** Lazy poll interval while stationary. Was hardcoded 3*60_000.                                                                                                                                                                                                                                                                        | DIS         | `180000`                   |
| `stationaryPollFast`      | `Number`          | Android      | **Since 4.5.1.** Aggressive poll near stationary boundary. Was hardcoded 60_000.                                                                                                                                                                                                                                                                     | DIS         | `60000`                    |
| `activityConfidenceThreshold` | `Number`      | Android, iOS | **Since 4.5.4.** 0-100. Ignore STILL/ACTIVE transitions below this confidence (prevents jittery GPS start/stop). iOS normalizes CMMotionActivityConfidence (Low/Med/High → 20/40/80).                                                                                                                                                                | ACT         | `50`                       |
| `maxAcceptedAccuracy`     | `Number`          | Android, iOS | **Since 4.5.4.** Maximum accepted horizontal accuracy in meters. Fixes worse than this are dropped before persist/POST/JS emission. **Off by default (`null`)** to avoid regressing existing apps. Recommended: `100` para tracking vehicular, `500` para tolerante.                                                                                | all         | `null`                     |

### Custom post template

`postTemplate` (alias: `bodyTemplate`) lets you control the JSON body sent to `url` / `syncUrl`. **Important: it REPLACES the default template completely — there is no merge.**

If you define `postTemplate`, the plugin only serialises the keys you list. Built-in placeholders (`@latitude`, `@longitude`, `@time`, etc.) resolve from each location; keys starting with `@` for which the location has no value resolve to JSON `null`. Static strings (e.g. `"deviceId": "ABC"`) pass through unchanged.

**Available placeholders** (all start with `@`):

`@id`, `@time` (UTC ms), `@time_seconds` (UTC seconds, since 4.5.5 — use this for Traccar OsmAnd and any backend that expects 10-digit seconds), `@timestamp_iso` (ISO 8601 string), `@provider`, `@locationProvider`, `@latitude`, `@longitude`, `@accuracy`, `@altitudeAccuracy`, `@speed`, `@altitude`, `@bearing`, `@radius`, `@isFromMockProvider`, `@mockLocationsEnabled` (Android), `@simulated` (iOS), `@events`, `@battery`, `@isCharging`, `@recordedAt` (iOS only).

**If you want `events`, `battery`, `isCharging` in your custom payload, include them explicitly**:

```js
BackgroundGeolocation.configure({
  url: 'https://my.api/locations',
  postTemplate: {
    deviceId: 'ABC-123',          // static — passes through
    lat: '@latitude',             // → number from location
    lon: '@longitude',
    t:   '@time',
    spd: '@speed',
    battery: '@battery',          // → number 0-100, or null
    charging: '@isCharging',      // → boolean, or null
    events: '@events'             // → JSON array of driving events, or null
  }
});
```

If you OMIT a placeholder from `postTemplate`, that field will NOT be sent — even if the plugin computed it internally. The default template (when no `postTemplate` is configured) includes all of the above automatically.

### Runtime permission helpers (since 4.5.0)

These are convenience wrappers around the OS runtime permission dialog. iOS / Android < min-API for the permission resolve immediately with `{ granted: true, notRequired: true }`.

```ts
BackgroundGeolocation.requestBackgroundLocationPermission(); // → { granted, denied?, notRequired? }
BackgroundGeolocation.requestActivityRecognitionPermission(); // Android 10+
BackgroundGeolocation.requestNotificationPermission();         // Android 13+
```

\*
DIS = DISTANCE\_FILTER\_PROVIDER
ACT = ACTIVITY\_PROVIDER
RAW = RAW\_PROVIDER

**Sync notification texts (Android):** Pass `notificationSyncTitle`, `notificationSyncText`, `notificationSyncCompletedText`, and `notificationSyncFailedText` in the same `configure()` call you use for the plugin. They are stored and used when sync runs (including `forceSync()`). If you never set them, the English defaults are shown. To verify they were saved, call `getConfig()` and check the returned object.

Partial reconfiguration is possible by later providing a subset of the configuration options:

```javascript
BackgroundGeolocation.configure({
  debug: true
});
```

In this case new configuration options will be merged with stored configuration options and changes will be applied immediately.

**Important:** Because configuration options are applied partially, it's not possible to reset option to default value just by omitting it's key name and calling `configure` method. To reset configuration option to the default value, it's key must be set to `null`!

```javascript
// Example: reset postTemplate to default
BackgroundGeolocation.configure({
  postTemplate: null
});
```

## getConfig(success, fail)

Platform: iOS, Android

Get current configuration. Method will return all configuration options and their values in success callback.
Because `configure` method can be called with subset of the configuration options only,
`getConfig` method can be used to check the actual applied configuration.

```javascript
BackgroundGeolocation.getConfig(function(config) {
  console.log(config);
});
```

## start()

Platform: iOS, Android

Start background geolocation.

## stop()

Platform: iOS, Android

Stop background geolocation.

## getCurrentLocation(success, fail, options)

Platform: iOS, Android

One time location check to get current location of the device.

| Option parameter           | Type      | Description                                                                            |
|----------------------------|-----------|----------------------------------------------------------------------------------------|
| `timeout`                  | `Number`  | Maximum time in milliseconds device will wait for location                             |
| `maximumAge`               | `Number`  | Maximum age in milliseconds of a possible cached location that is acceptable to return |
| `enableHighAccuracy`       | `Boolean` | if true and if the device is able to provide a more accurate position, it will do so   |

| Success callback parameter | Type      | Description                                                    |
|----------------------------|-----------|----------------------------------------------------------------|
| `location`                 | `Object`  | location object (@see [Location event](#location-event))       |

| Error callback parameter   | Type      | Description                                                    |
|----------------------------|-----------|----------------------------------------------------------------|
| `code`                     | `Number`  | Reason of an error occurring when using the geolocating device |
| `message`                  | `String`  | Message describing the details of the error                    |

Error codes:

| Value | Associated constant  | Description                                                              |
|-------|----------------------|--------------------------------------------------------------------------|
| 1     | PERMISSION_DENIED    | Request failed due missing permissions                                   |
| 2     | LOCATION_UNAVAILABLE | Internal source of location returned an internal error                   |
| 3     | TIMEOUT              | Timeout defined by `option.timeout` was exceeded                            |

## getStationaryLocation(success, fail)

Platform: iOS, Android

Returns the current stationary location if available (e.g. when in stationary mode). Success callback receives the location object or `null` if none.

## checkStatus(success, fail)

Check status of the service

| Success callback parameter | Type      | Description                                          |
|----------------------------|-----------|------------------------------------------------------|
| `isRunning`                | `Boolean` | true/false (true if service is running)              |
| `locationServicesEnabled`  | `Boolean` | true/false (true if location services are enabled)   |
| `authorization`            | `Number`  | authorization status                                 |

Authorization statuses:

* NOT_AUTHORIZED
* AUTHORIZED - authorization to run in background and foreground
* AUTHORIZED_FOREGROUND iOS only authorization to run in foreground only

Note: In the Android concept of authorization, these represent application permissions.

## showAppSettings()

Platform: Android >= 6, iOS >= 8.0

Show app settings to allow change of app location permissions.

## showLocationSettings()

Platform: Android

Show system settings to allow configuration of current location sources.

## openSettings()

Platform: Android, iOS

Open app settings (convenience alias for `showAppSettings()`). Use this to let the user change location permissions.

## getPluginVersion(success, fail)

Platform: Android, iOS

Returns the plugin version string (e.g. `"3.1.0"`). Useful for debugging or compatibility checks.

## getLocations(success, fail)

Platform: iOS, Android

Method will return all stored locations.
This method is useful for initial rendering of user location on a map just after application launch.

| Success callback parameter | Type    | Description                    |
|----------------------------|---------|--------------------------------|
| `locations`                | `Array` | collection of stored locations |

```javascript
BackgroundGeolocation.getLocations(
  function (locations) {
    console.log(locations);
  }
);
```

## getValidLocations(success, fail)

Platform: iOS, Android

Method will return locations which have not yet been posted to server.

| Success callback parameter | Type    | Description                    |
|----------------------------|---------|--------------------------------|
| `locations`                | `Array` | collection of stored locations |

## getValidLocationsAndDelete(success, fail)

Platform: iOS, Android

Method will return locations which have not yet been posted to server and delete to avoid getting them again.

| Success callback parameter | Type    | Description                    |
|----------------------------|---------|--------------------------------|
| `locations`                | `Array` | collection of stored locations |

## deleteLocation(locationId, success, fail)

Platform: iOS, Android

Delete location with locationId.

## deleteAllLocations(success, fail)

**Note:** You don't need to delete all locations. The plugin manages the number of stored locations automatically and the total count never exceeds the number as defined by `option.maxLocations`.

Platform: iOS, Android

Delete all stored locations.

**Note:** Locations are not actually deleted from database to avoid gaps in locationId numbering.
Instead locations are marked as deleted. Locations marked as deleted will not appear in output of `BackgroundGeolocation.getValidLocations`.

## switchMode(modeId, success, fail)

Platform: iOS

Normally the plugin will handle switching between **BACKGROUND** and **FOREGROUND** mode itself.
Calling switchMode you can override plugin behavior and force it to switch into other mode.

In **FOREGROUND** mode the plugin uses iOS local manager to receive locations and behavior is affected
by `option.desiredAccuracy` and `option.distanceFilter`.

In **BACKGROUND** mode plugin uses significant changes and region monitoring to receive locations
and uses `option.stationaryRadius` only.

```javascript
// switch to FOREGROUND mode
BackgroundGeolocation.switchMode(BackgroundGeolocation.FOREGROUND_MODE);

// switch to BACKGROUND mode
BackgroundGeolocation.switchMode(BackgroundGeolocation.BACKGROUND_MODE);
```

## forceSync()

Platform: Android, iOS

Force sync of pending locations. Option `syncThreshold` will be ignored and
all pending locations will be immediately posted to `syncUrl` in single batch.
No-op if `sync` is `false` in config.

## clearSync(success, fail)

Platform: Android, iOS

Clear the pending sync queue: discard all locations waiting to be sent to `syncUrl`.
They will not be synced. Use when the user wants to discard pending locations (e.g. "Clear queue" button).
After calling, `getPendingSyncCount()` will return 0 until new locations are stored for sync.

## getPendingSyncCount(success, fail)

Platform: Android, iOS

Returns the number of locations pending to be synced (not yet sent to `syncUrl`).
Use with `forceSync()` to show "X locations pending" and let the user trigger sync on demand.

```javascript
BackgroundGeolocation.getPendingSyncCount()
  .then(function (count) {
    console.log('Pending to sync:', count);
    if (count > 0) {
      // optionally: BackgroundGeolocation.forceSync();
    }
  });
```

## startSession(success, fail)

Platform: Android, iOS

Starts a *recording session*: clears the session table and from then on every new location is also stored in the session table. Session data is **not** removed when locations are synced to the server. Call when the user starts a route (e.g. "Start" button).

When the user reopens the app without internet, use `getSessionLocations()` to get all points and restore the track. When the route is finished and sync has succeeded, call `clearSession()` to clear the session table.

```javascript
// When user taps "Start" on a route
BackgroundGeolocation.startSession().then(function () {
  return BackgroundGeolocation.start();
});
```

## getSessionLocations(success, fail)

Platform: Android, iOS

Returns all locations currently stored in the session table, ordered by time. Same format as `Location` (latitude, longitude, time, speed, altitude, bearing, accuracy, etc.). Use when reopening the app without internet to rebuild the full route/track.

```javascript
BackgroundGeolocation.getSessionLocations().then(function (locations) {
  // Redraw the route on the map, compute distance, etc.
  console.log('Session points:', locations.length);
});
```

## clearSession(success, fail)

Platform: Android, iOS

Clears the session table. Call when the route is finished and sync to the server has succeeded. After this, the next `startSession()` will start with an empty session.

```javascript
// When user finishes the route and sync OK
BackgroundGeolocation.clearSession().then(function () {
  console.log('Session cleared');
});
```

## getSessionLocationsCount(success, fail)

Platform: Android, iOS

Returns the number of locations in the current session. Useful to show "X points" in the UI without loading all locations.

```javascript
BackgroundGeolocation.getSessionLocationsCount().then(function (count) {
  console.log('Points in session:', count);
});
```

## getLogEntries(limit, fromId, minLevel, success, fail)

Platform: Android, iOS

Return all logged events. Useful for plugin debugging.

| Parameter  | Type          | Description                                                                                       |
|------------|---------------|---------------------------------------------------------------------------------------------------|
| `limit`    | `Number`      | limits number of returned entries                                                                 |
| `fromId`   | `Number`      | return entries after fromId. Useful for pagination / infinite log scrolling                        |
| `minLevel` | `String`      | return log entries above level. Available levels: "TRACE", "DEBUG", "INFO", "WARN", "ERROR"         |
| `success`  | `Function`    | callback function which will be called with log entries                                           |

Format of log entry:

| Parameter   | Type          | Description                                                                                       |
|-------------|---------------|---------------------------------------------------------------------------------------------------|
| `id`        | `Number`      | id of log entry as stored in db                                                                   |
| `timestamp` | `Number`      | timestamp in milliseconds since beginning of UNIX epoch                                           |
| `level`     | `String`      | log level                                                                                         |
| `message`   | `String`      | log message                                                                                       |
| `stackTrace`| `String`      | recorded stacktrace (Android only, on iOS part of message)                                        |

## removeAllListeners(event)

Unregister all event listeners for given event. If parameter `event` is not provided then all event listeners will be removed.

## getDiagnostics(success, fail)

Platform: Android, iOS — **Since 3.5.0**

Returns extended diagnostics that help explain *why* tracking may not be running in production: missing background-location permission on Android 10+, OEM that killed the foreground service, `preciseLocationEnabled: false` on iOS, etc.

```javascript
BackgroundGeolocation.getDiagnostics()
  .then(function (d) {
    console.log('isRunning?', d.isRunning);
    console.log('background granted?', d.backgroundLocationGranted);    // Android
    console.log('battery whitelist?', d.batteryOptimizationIgnored);    // Android
    console.log('manufacturer:', d.manufacturer);                       // Android (xiaomi/huawei/...)
    console.log('precise location?', d.preciseLocationEnabled);         // iOS 14+
    console.log('background refresh:', d.backgroundRefreshStatus);      // iOS
    console.log('low power mode:', d.lowPowerModeEnabled);              // iOS
  });
```

Returned fields (all optional except `isRunning` and `locationServicesEnabled`):

**Common:** `isRunning`, `locationServicesEnabled`, `startOnBoot`, `pendingSyncCount`, `lastLocationAt`.

**Android only:** `fineLocationGranted`, `coarseLocationGranted`, `backgroundLocationGranted` (always `true` on Android < 10), `notificationPermissionGranted` (always `true` on Android < 13), `activityRecognitionGranted`, `batteryOptimizationIgnored`, `manufacturer`, `foregroundServiceType`.

**iOS only:** `preciseLocationEnabled` (iOS 14+), `backgroundRefreshStatus`, `lowPowerModeEnabled`, `motionPermissionStatus`, `authorizationStatusText`.

Use `getDiagnostics()` proactively in your app to surface a "Tracking is not active because…" message to users and link them to the right Settings screen.

## Driver insights (since 4.0.0)

GPS-only state machine that emits `tripStart`, `tripEnd`, `moving`, `stopped`, `speeding`, `providerChange` and `sos`. v4.1 added GPS-derived sensor-like events (`hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`) computed from speed/bearing deltas — no sensors needed. v4.2 adds **real sensor fusion** (opt-in) using accelerometer + gyroscope to refine `possibleCrash` at low speed and to detect `phoneUsageWhileDriving`.

### Enable

```javascript
BackgroundGeolocation.configure({
  // ...
  drivingEvents: {
    enabled: true,
    speedLimit: 80,           // km/h; 0 disables `speeding`
    minMovingSpeed: 1.0,      // m/s, default
    stoppedDuration: 60000,
    minTripSpeed: 3.0,
    minTripDuration: 30000,
    // v4.1 GPS-derived events — set 0 to disable any
    hardBrakeMps2: 3.5,
    rapidAccelMps2: 3.5,
    sharpTurnDegPerSec: 30,
    crashImpactKmh: 25,
    crashWindowMs: 2000,
    // v4.2 real sensor fusion — opt-in. Off by default.
    sensorFusion: true,             // start accelerometer + gyroscope while a trip is active
    crashImpactG: 3.0,              // |a| threshold in g for sensor-driven possibleCrash
    sensorCrashCooldownMs: 10000,
    phoneUsageWindowMs: 4000,       // sustained jitter window for phoneUsageWhileDriving
    phoneUsageCooldownMs: 60000
  }
});

BackgroundGeolocation.on('tripStart', function (location) { /* ... */ });
BackgroundGeolocation.on('tripEnd',   function (data)     { /* { location, distance (m), durationMs } */ });
BackgroundGeolocation.on('moving',    function (location) { /* ... */ });
BackgroundGeolocation.on('stopped',   function (location) { /* ... */ });
BackgroundGeolocation.on('speeding',  function (data)     { /* { location, speedKmh, limitKmh } */ });
BackgroundGeolocation.on('providerChange', function (data){ /* { provider } */ });
BackgroundGeolocation.on('sos',       function (data)     { /* user payload + { location? } */ });

// v4.1 GPS-derived sensor-like events — payload `{ location, value }`
BackgroundGeolocation.on('hardBrake',         function (d) { /* d.value: m/s² (negative) */ });
BackgroundGeolocation.on('rapidAcceleration', function (d) { /* d.value: m/s² (positive) */ });
BackgroundGeolocation.on('sharpTurn',         function (d) { /* d.value: deg/s */ });

// v4.1+ — `source` field added in v4.2 to distinguish GPS heuristic vs accelerometer pipeline.
BackgroundGeolocation.on('possibleCrash', function (d) {
  // d.location, d.value (km/h drop if source==='gps', impact-g if source==='sensor'), d.source
  if (d.source === 'sensor') {
    // Higher confidence — accelerometer measured a real impact during an active trip.
  } else {
    // GPS heuristic — confirm with the user before notifying contacts.
  }
});

// v4.2 sensor fusion — emitted only when drivingEvents.sensorFusion === true.
BackgroundGeolocation.on('phoneUsageWhileDriving', function (location) {
  // Sustained device interaction while a trip is active and the screen is on.
  // Use to power "stop using your phone while driving" UX.
});
```

### v4.2 sensor fusion — when to enable it

`sensorFusion: true` registers `Sensor.TYPE_LINEAR_ACCELERATION` + `Sensor.TYPE_GYROSCOPE` on Android (50 Hz, `SENSOR_DELAY_GAME`) and `CMMotionManager.startDeviceMotionUpdatesToQueue` on iOS (50 Hz). Sampling **only happens while a trip is active** (`tripActive == true`); when the trip ends the pipeline goes idle, so battery cost is bounded by drive time.

Turn it on when your app needs:
- **High-confidence crash detection at low speed** — e.g. a parking-lot collision where GPS speed never crosses the velocity-drop threshold. Pair `crashImpactG` (default 3 g) with the existing GPS heuristic for redundancy.
- **`phoneUsageWhileDriving`** — there is no GPS-only equivalent. Without sensor fusion this event is never emitted.

Leave it off (default) if your app only needs `tripStart`/`tripEnd` plus the GPS-derived events; v4.1 covers those without touching sensors.

Hot-reload: changing `drivingEvents.sensorFusion` via `configure()` while the service is running starts/stops the pipeline without restarting tracking. Current `tripActive` and `lastLocation` are re-injected so a config arriving mid-trip starts in the right mode.

iOS heuristic note: `phoneUsageWhileDriving` requires the app to be **foreground active** (screen on). Background interaction by a passenger is intentionally ignored.

### triggerSOS(payload?, success?, fail?)

Fires a single `sos` JS event carrying the latest known location plus the supplied payload. The host app is responsible for the actual SOS workflow (notify contacts, push notification, alarm UI). This method just guarantees a single emission with the freshest fix the plugin has.

```javascript
BackgroundGeolocation.triggerSOS({
  reason: 'panic_button',
  user: 'USER_DEVICE_123'
});
```

## Battery / OEM helpers (since 3.6.0)

These methods help the app guide the user through the steps required for reliable background tracking on aggressive OEMs (Xiaomi/Huawei/Oppo/Vivo/Samsung). They are most useful in combination with `getDiagnostics()`: when `batteryOptimizationIgnored` is `false` or `manufacturer` matches an OEM that kills foreground services, render an actionable help screen.

### isIgnoringBatteryOptimizations(success, fail)

Android: returns `true` if the app is on the battery-optimisation whitelist. iOS: resolves `true`.

```javascript
BackgroundGeolocation.isIgnoringBatteryOptimizations()
  .then(function (whitelisted) {
    if (!whitelisted) {
      // show banner: "Disable battery optimisation for reliable tracking"
    }
  });
```

### requestIgnoreBatteryOptimizations(success, fail)

Android: opens the system dialog so the user can add the app to the whitelist. The user must accept; the plugin cannot grant this on its own. iOS: no-op.

### openBatterySettings(success, fail)

Android: opens the per-app battery settings (with fallback to app-info). iOS: opens the app's Settings entry.

### openAutoStartSettings(success, fail)

Android: opens the OEM-specific auto-start / background-activity screen. Returns `{ opened, manufacturer, screen }`. Supports Xiaomi (MIUI/Redmi/Poco), Huawei/Honor (EMUI), Oppo (ColorOS), Vivo (FunTouch), OnePlus, Asus. Samsung falls back to app-info because there is no stable component for "Sleeping apps". iOS: opens the app's Settings entry and reports `manufacturer: 'apple'`.

```javascript
BackgroundGeolocation.openAutoStartSettings()
  .then(function (info) {
    console.log('OEM:', info.manufacturer, 'opened:', info.opened);
  });
```

### getManufacturerHelp(success, fail)

Returns `{ manufacturer, steps: string[] }` with OEM-specific guidance text the app can render in a help screen. Covers Xiaomi/Huawei/Oppo/Vivo/Samsung/OnePlus/Asus and a generic Android fallback. iOS returns Apple-specific steps (Always location, Precise Location, Background App Refresh, Low Power Mode).

```javascript
BackgroundGeolocation.getManufacturerHelp()
  .then(function (help) {
    // render help.steps as a bulleted list, prefixed with "On " + help.manufacturer
  });
```

## HTTP transport (since 3.3.0)

The plugin is **backend-agnostic**. There is no `traccarMode`, `osmandMode` or similar — instead you compose `httpMethod`, `httpMode`, URL templating, headers and body templating to match any backend.

### Options

- `httpMethod`: `POST` (default) `| GET | PUT | PATCH`.
- `syncHttpMethod`: `POST` (default) `| PUT | PATCH`. **`GET` is not valid** (coerced to `POST` in `configure()` since 5.0.1, with a log — tracking is not aborted): the sync URL is resolved once for the whole batch, so `{latitude}` & co. cannot be substituted. Use the real-time `url` for GET backends, or put a gateway that accepts a body in front.
- `httpMode`: `single` (default since 5.0.1, = v4 payload shape `{...}`) or `batch` (`[{...}]`). Required to be `single` when `httpMethod` is `GET`.
- `syncMode`: `batch` (default) or `single`. Independent of `httpMode`; with `Content-Type: application/x-www-form-urlencoded` the sync route always goes per location regardless, because an array cannot be flattened to `key=value`.
- `url`, `syncUrl`: support placeholders.
- `headers`: alias of `httpHeaders`.
- `bodyTemplate`: alias of `postTemplate`. Supports both the legacy `@latitude` syntax and the new `{latitude}` placeholder syntax on string values.
- `queryParams`: static map used to fill placeholders that don't come from a location (`{device_id}`, `{token}`, ...).

### Placeholders

Available in `url`, `syncUrl`, and string values inside `bodyTemplate` / `postTemplate`:

`{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}` (ms), `{timestamp}` (ms), `{timestamp_iso}` (ISO 8601 UTC), `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`, `{is_moving}` — plus any key from `queryParams`.

Unknown placeholders are left as-is, so partial templates keep working (e.g. only static keys for batch mode).

### Examples

**REST JSON, body as an array (`httpMode: 'batch'` — no longer the default):**

```javascript
BackgroundGeolocation.configure({
  url: 'https://api.example.com/locations',
  httpMethod: 'POST',
  httpMode: 'batch',
  headers: { 'Authorization': 'Bearer TOKEN', 'Content-Type': 'application/json' },
  bodyTemplate: { lat: '{latitude}', lon: '{longitude}', t: '{time}', acc: '{accuracy}' }
});
```

**GET with URL templating (single):**

```javascript
BackgroundGeolocation.configure({
  url: 'https://api.example.com/track?uid={uid}&lat={latitude}&lon={longitude}&t={timestamp_iso}',
  httpMethod: 'GET',
  httpMode: 'single',
  queryParams: { uid: 'USER_DEVICE_123' }
});
```

**Form-urlencoded:**

```javascript
BackgroundGeolocation.configure({
  url: 'https://legacy.example.com/track.php',
  httpMethod: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  bodyTemplate: { lat: '{latitude}', lng: '{longitude}', t: '{time}' }
});
```

### Backward compatibility

Apps that only set `url` + `httpHeaders` + `postTemplate` continue to work without changes. Defaults are `httpMethod: 'POST'` and `httpMode: 'single'` (i.e. the real-time body is a plain object, exactly as in v4).

For more examples (Firebase, n8n, GraphQL, Traccar) see [http-transport.md](http-transport.md) and [traccar.md](traccar.md).

## Auto-start on Android boot (since 3.3.0)

To restart tracking automatically after the device reboots, set `startOnBoot: true` and request `ACCESS_BACKGROUND_LOCATION` at runtime.

### Required permissions

The plugin already declares these in its manifest:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (Android 10+)
- `POST_NOTIFICATIONS` (Android 13+)
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`

You must request the runtime ones from the app — the plugin only declares them.

### Recommended runtime flow

1. Request `ACCESS_FINE_LOCATION` (foreground).
2. Request `POST_NOTIFICATIONS` (Android 13+).
3. Show an explanation, then request `ACCESS_BACKGROUND_LOCATION`. Android 11+ may redirect to Settings; the user must choose **Allow all the time**.
4. Configure with `startOnBoot: true` and `stopOnTerminate: false`.

```javascript
BackgroundGeolocation.configure({
  startOnBoot: true,
  stopOnTerminate: false,
  notificationsEnabled: true,
  notificationTitle: 'Tracking active',
  notificationText: 'Sending your location'
});
```

The plugin's boot receiver listens to `BOOT_COMPLETED`, `QUICKBOOT_POWERON` (HTC, MIUI), `com.htc.intent.action.QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` (so the service also relaunches after a Play Store update).

If `ACCESS_BACKGROUND_LOCATION` is not granted on Android 10+, the receiver logs a warning and skips the start. iOS does **not** allow auto-start on device boot (Apple restriction); the app must be opened at least once.

For OEM-specific tweaks (Xiaomi/Huawei AutoStart, battery optimization), see [auto-start.md](auto-start.md).
