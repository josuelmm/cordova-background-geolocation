import { Injectable, InjectionToken, NgZone } from '@angular/core';

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

  /**
   * Native plugin events are emitted from outside the Angular zone, so change
   * detection would not run for them. Every listener registered through on() is
   * re-entered into the zone with zone.run().
   */
  constructor(private zone: NgZone) {}

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

  /**
   * Extended diagnostics. Returns permissions, battery optimisation state,
   * last fix age, pending sync count, OEM info and (on iOS) precise location /
   * background refresh / low power flags.
   *
   * @since 3.5.0
   */
  getDiagnostics(
    success?: (diagnostics: any) => void,
    fail?: (error: any) => void
  ): Promise<any> {
    return this.ensurePlugin().getDiagnostics(success, fail);
  }

  /** @since 3.6.0 */
  isIgnoringBatteryOptimizations(
    success?: (whitelisted: boolean) => void,
    fail?: (error: any) => void
  ): Promise<boolean> {
    return this.ensurePlugin().isIgnoringBatteryOptimizations(success, fail);
  }

  /** @since 3.6.0 */
  requestIgnoreBatteryOptimizations(
    success?: (whitelisted: boolean) => void,
    fail?: (error: any) => void
  ): Promise<boolean> {
    return this.ensurePlugin().requestIgnoreBatteryOptimizations(success, fail);
  }

  /** @since 3.6.0 */
  openBatterySettings(
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().openBatterySettings(success, fail);
  }

  /** @since 3.6.0 */
  openAutoStartSettings(
    success?: (info: { opened: boolean; manufacturer: string; screen: string }) => void,
    fail?: (error: any) => void
  ): Promise<{ opened: boolean; manufacturer: string; screen: string }> {
    return this.ensurePlugin().openAutoStartSettings(success, fail);
  }

  /** @since 3.6.0 */
  getManufacturerHelp(
    success?: (info: { manufacturer: string; steps: string[] }) => void,
    fail?: (error: any) => void
  ): Promise<{ manufacturer: string; steps: string[] }> {
    return this.ensurePlugin().getManufacturerHelp(success, fail);
  }

  /** @since 4.0.0 */
  triggerSOS(
    payload?: { [key: string]: any },
    success?: () => void,
    fail?: (error: any) => void
  ): Promise<void> {
    return this.ensurePlugin().triggerSOS(payload, success, fail);
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
    const zone = this.zone;
    if (callback !== undefined) {
      const zoned = (value: any) => zone.run(() => callback(value));
      const sub = plugin.on(eventName, zoned) as { remove?: () => void };
      const subscription = {
        // v5.0.1 — el argumento de subscribe() se estaba IGNORANDO: `on(evt, a).subscribe(b)`
        // registraba `a` y descartaba `b` en silencio (compilaba, no llamaba nunca a b).
        // v4 sí lo registraba. Se restaura: si llegan callbacks extra, se suscriben de verdad,
        // envueltos en NgZone igual que el primero, y su unsubscribe libera solo el suyo.
        subscribe(cb?: (value: any) => void) {
          if (cb === undefined) {
            return { unsubscribe() { sub.remove?.(); } };
          }
          const extraZoned = (value: any) => zone.run(() => cb(value));
          const extra = plugin.on(eventName, extraZoned) as { remove?: () => void };
          return { unsubscribe() { extra.remove?.(); } };
        },
        unsubscribe() { sub.remove?.(); }
      };
      return subscription;
    }
    const channel = plugin.on(eventName) as { subscribe: (cb: (v: any) => void) => void; unsubscribe: (cb: (v: any) => void) => void };
    return {
      subscribe(cb: (value: any) => void) {
        const zoned = (value: any) => zone.run(() => cb(value));
        channel.subscribe(zoned);
        return { unsubscribe() { channel.unsubscribe(zoned); } };
      },
      unsubscribe() { /* no-op when no callback */ }
    };
  }

  /** Convenience: access plugin constants (e.g. ACTIVITY_PROVIDER, HIGH_ACCURACY). */
  get native(): any {
    return this.plugin;
  }
}
