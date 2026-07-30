/**
 * Regression test: every `export enum` declared in www/BackgroundGeolocation.d.ts must have
 * a matching RUNTIME value on the module.
 *
 * Plain Node, no framework:
 *   node www/__tests__/enums.test.js
 *
 * Historical bug: `export enum X` inside a .d.ts is a TYPE-ONLY declaration — it emits no
 * JavaScript. App code such as `BackgroundGeolocationEvents.location` type-checked fine and
 * was `undefined` at runtime, because BackgroundGeolocation.js never defined the objects.
 *
 * The EXPECTED tables below are transcribed BY HAND from the .d.ts on purpose. Nothing here
 * parses the .d.ts at test time: a hand-written copy is what makes the test able to catch a
 * drift between the declaration and the runtime value.
 */

'use strict';

var path = require('path');
var Module = require('module');

var WWW = path.join(__dirname, '..');

// Resolve the Cordova modules to the browser stubs shipped in www/.
var originalResolve = Module._resolveFilename;
Module._resolveFilename = function (request) {
  if (request === 'cordova/exec') return path.join(WWW, 'cordova-exec-stub.js');
  if (request === 'cordova/channel') return path.join(WWW, 'cordova-channel-stub.js');
  return originalResolve.apply(this, arguments);
};

var BackgroundGeolocation = require(path.join(WWW, 'BackgroundGeolocation.js'));

// ---------------------------------------------------------------- test harness

var failures = 0;
var passes = 0;

function check(name, condition, detail) {
  if (condition) {
    passes++;
    console.log('  OK   ' + name);
  } else {
    failures++;
    console.log('  FAIL ' + name + (detail ? ' -> ' + detail : ''));
  }
}

function eq(name, actual, expected) {
  check(name, actual === expected, 'expected ' + JSON.stringify(expected) + ', got ' + JSON.stringify(actual));
}

function section(title) {
  console.log('\n' + title);
}

// ------------------------------------------------------------ expected members
//
// Transcribed by hand from the `export enum` blocks in www/BackgroundGeolocation.d.ts.

var EXPECTED = {
  // .d.ts: export enum BackgroundGeolocationEvents  (28 members, name === value)
  BackgroundGeolocationEvents: {
    http_authorization: 'http_authorization',
    abort_requested: 'abort_requested',
    background: 'background',
    foreground: 'foreground',
    authorization: 'authorization',
    error: 'error',
    stop: 'stop',
    start: 'start',
    activity: 'activity',
    stationary: 'stationary',
    location: 'location',
    heartbeat: 'heartbeat',
    syncStart: 'syncStart',
    syncProgress: 'syncProgress',
    syncSuccess: 'syncSuccess',
    syncError: 'syncError',
    tripStart: 'tripStart',
    tripEnd: 'tripEnd',
    moving: 'moving',
    stopped: 'stopped',
    speeding: 'speeding',
    providerChange: 'providerChange',
    sos: 'sos',
    hardBrake: 'hardBrake',
    rapidAcceleration: 'rapidAcceleration',
    sharpTurn: 'sharpTurn',
    possibleCrash: 'possibleCrash',
    phoneUsageWhileDriving: 'phoneUsageWhileDriving'
  },

  // .d.ts: export enum BackgroundGeolocationLocationCode  (3 members)
  BackgroundGeolocationLocationCode: {
    PERMISSION_DENIED: 1,
    LOCATION_UNAVAILABLE: 2,
    TIMEOUT: 3
  },

  // .d.ts: export enum BackgroundGeolocationNativeProvider  (4 members)
  BackgroundGeolocationNativeProvider: {
    gps: 'gps',
    network: 'network',
    passive: 'passive',
    fused: 'fused'
  },

  // .d.ts: export enum BackgroundGeolocationLocationProvider  (3 members)
  BackgroundGeolocationLocationProvider: {
    DISTANCE_FILTER_PROVIDER: 0,
    ACTIVITY_PROVIDER: 1,
    RAW_PROVIDER: 2
  },

  // .d.ts: export enum BackgroundGeolocationAuthorizationStatus  (3 members)
  BackgroundGeolocationAuthorizationStatus: {
    NOT_AUTHORIZED: 0,
    AUTHORIZED: 1,
    AUTHORIZED_FOREGROUND: 2
  },

  // .d.ts: export enum BackgroundGeolocationLogLevel  (5 members)
  BackgroundGeolocationLogLevel: {
    TRACE: 'TRACE',
    DEBUG: 'DEBUG',
    INFO: 'INFO',
    WARN: 'WARN',
    ERROR: 'ERROR'
  },

  // .d.ts: export enum BackgroundGeolocationProvider  (3 members)
  BackgroundGeolocationProvider: {
    ANDROID_DISTANCE_FILTER_PROVIDER: 0,
    ANDROID_ACTIVITY_PROVIDER: 1,
    RAW_PROVIDER: 2
  },

  // .d.ts: export enum BackgroundGeolocationAccuracy  (4 members)
  // NOTE: this plugin's values, not @awesome-cordova-plugins' 10/100/1000.
  BackgroundGeolocationAccuracy: {
    HIGH: 0,
    MEDIUM: 100,
    LOW: 1000,
    PASSIVE: 10000
  },

  // .d.ts: export enum BackgroundGeolocationMode  (2 members)
  BackgroundGeolocationMode: {
    BACKGROUND: 0,
    FOREGROUND: 1
  },

  // .d.ts: export enum BackgroundGeolocationIOSActivity  (4 members)
  BackgroundGeolocationIOSActivity: {
    AutomotiveNavigation: 'AutomotiveNavigation',
    OtherNavigation: 'OtherNavigation',
    Fitness: 'Fitness',
    Other: 'Other'
  }
};

var ENUM_NAMES = Object.keys(EXPECTED);

// ------------------------------------------------- 1. every enum exists at runtime

section('BackgroundGeolocation.js — enum objects exist at runtime');

eq('all 10 .d.ts enums are covered by this test', ENUM_NAMES.length, 10);

ENUM_NAMES.forEach(function (name) {
  var actual = BackgroundGeolocation[name];
  check(name + ' is an object at runtime (not undefined)',
    actual !== null && typeof actual === 'object',
    'got ' + typeof actual);
});

// ------------------------------------------- 2. member names and values match the .d.ts

section('enum members match the .d.ts declarations exactly');

ENUM_NAMES.forEach(function (name) {
  var expected = EXPECTED[name];
  var actual = BackgroundGeolocation[name] || {};
  var memberNames = Object.keys(expected);

  memberNames.forEach(function (member) {
    // `in` rather than a truthiness check: values 0 and '' are legitimate.
    check(name + '.' + member + ' exists', Object.prototype.hasOwnProperty.call(actual, member));
    eq(name + '.' + member, actual[member], expected[member]);
  });

  // No forward member beyond the declaration. Numeric enums additionally carry the
  // reverse mapping a real TypeScript enum has, so those keys are expected too.
  var allowed = {};
  memberNames.forEach(function (member) {
    allowed[member] = true;
    if (typeof expected[member] === 'number') {
      allowed[String(expected[member])] = true;
    }
  });
  var extra = Object.keys(actual).filter(function (key) { return !allowed[key]; });
  check(name + ' declares no members beyond the .d.ts', extra.length === 0, 'extra: ' + extra.join(', '));
});

// -------------------------------------- 3. numeric enums are bidirectional, like TS enums

section('numeric enums keep the TypeScript reverse mapping');

ENUM_NAMES.forEach(function (name) {
  var expected = EXPECTED[name];
  Object.keys(expected).forEach(function (member) {
    var value = expected[member];
    if (typeof value !== 'number') return;
    eq(name + '[' + value + '] === ' + JSON.stringify(member), BackgroundGeolocation[name][value], member);
  });
});

// ------------------------------------------------------------ 4. enums are frozen

section('enum objects are frozen');

ENUM_NAMES.forEach(function (name) {
  var target = BackgroundGeolocation[name];
  check(name + ' is frozen', Object.isFrozen(target));

  // Mutation must not take effect (silently ignored in sloppy mode, throws in strict).
  var firstMember = Object.keys(EXPECTED[name])[0];
  var before = target[firstMember];
  try {
    target[firstMember] = '__mutated__';
  } catch (e) { /* strict-mode TypeError is fine */ }
  eq(name + '.' + firstMember + ' survives a write attempt', target[firstMember], before);

  try {
    target.__injected__ = 1;
  } catch (e) { /* strict-mode TypeError is fine */ }
  check(name + ' rejects a new property', target.__injected__ === undefined);
});

// ------------------------- 5. enums stay consistent with the plugin's flat constants

section('enums agree with the existing flat constants on the plugin object');

eq('LocationCode.PERMISSION_DENIED === PERMISSION_DENIED',
  BackgroundGeolocation.BackgroundGeolocationLocationCode.PERMISSION_DENIED, BackgroundGeolocation.PERMISSION_DENIED);
eq('LocationCode.LOCATION_UNAVAILABLE === LOCATION_UNAVAILABLE',
  BackgroundGeolocation.BackgroundGeolocationLocationCode.LOCATION_UNAVAILABLE, BackgroundGeolocation.LOCATION_UNAVAILABLE);
eq('LocationCode.TIMEOUT === TIMEOUT',
  BackgroundGeolocation.BackgroundGeolocationLocationCode.TIMEOUT, BackgroundGeolocation.TIMEOUT);

eq('LocationProvider.DISTANCE_FILTER_PROVIDER === DISTANCE_FILTER_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationLocationProvider.DISTANCE_FILTER_PROVIDER, BackgroundGeolocation.DISTANCE_FILTER_PROVIDER);
eq('LocationProvider.ACTIVITY_PROVIDER === ACTIVITY_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationLocationProvider.ACTIVITY_PROVIDER, BackgroundGeolocation.ACTIVITY_PROVIDER);
eq('LocationProvider.RAW_PROVIDER === RAW_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationLocationProvider.RAW_PROVIDER, BackgroundGeolocation.RAW_PROVIDER);

eq('AuthorizationStatus.NOT_AUTHORIZED === NOT_AUTHORIZED',
  BackgroundGeolocation.BackgroundGeolocationAuthorizationStatus.NOT_AUTHORIZED, BackgroundGeolocation.NOT_AUTHORIZED);
eq('AuthorizationStatus.AUTHORIZED === AUTHORIZED',
  BackgroundGeolocation.BackgroundGeolocationAuthorizationStatus.AUTHORIZED, BackgroundGeolocation.AUTHORIZED);
eq('AuthorizationStatus.AUTHORIZED_FOREGROUND === AUTHORIZED_FOREGROUND',
  BackgroundGeolocation.BackgroundGeolocationAuthorizationStatus.AUTHORIZED_FOREGROUND, BackgroundGeolocation.AUTHORIZED_FOREGROUND);

eq('LogLevel.TRACE === LOG_TRACE', BackgroundGeolocation.BackgroundGeolocationLogLevel.TRACE, BackgroundGeolocation.LOG_TRACE);
eq('LogLevel.DEBUG === LOG_DEBUG', BackgroundGeolocation.BackgroundGeolocationLogLevel.DEBUG, BackgroundGeolocation.LOG_DEBUG);
eq('LogLevel.INFO === LOG_INFO', BackgroundGeolocation.BackgroundGeolocationLogLevel.INFO, BackgroundGeolocation.LOG_INFO);
eq('LogLevel.WARN === LOG_WARN', BackgroundGeolocation.BackgroundGeolocationLogLevel.WARN, BackgroundGeolocation.LOG_WARN);
eq('LogLevel.ERROR === LOG_ERROR', BackgroundGeolocation.BackgroundGeolocationLogLevel.ERROR, BackgroundGeolocation.LOG_ERROR);

eq('Provider.ANDROID_DISTANCE_FILTER_PROVIDER === DISTANCE_FILTER_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationProvider.ANDROID_DISTANCE_FILTER_PROVIDER, BackgroundGeolocation.DISTANCE_FILTER_PROVIDER);
eq('Provider.ANDROID_ACTIVITY_PROVIDER === ACTIVITY_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationProvider.ANDROID_ACTIVITY_PROVIDER, BackgroundGeolocation.ACTIVITY_PROVIDER);
eq('Provider.RAW_PROVIDER === RAW_PROVIDER',
  BackgroundGeolocation.BackgroundGeolocationProvider.RAW_PROVIDER, BackgroundGeolocation.RAW_PROVIDER);

eq('Accuracy.HIGH === HIGH_ACCURACY', BackgroundGeolocation.BackgroundGeolocationAccuracy.HIGH, BackgroundGeolocation.HIGH_ACCURACY);
eq('Accuracy.MEDIUM === MEDIUM_ACCURACY', BackgroundGeolocation.BackgroundGeolocationAccuracy.MEDIUM, BackgroundGeolocation.MEDIUM_ACCURACY);
eq('Accuracy.LOW === LOW_ACCURACY', BackgroundGeolocation.BackgroundGeolocationAccuracy.LOW, BackgroundGeolocation.LOW_ACCURACY);
eq('Accuracy.PASSIVE === PASSIVE_ACCURACY', BackgroundGeolocation.BackgroundGeolocationAccuracy.PASSIVE, BackgroundGeolocation.PASSIVE_ACCURACY);

eq('Mode.BACKGROUND === BACKGROUND_MODE', BackgroundGeolocation.BackgroundGeolocationMode.BACKGROUND, BackgroundGeolocation.BACKGROUND_MODE);
eq('Mode.FOREGROUND === FOREGROUND_MODE', BackgroundGeolocation.BackgroundGeolocationMode.FOREGROUND, BackgroundGeolocation.FOREGROUND_MODE);

// Every event name in the enum must be accepted by on() — i.e. present in `events`.
Object.keys(EXPECTED.BackgroundGeolocationEvents).forEach(function (member) {
  check('events[] contains BackgroundGeolocationEvents.' + member,
    BackgroundGeolocation.events.indexOf(member) > -1);
});
eq('events[] has no event missing from the enum',
  BackgroundGeolocation.events.length, Object.keys(EXPECTED.BackgroundGeolocationEvents).length);

// ---------------------------------------------------------------- report

console.log('\n' + '-'.repeat(52));
console.log(failures === 0 ? 'OK   — ' + passes + ' assertions passed'
                           : 'FAIL — ' + failures + ' of ' + (passes + failures) + ' assertions failed');
process.exit(failures === 0 ? 0 : 1);
