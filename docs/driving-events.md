# Driving events (driver insights)

Detección de eventos de conducción **dentro del plugin**, sin servicio externo.

> Estado: **implementado**.
> - **v4.0** — máquina de estados basada solo en GPS: `moving`, `stopped`, `tripStart`, `tripEnd`, `speeding`, `providerChange`, `sos`.
> - **v4.1** — eventos derivados de GPS sin sensores extra: `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`.
> - **v4.2** — fusión de sensores real (`sensorFusion: true`): refina `possibleCrash` con el acelerómetro y añade `phoneUsageWhileDriving`.
>
> **El plugin se mantiene backend-agnóstico**: estos eventos se emiten al cliente JS y se pueden enviar a cualquier endpoint usando el HTTP transport personalizable.

Todo está desactivado por defecto: hay que poner `drivingEvents.enabled: true` en `configure()`.

---

## Configuración real

La forma canónica está en `www/BackgroundGeolocation.d.ts`, interfaz `ConfigureOptions.drivingEvents`.

```ts
BackgroundGeolocation.configure({
  drivingEvents: {
    // --- v4.0: máquina de estados GPS ---
    enabled: true,              // master switch. Default: false
    speedLimit: 80,             // km/h para `speeding`. 0 = desactivado. Default: 0
    minMovingSpeed: 1.0,        // m/s por debajo del cual se considera parado. Default: 1.0
    stoppedDuration: 60000,     // ms continuados bajo umbral para confirmar `stopped`. Default: 60000
    minTripSpeed: 3.0,          // m/s para empezar a contar viaje. Default: 3.0 (~10.8 km/h)
    minTripDuration: 30000,     // ms continuados sobre umbral para confirmar `tripStart`. Default: 30000

    // --- v4.1: eventos derivados de GPS (0 desactiva cada uno) ---
    hardBrakeMps2: 3.5,         // m/s² (valor positivo) para `hardBrake`. Default: 3.5
    rapidAccelMps2: 3.5,        // m/s² para `rapidAcceleration`. Default: 3.5
    sharpTurnDegPerSec: 30,     // deg/s para `sharpTurn` (requiere speed >= 5 m/s). Default: 30
    crashImpactKmh: 25,         // caída de velocidad en km/h dentro de crashWindowMs. Default: 25
    crashWindowMs: 2000,        // ventana ms para evaluar el impacto. Default: 2000

    // --- v4.2: fusión de sensores real (acelerómetro + giroscopio) ---
    sensorFusion: false,        // Default: false. Coste de batería moderado
    crashImpactG: 3.0,          // g (1g = 9.81 m/s²) para `possibleCrash` por sensor. Default: 3.0
    sensorCrashCooldownMs: 10000, // ms entre detecciones de crash por sensor. Default: 10000
    phoneUsageWindowMs: 4000,   // ms de jitter sostenido para `phoneUsageWhileDriving`. Default: 4000
    phoneUsageCooldownMs: 60000 // ms entre eventos `phoneUsageWhileDriving`. Default: 60000
  }
});
```

### Unidades — atención

| Opción | Unidad | Nota |
|---|---|---|
| `speedLimit` | **km/h** | única opción de velocidad en km/h |
| `minMovingSpeed`, `minTripSpeed` | **m/s** | NO km/h |
| `stoppedDuration`, `minTripDuration`, `crashWindowMs`, `sensorCrashCooldownMs`, `phoneUsageWindowMs`, `phoneUsageCooldownMs` | **ms** | NO segundos |
| `hardBrakeMps2`, `rapidAccelMps2` | **m/s²** | valor positivo (magnitud) |
| `sharpTurnDegPerSec` | **deg/s** | tasa de cambio de rumbo |
| `crashImpactKmh` | **km/h** | caída de velocidad |
| `crashImpactG` | **g** | solo con `sensorFusion: true` |

---

## Eventos y payloads

No existe un evento agregado `drivingEvent`: **cada tipo es un evento propio** de `on()`.

| Evento | Payload | Desde |
|---|---|---|
| `tripStart` | `Location` | 4.0 |
| `tripEnd` | `{ location, distance /* m */, durationMs }` | 4.0 |
| `moving` | `Location` | 4.0 |
| `stopped` | `Location` | 4.0 |
| `speeding` | `{ location, speedKmh, limitKmh }` | 4.0 |
| `providerChange` | `{ provider: string }` | 4.0 |
| `sos` | `{ location?, ...payload de triggerSOS() }` | 4.0 |
| `hardBrake` | `{ location, value /* m/s², negativo */ }` | 4.1 |
| `rapidAcceleration` | `{ location, value /* m/s², positivo */ }` | 4.1 |
| `sharpTurn` | `{ location, value /* deg/s */ }` | 4.1 |
| `possibleCrash` | `{ location, value, source: 'gps' \| 'sensor' }` | 4.1 |
| `phoneUsageWhileDriving` | `Location \| undefined` | 4.2 |

```ts
BackgroundGeolocation.on('tripEnd', ({ location, distance, durationMs }) => {
  console.log('viaje:', distance, 'm en', durationMs, 'ms');
});

BackgroundGeolocation.on('hardBrake', ({ location, value }) => {
  console.log('frenada brusca', value, 'm/s² en', location.latitude, location.longitude);
});

BackgroundGeolocation.on('possibleCrash', ({ location, value, source }) => {
  // source === 'gps'    -> value es la caída de velocidad en km/h
  // source === 'sensor' -> value es la magnitud del impacto en g
  // SIEMPRE confirmar con el usuario antes de avisar a contactos.
});
```

En `possibleCrash`, `value` depende de `source`: km/h de caída de velocidad si `'gps'`, magnitud en g si `'sensor'`.

### Sin callback (estilo Observable / Angular)

```ts
const sub = BackgroundGeolocation.on('speeding').subscribe(({ speedKmh, limitKmh }) => { /* ... */ });
sub.unsubscribe();
```

Con callback, `on()` devuelve `{ remove() }`; sin callback devuelve `{ subscribe(cb) }`. Ver [angular.md](angular.md) para el envoltorio del servicio Angular.

---

## SOS

```ts
await BackgroundGeolocation.triggerSOS({ reason: 'panic-button' });
// emite el evento `sos` con { location?, reason: 'panic-button' }
```

---

## Eventos persistidos en la localización

Desde **v4.3**, los eventos de conducción emitidos entre dos fixes se adjuntan al objeto `Location` en el array `events`:

```ts
location.events // Array<{ type: string; time: number; [key: string]: any }>
```

`payload` por tipo:

- `hardBrake` / `rapidAcceleration` / `sharpTurn` → `value: number`
- `speeding` → `speedKmh: number`, `limitKmh: number`
- `tripEnd` → `distance: number`, `durationMs: number`
- `possibleCrash` → `value: number`, `source: 'gps' | 'sensor'`
- `providerChange` → `provider: string`
- `moving` / `stopped` / `tripStart` / `phoneUsageWhileDriving` → solo `type` + `time`

Desde **v4.5.0** `events` se persiste en la cola de sync y sobrevive a POST fallidos, así que llegan al backend aunque la subida falle en el momento.

---

## Sensores usados

| Sensor | Android | iOS | Requiere |
|---|---|---|---|
| GPS | ya disponible | ya disponible | siempre |
| Acelerómetro lineal | `Sensor.TYPE_LINEAR_ACCELERATION` | `CMMotionManager.deviceMotion.userAcceleration` | `sensorFusion: true` |
| Giroscopio | `Sensor.TYPE_GYROSCOPE` | `CMMotionManager.deviceMotion.rotationRate` | `sensorFusion: true` |

Con `sensorFusion: false` (por defecto) **no** se abren sensores: todo se deriva de la secuencia de fixes GPS.

---

## Consideraciones

- La fusión de sensores corre dentro del FGS existente para no impactar batería adicional por wakelocks.
- `sensorFusion: true` añade coste de batería moderado; solo se muestrea mientras hay un viaje activo.
- `possibleCrash` NO es "detección exacta". Pedir confirmación al usuario antes de alertar contactos.
- Falsos positivos típicos: caída del teléfono, baches fuertes, frenadas en bajada. Los cooldowns y el requisito de viaje activo los mitigan.
- `sharpTurn` solo se evalúa con speed ≥ 5 m/s para evitar el jitter de rumbo del GPS a baja velocidad.

### Condiciones reales de `possibleCrash` (source `'gps'`)

`crashImpactKmh` **no** es la única condición. Todas estas deben cumplirse
(`DrivingEventsDetector`, Android; iOS replica la lógica):

| # | Condición | Configurable |
|---|-----------|--------------|
| 1 | `drivingEvents.enabled: true` **y viaje activo** (`tripStart` emitido, `tripEnd` no) | vía `minTripSpeed` / `minTripDuration` |
| 2 | El fix trae `speed` y existe una muestra de velocidad anterior | no |
| 3 | `crashImpactKmh > 0` y `crashWindowMs > 0` | sí |
| 4 | Existe un `peak` en la ventana deslizante: velocidad máxima con antigüedad ≤ `crashWindowMs` y ≥ `min(500 ms, crashWindowMs)` | parcialmente (`crashWindowMs`) |
| 5 | `(peak - speed) * 3.6 >= crashImpactKmh` (la caída en sí) | sí |
| 6 | **`speed < 1.5 m/s`** (~5,4 km/h): el vehículo debe acabar casi parado | **NO** — constante |
| 7 | **`peak * 3.6 >= crashImpactKmh`**: la velocidad previa debe superar también el umbral | no (derivada) |
| 8 | **Cooldown fijo de 4000 ms** (`DRIVING_EVENT_COOLDOWN_MS`) desde el último `possibleCrash` | **NO** — constante |

Notas:

- `sensorCrashCooldownMs` (default 10000) aplica **solo** al pipeline de sensores
  (`source: 'sensor'`, `sensorFusion: true`). El heurístico GPS usa el cooldown fijo de 4 s.
- El mismo cooldown fijo de 4 s se aplica a `hardBrake`, `rapidAcceleration`, `sharpTurn` y
  `speeding`.
- Consecuencia práctica de (6) y (7): una deceleración fuerte que termina a 40 km/h **no**
  dispara el evento; tampoco una parada brusca desde velocidad baja.
- Consecuencia práctica de (4): con intervalos de actualización de flota (10-60 s) una ventana
  de 2000 ms casi nunca contiene un `peak` válido y el evento queda mudo. Con baja frecuencia
  de fixes hay que ampliar `crashWindowMs`.

### Condiciones reales de `sharpTurn`

- El fix debe traer **bearing**; un fix sin bearing invalida el ancla anterior.
- El intervalo entre anclas de bearing debe estar entre **500 ms y 5000 ms**
  (`MIN_DELTA_MS` / `MAX_DELTA_MS`, no configurables).
- El fix debe traer **speed ≥ 5 m/s** (~18 km/h) — constante, no configurable.
- `rate >= sharpTurnDegPerSec` y **cooldown fijo de 4000 ms**.
- A diferencia de `hardBrake` / `rapidAcceleration` / `possibleCrash`, `sharpTurn` **no**
  exige viaje activo: basta `drivingEvents.enabled: true`.
- `speeding` se dispara una vez al cruzar el límite; no se repite hasta que la velocidad vuelve a bajar del límite.

---

## Driving events vs backends server-side

Backends como Traccar tienen reglas server-side (`overspeed`, `deviceMoving`, `deviceStopped`), pero **no** pueden detectar hardBrake / sharpTurn / crash: eso requiere la serie temporal de alta frecuencia (y, en `sensorFusion`, el acelerómetro y giroscopio) del cliente, datos que no llegan al servidor.

Por eso esta lógica vive en el plugin (cliente). Una vez detectado, el evento puede enviarse a cualquier backend usando el HTTP transport personalizable, o viaja dentro de `location.events`.

---

## Relación con `mockLocationPolicy`

Driving events deben ignorar (o marcar) posiciones con `isFromMockProvider == true` (Android) o `simulated == true` (iOS). Si la política activa es `'drop'`, los driving events no se calculan sobre esas muestras.
