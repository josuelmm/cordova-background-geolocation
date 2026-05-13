---
layout: default
title: Troubleshooting
nav_order: 10
---

# Troubleshooting

Casos típicos y cómo diagnosticarlos. Antes que nada, **siempre** correr `getDiagnostics()` y revisar:

```js
const d = await BackgroundGeolocation.getDiagnostics();
console.log(d);
```

Devuelve: `isRunning`, `locationServicesEnabled`, `lastLocationAt`, `pendingSyncCount`, permisos vigentes, OEM, foregroundServiceType, low-power mode, etc.

---

## No llegan ubicaciones en background

**Causas probables:**

1. **Falta `ACCESS_BACKGROUND_LOCATION`** (Android 10+) — `getDiagnostics().backgroundLocationGranted === false`. Fix: `requestBackgroundLocationPermission()` + guiar al usuario a "Allow all the time".
2. **OEM agresivo (Xiaomi/Huawei/Oppo/Vivo)** — `getDiagnostics().manufacturer` muestra el fabricante. Fix: pedirle al usuario que desactive la optimización de batería para la app. Usar `requestIgnoreBatteryOptimizations()` / `openBatterySettings()` / `openAutoStartSettings()`.
3. **iOS Background Modes mal configurado** — Xcode → Signing & Capabilities → Background Modes → Location updates debe estar activado.
4. **iOS `pauseLocationUpdates: true` + `activityType` agresivo** — el OS pausa updates al detectar quietud prolongada. Cambiar `activityType` o setear `pauseLocationUpdates: false`.

---

## No funciona en Huawei / AOSP / sin Play Services

**Síntoma:** ACTIVITY_PROVIDER no produce updates.

**Causa:** `ACTIVITY_PROVIDER` requiere `ActivityRecognitionClient` (Google Play Services). Sin GMS no funciona.

**Fix:** usar `DISTANCE_FILTER_PROVIDER` (híbrido desde v4.5.4, automáticamente fallback a `LocationManager` cuando no hay GMS) o `RAW_PROVIDER`.

```js
BackgroundGeolocation.configure({
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  // ...
});
```

Verificar en logs: `DISTANCE_FILTER_PROVIDER falling back to LocationManager (Play Services unavailable, code=N)`.

---

## No inicia al reiniciar (startOnBoot)

**Requisitos para `startOnBoot`:**

1. `startOnBoot: true` en config (persiste en SQLite).
2. `stopOnTerminate: false` o config persistida correctamente.
3. `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (Android 10+) concedidos antes del reboot.
4. El OEM no debe bloquear el autostart.

**Comprobaciones:**

- Después de reboot: `adb logcat | grep BootCompletedReceiver`. Debe loggear "Received boot/replace broadcast: android.intent.action.BOOT_COMPLETED".
- Si en el log aparece "Skipping start on boot: ACCESS_FINE_LOCATION...": el permiso se revocó tras el reboot (común en Android 11+ "auto-revoke unused permissions").
- Xiaomi MIUI: Settings → Permissions → Autostart → habilitar para la app.
- Huawei: Settings → Apps → app → Battery → "Manage manually" → permitir Auto-launch + Secondary launch.

---

## No llegan events al backend

**Síntoma:** `hardBrake`, `speeding`, etc. se emiten en JS pero el server no los recibe.

**Causas:**

1. **`postTemplate` custom no incluye `@events`** — `postTemplate` reemplaza el payload completo (no hace merge). Si no incluyes `@events` literal, el plugin no lo manda. Fix:
    ```js
    postTemplate: {
      lat: '@latitude',
      lng: '@longitude',
      events: '@events',
      battery: '@battery',
      isCharging: '@isCharging'
    }
    ```
2. **Buffer cap (20) o TTL (60s) excedido** — los `pendingDrivingEvents` son in-memory; si el detector dispara más eventos que el cap o pasan >60s sin un fix, se descartan. Reducir `interval` o subir el cap (no expuesto en config; abrir issue si bloquea).
3. **POST OK pero sync queue no manda events** — verificar schema SQLite (v22 Android / v7 iOS). Versiones anteriores a 4.5.0 no persisten events. Reinstalar plugin o ejecutar auto-migración (debería pasar al primer `configure()`).

---

## POST funciona pero sync no

**Síntoma:** locations en real-time llegan, pero cuando la red cae no se reenvían después.

**Causas:**

1. **`syncUrl` no definido** — la cola de sync requiere `syncUrl` explícito.
2. **`sync: false`** — la sincronización está deshabilitada por config.
3. **`syncThreshold` muy alto** — sync solo dispara cuando hay ≥ N locations pendientes. Default 100. Bajar para forzar sync más temprano.
4. **`forceSync()` no se llama** — el sync automático corre cada N (depende del OS/Doze). Forzar manualmente cuando la app vuelve a foreground.

Verificar: `getPendingSyncCount()` debe disminuir tras `forceSync()` exitoso. Si no, revisar logs Android (`adb logcat | grep "MAURBackgroundSync\|BackgroundSync"`).

---

## Notificación no aparece (Android 13+)

**Causa:** falta `POST_NOTIFICATIONS` (concedido por el usuario).

**Fix:**

```js
await BackgroundGeolocation.requestNotificationPermission();
```

Comprobar en `getDiagnostics().notificationPermissionGranted`. Si `false`, el servicio sigue corriendo (es foreground por otra razón) pero el usuario no ve la notificación.

---

## ACTIVITY_PROVIDER no detecta STILL (Android)

**Síntomas:** el provider funciona pero nunca pausa por estar quieto.

**Causas:**

1. **Falta `ACTIVITY_RECOGNITION`** (Android 10+) — `getDiagnostics().activityRecognitionGranted === false`. El provider degrada a tracking continuo. Fix: pedir el permiso.
2. **Confianza siempre baja** — `activityConfidenceThreshold` por defecto es 50; si el dispositivo o el ambiente tiene mucho ruido, todas las transiciones se filtran. Bajar el umbral a 30 temporalmente para depurar.
3. **`stopOnStillActivity: false`** — el plugin ignora STILL si esto está deshabilitado. Default es `true`; verificar config.

---

## ACTIVITY_PROVIDER no detecta STILL (iOS)

**Causas:**

1. **Falta permiso "Motion & Fitness"** — verificar en Settings → Privacy & Security → Motion & Fitness → app activa.
2. **Dispositivo sin motion coprocessor** — `getDiagnostics().motionPermissionStatus` mostrará si está disponible. Modelos pre-iPhone 5s no tienen CoreMotion.
3. **`activityConfidenceThreshold` mal calibrado** — iOS normaliza Low/Med/High a 20/40/80. Umbral 50 filtra los `Low`. Si querés también filtrar `Medium`, subir a 50+ (default ya hace esto).

---

## El plugin "se cuelga" en Android 12+ al startear

**Causa:** `ForegroundServiceStartNotAllowedException`. Android 12+ requiere que el foreground service se inicie cuando la app está visible.

**Fix:** invocar `start()` desde un user gesture (botón) o desde un Activity visible. No desde `BootCompletedReceiver` si la app no tiene exenciones. El plugin lo captura y loggea, pero no puede sortear la restricción.

Verificar logs: `Start on boot blocked: ForegroundServiceStartNotAllowedException`.

---

## iOS no emite stationary inicial al arrancar quieto

**Síntoma con ACTIVITY_PROVIDER (iOS):** abrís la app estando quieto y no llega ni location ni stationary.

**Estado:** corregido en v4.5.4. `onStart` ahora arranca `startTracking` inmediatamente para tomar el primer fix; cuando CoreMotion confirma STILL, el primer fix se emite como `onStationaryChanged` y el GPS se apaga.

Si lo ves en una versión < 4.5.4: actualizar.

---

## Heartbeat no dispara

**Síntoma:** registraste listener de `heartbeat` pero nunca llega.

**Causas:**

1. `heartbeatInterval: 0` (default) — feature desactivada.
2. iOS app suspendida sin background mode "Location updates" — el `NSTimer` no corre.
3. `heartbeatInterval` muy alto para la sesión de prueba.

---

## La app crashea con NPE en `hasMockLocationsEnabled`

**Estado:** corregido en v4.5.4. `Settings.Secure.getString` podía devolver null en algunos dispositivos. Si lo ves en 4.5.1: actualizar.

---

## Comandos útiles

```bash
# Android — logs del plugin
adb logcat | grep -E "BackgroundGeolocation|MAURBackgroundGeolocation|LocationServiceImpl"

# Android — stats batería
adb shell dumpsys batterystats --reset
# ... usar app 1h ...
adb shell dumpsys batterystats > out.txt

# Android — estado del foreground service
adb shell dumpsys activity services | grep -A 20 LocationServiceImpl

# iOS — capturar logs en Console.app filtrando por bundle id
```

Si nada de lo anterior aplica: abrir issue con `getDiagnostics()` completo + logs.
