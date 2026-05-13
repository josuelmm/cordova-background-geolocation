/**
 * Event names for BackgroundGeolocation.on(event).subscribe(...).
 * Mirrors the enum in the main package so it is available at runtime from /angular.
 */
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
  // v3.5+
  heartbeat = 'heartbeat',
  syncStart = 'syncStart',
  syncProgress = 'syncProgress',
  syncSuccess = 'syncSuccess',
  syncError = 'syncError',
  // v4.0
  tripStart = 'tripStart',
  tripEnd = 'tripEnd',
  moving = 'moving',
  stopped = 'stopped',
  speeding = 'speeding',
  providerChange = 'providerChange',
  sos = 'sos',
  // v4.1
  hardBrake = 'hardBrake',
  rapidAcceleration = 'rapidAcceleration',
  sharpTurn = 'sharpTurn',
  possibleCrash = 'possibleCrash',
  // v4.2 sensor fusion
  phoneUsageWhileDriving = 'phoneUsageWhileDriving',
}
