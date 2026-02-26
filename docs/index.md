---
layout: default
nav_order: 1
title: Introduction
---

# Introduction

Cross-platform geolocation for Cordova with battery-saving "circular region monitoring" and "stop detection"
{: .fw-500 }

This plugin can be used for geolocation when the app is running in the foreground or background. It is more battery and data efficient than html5 geolocation or cordova-geolocation plugin. It can be used side by side with other geolocation providers (eg. html5 navigator.geolocation).

This project is based on [@mauron85/cordova-plugin-background-geolocation](https://github.com/mauron85/cordova-plugin-background-geolocation), which in turn was based on the original [cordova-background-geolocation plugin](https://github.com/christocracy/cordova-plugin-background-geolocation) by [christocracy](https://github.com/christocracy). This independent fork is maintained at [josuelmm/cordova-background-geolocation](https://github.com/josuelmm/cordova-background-geolocation). If you have any fixes, features or updates that you would like included, please do raise a PR or issue on the GitHub repository.

The NPM package can be found at [@josuelmm/cordova-background-geolocation](https://www.npmjs.com/package/@josuelmm/cordova-background-geolocation).

## Installing the plugin

```bash
npm install @josuelmm/cordova-background-geolocation
# or
cordova plugin add @josuelmm/cordova-background-geolocation
```

You may also want to change default iOS permission prompts and set specific google play version and android support library version for compatibility with other plugins.

**Note:** Always consult documentation of other plugins to figure out compatible versions.

```bash
cordova plugin add @josuelmm/cordova-background-geolocation \
  --variable GOOGLE_PLAY_SERVICES_VERSION=17+ \
  --variable ANDROID_SUPPORT_LIBRARY_VERSION=26+ \
  --variable ALWAYS_USAGE_DESCRIPTION="App requires ..." \
  --variable MOTION_USAGE_DESCRIPTION="App requires motion detection"
```

**Note:** To apply changes, you must remove and reinstall plugin.

## Usage

**TypeScript / Angular:** For TypeScript you can use the built-in types (native or Awesome-style aliases and enums). In **Angular / Ionic**, use a **single import** from `@josuelmm/cordova-background-geolocation/angular` for the injectable service and common types; do not inject the global `BackgroundGeolocation`. See the [README TypeScript section](https://github.com/josuelmm/cordova-background-geolocation#typescript-imports) and [Angular](angular).

First, configure the plugin with the settings you require.

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
    url: 'http://192.168.81.15:3000/location',
    httpHeaders: {
      'X-FOO': 'bar'
    },
    // customize post properties
    postTemplate: {
      lat: '@latitude',
      lon: '@longitude',
      foo: 'bar' // you can also add your own properties
    }
  });
```

Then call `start()` to start location tracking.

For a more detailed example, see [Example](example). For **Angular / Ionic Angular** (injectable service, same API), see [Angular](angular).

### Location Providers

You can choose from three location providers:

* **DISTANCE_FILTER_PROVIDER**
* **ACTIVITY_PROVIDER**
* **RAW_PROVIDER**

*See [Which provider should I use?](providers) for more information about providers.*

## Sending locations to a server (url, syncUrl, sync)

When `url` is set, each location is posted immediately. When `syncUrl` is set, locations that fail to post (or that are only queued for sync) are sent in batch when their count reaches `syncThreshold`, or when you call **`forceSync()`**. You can get the pending count with **`getPendingSyncCount()`** and discard the queue with **`clearSync()`**. Set **`sync: false`** in config to disable automatic and manual sync (locations are still stored). See [HTTP Location Posting](http_posting) for headers, Content-Type (JSON vs form-urlencoded), and [API](api) for `forceSync`, `clearSync`, `getPendingSyncCount`.

## Compilation

### Compatibility

| Plugin version | Cordova CLI | Cordova Android | Cordova iOS |
|----------------|-------------|-----------------|-------------|
| 1.x            | ≥ 8.0.0     | ≥ 8.0.0         | ≥ 6.0.0    |
| 2.x            | ≥ 10.0.0    | ≥ 10.0.0        | ≥ 6.0.0    |

**Please note** that as of Cordova Android 8.0.0 icons are by default mipmap/ic_launcher not mipmap/icon. On Cordova Android &lt; 8.0.0 you may need to update the icons in AndroidManifest.xml.

### Android SDKs

You will need to ensure that you have installed the following items through the Android SDK Manager:

| Name                       | Version  |
|----------------------------|----------|
| Android SDK Tools          | >26.0.2  |
| Android SDK Platform-tools | >26.0.2  |
| Android SDK Build-tools    | >26.0.2  |
| Android Support Repository | >47      |
| Android Support Library    | >26.1.0  |
| Google Play Services       | >11.8.0  |
| Google Repository          | >58      |

Android is no longer supporting downloading support libraries through the SDK Manager.
The support libraries are now available through Google's Maven repository.

## Geofencing

There is nice cordova plugin [cordova-plugin-geofence](https://github.com/cowbell/cordova-plugin-geofence), which does exactly that. Let's keep this plugin lightweight as much as possible.

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
