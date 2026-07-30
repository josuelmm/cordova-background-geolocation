/**
 * Stub for cordova/channel so the plugin can be bundled by webpack (ng serve / browser).
 * When running inside Cordova (native), uses the real cordova channel.
 * When running in browser, provides deviceready.subscribe() that runs the callback on load.
 */
function getChannel() {
  if (typeof window !== 'undefined' && window.cordova && typeof window.cordova.require === 'function') {
    try {
      return window.cordova.require('cordova/channel');
    } catch (e) {
      console.warn('[BackgroundGeolocation] cordova.require("cordova/channel") failed, falling back to the browser stub:', e);
    }
  }
  return {
    deviceready: {
      subscribe: function (cb) {
        if (typeof window !== 'undefined') {
          if (document.readyState === 'complete') {
            setTimeout(cb, 0);
          } else {
            window.addEventListener('load', cb);
          }
        }
      }
    }
  };
}

// Resolve the channel lazily on every call. Evaluating getChannel() at import time
// caches the decision forever: if window.cordova is not defined yet when this module
// is first required, the browser stub would stay pinned even on a real device and the
// native deviceready listener would never be registered.
module.exports = {
  deviceready: {
    subscribe: function (cb) {
      getChannel().deviceready.subscribe(cb);
    }
  }
};
