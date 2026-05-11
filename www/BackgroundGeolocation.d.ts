// Type definitions for @josuelmm/cordova-background-geolocation.
// Project: https://github.com/josuelmm/cordova-background-geolocation
// Definitions by: Mauron85 (@mauron85), Norbert Györög (@djereg)
// Definitions: https://github.com/josuelmm/cordova-background-geolocation/blob/master/www/BackgroundGeolocation.d.ts

export type Event = 'location' | 'stationary' | 'activity' | 'start' | 'stop' | 'error' | 'authorization' | 'foreground' | 'background' | 'abort_requested' | 'http_authorization' | 'heartbeat' | 'syncStart' | 'syncProgress' | 'syncSuccess' | 'syncError' | 'tripStart' | 'tripEnd' | 'moving' | 'stopped' | 'speeding' | 'providerChange' | 'sos' | 'hardBrake' | 'rapidAcceleration' | 'sharpTurn' | 'possibleCrash' | 'phoneUsageWhileDriving';

/** Event names enum (compatibility with @awesome-cordova-plugins style). Use e.g. BackgroundGeolocation.on(BackgroundGeolocationEvents.location, cb). */
export enum BackgroundGeolocationEvents {
  http_authorization = 'http_authorization',
  abort_requested = 'abort_requested',
  background = 'background',
  foreground = 'foreground',
  authorization = 'authorization',
  error = 'error',
  stop = 'stop',
  start = 'start',
  activity = 'activity',
  stationary = 'stationary',
  location = 'location',
  // v3.5 Phase 4 — diagnostics & sync events
  heartbeat = 'heartbeat',
  syncStart = 'syncStart',
  syncProgress = 'syncProgress',
  syncSuccess = 'syncSuccess',
  syncError = 'syncError',
  // v4.0 Phase 6 — driver insights
  tripStart = 'tripStart',
  tripEnd = 'tripEnd',
  moving = 'moving',
  stopped = 'stopped',
  speeding = 'speeding',
  providerChange = 'providerChange',
  sos = 'sos',
  // v4.1 — GPS-derived sensor-like events
  hardBrake = 'hardBrake',
  rapidAcceleration = 'rapidAcceleration',
  sharpTurn = 'sharpTurn',
  possibleCrash = 'possibleCrash',
  // v4.2 — sensor fusion
  phoneUsageWhileDriving = 'phoneUsageWhileDriving',
}

type HeadlessTaskEventName = 'location' | 'stationary' | 'activity';
type iOSActivityType = 'AutomotiveNavigation' | 'OtherNavigation' | 'Fitness' | 'Other';
type NativeProvider = 'gps' | 'network' | 'passive' | 'fused';
type ActivityType = 'IN_VEHICLE' | 'ON_BICYCLE' | 'ON_FOOT' | 'RUNNING' | 'STILL' | 'TILTING' | 'UNKNOWN' | 'WALKING';
type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
type LocationProvider = 0 | 1 | 2;
type AuthorizationStatus = 0 | 1 | 2;
type AccuracyLevel = 0 | 100 | 1000 | 10000 | number;
type LocationErrorCode = 1 | 2 | 3;
type ServiceMode = 0 | 1;

export interface Unsubscribable {
  unsubscribe(): void;
}

export interface Subscribable<T> {
  subscribe(callback: (value: T) => any): Unsubscribable;
}

export interface ConfigureOptions {
  /**
   * Set location provider
   *
   * Platform: all
   * Available providers:
   *  DISTANCE_FILTER_PROVIDER,
   *  ACTIVITY_PROVIDER
   *  RAW_PROVIDER
   *
   * @default DISTANCE_FILTER_PROVIDER
   * @example
   * { locationProvider: BackgroundGeolocation.RAW_PROVIDER }
   */
  locationProvider?: LocationProvider;

  /**
   * Desired accuracy in meters.
   *
   * Platform: all
   * Provider: all
   * Possible values:
   *  HIGH_ACCURACY,
   *  MEDIUM_ACCURACY,
   *  LOW_ACCURACY,
   *  PASSIVE_ACCURACY
   * Note: Accuracy has direct effect on power drain. Lower accuracy = lower power drain.
   *
   * @default MEDIUM_ACCURACY
   * @example
   * { desiredAccuracy: BackgroundGeolocation.LOW_ACCURACY }
   */
  desiredAccuracy?: AccuracyLevel;

  /**
   * Stationary radius in meters.
   *
   * When stopped, the minimum distance the device must move beyond the stationary location for aggressive background-tracking to engage.
   * Platform: all
   * Provider: DISTANCE_FILTER
   *
   * @default 50
   */
  stationaryRadius?: number;

  /**
   * When enabled, the plugin will emit sounds for life-cycle events of background-geolocation! See debugging sounds table.
   *
   * Platform: all
   * Provider: all
   *
   * @default false
   */
  debug?: boolean;

  /**
   * The minimum distance (measured in meters) a device must move horizontally before an update event is generated.
   *
   * Platform: all
   * Provider: DISTANCE_FILTER, RAW
   *
   * @default 500
   * @see {@link https://apple.co/2oHo2CV|Apple docs}
   */
  distanceFilter?: number;

  /**
   * Enable this in order to force a stop() when the application terminated.
   * E.g. on iOS, double-tap home button, swipe away the app.
   *
   * Platform: all
   * Provider: all
   *
   * @default true
   */
  stopOnTerminate?: boolean;

  /**
   * Start background service on device boot.
   *
   * Platform: Android
   * Provider: all
   *
   * @default false
   */
  startOnBoot?: boolean;

  /**
   * The minimum time interval between location updates in milliseconds.
   *
   * Platform: Android
   * Provider: all
   *
   * @default 600000
   * @see {@link https://bit.ly/1x00RUu|Android docs}
   */
  interval?: number;

  /**
   * Fastest rate in milliseconds at which your app can handle location updates.
   *
   * Platform: Android
   * Provider: ACTIVITY
   *
   * @default 120000
   * @see {@link https://bit.ly/1x00RUu|Android docs}
   */
  fastestInterval?: number;

  /**
   * Rate in milliseconds at which activity recognition occurs.
   * Larger values will result in fewer activity detections while improving battery life.
   *
   * Platform: Android
   * Provider: ACTIVITY
   *
   * @default 10000
   */
  activitiesInterval?: number;

  /**
   * @deprecated Stop location updates, when the STILL activity is detected.
   */
  stopOnStillActivity?: boolean;

  /**
   * If true, when no location update is received for ~60s the provider is restarted
   * to avoid silent stops on some Android devices.
   *
   * Platform: Android
   * @default false
   */
  enableWatchdog?: boolean;

  /**
   * Enable/disable local notifications when tracking and syncing locations.
   *
   * Platform: Android
   * Provider: all
   *
   * @default true
   */
  notificationsEnabled?: boolean;

  /**
   * Allow location sync service to run in foreground state.
   * Foreground state also requires a notification to be presented to the user.
   *
   * Platform: Android
   * Provider: all
   *
   * @default false
   */
  startForeground?: boolean;

  /**
   * Custom notification title in the drawer.
   *
   * Platform: Android
   * Provider: all

   * @default "Background tracking"
   */
  notificationTitle?: string;

  /**
   * Custom notification text in the drawer.
   *
   * Platform: Android
   * Provider: all
   *
   * @default "ENABLED"
   */
  notificationText?: string;

  /**
   * When true, the foreground notification shows a live elapsed time (HH:mm:ss) since the session started.
   * Requires startForeground. Updates every second.
   *
   * Platform: Android
   * @default false
   */
  showTime?: boolean;

  /**
   * When true, the foreground notification shows accumulated distance (km) since the session started.
   * Requires startForeground. Updates when each new location is received.
   *
   * Platform: Android
   * @default false
   */
  showDistance?: boolean;

  /**
   * Title shown in the notification while locations are syncing to the server.
   * Use this (and notificationSyncText, etc.) to localize sync notifications.
   *
   * Platform: Android
   * @default "Syncing locations"
   */
  notificationSyncTitle?: string;

  /**
   * Text shown in the sync notification while upload is in progress.
   * @default "Sync in progress"
   */
  notificationSyncText?: string;

  /**
   * Text shown when sync completes successfully.
   * @default "Sync completed"
   */
  notificationSyncCompletedText?: string;

  /**
   * Text shown when sync fails (prefix before " (HTTP …)" or ": error").
   * @default "Sync failed"
   */
  notificationSyncFailedText?: string;

  /**
   * The accent color (hex triplet) to use for notification.
   * Eg. <code>#4CAF50</code>.
   *
   * Platform: Android
   * Provider: all
   */
  notificationIconColor?: string;

  /**
   * The filename of a custom notification icon.
   *
   * Platform: Android
   * Provider: all
   */
  notificationIconLarge?: string;

  /**
   * The filename of a custom notification icon.
   *
   * Platform: Android
   * Provider: all
   */
  notificationIconSmall?: string;

  /**
   * Activity type.
   * Presumably, this affects iOS GPS algorithm.
   *
   * Possible values:
   * "AutomotiveNavigation", "OtherNavigation", "Fitness", "Other"
   *
   * Platform: iOS
   * Provider: all
   *
   * @default "OtherNavigation"
   * @see {@link https://apple.co/2oHofpH|Apple docs}
   */
  activityType?: iOSActivityType;

  /**
   * Pauses location updates when app is paused.
   *
   * Platform: iOS
   * Provider: all
   *
   * @default false
   * @see {@link https://apple.co/2CbjEW2|Apple docs}
   */
  pauseLocationUpdates?: boolean;

  /**
   * Switch to less accurate significant changes and region monitory when in background.
   *
   * Platform: iOS
   * Provider: all
   *
   * @default false
   */
  saveBatteryOnBackground?: boolean;

  /**
   * Server url where to send HTTP POST with recorded locations
   *
   * Platform: all
   * Provider: all
   */
  url?: string;

  /**
   * Server url where to send fail to post locations
   *
   * Platform: all
   * Provider: all
   */
  syncUrl?: string;

  /**
   * Specifies how many previously failed locations will be sent to server at once.
   *
   * Platform: all
   * Provider: all
   *
   * @default 100
   */
  syncThreshold?: number;

  /**
   * Whether synchronization to syncUrl is enabled (automatic and forceSync).
   * When false, no sync runs; locations are still stored and can be synced later by setting sync: true.
   *
   * Platform: Android, iOS
   * @default true
   */
  sync?: boolean;

  /**
   * Optional HTTP headers sent along in HTTP request.
   *
   * Platform: all
   * Provider: all
   */
  httpHeaders?: any;

  /**
   * Alias of `httpHeaders` introduced for the v3.3 backend-agnostic transport.
   * If both are provided, `headers` takes precedence.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  headers?: { [key: string]: string };

  /**
   * HTTP method used to post each location to `url`. Default `POST`.
   * Use `GET` together with URL templating for backends that expect query-string transport.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  httpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';

  /**
   * HTTP method used by the sync queue when posting failed locations to `syncUrl`. Default `POST`.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  syncHttpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';

  /**
   * How real-time locations are delivered to `url`.
   *  - `batch` (default): one HTTP request with an array of locations.
   *  - `single`: one HTTP request per location.
   * `single` is required when `httpMethod` is `GET` (no body).
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  httpMode?: 'batch' | 'single';

  /**
   * How sync-queue locations are delivered to `syncUrl`. Default `batch`.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  syncMode?: 'batch' | 'single';

  /**
   * Static placeholder values used by URL/body templating. The plugin replaces
   * `{key}` occurrences in `url`, `syncUrl` and string values inside `bodyTemplate`/`postTemplate`.
   *
   * Built-in placeholders resolved from each location:
   * `{latitude}`, `{longitude}`, `{lat}`, `{lon}`, `{time}`, `{timestamp}`, `{timestamp_iso}`,
   * `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`.
   * Any extra keys in `queryParams` are also available (e.g. `{device_id}`, `{token}`).
   *
   * Placeholders not found are left as-is so partial templates keep working.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  queryParams?: { [key: string]: string | number };

  /**
   * Limit maximum number of locations stored into db.
   *
   * Platform: all
   * Provider: all
   *
   * @default 10000
   */
  maxLocations?: number;

  /**
   * Customization post template.
   *
   * Platform: all
   * Provider: all
   */
  postTemplate?: any;

  /**
   * Alias of `postTemplate` introduced for the v3.3 backend-agnostic transport.
   * If both are provided, `bodyTemplate` takes precedence.
   *
   * Platform: Android, iOS
   * @since 3.3.0
   */
  bodyTemplate?: any;

  /**
   * iOS 11+ only. When `true`, iOS shows the blue status bar / pill while the app uses
   * location in the background. Apple recommends `true` for user transparency in apps that
   * track continuously. Default: system default (`false`).
   *
   * Platform: iOS
   * @since 3.4.0
   */
  showsBackgroundLocationIndicator?: boolean;

  /**
   * Interval in milliseconds at which the plugin emits a `heartbeat` event with the latest
   * known location. Useful to confirm the service is alive without waiting for a fresh fix.
   * `0` (default) disables the heartbeat.
   *
   * Native emission is implemented end-to-end (Android `ScheduledExecutorService`, iOS `NSTimer`).
   * On the first ticks before any GPS fix is received the event arrives with no location payload.
   *
   * Platform: Android, iOS
   * @since 3.5.0
   */
  heartbeatInterval?: number;

  /**
   * Policy applied to locations flagged as mocked (Android `isFromMockProvider` /
   * iOS `simulated`). Detection has always existed; the policy controls what to do with
   * those samples:
   *  - `'allow'` (default): keep them as regular locations.
   *  - `'flag'`: deliver them but tag with `mocked: true` so the app/server can filter.
   *  - `'drop'`: discard them silently before persisting / posting.
   *
   * Recommended for Traccar/anti-fraud: `'flag'` — keeps history, lets the server decide.
   *
   * Platform: Android, iOS
   * @since 3.5.0
   */
  mockLocationPolicy?: 'allow' | 'flag' | 'drop';

  /**
   * v4.4 — Stamp device battery percentage (0-100) and charging state on every location
   * sent to the backend. Default `true`. Set `false` to opt out.
   *
   * - With `bodyTemplate`/`postTemplate`, use placeholders `'@battery'` and `'@isCharging'`.
   * - Default JSON includes `battery` and `isCharging` keys automatically.
   *
   * Android: read via `BatteryManager` sticky broadcast (no permission required).
   * iOS: read via `UIDevice.batteryLevel` (free).
   *
   * Platform: Android, iOS
   * @since 4.4.0
   */
  includeBattery?: boolean;

  /**
   * v4.5.1 — WakeLock policy (Android only). Controls whether the service holds a
   * `PARTIAL_WAKE_LOCK` while tracking. Default `'posting'`.
   *
   * - `'none'`     — never acquire a wake lock. Best battery, but the device may
   *                  sleep mid-POST. Recommended only with `httpMode: 'batch'`.
   * - `'posting'`  — acquire a 30 s wake lock on every fix while writing to SQLite
   *                  and posting. Default. Good battery / reliability trade-off.
   * - `'always'`   — keep CPU awake the whole time the service is running. Most
   *                  reliable, worst battery. Use only for fleet/emergency apps.
   *
   * @platform Android
   * @since 4.5.1
   */
  wakeLockMode?: 'none' | 'posting' | 'always';

  /**
   * v4.5.1 — Stationary detection knobs (Android `DISTANCE_FILTER_PROVIDER`).
   * Override the previously hard-coded constants. All values in milliseconds.
   *
   * - `stationaryTimeout` (default 300_000) — time of no movement before declaring stationary.
   * - `stationaryPollInterval` (default 180_000) — lazy poll while stationary.
   * - `stationaryPollFast` (default 60_000) — aggressive poll near boundary.
   *
   * @platform Android
   * @since 4.5.1
   */
  stationaryTimeout?: number;
  stationaryPollInterval?: number;
  stationaryPollFast?: number;

  /**
   * v4.0 Phase 6 — Driver insights configuration. Enables a GPS-based state machine
   * that emits `moving`, `stopped`, `tripStart`, `tripEnd`, `speeding` and
   * `providerChange` events without additional sensors.
   *
   * v4.1 adds GPS-derived hardBrake, rapidAcceleration, sharpTurn and possibleCrash
   * (no sensors required). v4.2 adds real sensor fusion (`sensorFusion: true`)
   * to refine `possibleCrash` at low speed via accelerometer impact and to detect
   * `phoneUsageWhileDriving`.
   *
   * Platform: Android, iOS
   * @since 4.0.0
   */
  drivingEvents?: {
    /** Master switch. When `false` (default) no driver-insight events are emitted. */
    enabled?: boolean;
    /** Speed limit in km/h for the `speeding` event. `0` disables. Default 0. */
    speedLimit?: number;
    /** m/s threshold below which the user is considered stopped. Default 1.0. */
    minMovingSpeed?: number;
    /** ms of continuous below-threshold speed needed to confirm "stopped". Default 60000. */
    stoppedDuration?: number;
    /** m/s threshold to start counting a trip. Default 3.0 (~10.8 km/h). */
    minTripSpeed?: number;
    /** ms of continuous above-threshold speed needed to confirm `tripStart`. Default 30000. */
    minTripDuration?: number;

    // v4.1 — GPS-derived sensor-like driving events. Set 0 to disable each one.
    /** Deceleration threshold (m/s², positive value). Triggers `hardBrake` when |Δspeed/Δt| ≥ this during an active trip. Default 3.5. */
    hardBrakeMps2?: number;
    /** Acceleration threshold (m/s²) for `rapidAcceleration` during a trip. Default 3.5. */
    rapidAccelMps2?: number;
    /** Bearing change rate (deg/s) for `sharpTurn`. Requires speed ≥ 5 m/s. Default 30. */
    sharpTurnDegPerSec?: number;
    /** Velocity drop in km/h within `crashWindowMs` while tripActive — triggers `possibleCrash`. Default 25. */
    crashImpactKmh?: number;
    /** Window in ms used to evaluate the crash impact. Default 2000. */
    crashWindowMs?: number;

    // v4.2 — Real sensor fusion (accelerometer + gyroscope).
    /** Enable real sensor fusion. When `true` and `enabled` is `true`, the plugin samples
     * linear acceleration + gyroscope while a trip is active and emits high-confidence
     * `possibleCrash` and `phoneUsageWhileDriving`. Adds modest battery cost. Default false. */
    sensorFusion?: boolean;
    /** Crash impact threshold in g (1g = 9.81 m/s²). Triggers `possibleCrash` from the sensor pipeline. Default 3.0. */
    crashImpactG?: number;
    /** Cooldown ms between successive sensor-driven crash detections. Default 10000. */
    sensorCrashCooldownMs?: number;
    /** Sustained device-jitter window (ms) needed to fire `phoneUsageWhileDriving`. Default 4000. */
    phoneUsageWindowMs?: number;
    /** Cooldown ms between successive `phoneUsageWhileDriving` events. Default 60000. */
    phoneUsageCooldownMs?: number;
  };
}

export interface LocationOptions {
  /**
   * Maximum time in milliseconds device will wait for location.
   */
  timeout?: number;

  /**
   * Maximum age in milliseconds of a possible cached location that is acceptable to return.
   */
  maximumAge?: number;

  /**
   * If true and if the device is able to provide a more accurate position, it will do so.
   */
  enableHighAccuracy?: boolean;
}

export interface Location {
  /** ID of location as stored in DB (or null) */
  id: number;

  /**
   * Native provider reponsible for location.
   *
   * Possible values:
   * "gps", "network", "passive" or "fused"
   */
  provider: NativeProvider;

  /** Configured location provider. */
  locationProvider: number;

  /** UTC time of this fix, in milliseconds since January 1, 1970. */
  time: number;

  /** Latitude, in degrees. */
  latitude: number;

  /** Longitude, in degrees. */
  longitude: number;

  /** Estimated accuracy of this location, in meters. */
  accuracy: number;

  /**
   * Speed if it is available, in meters/second over ground.
   *
   * Note: Not all providers are capable of providing speed.
   * Typically network providers are not able to do so.
   */
  speed: number;

  /** Altitude if available, in meters above the WGS 84 reference ellipsoid. */
  altitude: number;

  /** Bearing, in degrees. */
  bearing: number;

  /**
   * True if location was recorded by mock provider. (ANDROID ONLY)
   *
   * Note: this property is not enabled by default!
   * You can enable it "postTemplate" configure option.
   */
  isFromMockProvider?: boolean;

  /**
   * True if device has mock locations enabled. (ANDROID ONLY)
   *
   * Note: this property is not enabled by default!
   * You can enable it "postTemplate" configure option.
   */
  mockLocationsEnabled?: boolean;

  /**
   * True if location was simulated by software (e.g. Simulator). (iOS 15+)
   */
  simulated?: boolean;

  /**
   * v4.3 — Driving events anexados a este fix por el detector interno.
   *
   * Solo presente cuando un evento se disparó al mismo tiempo que esta location y
   * `drivingEvents.enabled` está activo. Cada elemento es `{ type, time, ...payload }`,
   * donde `payload` depende del tipo:
   *   - hardBrake / rapidAcceleration / sharpTurn → `value: number`
   *   - speeding → `speedKmh: number, limitKmh: number`
   *   - tripEnd → `distance: number, durationMs: number`
   *   - possibleCrash → `value: number, source: 'gps' | 'sensor'`
   *   - providerChange → `provider: string`
   *   - moving / stopped / tripStart / phoneUsageWhileDriving → solo type+time.
   *
   * Desde v4.5.0, `events` se persiste en la cola de sync y sobrevive a POST fallidos.
   *
   * @since 4.3.0
   */
  events?: Array<{ type: string; time: number; [key: string]: any }>;

  /** v4.4 — Device battery percentage (0-100) at the time of the fix. Disabled with
   *  `includeBattery: false` in ConfigureOptions. @since 4.4.0 */
  battery?: number;
  /** v4.4 — Whether the device is charging at the time of the fix. @since 4.4.0 */
  isCharging?: boolean;
}

export interface StationaryLocation extends Location {
  radius: number
}

export interface LocationError {
  /**
   * Reason of an error occurring when using the geolocating device.
   *
   * Possible error codes:
   *  1. PERMISSION_DENIED
   *  2. LOCATION_UNAVAILABLE
   *  3. TIMEOUT
   */
  code: LocationErrorCode;

  /** Message describing the details of the error */
  message: string;
}

export interface BackgroundGeolocationError {
  code: number;
  message: string;
}

export interface Activity {
  /** Percentage indicating the likelihood user is performing this activity. */
  confidence: number;

  /**
   * Type of the activity.
   *
   * Possible values:
   * IN_VEHICLE, ON_BICYCLE, ON_FOOT, RUNNING, STILL, TILTING, UNKNOWN, WALKING
   */
  type: ActivityType;
}

export interface ServiceStatus {
  /** TRUE if service is running. */
  isRunning: boolean;

  /** TRUE if location services are enabled */
  locationServicesEnabled: boolean;

  /**
   * Authorization status.
   *
   * Posible values:
   *  NOT_AUTHORIZED, AUTHORIZED, AUTHORIZED_FOREGROUND
   *
   * @example
   * if (authorization == BackgroundGeolocation.NOT_AUTHORIZED) {...}
   */
  authorization: AuthorizationStatus;
}

/**
 * Extended diagnostics returned by `getDiagnostics()`.
 * Helps explain why tracking may not be running in production.
 *
 * @since 3.5.0
 */
export interface Diagnostics {
  // ---- common ----
  /** TRUE if the native service is currently running. */
  isRunning: boolean;
  /** TRUE if the OS-level location services are enabled. */
  locationServicesEnabled: boolean;
  /** Configured `startOnBoot` flag. */
  startOnBoot?: boolean;
  /** Number of locations queued for sync (`getPendingSyncCount`). */
  pendingSyncCount?: number;
  /** UTC ms of the last received location, or `null` if none yet. */
  lastLocationAt?: number | null;

  // ---- Android ----
  /** Android: TRUE if `ACCESS_FINE_LOCATION` is granted. */
  fineLocationGranted?: boolean;
  /** Android: TRUE if `ACCESS_COARSE_LOCATION` is granted. */
  coarseLocationGranted?: boolean;
  /** Android 10+: TRUE if `ACCESS_BACKGROUND_LOCATION` is granted. Always `true` on Android <10. */
  backgroundLocationGranted?: boolean;
  /** Android 13+: TRUE if `POST_NOTIFICATIONS` is granted. Always `true` on Android <13. */
  notificationPermissionGranted?: boolean;
  /** Android 10+: TRUE if `ACTIVITY_RECOGNITION` is granted. Always `true` on Android <10. */
  activityRecognitionGranted?: boolean;
  /** Android: TRUE if the app is on the battery optimisation whitelist. */
  batteryOptimizationIgnored?: boolean;
  /** Android: device manufacturer (`Build.MANUFACTURER`). Helps detect aggressive OEMs. */
  manufacturer?: string;
  /** Android: declared `foregroundServiceType` of the location service (numeric, e.g. 8 = LOCATION). */
  foregroundServiceType?: number;

  // ---- iOS ----
  /** iOS 14+: TRUE if the user granted Precise Location ("on" in Settings). */
  preciseLocationEnabled?: boolean;
  /**
   * iOS: status of system-wide Background App Refresh.
   * One of `available | denied | restricted`.
   */
  backgroundRefreshStatus?: 'available' | 'denied' | 'restricted';
  /** iOS: TRUE if Low Power Mode is currently enabled (system-wide). */
  lowPowerModeEnabled?: boolean;
  /** iOS: status of the Motion & Fitness permission. */
  motionPermissionStatus?: 'authorized' | 'denied' | 'restricted' | 'notDetermined';
  /** iOS: human-readable label of the current `CLAuthorizationStatus`. */
  authorizationStatusText?: string;
}

export interface LogEntry {
  /** ID of log entry as stored in db. */
  id: number;

  /** Timestamp in milliseconds since beginning of UNIX epoch. */
  timestamp: number;

  /** Log level */
  level: LogLevel;

  /** Log message */
  message: string;

  /** Recorded stacktrace. (Android only, on iOS part of message) */
  stackTrace: string;
}

export interface EventSubscription {
  remove(): void;
}

export interface HeadlessTaskEvent {
  /** Name of the event [ "location", "stationary", "activity" ] */
  name: HeadlessTaskEventName;

  /** Event parameters. */
  params: any;
}

export interface BackgroundGeolocationPlugin {

  DISTANCE_FILTER_PROVIDER: LocationProvider;
  ACTIVITY_PROVIDER: LocationProvider;
  RAW_PROVIDER: LocationProvider;

  BACKGROUND_MODE: ServiceMode;
  FOREGROUND_MODE: ServiceMode;

  NOT_AUTHORIZED: AuthorizationStatus;
  AUTHORIZED: AuthorizationStatus;
  AUTHORIZED_FOREGROUND: AuthorizationStatus;

  HIGH_ACCURACY: AccuracyLevel;
  MEDIUM_ACCURACY: AccuracyLevel;
  LOW_ACCURACY: AccuracyLevel;
  PASSIVE_ACCURACY: AccuracyLevel;

  LOG_ERROR: LogLevel;
  LOG_WARN: LogLevel;
  LOG_INFO: LogLevel;
  LOG_DEBUG: LogLevel;
  LOG_TRACE: LogLevel;

  PERMISSION_DENIED: LocationErrorCode;
  LOCATION_UNAVAILABLE: LocationErrorCode;
  TIMEOUT: LocationErrorCode;

  events: Event[];

  /**
   * Configure plugin.
   * Platform: iOS, Android
   *
   * @param options
   * @param success
   * @param fail
   */
  configure(
    options: ConfigureOptions,
    success?: () => void,
    fail?: () => void
  ): Promise<void>;

  /**
   * Start background geolocation.
   * Platform: iOS, Android
   */
  start(): Promise<void>;

  /**
   * Stop background geolocation.
   * Platform: iOS, Android
   */
  stop(): Promise<void>;

  /**
   * One time location check to get current location of the device.
   *
   * Platform: all
   *
   * @param success
   * @param fail
   * @param options
   */
  getCurrentLocation(
    success?: (location: Location) => void,
    fail?: (error: LocationError) => void | null,
    options?: LocationOptions
  ): Promise<Location>;

  /**
   * Returns current stationaryLocation if available. Null if not
   *
   * Platform: all
   *
   * @param success
   * @param fail
   */
  getStationaryLocation(
    success?: (location: StationaryLocation | null) => void,
    fail?: (error: BackgroundGeolocationError) => void,
  ): Promise<StationaryLocation>;

  /**
   * Check status of the service
   *
   * @param success
   * @param fail
   */
  checkStatus(
    success?: (status: ServiceStatus) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<ServiceStatus>;

  /**
   * Extended diagnostics. Returns permissions, battery optimisation state,
   * last fix age, pending sync count, OEM info and (on iOS) precise location /
   * background refresh / low power flags.
   *
   * Use this to diagnose "tracking is not running" issues in production:
   * a missing `ACCESS_BACKGROUND_LOCATION` on Android 10+, an OEM that killed
   * the foreground service, or `preciseLocationEnabled: false` on iOS will all
   * surface here.
   *
   * Platform: Android, iOS
   * @since 3.5.0
   */
  getDiagnostics(
    success?: (diagnostics: Diagnostics) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<Diagnostics>;

  /**
   * Android: returns `true` if the app is on the battery optimisation whitelist
   * (Settings → Battery → "Don't optimize"). On iOS resolves to `true` (concept
   * does not apply; iOS already restricts background activity by other means).
   *
   * @since 3.6.0
   */
  isIgnoringBatteryOptimizations(
    success?: (whitelisted: boolean) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<boolean>;

  /**
   * Android: opens the system dialog to add the app to the battery optimisation
   * whitelist. The user must accept; the app cannot grant this on its own.
   * Resolves with the up-to-date whitelist state. iOS: resolves `true`.
   *
   * @since 3.6.0
   */
  requestIgnoreBatteryOptimizations(
    success?: (whitelisted: boolean) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<boolean>;

  /**
   * Android: opens the battery-related settings screen for this app. iOS: no-op.
   *
   * @since 3.6.0
   */
  openBatterySettings(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Android only: opens the OEM-specific "auto-start" / "background activity"
   * settings screen on Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch,
   * Samsung One UI. Falls back to the standard app-info screen when the OEM
   * is unknown. Resolves with `{ opened: boolean, manufacturer: string, screen: string }`.
   * iOS: resolves `{ opened: false, manufacturer: 'apple', screen: '' }`.
   *
   * @since 3.6.0
   */
  openAutoStartSettings(
    success?: (info: { opened: boolean; manufacturer: string; screen: string }) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<{ opened: boolean; manufacturer: string; screen: string }>;

  /**
   * Returns OEM-specific guidance for the user. The `steps` array contains
   * short instructions like "Settings → Apps → [your app] → Battery → Sin restricciones".
   * Use to render an actionable help screen when `getDiagnostics()` shows the
   * service is being killed by the OEM.
   *
   * @since 3.6.0
   */
  getManufacturerHelp(
    success?: (info: { manufacturer: string; steps: string[] }) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<{ manufacturer: string; steps: string[] }>;

  /**
   * v4.0 Phase 6 — Trigger an SOS event from JS. The plugin emits an `sos` JS event
   * with the latest known location plus the provided payload (anything serialisable).
   * The host app is responsible for the actual SOS workflow (notify contacts, push,
   * alarm UI). This method just guarantees a single emission carrying the most recent
   * fix the plugin knows about.
   *
   * @since 4.0.0
   */
  triggerSOS(
    payload?: { [key: string]: any },
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * v4.5 — Request `ACCESS_BACKGROUND_LOCATION` runtime permission (Android 10+).
   * On Android < 10 resolves immediately as `{ granted: true, notRequired: true }`.
   * On iOS resolves as `{ granted: true, notRequired: true }` (Apple does not surface
   * a separate background permission — the standard "Always" authorization covers it).
   * @since 4.5.0
   */
  requestBackgroundLocationPermission(
    success?: (result: { granted: boolean; denied?: string[]; notRequired?: boolean }) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<{ granted: boolean; denied?: string[]; notRequired?: boolean }>;

  /**
   * v4.5 — Request `ACTIVITY_RECOGNITION` runtime permission (Android 10+).
   * Required by `ActivityLocationProvider`. iOS / Android < 10 resolve `granted: true, notRequired`.
   * @since 4.5.0
   */
  requestActivityRecognitionPermission(
    success?: (result: { granted: boolean; denied?: string[]; notRequired?: boolean }) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<{ granted: boolean; denied?: string[]; notRequired?: boolean }>;

  /**
   * v4.5 — Request `POST_NOTIFICATIONS` runtime permission (Android 13+).
   * Without it the foreground-service notification is invisible. iOS / Android < 13
   * resolve `granted: true, notRequired`.
   * @since 4.5.0
   */
  requestNotificationPermission(
    success?: (result: { granted: boolean; denied?: string[]; notRequired?: boolean }) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<{ granted: boolean; denied?: string[]; notRequired?: boolean }>;

  /**
   * Show app settings to allow change of app location permissions.
   *
   * Platform: Android >= 6, iOS >= 8.0
   */
  showAppSettings(): Promise<void>;

  /**
   * Open app settings (convenience alias for showAppSettings).
   *
   * Platform: Android, iOS
   */
  openSettings(): Promise<void>;

  /**
   * Show system settings to allow configuration of current location sources.
   *
   * Platform: Android
   */
  showLocationSettings(): Promise<void>;

  /**
   * Get the plugin version from native code.
   *
   * Platform: Android, iOS
   */
  getPluginVersion(
    success?: (version: string) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<string>;

  /**
   * Return all stored locations.
   * Useful for initial rendering of user location on a map just after application launch.
   *
   * Platform: iOS, Android
   *
   * @param success
   * @param fail
   */
  getLocations(
    success?: (locations: Location[]) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<Location[]>;

  /**
   * Method will return locations which have not yet been posted to server.
   * Platform: iOS, Android
   * @param success
   * @param fail
   */
  getValidLocations(
    success?: (location: Location[]) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<Location[]>;

    /**
   * Method will return locations which have not yet been posted to server and delete to avoid getting them again.
   * Platform: iOS, Android
   * @param success
   * @param fail
   */
  getValidLocationsAndDelete(
    success?: (location: Location[]) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<Location[]>;

  /**
   * Delete location by locationId.
   *
   * Platform: iOS, Android
   *
   * @param locationId
   * @param success
   * @param fail
   */
  deleteLocation(
    locationId: number,
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Delete all stored locations.
   *
   * Platform: iOS, Android
   *
   * Note: You don't need to delete all locations.
   * The plugin manages the number of stored locations automatically and the total count never exceeds the number as defined by <code>option.maxLocations</code>.
   *
   * @param success
   * @param fail
   */
  deleteAllLocations(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Switch plugin operation mode,
   *
   * Platform: iOS
   *
   * Normally the plugin will handle switching between <b>BACKGROUND</b> and <b>FOREGROUND</b> mode itself.
   * Calling <code>switchMode</code> you can override plugin behavior and force it to switch into other mode.
   *
   * @example
   * // switch to FOREGROUND mode
   * BackgroundGeolocation.switchMode(BackgroundGeolocation.FOREGROUND_MODE);
   *
   * // switch to BACKGROUND mode
   * BackgroundGeolocation.switchMode(BackgroundGeolocation.BACKGROUND_MODE);
   */
  switchMode(
    modeId: ServiceMode,
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Force sync of pending locations.
   * Option <code>syncThreshold</code> will be ignored and all pending locations will be immediately posted to <code>syncUrl</code> in single batch.
   * No-op if <code>sync</code> is false in config.
   *
   * Platform: Android, iOS
   *
   * @param success
   * @param fail
   */
  forceSync(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Clear the pending sync queue: discard all locations waiting to be sent to syncUrl.
   * They will not be synced. Use when the user wants to discard pending locations.
   *
   * Platform: Android, iOS
   *
   * @param success
   * @param fail
   */
  clearSync(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Get the number of locations pending to be synced (not yet sent to syncUrl).
   * Use with forceSync() to sync on demand.
   *
   * Platform: Android, iOS
   *
   * @param success Called with the pending count (number).
   * @param fail
   */
  getPendingSyncCount(
    success?: (count: number) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<number>;

  /**
   * Start recording session: clear session table and store all new locations in it.
   * Call when user starts a route. Session locations are independent of sync (not cleared when sync succeeds).
   *
   * Platform: Android, iOS
   */
  startSession(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Return all locations stored in the current session (ordered by time).
   * Same format as Location (latitude, longitude, time, speed, altitude, bearing, accuracy).
   *
   * Platform: Android, iOS
   */
  getSessionLocations(
    success?: (locations: Location[]) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<Location[]>;

  /**
   * Clear the session table and stop storing. Call when route is finished and sync OK.
   *
   * Platform: Android, iOS
   */
  clearSession(
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * Get the number of locations in the current session.
   *
   * Platform: Android, iOS
   */
  getSessionLocationsCount(
    success?: (count: number) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<number>;

  /**
   * Get stored configuration options.
   *
   * @param success
   * @param fail
   */
  getConfig(
    success?: (options: ConfigureOptions) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<ConfigureOptions>;

  /**
   * Return all logged events. Useful for plugin debugging.
   *
   * Platform: Android, iOS
   *
   * @param limit Limits number of returned entries.
   * @param fromId Return entries after <code>fromId</code>. Useful if you plan to implement infinite log scrolling
   * @param minLevel Available levels: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
   * @param success
   * @param fail
   */
  getLogEntries(
    limit: number,
    fromId: number,
    minLevel: LogLevel,
    success?: (entries: LogEntry[]) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<LogEntry[]>;

  /**
   * Unregister all event listeners for given event.
   *
   * If parameter <code>event</code> is not provided then all event listeners will be removed.
   *
   * @param event
   */
  removeAllListeners(event?: Event): void;


  /**
   * Start background task (iOS only)
   *
   * To perform any long running operation on iOS
   * you need to create background task
   * IMPORTANT: task has to be ended by endTask
   *
   * @param success
   * @param fail
   */
  startTask(
    success?: (taskKey: number) => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<number>;

  /**
   * End background task indentified by taskKey (iOS only)
   *
   * @param taskKey
   * @param success
   * @param fail
   */
  endTask(
    taskKey: number,
    success?: () => void,
    fail?: (error: BackgroundGeolocationError) => void
  ): Promise<void>;

  /**
   * **Android only.** A special task that gets executed when the app is terminated,
   * but the plugin was configured to continue running in the background
   * (option <code>stopOnTerminate: false</code>).
   *
   * In this scenario the Activity was killed by the system and all registered
   * event listeners will not be triggered until the app is relaunched.
   *
   * **iOS:** Apple does not support running JS in a killed-app scenario the same
   * way; on iOS this method is a no-op. Use `significantLocationChanges` and the
   * normal `BackgroundGeolocation.on(...)` listeners with the standard background
   * location mode instead.
   *
   * Platform: Android
   *
   * @example
   *  BackgroundGeolocation.headlessTask(function(event) {
   *
   *      if (event.name === 'location' || event.name === 'stationary') {
   *          var xhr = new XMLHttpRequest();
   *          xhr.open('POST', 'http://192.168.81.14:3000/headless');
   *          xhr.setRequestHeader('Content-Type', 'application/json');
   *          xhr.send(JSON.stringify(event.params));
   *      }
   *
   *      return 'Processing event: ' + event.name; // will be logged
   *  });
   */
  headlessTask(
    task: (event: HeadlessTaskEvent) => void
  ): void;

  /**
   * Register location event listener.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'location',
    callback?: (location: Location) => void
  ): Subscribable<Location>;

  /**
   * Register stationary location event listener.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'stationary',
    callback?: (location: StationaryLocation) => void
  ): Subscribable<StationaryLocation>;

  /**
   * Register activity monitoring listener.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'activity',
    callback?: (activity: Activity) => void
  ): Subscribable<Activity>;

  /**
   * Register start event listener.
   *
   * Event is triggered when background service has been started succesfully.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'start',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register stop event listener.
   *
   * Triggered when background service has been stopped succesfully.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'stop',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register error listener.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'error',
    callback?: (error: BackgroundGeolocationError) => void
  ): Subscribable<BackgroundGeolocationError>;

  /**
   * Register authorization listener.
   *
   * Triggered when user changes authorization/permissions for
   * the app or toggles location services.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'authorization',
    callback?: (status: AuthorizationStatus) => void
  ): Subscribable<AuthorizationStatus>;

  /**
   * Register foreground event listener.
   *
   * Triggered when app entered foreground state and (visible to the user).
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'foreground',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register background event listener.
   *
   * Triggered when app entered background state and (not visible to the user).
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'background',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register abort_requested event listener.
   *
   * Triggered when server responded with "<code>285 Updates Not Required</code>" to post/sync request.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'abort_requested',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register http_authorization event listener.
   *
   * Triggered when server responded with "<code>401 Unauthorized</code>" to post/sync request.
   *
   * @param eventName
   * @param callback
   */
  on(
    eventName: 'http_authorization',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register heartbeat listener.
   *
   * Triggered every `heartbeatInterval` ms while the service is running.
   * The `location` argument is the latest known fix; it may be `undefined` on
   * the very first ticks if no GPS fix has been received yet.
   *
   * @since 3.5.0
   */
  on(
    eventName: 'heartbeat',
    callback?: (location?: Location) => void
  ): Subscribable<Location | void>;

  /**
   * Register sync-start listener.
   *
   * Triggered when a batch upload to `syncUrl` begins.
   *
   * @since 3.5.0
   */
  on(
    eventName: 'syncStart',
    callback?: () => void
  ): Subscribable<void>;

  /**
   * Register sync-progress listener.
   *
   * Triggered with a 0..100 percentage while a sync upload is in flight.
   *
   * @since 3.5.0
   */
  on(
    eventName: 'syncProgress',
    callback?: (progress: number) => void
  ): Subscribable<number>;

  /**
   * Register sync-success listener.
   *
   * Payload: `{ sent: number }` — locations included in the successful upload.
   *
   * @since 3.5.0
   */
  on(
    eventName: 'syncSuccess',
    callback?: (data: { sent: number }) => void
  ): Subscribable<{ sent: number }>;

  /**
   * Register sync-error listener.
   *
   * Payload: `{ httpStatus: number; message: string }` — non-2xx response or IO/network failure.
   *
   * @since 3.5.0
   */
  on(
    eventName: 'syncError',
    callback?: (data: { httpStatus: number; message: string }) => void
  ): Subscribable<{ httpStatus: number; message: string }>;

  /**
   * v4.0 Phase 6 — Trip starts (state goes from "stopped" to "moving" with sustained
   * speed >= `drivingEvents.minTripSpeed` for `minTripDuration`).
   * @since 4.0.0
   */
  on(
    eventName: 'tripStart',
    callback?: (location: Location) => void
  ): Subscribable<Location>;

  /**
   * v4.0 Phase 6 — Trip ends (sustained speed near zero for `drivingEvents.stoppedDuration`).
   * Payload includes basic trip stats: distance (m) and duration (ms).
   * @since 4.0.0
   */
  on(
    eventName: 'tripEnd',
    callback?: (data: { location: Location; distance: number; durationMs: number }) => void
  ): Subscribable<{ location: Location; distance: number; durationMs: number }>;

  /**
   * v4.0 Phase 6 — User started moving (speed crossed above `minMovingSpeed`).
   * @since 4.0.0
   */
  on(
    eventName: 'moving',
    callback?: (location: Location) => void
  ): Subscribable<Location>;

  /**
   * v4.0 Phase 6 — User stopped (speed below threshold for `stoppedDuration`).
   * @since 4.0.0
   */
  on(
    eventName: 'stopped',
    callback?: (location: Location) => void
  ): Subscribable<Location>;

  /**
   * v4.0 Phase 6 — Speed crossed above `drivingEvents.speedLimit` (km/h).
   * Fires once when crossing; further fixes above the limit do not refire until the
   * speed drops back below the limit and crosses again.
   * @since 4.0.0
   */
  on(
    eventName: 'speeding',
    callback?: (data: { location: Location; speedKmh: number; limitKmh: number }) => void
  ): Subscribable<{ location: Location; speedKmh: number; limitKmh: number }>;

  /**
   * v4.0 Phase 6 — Native location provider changed (GPS ↔ Network ↔ Fused).
   * Useful to react to GPS being turned off or losing signal indoors.
   * @since 4.0.0
   */
  on(
    eventName: 'providerChange',
    callback?: (data: { provider: string }) => void
  ): Subscribable<{ provider: string }>;

  /**
   * v4.0 Phase 6 — `triggerSOS()` was invoked. Payload is the user-supplied object
   * plus the latest known `location` (may be `undefined` if no fix yet).
   * @since 4.0.0
   */
  on(
    eventName: 'sos',
    callback?: (data: { location?: Location; [key: string]: any }) => void
  ): Subscribable<{ location?: Location; [key: string]: any }>;

  /**
   * v4.1 — GPS-derived hard brake. Payload `{ location, value }` where `value` is the
   * computed deceleration in m/s² (negative number, more negative = harder brake).
   * @since 4.1.0
   */
  on(
    eventName: 'hardBrake',
    callback?: (data: { location: Location; value: number }) => void
  ): Subscribable<{ location: Location; value: number }>;

  /**
   * v4.1 — GPS-derived rapid acceleration (m/s²). Positive value.
   * @since 4.1.0
   */
  on(
    eventName: 'rapidAcceleration',
    callback?: (data: { location: Location; value: number }) => void
  ): Subscribable<{ location: Location; value: number }>;

  /**
   * v4.1 — GPS-derived sharp turn. `value` is the bearing-change rate in deg/s.
   * Only fires when speed ≥ 5 m/s to avoid GPS jitter at low speeds.
   * @since 4.1.0
   */
  on(
    eventName: 'sharpTurn',
    callback?: (data: { location: Location; value: number }) => void
  ): Subscribable<{ location: Location; value: number }>;

  /**
   * v4.1+ — Heuristic possible-crash detection. `value` is the velocity drop in km/h
   * (when `source === 'gps'`) or the impact magnitude in g (when `source === 'sensor'`).
   * v4.2 adds the `source` field to distinguish the GPS heuristic from the accelerometer
   * pipeline (`drivingEvents.sensorFusion`). App should ALWAYS confirm with the user
   * before notifying contacts — false positives are possible.
   * @since 4.1.0
   */
  on(
    eventName: 'possibleCrash',
    callback?: (data: { location: Location; value: number; source: 'gps' | 'sensor' }) => void
  ): Subscribable<{ location: Location; value: number; source: 'gps' | 'sensor' }>;

  /**
   * v4.2 — Sustained device interaction during an active trip with the screen on.
   * Conservative heuristic combining accelerometer/gyroscope jitter; meant to power
   * "stop using your phone while driving" UX. Disabled unless `drivingEvents.sensorFusion === true`.
   * @since 4.2.0
   */
  on(
    eventName: 'phoneUsageWhileDriving',
    callback?: (location?: Location) => void
  ): Subscribable<Location | void>;

  /**
   * Register event listener (accepts BackgroundGeolocationEvents enum for compatibility).
   */
  on(
    eventName: BackgroundGeolocationEvents,
    callback?: (data: Location | StationaryLocation | Activity | BackgroundGeolocationError | AuthorizationStatus | number | { sent: number } | { httpStatus: number; message: string } | void) => void
  ): Subscribable<Location | StationaryLocation | Activity | BackgroundGeolocationError | AuthorizationStatus | number | { sent: number } | { httpStatus: number; message: string } | void>;

}

declare const BackgroundGeolocation: BackgroundGeolocationPlugin;

export default BackgroundGeolocation;
export { BackgroundGeolocation };

/**
 * Type of the plugin API (use for variables/parameters).
 * In Angular/Ionic do NOT inject this type — inject BackgroundGeolocationService from '@josuelmm/cordova-background-geolocation/angular' instead.
 */
export type BackgroundGeolocation = BackgroundGeolocationPlugin;

/** Alias for ConfigureOptions (compatibility with @awesome-cordova-plugins style). */
export type BackgroundGeolocationConfig = ConfigureOptions;

/** Alias for Location (compatibility with @awesome-cordova-plugins style). */
export type BackgroundGeolocationResponse = Location;

/** Alias for LocationOptions (compatibility with @awesome-cordova-plugins style). */
export type BackgroundGeolocationCurrentPositionConfig = LocationOptions;

/** Alias for LogEntry (compatibility with @awesome-cordova-plugins style). */
export type BackgroundGeolocationLogEntry = LogEntry;

/** Error shape (compatibility with @awesome-cordova-plugins style). */
export interface BackgroundGeolocationErrorLike {
  code: BackgroundGeolocationLocationCode;
  message: string;
}

/** Location error codes (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationLocationCode {
  PERMISSION_DENIED = 1,
  LOCATION_UNAVAILABLE = 2,
  TIMEOUT = 3,
}

/** Native provider strings (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationNativeProvider {
  gps = 'gps',
  network = 'network',
  passive = 'passive',
  fused = 'fused',
}

/** Location provider IDs (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationLocationProvider {
  DISTANCE_FILTER_PROVIDER = 0,
  ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

/** Authorization status (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationAuthorizationStatus {
  NOT_AUTHORIZED = 0,
  AUTHORIZED = 1,
  AUTHORIZED_FOREGROUND = 2,
}

/** Log levels (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationLogLevel {
  TRACE = 'TRACE',
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

/** Provider enum (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationProvider {
  ANDROID_DISTANCE_FILTER_PROVIDER = 0,
  ANDROID_ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

/** Desired accuracy in meters (compatibility with @awesome-cordova-plugins style). Values match this plugin. */
export enum BackgroundGeolocationAccuracy {
  HIGH = 0,
  MEDIUM = 100,
  LOW = 1000,
  PASSIVE = 10000,
}

/** Mode for switchMode (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationMode {
  BACKGROUND = 0,
  FOREGROUND = 1,
}

/** iOS activity type (compatibility with @awesome-cordova-plugins style). */
export enum BackgroundGeolocationIOSActivity {
  AutomotiveNavigation = 'AutomotiveNavigation',
  OtherNavigation = 'OtherNavigation',
  Fitness = 'Fitness',
  Other = 'Other',
}
