/**
 * Stub for cordova/exec so the plugin can be bundled by webpack (ng serve / browser).
 * When running inside Cordova (native), delegates to the real cordova.exec.
 * When running in browser, calls the failure callback so the app does not break.
 */
function exec(success, fail, service, method, args) {
  if (typeof window !== 'undefined' && window.cordova && typeof window.cordova.exec === 'function') {
    return window.cordova.exec(success, fail, service, method, args || []);
  }
  if (typeof fail === 'function') {
    fail({ message: 'Cordova is not available. Run on a device or emulator.' });
  }
}

module.exports = exec;
