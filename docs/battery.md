---
layout: default
title: Battery optimization
nav_order: 8
---

# Battery Optimization Guide

GPS background tracking siempre consume batería. El plugin minimiza el costo combinando varias palancas. Esta guía resume cuál ajustar para cada caso de uso.

## Opciones que afectan batería

| Opción | Efecto | Default |
|---|---|---|
| `locationProvider` | Provider activo (state machine vs raw) | `DISTANCE_FILTER_PROVIDER` |
| `desiredAccuracy` | Mapea a `Priority.*` (FLP) / `kCLLocationAccuracy*` (iOS) | `100` (BALANCED) |
| `distanceFilter` | Mínima distancia (m) entre fixes emitidos | `500` |
| `stationaryRadius` | Radio de la región stationary | `50` |
| `interval` | Intervalo deseado entre fixes (Android, FLP) | `600000` (10 min) |
| `fastestInterval` | Tope superior si otra app pide más rápido (Android) | `120000` (2 min) |
| `activitiesInterval` | Frecuencia de detección de actividad | `10000` (10 s) |
| `activityConfidenceThreshold` | Filtra transiciones STILL/ACTIVE débiles | `50` |
| `maxAcceptedAccuracy` | Descarta fixes peores que este valor (m) | `null` (off) |
| `stationaryTimeout` | ms sin movimiento → declarar stationary | `300000` (5 min) |
| `stationaryPollInterval` | Poll lazy mientras stationary (ms) | `180000` (3 min) |
| `stationaryPollFast` | Poll aggressive cerca del borde (ms) | `60000` (1 min) |
| `wakeLockMode` | Política de `PARTIAL_WAKE_LOCK` (Android) | `'posting'` |
| `enableWatchdog` | Reinicia provider si no hay fixes ~60s | `false` |
| `pauseLocationUpdates` | iOS pausa updates al detectar quietud | `false` |
| `saveBatteryOnBackground` | iOS usa significant changes en background | `false` |

## wakeLockMode (Android)

Decide cuándo el plugin mantiene CPU despierto:

- **`'none'`** — nunca. Mejor batería; usar solo con `httpMode: 'batch'` (la red puede caerse mid-POST sin esto).
- **`'posting'` (default)** — wake lock de 30s alrededor de cada fix (SQLite + POST). Recomendado.
- **`'always'`** — CPU despierta todo el tiempo del servicio. Legacy, solo fleet/emergency.

## Recommended profiles

### Tracking personal / couriers (ahorro máximo)

```js
{
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.MEDIUM_ACCURACY,  // 100m
  distanceFilter: 100,
  stationaryRadius: 50,
  wakeLockMode: 'posting',
  enableWatchdog: false,
  // iOS:
  pauseLocationUpdates: true,
  saveBatteryOnBackground: true
}
```

### Apps con detección de actividad (mejor ahorro si hay GMS)

```js
{
  locationProvider: BackgroundGeolocation.ACTIVITY_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  interval: 30000,
  fastestInterval: 10000,
  activitiesInterval: 10000,
  activityConfidenceThreshold: 50,
  stopOnStillActivity: true,
  wakeLockMode: 'posting'
}
```

### Vehicular / fleet (precisión por encima de batería)

```js
{
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  distanceFilter: 30,
  stationaryRadius: 30,
  interval: 5000,
  maxAcceptedAccuracy: 100,        // descarta fixes peores que 100m
  wakeLockMode: 'posting',
  enableWatchdog: true              // reinicia si no llega location
}
```

### Tracking continuo / dashcam

```js
{
  locationProvider: BackgroundGeolocation.RAW_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  interval: 1000,
  distanceFilter: 0,
  wakeLockMode: 'always'   // máximo consumo, mínimo riesgo de gap
}
```

## OEM-specific battery optimizations

Xiaomi MIUI, Huawei EMUI, Oppo ColorOS, Vivo FunTouch y Samsung One UI matan agresivamente apps en background, incluso con foreground service. El plugin no puede sortearlas por sí solo. Usar los helpers:

- `isIgnoringBatteryOptimizations()` — comprueba whitelist.
- `requestIgnoreBatteryOptimizations()` — dispara diálogo del sistema.
- `openBatterySettings()` — abre la pantalla de batería de la app.
- `openAutoStartSettings()` — abre el panel OEM (auto-start, background activity).
- `getManufacturerHelp()` — devuelve pasos textuales que la app puede mostrar al usuario.

Sin esto, en algunos modelos el servicio se mata al cerrar la app aunque `stopOnTerminate: false`. Es una limitación de Android, no del plugin.

## Doze / App Standby

A partir de Android 6+, el sistema aplica Doze (deep sleep) tras tiempo sin interacción. Mitigaciones:

- Foreground service con `foregroundServiceType="location"` (ya configurado por el plugin).
- `AlarmManager.setInexactRepeating` para el polling stationary — Doze-compatible.
- En Android 12+ aplican `ForegroundServiceStartNotAllowedException` si la app está en background sin razón. El plugin lo captura y loggea en `BootCompletedReceiver` y `LocationServiceImpl`.

## Cómo medir consumo

Sin métricas reales no podés tunear:

1. `adb shell dumpsys batterystats --reset`
2. Usar la app por 1 hora.
3. `adb shell dumpsys batterystats > out.txt`
4. Buscar el package en `out.txt` — sección "Estimated power use".

iOS: Settings → Battery → últimas 24 h, listar app.

Combinar con `getDiagnostics()` para ver: último fix, último sync, pending count, modo GMS/fallback. Si `lastLocationAt` no se actualiza, ningún tuning de batería va a ayudar — investigá permisos / OEM primero.

Ver también: [`providers.md`](providers.md), [`permissions.md`](permissions.md), [`troubleshooting.md`](troubleshooting.md).
