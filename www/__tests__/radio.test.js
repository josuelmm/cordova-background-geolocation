/**
 * Regression test: subscribe -> unsubscribe -> subscribe again must NOT accumulate listeners.
 *
 * Plain Node, no framework:
 *   node www/__tests__/radio.test.js
 *
 * Covers two historical bugs:
 *
 *  1) BackgroundGeolocation.on(event) with no callback used to `return radio(event)`,
 *     i.e. the radio.$ SINGLETON. Its `channelName` is global state that mutates on every
 *     radio(x) call and on every broadcast, so a later `unsubscribe(cb)` operated on
 *     whatever channel happened to be selected last and the listener was never removed.
 *
 *  2) radio.subscribe() pushed without checking for duplicates and radio.unsubscribe()
 *     removed EVERY match, so subscribe/unsubscribe cycles were asymmetric.
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

var radio = require(path.join(WWW, 'radio.js'));
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
  check(name, actual === expected, 'expected ' + expected + ', got ' + actual);
}

function listenerCount(event) {
  var c = radio.$.channels[event];
  return c ? c.length : 0;
}

function section(title) {
  console.log('\n' + title);
}

// ------------------------------------------------- 1. radio: subscribe/unsubscribe symmetry

section('radio.js — subscribe/unsubscribe symmetry');

radio(); // reset all channels

var cb = function () {};

radio('t1').subscribe(cb);
eq('subscribe once -> 1 listener', listenerCount('t1'), 1);

radio('t1').subscribe(cb);
eq('subscribe same callback twice -> still 1 listener (dedup)', listenerCount('t1'), 1);

radio('t1').unsubscribe(cb);
eq('unsubscribe once -> 0 listeners', listenerCount('t1'), 0);

// one unsubscribe() must undo exactly one subscribe(), never more
var other = function () {};
radio('t2').subscribe(cb);
radio('t2').subscribe(other);
eq('two distinct callbacks -> 2 listeners', listenerCount('t2'), 2);
radio('t2').unsubscribe(cb);
eq('unsubscribe removes only the matching callback', listenerCount('t2'), 1);
check('the surviving listener is the other callback', radio.$.channels['t2'][0][0] === other);
radio('t2').unsubscribe(other);
eq('channel drained', listenerCount('t2'), 0);

// unsubscribing something that was never subscribed must be a no-op
radio('t2').unsubscribe(cb);
eq('unsubscribe of unknown callback is a no-op', listenerCount('t2'), 0);

// ------------------------------------------- 2. on(event) without callback: no leak

section('BackgroundGeolocation.on(event) without callback — no listener accumulation');

radio(); // reset

var EVENT = 'location';
var received = 0;
var handler = function () { received++; };

var CYCLES = 50;
for (var i = 0; i < CYCLES; i++) {
  var sub = BackgroundGeolocation.on(EVENT).subscribe(handler);

  // Mutate the radio singleton's channelName in between, exactly like a real app does.
  // This is what used to break unsubscribe(): it operated on the wrong channel.
  radio('activity').broadcast({ type: 'STILL' });
  BackgroundGeolocation.on('error');

  sub.unsubscribe();
}

eq('after ' + CYCLES + ' subscribe/unsubscribe cycles -> 0 listeners', listenerCount(EVENT), 0);

// after a full cycle nothing must be delivered any more
radio(EVENT).broadcast({ latitude: 1 });
eq('broadcast after unsubscribe delivers nothing', received, 0);

// and a fresh subscription must be delivered exactly once
var sub2 = BackgroundGeolocation.on(EVENT).subscribe(handler);
eq('re-subscribe after full cycle -> 1 listener', listenerCount(EVENT), 1);
radio(EVENT).broadcast({ latitude: 2 });
eq('broadcast delivered exactly once (no duplicates)', received, 1);
sub2.unsubscribe();
eq('final unsubscribe -> 0 listeners', listenerCount(EVENT), 0);

// the object returned by on(event) also exposes unsubscribe(cb) directly
var chan = BackgroundGeolocation.on(EVENT);
chan.subscribe(handler);
radio('activity').broadcast({ type: 'WALKING' }); // move the singleton channelName again
chan.unsubscribe(handler);
eq('channel.unsubscribe(cb) removes the listener despite channelName drift', listenerCount(EVENT), 0);

// ------------------------------------------- 3. on(event, callback): remove() works

section('BackgroundGeolocation.on(event, callback) — remove() is symmetric');

radio(); // reset

var received2 = 0;
var handler2 = function () { received2++; };

for (var j = 0; j < CYCLES; j++) {
  var subscription = BackgroundGeolocation.on(EVENT, handler2);
  radio('error').broadcast(new Error('noise')); // channelName drift
  subscription.remove();
}
eq('after ' + CYCLES + ' on()/remove() cycles -> 0 listeners', listenerCount(EVENT), 0);

var s3 = BackgroundGeolocation.on(EVENT, handler2);
radio(EVENT).broadcast({ latitude: 3 });
eq('callback invoked exactly once', received2, 1);
s3.remove();
radio(EVENT).broadcast({ latitude: 4 });
eq('callback not invoked after remove()', received2, 1);

// ------------------------------------------- 4. removeAllListeners

section('BackgroundGeolocation.removeAllListeners()');

radio(); // reset

BackgroundGeolocation.on(EVENT).subscribe(function () {});
BackgroundGeolocation.on(EVENT).subscribe(function () {});
BackgroundGeolocation.on('activity').subscribe(function () {});
eq('two listeners on ' + EVENT, listenerCount(EVENT), 2);

BackgroundGeolocation.removeAllListeners(EVENT);
eq('removeAllListeners(event) drains that channel', listenerCount(EVENT), 0);
eq('removeAllListeners(event) leaves other channels alone', listenerCount('activity'), 1);

BackgroundGeolocation.removeAllListeners();
eq('removeAllListeners() drains every channel', listenerCount('activity'), 0);

// ------------------------------------------- 5. execWithPromise always returns a Promise

section('execWithPromise always returns a Promise');

var pending = [];

var r1 = BackgroundGeolocation.start();
check('start() returns a Promise', r1 instanceof Promise);
pending.push(r1);

var r2 = BackgroundGeolocation.getConfig(function () {});
check('getConfig(success) returns a Promise', r2 instanceof Promise);
pending.push(r2);

var r3 = BackgroundGeolocation.checkStatus(null, function () {});
check('checkStatus(null, fail) returns a Promise', r3 instanceof Promise);
pending.push(r3);

// Outside Cordova the exec stub must reject with a real Error carrying a code.
r1.then(
  function () {
    check('exec stub rejects outside Cordova', false, 'promise resolved instead');
    report();
  },
  function (err) {
    check('exec stub rejects with an Error instance', err instanceof Error);
    eq('rejection carries code 0', err.code, 0);
    report();
  }
);

// swallow the other rejections so Node does not report them as unhandled
pending.forEach(function (p) { p.catch(function () {}); });

// ---------------------------------------------------------------- report

function report() {
  console.log('\n' + '-'.repeat(52));
  console.log(failures === 0 ? 'OK   — ' + passes + ' assertions passed'
                             : 'FAIL — ' + failures + ' of ' + (passes + failures) + ' assertions failed');
  process.exit(failures === 0 ? 0 : 1);
}
