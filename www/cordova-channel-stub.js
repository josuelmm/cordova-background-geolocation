/**
 * Stub for cordova/channel so the plugin can be bundled by webpack (ng serve / browser).
 * When running inside Cordova (native), uses the real cordova channel.
 * When running in browser, provides deviceready.subscribe() that runs the callback on load.
 */
function getChannel() {
  if (typeof window !== 'undefined' && window.cordova && typeof window.cordova.require === 'function') {
    try {
      return window.cordova.require('cordova/channel');
    } catch (e) {}
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

module.exports = getChannel();
