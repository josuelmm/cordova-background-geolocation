/**
 * Public API for @josuelmm/cordova-background-geolocation/angular.
 * Entry point for ng-packagr (Ivy/AOT build).
 */
export {
  BackgroundGeolocationService,
  BACKGROUND_GEOLOCATION_SERVICE,
} from './background-geolocation.service';
export { BackgroundGeolocationModule } from './background-geolocation.module';
export { BackgroundGeolocationEvents } from './background-geolocation-events';
export {
  BackgroundGeolocationLocationCode,
  BackgroundGeolocationNativeProvider,
  BackgroundGeolocationLocationProvider,
  BackgroundGeolocationAuthorizationStatus,
  BackgroundGeolocationLogLevel,
  BackgroundGeolocationProvider,
  BackgroundGeolocationAccuracy,
  BackgroundGeolocationMode,
  BackgroundGeolocationIOSActivity,
} from './background-geolocation-enums';
export type {
  BackgroundGeolocationConfig,
  BackgroundGeolocationResponse,
  ServiceStatus,
  BackgroundGeolocationLogEntry,
} from '../www/BackgroundGeolocation';
