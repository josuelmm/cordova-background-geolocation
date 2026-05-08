# Modernización de location APIs (Fase 3, v3.4)

Estado: **parcialmente entregada en 3.4.0**. Resto pendiente para 3.4.1+.
Objetivo: actualizar los providers Android/iOS a las APIs modernas, eliminar deprecations y mejorar comportamiento en background prolongado.

## Entregado en 3.4.0

- ✅ Android `ActivityRecognitionLocationProvider`: `LocationRequest.Builder` + `Priority.PRIORITY_*`.
- ✅ Android `RawLocationProvider`: sin `Criteria`; selección GPS-first / Network-fallback explícita.
- ✅ `plugin.xml`: `GOOGLE_PLAY_SERVICES_VERSION` default `21.0.1`.
- ✅ iOS `MAURPostLocationTask`: `NSURLConnection` → `NSURLSession + dispatch_semaphore`.
- ✅ iOS `MAURDistanceFilterLocationProvider`: callback iOS 14+ `locationManagerDidChangeAuthorization:`.
- ✅ iOS `showsBackgroundLocationIndicator` config option.
- ✅ TS types `showsBackgroundLocationIndicator?: boolean`.

## Pendiente (3.4.1+)

- ⏳ Android `DistanceFilterLocationProvider.java`: eliminar `Criteria`, migrar `getLastKnownLocation` → `getCurrentLocation`, `requestLocationUpdates` con `LocationRequest.Builder` (API 31+) + fallback. Refactor grande del path stationary y stop-detection.
- ⏳ Stationary detection: retirar `AlarmManager.setInexactRepeating` y dejar el FGS tomando muestras vía `LocationCallback` con interval grande durante stationary.

> Esta fase es **previa al diagnóstico** (Fase 4, v3.5) porque sin las APIs modernas el `getDiagnostics()` reportaría datos sobre un stack que ya está marcado como obsoleto por Google y Apple.

---

## Android

### A. `ActivityRecognitionLocationProvider.java`

Estado actual: usa APIs deprecated en `play-services-location 21.0.0` (oct 2022).

| Línea | API actual | API moderna |
|---|---|---|
| 139 | `LocationRequest.create()` | `new LocationRequest.Builder(priority, intervalMillis)` |
| 140 | `.setPriority(int)` | constructor del Builder |
| 141 | `.setFastestInterval(ms)` | `.setMinUpdateIntervalMillis(ms)` |
| 142 | `.setInterval(ms)` | constructor del Builder |
| 217-228 | `LocationRequest.PRIORITY_*` | `com.google.android.gms.location.Priority.PRIORITY_*` |

Adicional recomendado en el `Builder`:
- `.setMinUpdateDistanceMeters(meters)` — equivalente a smallest displacement.
- `.setMaxUpdateDelayMillis(ms)` — agrupa fixes para ahorrar batería.
- `.setWaitForAccurateLocation(true)` — descarta primer fix si imprecise.
- `.setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)` — alinea con permisos.

Ejemplo destino:

```java
LocationRequest req = new LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        mConfig.getInterval())
    .setMinUpdateIntervalMillis(mConfig.getFastestInterval())
    .setMinUpdateDistanceMeters(0)
    .setMaxUpdateDelayMillis(0)
    .setWaitForAccurateLocation(false)
    .build();
```

### B. `DistanceFilterLocationProvider.java`

Estado actual: usa APIs deprecated desde Android 12 (API 31). Funciona pero day-X queda roto.

| Tema | Actual | Moderno |
|---|---|---|
| Selección de provider | `Criteria` + `LocationManager.getBestProvider(criteria, true)` (L237) | Construir `LocationRequest` con `Priority.*` y `Granularity.*`; pasar provider explícito (`GPS_PROVIDER` / `FUSED_PROVIDER` API 31+) |
| Last known location | `LocationManager.getLastKnownLocation(provider)` (L298) | `LocationManager.getCurrentLocation(provider, null, executor, locationConsumer)` |
| Request updates | `requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener)` (L233, L246) | `requestLocationUpdates(provider, LocationRequest, Executor, LocationListener)` (API 31+); fallback al método clásico para <31 |
| `Criteria` | `criteria.setAccuracy/setHorizontalAccuracy/setPowerRequirement` | Eliminar la clase y reemplazar con presets via Priority/Granularity |
| `translateDesiredAccuracy(...)` | retorna constantes `Criteria.ACCURACY_*` | retornar `Priority.PRIORITY_*` y `Granularity.GRANULARITY_*` |

Ejemplo destino:

```java
// Reemplaza criteria + getBestProvider:
String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    ? LocationManager.GPS_PROVIDER
    : LocationManager.NETWORK_PROVIDER;

LocationRequest req = new LocationRequest.Builder(
        translatePriority(mConfig.getDesiredAccuracy()),
        mConfig.getInterval())
    .setMinUpdateDistanceMeters(scaledDistanceFilter)
    .build();

if (Build.VERSION.SDK_INT >= 31) {
    locationManager.requestLocationUpdates(provider, req, executor, this);
} else {
    locationManager.requestLocationUpdates(provider, mConfig.getInterval(),
            scaledDistanceFilter, this);
}
```

Y para last known:

```java
if (Build.VERSION.SDK_INT >= 30) {
    locationManager.getCurrentLocation(provider, null, executor, location -> { /* ... */ });
} else {
    Location loc = locationManager.getLastKnownLocation(provider);
}
```

### C. Stationary detection / Doze

Estado actual: `AlarmManager.setInexactRepeating` (L458) cada 1-3 min para muestrear stationary location.

Problema: desde Android 6 (Doze), las alarmas inexactas se agrupan a ventanas de ≥9 min en idle, ≥15 min con doze deep. El usuario configura "muestrea cada 1 min" y obtiene "cada 15 min".

Opciones de migración:

1. **`setExactAndAllowWhileIdle`** + permiso `SCHEDULE_EXACT_ALARM` (Android 12+ obliga a pedirlo).
   - Funciona pero requiere flujo runtime para pedir el permiso (similar a `ACCESS_BACKGROUND_LOCATION`).
2. **`WorkManager` periodic** con constraints (`NetworkType.CONNECTED`, `RequiresCharging.NO`) y `setRequiresDeviceIdle(false)`.
   - Mínimo 15 min — NO sirve para 1-3 min.
3. **Foreground service en estado stationary también** + `LocationCallback` con interval mínimo.
   - El FGS ya está corriendo en este plugin; aprovecharlo.
   - Recomendado: en stationary, dejar el FGS recibiendo location updates con interval grande (5 min) en lugar de matar el listener y reactivarlo via AlarmManager.

Recomendación v3.4: **opción 3** + retirar `AlarmManager` y `setInexactRepeating`. El FGS ya está vivo, no hay razón para depender del scheduler del sistema.

### D. RawLocationProvider

Mismas deprecaciones que DistanceFilter pero más sencillas (no usa Criteria). Migración paralela.

### E. Permisos manifest

Si se adopta `setExactAndAllowWhileIdle` (no recomendado, ver §C):
- Añadir `SCHEDULE_EXACT_ALARM` en `plugin.xml`.
- Documentar flujo runtime.

Si se adopta opción 3 (recomendada): no requiere permisos nuevos.

---

## iOS

### A. `MAURPostLocationTask.m` — `NSURLConnection`

Estado actual (L200): `[NSURLConnection sendSynchronousRequest:returningResponse:error:]` — deprecated desde iOS 9 (2015).

Migración a `NSURLSession`:

```objc
__block NSData *responseData = nil;
__block NSHTTPURLResponse *urlResponse = nil;
__block NSError *taskError = nil;
dispatch_semaphore_t sema = dispatch_semaphore_create(0);

NSURLSessionDataTask *task = [[NSURLSession sharedSession]
    dataTaskWithRequest:request
      completionHandler:^(NSData *data, NSURLResponse *response, NSError *err) {
        responseData = data;
        urlResponse = (NSHTTPURLResponse *)response;
        taskError = err;
        dispatch_semaphore_signal(sema);
    }];
[task resume];
dispatch_semaphore_wait(sema, dispatch_time(DISPATCH_TIME_NOW, 120 * NSEC_PER_SEC));
*outError = taskError;
NSInteger statusCode = urlResponse.statusCode;
```

Nota: el código vive en un thread de background, NO en main, así que el semáforo es seguro. Verificar que el call site no es main para evitar congelar UI.

### B. `MAURBackgroundSync.m`

Ya usa `NSURLSession uploadTaskWithRequest:fromFile:` con delegate moderno. **OK, no requiere migración.**

### C. `MAURDistanceFilterLocationProvider.m`

Mostly OK, pero exponer al config:

| Propiedad nativa | Estado actual | Acción |
|---|---|---|
| `showsBackgroundLocationIndicator` | Solo en `MAURLocationManager.m` (L152), no expuesto al usuario | Añadir `showsBackgroundLocationIndicator` a `MAURConfig` y al provider principal |
| `pausesLocationUpdatesAutomatically` | Configurable pero no documentado en `.d.ts` | Documentar en `ConfigureOptions` |
| `activityType` | Configurable | Documentar valores válidos en `.d.ts` |
| `desiredAccuracy = kCLLocationAccuracyBestForNavigation` | Hardcoded en algunos paths (L199) | Respetar config o documentar |

### D. CLLocationManager auth callbacks

Estado actual: usa `[locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]` (L138). El check es histórico (pre-iOS 8). Hoy iOS 14+ requiere:

- `locationManagerDidChangeAuthorization:` (iOS 14+) — reemplaza `locationManager:didChangeAuthorizationStatus:`.
- `accuracyAuthorization` (iOS 14+) — Precise vs Reduced.
- `requestTemporaryFullAccuracyAuthorizationWithPurposeKey:` — para pedir full accuracy temporal cuando el usuario eligió Reduced.

Acción v3.4:
- Implementar el callback nuevo manteniendo el legacy para iOS <14.
- Añadir `accuracyAuthorization` al diagnóstico (Fase 4).

---

## Resumen de tareas v3.4

### Android

- [ ] `ActivityRecognitionLocationProvider.java`: migrar `LocationRequest.create` → Builder, `Priority.*`.
- [ ] `DistanceFilterLocationProvider.java`: eliminar `Criteria`, migrar `getBestProvider`, `getLastKnownLocation` → `getCurrentLocation`, usar `LocationRequest.Builder` para `requestLocationUpdates` (API 31+ con fallback).
- [ ] `RawLocationProvider.java`: misma migración.
- [ ] Stationary detection: retirar `AlarmManager.setInexactRepeating`. Mantener FGS con `LocationCallback` interval grande durante stationary.
- [ ] Eliminar `Criteria` y todos sus usos.
- [ ] Bump `play-services-location` a `21.x`.

### iOS

- [ ] `MAURPostLocationTask.m`: migrar `NSURLConnection sendSynchronousRequest` → `NSURLSession dataTaskWithRequest` + semáforo.
- [ ] `MAURDistanceFilterLocationProvider.m`: exponer `showsBackgroundLocationIndicator`, documentar `pausesLocationUpdatesAutomatically` y `activityType` en `.d.ts`.
- [ ] `MAURConfig.h/m`: añadir `showsBackgroundLocationIndicator`.
- [ ] Implementar `locationManagerDidChangeAuthorization:` (iOS 14+) manteniendo legacy.
- [ ] `accuracyAuthorization`: persistir y exponer en `getDiagnostics()` (link con Fase 4).

### TypeScript

- [ ] `www/BackgroundGeolocation.d.ts`: añadir `showsBackgroundLocationIndicator?: boolean` (iOS).
- [ ] Documentar valores válidos de `activityType` (`Other`, `OtherNavigation`, `AutomotiveNavigation`, `Fitness`, `Airborne`).

### Build

- [ ] `dependencies.gradle`: bump `play-services-location` a `21.0.1+`.
- [ ] `compileSdk` 34+ requerido para usar APIs nuevas.

---

## Compatibilidad

- **Backward compatibility:** todas las opciones existentes siguen aceptándose. La traducción `desiredAccuracy` (0/10/100/1000) sigue funcionando — solo cambia internamente el mapeo a `Priority.*`.
- **Min Android version:** las APIs modernas (`getCurrentLocation`, `requestLocationUpdates(provider, LocationRequest, ...)` directo) requieren API 30+/31+. Para versiones anteriores se mantiene el camino legacy con feature detection (`Build.VERSION.SDK_INT`).
- **Min iOS version:** `NSURLSession` está en iOS 7+; `locationManagerDidChangeAuthorization:` es iOS 14+ (con fallback al legacy).

---

## No incluido en v3.4

Se difiere a fases posteriores:

- `getDiagnostics()` extendido (Fase 4, v3.5).
- `accuracyAuthorization` exposición al usuario (necesita el diagnóstico).
- Battery / OEM helpers (Fase 5, v3.6).
- Driving events (Fase 6, v4.0).
