import { NgModule } from '@angular/core';
import {
  BackgroundGeolocationService,
  BACKGROUND_GEOLOCATION_SERVICE,
} from './background-geolocation.service';

/**
 * NgModule that provides BackgroundGeolocationService.
 * Uses InjectionToken + useFactory so Angular never calls getFactoryDef on the class
 * (avoids JIT requirement in AOT/production). useExisting lets you still inject
 * constructor(private bg: BackgroundGeolocationService) {}.
 *
 * Import in your AppModule or feature module:
 *   imports: [BackgroundGeolocationModule]
 * Then inject: constructor(private bg: BackgroundGeolocationService) {}
 */
@NgModule({
  providers: [
    {
      provide: BACKGROUND_GEOLOCATION_SERVICE,
      useFactory: () => new BackgroundGeolocationService(),
    },
    {
      provide: BackgroundGeolocationService,
      useExisting: BACKGROUND_GEOLOCATION_SERVICE,
    },
  ],
})
export class BackgroundGeolocationModule {}
