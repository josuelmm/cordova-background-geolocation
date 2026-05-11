---
layout: default
title: Permissions
nav_order: 9
---

# Permissions

El plugin requiere permisos diferentes según plataforma y versión del OS. El plugin **valida permisos antes de iniciar** y emite errores explícitos cuando faltan, pero **no fuerza todo el flujo moderno automáticamente**. La app debe controlar explícitamente los permisos críticos para Android moderno (`ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`) usando los helpers que el plugin expone o su propio flujo.

## Android — por versión

| Permiso | API mínima | Para qué | Cómo |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | API 1 | Tracking GPS estándar | Diálogo runtime (API 23+) |
| `ACCESS_COARSE_LOCATION` | API 1 | Fallback de menor precisión | Diálogo runtime (API 23+) |
| `ACCESS_BACKGROUND_LOCATION` | API 29 (Android 10) | Tracking en background | `requestBackgroundLocationPermission()` |
| `ACTIVITY_RECOGNITION` | API 29 (Android 10) | `ACTIVITY_PROVIDER` | `requestActivityRecognitionPermission()` |
| `POST_NOTIFICATIONS` | API 33 (Android 13) | Notificación del foreground service | `requestNotificationPermission()` |
| `FOREGROUND_SERVICE` | API 28 (Android 9) | Servicio foreground | Declarativo, no runtime |
| `FOREGROUND_SERVICE_LOCATION` | API 34 (Android 14) | Subtipo `location` del FS | Declarativo, no runtime |
| `WAKE_LOCK` | API 1 | `PARTIAL_WAKE_LOCK` (`wakeLockMode`) | Declarativo |
| `RECEIVE_BOOT_COMPLETED` | API 1 | `startOnBoot` | Declarativo |

Todos los permisos declarativos están en el `plugin.xml` y se mergean en el `AndroidManifest.xml` final de la app durante el build de Cordova/Capacitor.

### Orden recomendado de solicitud

```js
// 1. Foreground location (FINE + COARSE) — diálogo nativo del runtime
//    Hacerlo desde la propia app antes de configure().

// 2. Background location — solo después de FINE concedido (Android 10+)
await BackgroundGeolocation.requestBackgroundLocationPermission();

// 3. Activity recognition — solo si vas a usar ACTIVITY_PROVIDER
await BackgroundGeolocation.requestActivityRecognitionPermission();

// 4. Notifications — solo si vas a usar startForeground (Android 13+)
await BackgroundGeolocation.requestNotificationPermission();
```

Cada helper devuelve `{ granted: boolean, denied?: string[], notRequired?: boolean }`. En Android < 10 / iOS resuelven con `notRequired: true`.

### Comportamiento si falta un permiso

| Permiso ausente | Efecto |
|---|---|
| `ACCESS_FINE_LOCATION` | Plugin no inicia. Emite `PERMISSION_DENIED_ERROR`. |
| `ACCESS_BACKGROUND_LOCATION` (Android 10+) | Tracking funciona solo con app en foreground. En background el OS corta updates. |
| `ACTIVITY_RECOGNITION` (Android 10+) | `ACTIVITY_PROVIDER` emite `PERMISSION_DENIED_ERROR` y **degrada a tracking continuo** (no detecta STILL/ACTIVE). |
| `POST_NOTIFICATIONS` (Android 13+) | Notificación foreground invisible al usuario; servicio sigue corriendo. |

## iOS

| Permiso | Para qué | Cómo se pide |
|---|---|---|
| `NSLocationWhenInUseUsageDescription` | Location en foreground | `Info.plist` + `[CLLocationManager requestWhenInUseAuthorization]` |
| `NSLocationAlwaysAndWhenInUseUsageDescription` | Location en background ("Always") | `Info.plist` + `requestAlwaysAuthorization` |
| `NSMotionUsageDescription` | `CMMotionActivityManager` (ACTIVITY_PROVIDER) | `Info.plist`. Diálogo se dispara al primer uso. |
| Background Modes → Location updates | Continuar tracking con app suspendida | Xcode capability |
| Background Modes → Background fetch | Heartbeat / sync periódico | Opcional |

Configurar en `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Necesitamos tu ubicación para...</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>Necesitamos tu ubicación incluso en background para...</string>
<key>NSMotionUsageDescription</key>
<string>Detectamos cuándo te mueves para ahorrar batería.</string>
```

> Apple rechaza apps que pidan `NSLocationAlways*` sin justificar **claramente** el uso en background. El texto debe explicar la utilidad concreta para el usuario.

### Authorization status

`checkStatus()` y `getDiagnostics()` devuelven el estado actual. iOS 14+ expone también si la "Precise Location" está concedida (vs reducida).

## Comportamiento del plugin

- **No solicita permisos automáticamente.** El integrador debe pedirlos antes de `start()`.
- Emite `onAuthorizationChanged` cuando el usuario cambia permisos en Settings sin reiniciar la app.
- `getDiagnostics()` devuelve flags por permiso para diagnóstico en producción.

## Errores comunes

- **"Pedí background pero el OS lo niega"** — Android 10+ requiere haber concedido `ACCESS_FINE_LOCATION` primero, y el diálogo de background es un "Allow all the time" en la app de Settings, no un popup. Usar `showAppSettings()` para guiar al usuario.
- **"`ACTIVITY_PROVIDER` no pausa por STILL aunque diga "permiso concedido""** — confirma `ACTIVITY_RECOGNITION` (Android 10+) vía `getDiagnostics().activityRecognitionGranted`. Sin ese, ActivityRecognitionClient no entrega broadcasts.
- **"En iOS solo recibo location en foreground"** — Background Modes → Location updates no está habilitado en Xcode, o el `Info.plist` solo tiene `WhenInUse`.

Ver también: [`providers.md`](providers.md), [`troubleshooting.md`](troubleshooting.md), [`production-checklist.md`](production-checklist.md).
