# Cordova Background Geolocation

[![npm](https://img.shields.io/npm/v/@josuelmm/cordova-background-geolocation?style=flat-square)](https://www.npmjs.com/package/@josuelmm/cordova-background-geolocation)
![npm downloads](https://img.shields.io/npm/dm/@josuelmm/cordova-background-geolocation?style=flat-square)

[![GitHub issues](https://img.shields.io/github/issues/josuelmm/cordova-background-geolocation?style=flat-square)](https://github.com/josuelmm/cordova-background-geolocation/issues)
[![GitHub stars](https://img.shields.io/github/stars/josuelmm/cordova-background-geolocation?style=flat-square)](https://github.com/josuelmm/cordova-background-geolocation/stargazers)
![GitHub last commit](https://img.shields.io/github/last-commit/josuelmm/cordova-background-geolocation?style=flat-square)

## What it does

This plugin provides **background and foreground geolocation** for **Cordova**, **Capacitor**, and **Ionic** apps. It is tested and works with current versions of Capacitor and Ionic. It is more battery- and data-efficient than the HTML5 Geolocation API and is designed to keep tracking location even when the app is in the background or the screen is off.

**Features:**

- **Circular region monitoring** and **stop detection** to reduce battery use
- **Activity-based provider** (e.g. walking, driving, still) for smarter updates
- **Configurable distance/interval** filtering and optional **HTTP posting** of locations to your server
- **Foreground service** (Android) with a persistent notification so the OS does not kill the tracker
- Works alongside other geolocation sources (e.g. `navigator.geolocation`)

It works well with **[@awesome-cordova-plugins/background-geolocation](https://www.npmjs.com/package/@awesome-cordova-plugins/background-geolocation)** if you use Ionic and want a typed wrapper and dependency injection.

**Capacitor & Ionic:** Use the plugin in a Capacitor app (with or without Ionic). Install the package, run `npx cap sync`, then use the same JavaScript API. The plugin is compatible with recent Capacitor (e.g. 6.x, 7.x) and Ionic (7.x, 8.x) versions.

---

## Installation

### npm (Capacitor or modern Cordova)

```bash
npm install @josuelmm/cordova-background-geolocation
```

For Capacitor, sync native projects after installing:

```bash
npx cap sync
```

### Cordova CLI

```bash
cordova plugin add @josuelmm/cordova-background-geolocation
```

Optional: set variables for Google Play Services and iOS permission strings:

```bash
cordova plugin add @josuelmm/cordova-background-geolocation \
  --variable GOOGLE_PLAY_SERVICES_VERSION=17+ \
  --variable ALWAYS_USAGE_DESCRIPTION="Your app needs location for ..." \
  --variable MOTION_USAGE_DESCRIPTION="Your app uses motion for ..."
```

**Notes:**

- **AndroidX:** Use plugin version 2.x. For non-AndroidX projects use 1.x.
- **Android 14+:** You may need to justify foreground location usage (e.g. in Play Console) for `FOREGROUND_SERVICE_LOCATION`.
- **Android 13+:** Request runtime `POST_NOTIFICATION` permission if you want the tracking notification to show.
- After changing plugin options, remove and reinstall the plugin for changes to take effect.

---

## Usage

### 1. Configure

Set your preferred provider, accuracy, intervals, and optional server URL:

```js
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.ACTIVITY_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  stationaryRadius: 50,
  distanceFilter: 50,
  notificationTitle: 'Background tracking',
  notificationText: 'enabled',
  debug: true,
  interval: 10000,
  fastestInterval: 5000,
  activitiesInterval: 10000,
  url: 'https://yourserver.com/location',
  // HTTP headers can be sent in two ways:
  // 1) Static: set httpHeaders here — same headers on every POST/sync request (e.g. API key, Content-Type).
  httpHeaders: {
    'X-FOO': 'bar',
    'Authorization': 'Bearer YOUR_TOKEN'
  },
  postTemplate: {
    lat: '@latitude',
    lon: '@longitude'
  }
});
```

### 2. Start tracking

```js
BackgroundGeolocation.start();
```

### 3. Listen for events

```js
BackgroundGeolocation.on('location', function (location) {
  console.log(location.latitude, location.longitude);
});

BackgroundGeolocation.on('error', function (error) {
  console.warn(error.code, error.message);
});
```

**HTTP headers (second way — dynamic):** When your server responds with **401 Unauthorized**, the plugin emits the `http_authorization` event. You can then refresh your token and reconfigure headers so the next request uses them (e.g. a new `Authorization` header):

```js
BackgroundGeolocation.on('http_authorization', function () {
  // e.g. get a new token from your auth API, then:
  BackgroundGeolocation.configure({
    httpHeaders: { 'Authorization': 'Bearer ' + newToken }
  });
});
```

### 4. Stop tracking

```js
BackgroundGeolocation.stop();
```

More options (stationary, activity, start/stop events, headless task) are described in the [documentation](https://josuelmm.github.io/cordova-background-geolocation/).

---

## Awesome Cordova Plugins

If you use **Ionic** and **@awesome-cordova-plugins**, you can install the wrapper and use the plugin via dependency injection:

- [@awesome-cordova-plugins/background-geolocation](https://www.npmjs.com/package/@awesome-cordova-plugins/background-geolocation)

This package wraps the native plugin and works with `@josuelmm/cordova-background-geolocation` when the native plugin is installed as above.

---

## Compatibility

| Plugin version | Cordova CLI | Cordova Android | Cordova iOS |
|----------------|-------------|-----------------|-------------|
| 1.x            | ≥ 8.0.0     | ≥ 8.0.0         | ≥ 6.0.0     |
| 2.x            | ≥ 10.0.0    | ≥ 10.0.0        | ≥ 6.0.0     |

---

## Documentation and changelog

- [Documentation](https://josuelmm.github.io/cordova-background-geolocation/) (API, options, examples)
- [CHANGELOG](CHANGELOG.md) for version history

This project is based on [@mauron85/cordova-plugin-background-geolocation](https://github.com/mauron85/cordova-plugin-background-geolocation) and the original by [christocracy](https://github.com/christocracy). Maintained at [josuelmm/cordova-background-geolocation](https://github.com/josuelmm/cordova-background-geolocation). Issues and PRs welcome.

---

## Licence

[Apache License](http://www.apache.org/licenses/LICENSE-2.0)

Copyright (c) 2013 Christopher Scott, Transistor Software

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
