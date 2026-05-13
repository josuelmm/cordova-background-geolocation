---
layout: default
has_children: true
title: Location Providers
nav_order: 7
---

# Location Providers

The plugin ships three providers. Choose **one** in `configure()` via `locationProvider`. Each has different battery/accuracy/coverage tradeoffs.

## Compatibility matrix

| Provider | Android + GMS | Android sin GMS | iOS | Use case |
|---|:---:|:---:|:---:|---|
| `DISTANCE_FILTER_PROVIDER` (default) | ✅ FLP | ✅ LocationManager | ✅ CoreLocation | **Universal default.** Personal tracking, couriers. |
| `ACTIVITY_PROVIDER` | ✅ FLP + ActivityRecognition | ❌ requiere GMS | ✅ CoreLocation + CMMotionActivityManager | Mejor ahorro con detección de actividad. |
| `RAW_PROVIDER` | ✅ LocationManager | ✅ LocationManager | ✅ CoreLocation | Tracking básico/constante, mayor batería. |

> **GMS** = Google Play Services. Necesario para `ActivityRecognitionClient`. `FusedLocationProviderClient` se usa **opcionalmente** cuando está disponible (DISTANCE_FILTER es híbrido desde 4.5.4).

---

## DISTANCE_FILTER_PROVIDER (default)

Híbrido desde v4.5.4:

- **Con Google Play Services** → `FusedLocationProviderClient` + `LocationCallback`. Mezcla GPS+Network internamente, mejor batería, aplica `setMinUpdateDistanceMeters` y `Priority.*`.
- **Sin Google Play Services** (Huawei/HMS, AOSP, ChinaROMs) → fallback automático a `android.location.LocationManager` + `LocationListener`. Suscribe a GPS y Network simultáneamente cuando ambos están habilitados.

State machine compartida en ambos paths:
1. **Moving** — sampleo normal con `distanceFilter`.
2. **Acquiring** — burst alta frecuencia/alta precisión para fijar velocidad o ubicación estacionaria (5 muestras max).
3. **Stationary** — apaga GPS, programa `AlarmManager.setInexactRepeating` para poll lazy (3 min) o aggressive (1 min cerca del borde). **Sin geofences** desde v4.5.4 (decisión de producto).

**iOS:** Core Location con `pausesLocationUpdatesAutomatically`, `activityType`, region monitoring para detectar stationary exit.

Recomendado para: la mayoría de apps personales, couriers, tracking de uso general. Si dudas, **usa este**.

Opciones relevantes:
- `desiredAccuracy`, `distanceFilter`, `stationaryRadius`, `interval`
- `stationaryTimeout`, `stationaryPollInterval`, `stationaryPollFast` (Android)
- `maxAcceptedAccuracy` (filtro global)

---

## ACTIVITY_PROVIDER

**Android (4.5.4+):** `FusedLocationProviderClient` + `ActivityRecognitionClient`. **Requiere Google Play Services** + permiso `ACTIVITY_RECOGNITION` (Android 10+). En dispositivos sin GMS este provider no funciona — usar `DISTANCE_FILTER_PROVIDER` o `RAW_PROVIDER`.

**iOS (4.5.4+):** `CMMotionActivityManager` directo (CoreMotion). Sustituye la dependencia legacy de SOMotionDetector. Requiere permiso "Motion & Fitness". Confidence normalizada Low/Med/High → 20/40/80 para paridad con Android.

Comportamiento:
- Suscribe a updates de actividad. Filtra por `activityConfidenceThreshold` (default 50).
- Cuando se detecta **STILL** con confianza ≥ umbral → marca para pausar GPS al próximo fix.
- Cuando se detecta **ACTIVE** (walking, running, cycling, automotive) → arranca GPS.
- **UNKNOWN** → no toca el state machine ni tracking (uncertainty no debe pausar GPS).

Caveats:
- Android sin `ACTIVITY_RECOGNITION`: emite `PERMISSION_DENIED_ERROR` y **degrada a tracking continuo** (sin pausa por STILL).
- Para máxima compatibilidad multi-dispositivo, usar `DISTANCE_FILTER_PROVIDER` como default.

Opciones relevantes:
- `activitiesInterval`, `fastestInterval`, `interval`
- `activityConfidenceThreshold` (0–100, default 50)
- `stopOnStillActivity` (default true — necesario para que el state machine funcione)

---

## RAW_PROVIDER

`android.location.LocationManager` directo en Android, Core Location directo en iOS. **No tiene state machine** — cada fix que produce el OS se reenvía.

Desde v4.5.4 Android suscribe a GPS+Network simultáneamente y elige según `desiredAccuracy`:
- `< 1000m` → incluye GPS.
- `≥ 10m` → incluye Network.
- `≥ 1000m` → solo Network.

Recomendado para: vehículos con interval constante, dashcams, casos donde quieras el GPS "crudo" sin la inteligencia del plugin. **Mayor consumo de batería** porque no hay detección stationary.

Caveats:
- `onProviderDisabled` emite `SERVICE_ERROR` cuando no queda fallback (ambos GPS y Network deshabilitados).
- No usa `FusedLocationProviderClient` — no requiere GMS.

---

## Recommendation

```js
// Default universal — funciona en cualquier Android + iOS
locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER

// Mejor ahorro de batería con detección de actividad — solo Android con GMS / iOS
locationProvider: BackgroundGeolocation.ACTIVITY_PROVIDER

// Tracking básico de alta frecuencia (vehículos, dashcams)
locationProvider: BackgroundGeolocation.RAW_PROVIDER
```

Ver también: [`battery.md`](battery.md), [`permissions.md`](permissions.md), [`troubleshooting.md`](troubleshooting.md).
