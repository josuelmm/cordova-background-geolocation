/**
 * Enums as runtime values for use with @josuelmm/cordova-background-geolocation/angular.
 * Matches @awesome-cordova-plugins/background-geolocation names; numeric values match this plugin (e.g. accuracy 0, 100, 1000, 10000).
 */

export enum BackgroundGeolocationLocationCode {
  PERMISSION_DENIED = 1,
  LOCATION_UNAVAILABLE = 2,
  TIMEOUT = 3,
}

export enum BackgroundGeolocationNativeProvider {
  gps = 'gps',
  network = 'network',
  passive = 'passive',
  fused = 'fused',
}

export enum BackgroundGeolocationLocationProvider {
  DISTANCE_FILTER_PROVIDER = 0,
  ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

export enum BackgroundGeolocationAuthorizationStatus {
  NOT_AUTHORIZED = 0,
  AUTHORIZED = 1,
  AUTHORIZED_FOREGROUND = 2,
}

export enum BackgroundGeolocationLogLevel {
  TRACE = 'TRACE',
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

export enum BackgroundGeolocationProvider {
  ANDROID_DISTANCE_FILTER_PROVIDER = 0,
  ANDROID_ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

/** Values match this plugin: 0, 100, 1000, 10000 (not Awesome's 10, 100, 1000). */
export enum BackgroundGeolocationAccuracy {
  HIGH = 0,
  MEDIUM = 100,
  LOW = 1000,
  PASSIVE = 10000,
}

export enum BackgroundGeolocationMode {
  BACKGROUND = 0,
  FOREGROUND = 1,
}

export enum BackgroundGeolocationIOSActivity {
  AutomotiveNavigation = 'AutomotiveNavigation',
  OtherNavigation = 'OtherNavigation',
  Fitness = 'Fitness',
  Other = 'Other',
}
