---
layout: default
nav_order: 2
title: API
---

# API

Note that all methods now return a `Promise` when the `success` and `fail` callbacks are omitted, so you can use `async/await`.

**Quick reference — main methods:** `configure`, `start`, `stop`, `getConfig`, `getCurrentLocation`, `checkStatus`, `getLocations`, `getValidLocations`, `deleteLocation`, `deleteAllLocations`, **`getPendingSyncCount`**, **`forceSync`**, **`clearSync`**, `getPluginVersion`, `showAppSettings`, `openSettings`, `showLocationSettings`, `getLogEntries`, `switchMode` (iOS), `startTask` / `endTask` (iOS), `removeAllListeners`, `on`. For sync queue (`syncUrl`), see [forceSync](#forcesync), [clearSync](#clearsyncsuccess-fail), [getPendingSyncCount](#getpendingsynccountsuccess-fail); for HTTP posting see [HTTP Location Posting](http_posting).

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
