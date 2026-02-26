---
layout: default
title: Angular (Ionic Angular)
nav_order: 5
---

# Angular (Ionic Angular)

The package **@josuelmm/cordova-background-geolocation** includes an Angular integration: an injectable service and an optional NgModule. The API is the same as the global `BackgroundGeolocation`; you just inject the service instead of using the global object. Same methods, same options, same events.

For the full list of **configuration options**, **events payloads**, and **HTTP posting**, see [API](api), [Events](events), and [HTTP Location Posting](http_posting). This page focuses on **how to use the plugin with Angular**.

---

## Install

Same as the rest of the plugin:

```bash
npm install @josuelmm/cordova-background-geolocation
npx cap sync
```

For Cordova:

```bash
cordova plugin add @josuelmm/cordova-background-geolocation
```

---

## Import

**One import (service + types):**

```ts
import {
  BackgroundGeolocationService,
  BackgroundGeolocationConfig,
  BackgroundGeolocationEvents,
  BackgroundGeolocationResponse
} from '@josuelmm/cordova-background-geolocation/angular';
```

The angular entry re-exports the service and the most used types, so you don't need a second import from the main package.

**You must import `BackgroundGeolocationModule`** so the service is provided (same idea as [@awesome-cordova-plugins](https://github.com/ionic-team/awesome-cordova-plugins), where you add the plugin to the module). We use a factory in the module so it works with AOT/production builds; without importing the module you may see "JIT compilation failed" or "@angular/compiler is not available".

```ts
import { BackgroundGeolocationModule } from '@josuelmm/cordova-background-geolocation/angular';

@NgModule({
  imports: [BackgroundGeolocationModule],
  ...
})
export class AppModule {}  // or your feature module
```

Then inject `BackgroundGeolocationService` in your components or services as usual.

**If you are migrating from @awesome-cordova-plugins/background-geolocation:** there the wrapper is an injectable class named `BackgroundGeolocation`. In this package, `BackgroundGeolocation` is the **global plugin object**, not a class, so you cannot inject it. Use `BackgroundGeolocationService` instead and keep the same usage:

```ts
// Before (Awesome):
// constructor(private backgroundGeolocation: BackgroundGeolocation) {}

// After (this plugin):
constructor(private backgroundGeolocation: BackgroundGeolocationService) {}
```

---

## Service API (methods)

The service exposes the same methods as the global plugin. All methods that accept `success` / `fail` callbacks also return a **Promise** when callbacks are omitted.

| Method | Returns | Description |
|--------|---------|--------------|
| `configure(options, success?, fail?)` | `Promise<void>` | Set options (provider, accuracy, url, httpHeaders, etc.). See [API – configure](api#configureoptions-success-fail). |
| `start()` | `Promise<void>` | Start background geolocation. |
| `stop()` | `Promise<void>` | Stop background geolocation. |
| `getCurrentLocation(success?, fail?, options?)` | `Promise<Location>` | One-time location. Options: `timeout`, `maximumAge`, `enableHighAccuracy`. |
| `getStationaryLocation(success?, fail?)` | `Promise<Location or null>` | Current stationary location if available. |
| `checkStatus(success?, fail?)` | `Promise<ServiceStatus>` | `{ isRunning, locationServicesEnabled, authorization }`. |
| `showAppSettings()` | `Promise<void>` | Open app settings (location permissions). |
| `openSettings()` | `Promise<void>` | Alias for `showAppSettings()`. |
| `showLocationSettings()` | `Promise<void>` | Open system location settings (Android). |
| `getPluginVersion(success?, fail?)` | `Promise<string>` | Plugin version string. |
| `getLocations(success?, fail?)` | `Promise<Location[]>` | All stored locations. |
| `getValidLocations(success?, fail?)` | `Promise<Location[]>` | Locations not yet posted to server. |
| `getValidLocationsAndDelete(success?, fail?)` | `Promise<Location[]>` | Valid locations and delete them. |
| `deleteLocation(locationId, success?, fail?)` | `Promise<void>` | Delete one location by id. |
| `deleteAllLocations(success?, fail?)` | `Promise<void>` | Delete all stored locations. |
| `switchMode(modeId, success?, fail?)` | `Promise<void>` | Force BACKGROUND or FOREGROUND mode (iOS). Use `this.bg.native.BACKGROUND_MODE` / `FOREGROUND_MODE`. |
| `forceSync(success?, fail?)` | `Promise<void>` | Force sync of pending locations to `syncUrl`. No-op if `sync: false`. |
| `clearSync(success?, fail?)` | `Promise<void>` | Clear the pending sync queue (discard locations waiting to be sent to `syncUrl`). |
| `getPendingSyncCount(success?, fail?)` | `Promise<number>` | Number of locations pending to be synced. Use with `forceSync` / `clearSync` for sync UI. |
| `getConfig(success?, fail?)` | `Promise<ConfigureOptions>` | Current configuration. |
| `getLogEntries(limit, fromId, minLevel, success?, fail?)` | `Promise<LogEntry[]>` | Debug log entries. |
| `removeAllListeners(event?)` | `void` | Unregister listeners (one event or all). |
| `startTask(success?, fail?)` | `Promise<number>` | Start a long-running task (e.g. iOS); returns task key. |
| `endTask(taskKey, success?, fail?)` | `Promise<void>` | End task by key. |
| `headlessTask(fn)` | `void` | Register headless callback (Android). See [Headless](headless). |
| `on(eventName, callback?)` | `{ unsubscribe(): void }` | Subscribe to an event. Call `unsubscribe()` when done. |

**Constants (provider, accuracy, mode, etc.):** use the `native` getter to access the same constants as the global plugin, e.g. `this.bg.native.ACTIVITY_PROVIDER`, `this.bg.native.HIGH_ACCURACY`, `this.bg.native.BACKGROUND_MODE`.

**Sync (syncUrl):** Configure `syncUrl` (and optionally `sync: true`) to send pending locations in batch. Use `getPendingSyncCount()` for “X pending” UI, `forceSync()` to send now, and `clearSync()` to discard the queue. See [HTTP Location Posting](http_posting#sync-queue-getpendingsynccount-forcesync-clearsync).

---

## Events

Same events as the global API. Subscribe with `on()` and store the subscription to unsubscribe later (e.g. in `ngOnDestroy`).

| Event | Payload | Description |
|-------|---------|-------------|
| `location` | `Location` | New location. |
| `stationary` | `Location` | Device entered stationary mode. |
| `activity` | `Activity` | Activity change (Android). |
| `start` | — | Tracking started. |
| `stop` | — | Tracking stopped. |
| `error` | `{ code, message }` | Plugin error. |
| `authorization` | `status` | Authorization status change. |
| `foreground` | — | App entered foreground. |
| `background` | — | App entered background. |
| `abort_requested` | — | Server returned 285. |
| `http_authorization` | — | Server returned 401; reconfigure `httpHeaders` if needed. |

Example:

```ts
private sub: { unsubscribe(): void } | null = null;

ngOnInit() {
  this.sub = this.bg.on('location', (loc: Location) => {
    console.log(loc.latitude, loc.longitude);
  });
}

ngOnDestroy() {
  this.sub?.unsubscribe();
  // or: this.bg.removeAllListeners('location');
}
```

---

## Types

**Common types** are re-exported from the `/angular` entry, so one import is enough for most cases:

- `BackgroundGeolocationConfig`, `BackgroundGeolocationEvents`, `BackgroundGeolocationResponse`
- `BackgroundGeolocationAccuracy`, `BackgroundGeolocationMode`, `ServiceStatus`, `BackgroundGeolocationLogEntry`

For other types (e.g. `ConfigureOptions`, `Location`, `Activity`, `LogEntry`, `BackgroundGeolocationError`), import from the main package when needed:

```ts
import type { ConfigureOptions, Location, Activity } from '@josuelmm/cordova-background-geolocation';
```

The main package also exports **Awesome-style** aliases and enums (`BackgroundGeolocationEvents.location`, etc.); accuracy values in this plugin are `0, 100, 1000, 10000` (see [API – TypeScript](api#typescript) and README).

---

## Example: full flow

Single import from `/angular` (service + types):

```ts
import { Component, OnDestroy } from '@angular/core';
import {
  BackgroundGeolocationService,
  BackgroundGeolocationConfig,
  BackgroundGeolocationResponse
} from '@josuelmm/cordova-background-geolocation/angular';

@Component({
  selector: 'app-tracking',
  template: `
    <button (click)="start()">Start</button>
    <button (click)="stop()">Stop</button>
    <p>Last: {{ last?.latitude }}, {{ last?.longitude }}</p>
  `
})
export class TrackingPage implements OnDestroy {
  last: BackgroundGeolocationResponse | null = null;
  private sub: { unsubscribe(): void } | null = null;

  constructor(private bg: BackgroundGeolocationService) {}

  start(): void {
    const options: BackgroundGeolocationConfig = {
      distanceFilter: 50,
      desiredAccuracy: this.bg.native.HIGH_ACCURACY,
      url: 'https://yourserver.com/locations',
      httpHeaders: { 'Authorization': 'Bearer TOKEN' }
    };
    this.bg.configure(options)
      .then(() => this.bg.start())
      .catch(err => console.error(err));

    this.sub = this.bg.on('location', (loc: BackgroundGeolocationResponse) => {
      this.last = loc;
    });
  }

  stop(): void {
    this.sub?.unsubscribe();
    this.bg.stop();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
```

---

## Summary

| What | Where |
|------|--------|
| **Service + common types** | Single import: `import { BackgroundGeolocationService, BackgroundGeolocationConfig, BackgroundGeolocationResponse, ... } from '@josuelmm/cordova-background-geolocation/angular'` |
| **Module** (optional) | Same entry: `import { BackgroundGeolocationModule } from '@josuelmm/cordova-background-geolocation/angular'` |
| **Other types** (e.g. `Location`, `ConfigureOptions`) | Main package: `import type { ... } from '@josuelmm/cordova-background-geolocation'` |
| **Options / events detail** | [API](api), [Events](events), [HTTP posting](http_posting) |

Do **not** inject the global `BackgroundGeolocation` in Angular — it is not an injectable class. Use `BackgroundGeolocationService` instead. The service delegates to the same native plugin as the global object.
