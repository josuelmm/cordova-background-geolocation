/*
 According to apache license

 This is fork of christocracy cordova-plugin-background-geolocation plugin
 https://github.com/christocracy/cordova-plugin-background-geolocation
 */

var exec = require('cordova/exec');
var channel = require('cordova/channel');
var radio = require('./radio');
var TAG = 'CDVBackgroundGeolocation';

var assert = function (condition, msgArray) {
  if (!condition) {
      throw new Error(msgArray.join('') || 'Assertion failed');
  }
}

var eventHandler = function (event) {
  radio(event.name).broadcast(event.payload);
};

var errorHandler = function (error) {
  radio('error').broadcast(error);
};

var unsubscribeAll = function (channels) {
  channels.forEach(function(channel) {
    var topic = radio(channel);
    var callbacks = [].concat.apply([], topic.channels[channel]); // flatten array
    topic.unsubscribe.apply(topic, callbacks);
  });
}

var execWithPromise = function (suceess, failure, method, data) {
  var p = new Promise(function (resolve, reject) {
    exec(resolve, reject, 'BackgroundGeolocation', method, data || []);
  });
  if (suceess || failure) {
    p.then(suceess || function () {}, failure || function () {});
  }
  return p;
}

var BackgroundGeolocation = {
  events: [
    'location',
    'stationary',
    'activity',
    'start',
    'stop',
    'error',
    'authorization',
    'foreground',
    'background',
    'abort_requested',
    'http_authorization',
    // v3.5 Phase 4
    'heartbeat',
    'syncStart',
    'syncProgress',
    'syncSuccess',
    'syncError',
    // v4.0 Phase 6 — driver insights
    'tripStart',
    'tripEnd',
    'moving',
    'stopped',
    'speeding',
    'providerChange',
    'sos',
    // v4.1 — GPS-derived sensor-like events
    'hardBrake',
    'rapidAcceleration',
    'sharpTurn',
    'possibleCrash',
    // v4.2 — sensor fusion
    'phoneUsageWhileDriving'
  ],

  DISTANCE_FILTER_PROVIDER: 0,
  ACTIVITY_PROVIDER: 1,
  RAW_PROVIDER: 2,

  BACKGROUND_MODE: 0,
  FOREGROUND_MODE: 1,

  NOT_AUTHORIZED: 0,
  AUTHORIZED: 1,
  AUTHORIZED_FOREGROUND: 2,

  HIGH_ACCURACY: 0,
  MEDIUM_ACCURACY: 100,
  LOW_ACCURACY: 1000,
  PASSIVE_ACCURACY: 10000,

  LOG_ERROR: 'ERROR',
  LOG_WARN: 'WARN',
  LOG_INFO: 'INFO',
  LOG_DEBUG: 'DEBUG',
  LOG_TRACE: 'TRACE',

  PERMISSION_DENIED: 1,
  LOCATION_UNAVAILABLE: 2,
  TIMEOUT: 3,

  configure: function (config, success, failure) {
    return execWithPromise(success,
      failure,
      'configure',
      [config]
    );
  },

  start: function () {
    return execWithPromise(null, null, 'start');
  },

  stop: function () {
    return execWithPromise(null, null, 'stop');
  },

  switchMode: function (mode, success, failure) {
    return execWithPromise(success,
      failure,
      'switchMode', [mode]);
  },

  getConfig: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getConfig');
  },

  /**
   * Returns current stationaryLocation if available.  null if not
   */
  getStationaryLocation: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getStationaryLocation');
  },

  showAppSettings: function () {
    return execWithPromise(null,
      null,
      'showAppSettings');
  },

  /** Opens app settings (alias for showAppSettings). */
  openSettings: function () {
    return execWithPromise(null,
      null,
      'showAppSettings');
  },

  showLocationSettings: function () {
    return execWithPromise(null,
      null,
      'showLocationSettings');
  },

  getLocations: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getLocations');
  },

  getValidLocations: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getValidLocations');
  },

  getValidLocationsAndDelete: function (success, failure) {
    return execWithPromise(success, 
      failure,
      'getValidLocationsAndDelete');
  },

  deleteLocation: function (locationId, success, failure) {
    return execWithPromise(success,
      failure,
      'deleteLocation', [locationId]);
  },

  deleteAllLocations: function (success, failure) {
    return execWithPromise(success,
      failure,
      'deleteAllLocations');
  },

  getCurrentLocation: function(success, failure, options) {
    options = options || {};
    return execWithPromise(success,
      failure,
      'getCurrentLocation', [options.timeout, options.maximumAge, options.enableHighAccuracy]);
  },

  getLogEntries: function(limit, offset = 0, minLevel = "DEBUG", success, failure) {
    return execWithPromise(success,
      failure,
      'getLogEntries', [limit, offset, minLevel]);
  },

  getPluginVersion: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getPluginVersion');
  },

  checkStatus: function (success, failure) {
    return execWithPromise(success,
      failure,
      'checkStatus')
  },

  getDiagnostics: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getDiagnostics')
  },

  // v3.6 Phase 5 — Battery / OEM helpers (Android only; on iOS these resolve as no-ops)
  isIgnoringBatteryOptimizations: function (success, failure) {
    return execWithPromise(success, failure, 'isIgnoringBatteryOptimizations');
  },

  requestIgnoreBatteryOptimizations: function (success, failure) {
    return execWithPromise(success, failure, 'requestIgnoreBatteryOptimizations');
  },

  openBatterySettings: function (success, failure) {
    return execWithPromise(success, failure, 'openBatterySettings');
  },

  openAutoStartSettings: function (success, failure) {
    return execWithPromise(success, failure, 'openAutoStartSettings');
  },

  getManufacturerHelp: function (success, failure) {
    return execWithPromise(success, failure, 'getManufacturerHelp');
  },

  // v4.0 Phase 6 — Driver insights
  triggerSOS: function (payload, success, failure) {
    return execWithPromise(success, failure, 'triggerSOS', [payload || {}]);
  },

  // v4.5 — runtime permission helpers (Android). Resolve with { granted: bool, denied?: string[] }.
  requestBackgroundLocationPermission: function (success, failure) {
    return execWithPromise(success, failure, 'requestBackgroundLocationPermission');
  },
  requestActivityRecognitionPermission: function (success, failure) {
    return execWithPromise(success, failure, 'requestActivityRecognitionPermission');
  },
  requestNotificationPermission: function (success, failure) {
    return execWithPromise(success, failure, 'requestNotificationPermission');
  },

  startTask: function (success, failure) {
    return execWithPromise(success,
      failure,
      'startTask');
  },

  endTask: function (taskKey, success, failure) {
    return execWithPromise(success,
      failure,
      'endTask', [taskKey]);
  },

  headlessTask: function (func, success, failure) {
    return execWithPromise(success,
      failure,
      'registerHeadlessTask', [func.toString()]);
  },

  forceSync: function (success, failure) {
    return execWithPromise(success,
      failure,
      'forceSync');
  },

  clearSync: function (success, failure) {
    return execWithPromise(success,
      failure,
      'clearSync');
  },

  getPendingSyncCount: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getPendingSyncCount');
  },

  startSession: function (success, failure) {
    return execWithPromise(success,
      failure,
      'startSession');
  },

  getSessionLocations: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getSessionLocations');
  },

  clearSession: function (success, failure) {
    return execWithPromise(success,
      failure,
      'clearSession');
  },

  getSessionLocationsCount: function (success, failure) {
    return execWithPromise(success,
      failure,
      'getSessionLocationsCount');
  },

  on: function (event, callbackFn) {
    assert(this.events.indexOf(event) > -1, [TAG, '#on unknown event "' + event + '"']);
    if (!callbackFn) {
      // NOTE: do not return radio(event) directly. radio.$ is a singleton whose
      // channelName mutates on every radio(x) call and on every broadcast, so a
      // later unsubscribe(cb) would operate on whatever channel was selected last
      // and the listener would never be removed (listener leak).
      return {
        subscribe: function (cb) {
          radio(event).subscribe(cb);
          return {
            unsubscribe: function () {
              radio(event).unsubscribe(cb);
            }
          };
        },
        unsubscribe: function (cb) {
          radio(event).unsubscribe(cb);
        }
      };
    }
    radio(event).subscribe(callbackFn);
    return {
      remove: function () {
        radio(event).unsubscribe(callbackFn);
      }
    };
  },

  removeAllListeners: function (event) {
    if (!event) {
      unsubscribeAll(this.events);
      return void 0;
    }
    if (this.events.indexOf(event) < 0) {
      console.log('[WARN] ' + TAG + '#removeAllListeners for unknown event "' + event + '"');
      return void 0;
    }
    unsubscribeAll([event]);
  }
};

// ---------------------------------------------------------------------------
// Runtime values for the enums declared in BackgroundGeolocation.d.ts.
//
// `export enum X` in a .d.ts is a TYPE-ONLY declaration: it emits no JavaScript.
// TypeScript still compiles a member read into a property access on this module
// (`BackgroundGeolocation_1.X.member`), so without the objects below every such
// read is `undefined` at runtime.
//
// Values reuse the constants declared above wherever an equivalent one already
// exists, so the two can never drift. The objects are frozen, and numeric
// members also carry the reverse mapping a real TypeScript enum has
// (e.g. BackgroundGeolocationAccuracy[0] === 'HIGH').
// ---------------------------------------------------------------------------

var defineEnum = function (name, members) {
  var e = {};
  Object.keys(members).forEach(function (key) {
    var value = members[key];
    e[key] = value;
    if (typeof value === 'number') {
      e[value] = key; // TS numeric enums are bidirectional
    }
  });
  BackgroundGeolocation[name] = Object.freeze(e);
};

// Member name === member value for every event, so derive it from `events`.
defineEnum('BackgroundGeolocationEvents', BackgroundGeolocation.events.reduce(function (acc, event) {
  acc[event] = event;
  return acc;
}, {}));

defineEnum('BackgroundGeolocationLocationCode', {
  PERMISSION_DENIED: BackgroundGeolocation.PERMISSION_DENIED,
  LOCATION_UNAVAILABLE: BackgroundGeolocation.LOCATION_UNAVAILABLE,
  TIMEOUT: BackgroundGeolocation.TIMEOUT
});

defineEnum('BackgroundGeolocationNativeProvider', {
  gps: 'gps',
  network: 'network',
  passive: 'passive',
  fused: 'fused'
});

defineEnum('BackgroundGeolocationLocationProvider', {
  DISTANCE_FILTER_PROVIDER: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  ACTIVITY_PROVIDER: BackgroundGeolocation.ACTIVITY_PROVIDER,
  RAW_PROVIDER: BackgroundGeolocation.RAW_PROVIDER
});

defineEnum('BackgroundGeolocationAuthorizationStatus', {
  NOT_AUTHORIZED: BackgroundGeolocation.NOT_AUTHORIZED,
  AUTHORIZED: BackgroundGeolocation.AUTHORIZED,
  AUTHORIZED_FOREGROUND: BackgroundGeolocation.AUTHORIZED_FOREGROUND
});

defineEnum('BackgroundGeolocationLogLevel', {
  TRACE: BackgroundGeolocation.LOG_TRACE,
  DEBUG: BackgroundGeolocation.LOG_DEBUG,
  INFO: BackgroundGeolocation.LOG_INFO,
  WARN: BackgroundGeolocation.LOG_WARN,
  ERROR: BackgroundGeolocation.LOG_ERROR
});

defineEnum('BackgroundGeolocationProvider', {
  ANDROID_DISTANCE_FILTER_PROVIDER: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  ANDROID_ACTIVITY_PROVIDER: BackgroundGeolocation.ACTIVITY_PROVIDER,
  RAW_PROVIDER: BackgroundGeolocation.RAW_PROVIDER
});

defineEnum('BackgroundGeolocationAccuracy', {
  HIGH: BackgroundGeolocation.HIGH_ACCURACY,
  MEDIUM: BackgroundGeolocation.MEDIUM_ACCURACY,
  LOW: BackgroundGeolocation.LOW_ACCURACY,
  PASSIVE: BackgroundGeolocation.PASSIVE_ACCURACY
});

defineEnum('BackgroundGeolocationMode', {
  BACKGROUND: BackgroundGeolocation.BACKGROUND_MODE,
  FOREGROUND: BackgroundGeolocation.FOREGROUND_MODE
});

defineEnum('BackgroundGeolocationIOSActivity', {
  AutomotiveNavigation: 'AutomotiveNavigation',
  OtherNavigation: 'OtherNavigation',
  Fitness: 'Fitness',
  Other: 'Other'
});

channel.deviceready.subscribe(function () {
  // register app global listeners
  exec(eventHandler,
    errorHandler,
    'BackgroundGeolocation',
    'addEventListener'
  );
});


module.exports = BackgroundGeolocation;
