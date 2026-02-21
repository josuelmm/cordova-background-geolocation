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
}
