import { Injectable, InjectionToken } from '@angular/core';

/**
 * Token used to provide BackgroundGeolocationService without triggering JIT.
 * The module provides this token with useFactory; the class is aliased via useExisting
 * so you still inject constructor(private bg: BackgroundGeolocationService) {}.
 */
export const BACKGROUND_GEOLOCATION_SERVICE = new InjectionToken<BackgroundGeolocationService>(
  'BackgroundGeolocationService'
);

/**
 * Angular service that wraps the Cordova/Capacitor BackgroundGeolocation plugin.
 * Use dependency injection instead of the global BackgroundGeolocation object.
 *
 * The native plugin must be installed and available (e.g. after deviceready).
 * Types (ConfigureOptions, Location, etc.) can be imported from
 * '@josuelmm/cordova-background-geolocation'.
 *
 * Provided via BackgroundGeolocationModule using an InjectionToken + useFactory
 * so AOT builds never need the JIT compiler for this class.
 */
@Injectable()
export class BackgroundGeolocationService {

  /** Returns the global plugin instance (Cordova/Capacitor). */
  private get plugin(): any {
    if (typeof window === 'undefined') return null;
    return (window as any).BackgroundGeolocation || null;
  }

  private ensurePlugin(): any {
    const p = this.plugin;
    if (!p) {
      throw new Error(
        'BackgroundGeolocation is not available. Ensure the plugin is installed and the app is running in a native context (Cordova/Capacitor).'
      );
    }
    return p;
  }

  configure(options: any, success?: () => void, fail?: (err: any) => void): Promise<void> {
    return this.ensurePlugin().configure(options, success, fail);
  }

  start(): Promise<void> {
    return this.ensurePlugin().start();
  }

  stop(): Promise<void> {
    return this.ensurePlugin().stop();
  }

  /** Inform the native plugin that the background task may complete (iOS). Call after handling location/stationary in the callback. */
  finish(): Promise<void> {
    return this.ensurePlugin().finish();
  }

  /** Force the plugin to enter "moving" or "stationary" state (iOS). */
  changePace(isMoving: boolean): Promise<void> {
    return this.ensurePlugin().changePace(isMoving);
  }

  getCurrentLocation(
    success?: (location: any) => void,
    fail?: (error: any) => void,
    options?: any
  ): Promise<any> {
    return this.ensurePlugin().getCurrentLocation(success, fail, options);
  }

  getStationaryLocation(
    success?: (location: any) => void,
    fail?: (error: any) => void
  ): Promise<any> {
    return this.ensurePlugin().getStationaryLocation(success, fail);
  }

  checkStatus(
    success?: (status: any) => void,
    fail?: (error: any) => void
  ): Promise<any> {
    return this.ensurePlugin().checkStatus(success, fail);
  }

  showAppSettings(): Promise<void> {
    return this.ensurePlugin().showAppSettings();
  }

  /** Open app settings (alias for showAppSettings). */
  openSettings(): Promise<void> {
    return this.ensurePlugin().openSettings();
  }

  showLocationSettings(): Promise<void> {
    return this.ensurePlugin().showLocationSettings();
  }

  getPluginVersion(
    success?: (version: string) => void,
    fail?: (error: any) => void
  ): Promise<string> {
    return this.ensurePlugin().getPluginVersion(success, fail);
  }

  getLocations(
    success?: (locations: any[]) => void,
    fail?: (error: any) => void
  ): Promise<any[]> {
    return this.ensurePlugin().getLocations(success, fail);
  }

  getValidLocations(
    success?: (locations: any[]) => void,
    fail?: (error: any) => void
  ): Promise<any[]> {
    return this.ensurePlugin().getValidLocations(success, fail);
  }

  getValidLocationsAndDelete(
    success?: (locations: any[]) => void,
    fail?: (error: any) => void
  ): Promise<any[]> {
    return this.ensurePlugin().getValidLocationsAndDelete(success, fail);
  }

  deleteLocation(
    locationId: number,
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().deleteLocation(locationId, success, fail);
  }

  deleteAllLocations(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().deleteAllLocations(success, fail);
  }

  switchMode(
    modeId: number,
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().switchMode(modeId, success, fail);
  }

  forceSync(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().forceSync(success, fail);
  }

  clearSync(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().clearSync(success, fail);
  }

  getPendingSyncCount(
    success?: (count: number) => void,
    fail?: (error: any) => void
  ): Promise<number> {
    return this.ensurePlugin().getPendingSyncCount(success, fail);
  }

  startSession(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().startSession(success, fail);
  }

  getSessionLocations(
    success?: (locations: any[]) => void,
    fail?: (error: any) => void
  ): Promise<any[]> {
    return this.ensurePlugin().getSessionLocations(success, fail);
  }

  clearSession(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().clearSession(success, fail);
  }

  getSessionLocationsCount(
    success?: (count: number) => void,
    fail?: (error: any) => void
  ): Promise<number> {
    return this.ensurePlugin().getSessionLocationsCount(success, fail);
  }

  getConfig(
    success?: (config: any) => void,
    fail?: (error: any) => void
  ): Promise<any> {
    return this.ensurePlugin().getConfig(success, fail);
  }

  getLogEntries(
    limit: number,
    fromId: number,
    minLevel: string,
    success?: (entries: any[]) => void,
    fail?: (error: any) => void
  ): Promise<any[]> {
    return this.ensurePlugin().getLogEntries(limit, fromId, minLevel, success, fail);
  }

  removeAllListeners(event?: string): void {
    this.ensurePlugin().removeAllListeners(event);
  }

  startTask(
    success?: (taskKey: number) => void,
    fail?: (error: any) => void
  ): Promise<number> {
    return this.ensurePlugin().startTask(success, fail);
  }

  endTask(
    taskKey: number,
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().endTask(taskKey, success, fail);
  }

  headlessTask(task: (event: any) => void): void {
    this.ensurePlugin().headlessTask(task);
  }

  /**
   * Register an event listener. Compatible with Awesome-style usage:
   * .on(BackgroundGeolocationEvents.error).subscribe((err) => ...) — returns subscription with .unsubscribe().
   * .on('location', (loc) => ...) — also supported; returned object has .unsubscribe().
   */
  on(eventName: string, callback?: (value: any) => void): { subscribe(cb: (value: any) => void): { unsubscribe(): void }; unsubscribe(): void } {
    const plugin = this.ensurePlugin();
    if (callback !== undefined) {
      const sub = plugin.on(eventName, callback) as { remove?: () => void };
      return {
        subscribe(cb: (value: any) => void) {
          const s = cb ? (plugin.on(eventName, cb) as { remove?: () => void }) : null;
          return { unsubscribe() { s?.remove?.(); } };
        },
        unsubscribe() { sub.remove?.(); }
      };
    }
    const channel = plugin.on(eventName) as { subscribe: (cb: (v: any) => void) => void; unsubscribe: (cb: (v: any) => void) => void };
    return {
      subscribe(cb: (value: any) => void) {
        channel.subscribe(cb);
        return { unsubscribe() { channel.unsubscribe(cb); } };
      },
      unsubscribe() { /* no-op when no callback */ }
    };
  }

  /** Convenience: access plugin constants (e.g. ACTIVITY_PROVIDER, HIGH_ACCURACY). */
  get native(): any {
    return this.plugin;
  }
}
