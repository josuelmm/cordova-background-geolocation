# Driving events (Fase 6, planificado v4.0)

Detección de eventos de conducción **dentro del plugin**, sin servicio externo. Usa GPS + acelerómetro + giroscopio + activity recognition.

> Estado: **planificado, no implementado**. Es la última fase del roadmap (después de auto-start, HTTP transport, modernización de location APIs, diagnóstico y battery/OEM). Ver [ROADMAP.md](ROADMAP.md).
> **El plugin se mantiene backend-agnóstico**: estos eventos se emiten al cliente JS y se pueden enviar a cualquier endpoint usando el HTTP transport personalizable.

---

## Sensores requeridos

| Sensor | Android | iOS |
|---|---|---|
| GPS | ya disponible | ya disponible |
| Acelerómetro lineal | `Sensor.TYPE_LINEAR_ACCELERATION` | `CMMotionManager.deviceMotion.userAcceleration` |
| Giroscopio | `Sensor.TYPE_GYROSCOPE` | `CMMotionManager.deviceMotion.rotationRate` |
| Activity recognition | Google Play Services Activity API (ya integrado) | `CMMotionActivityManager` |

---

## Eventos a detectar

| Evento | Algoritmo |
|---|---|
| `tripStart` | `activity == IN_VEHICLE` con confianza ≥75% durante ≥30s + speed ≥10 km/h |
| `tripEnd` | `activity != IN_VEHICLE` durante ≥3 min, o speed ≈0 durante ≥5 min |
| `hardBrake` | Δspeed GPS ≥ 8 m/s en ≤2s + lin.accel < -3.5 m/s² |
| `rapidAcceleration` | Δspeed GPS ≥ 7 m/s en ≤2s + lin.accel > +3.5 m/s² |
| `sharpTurn` | gyro Z > 0.6 rad/s + bearing change ≥45° en ≤2s |
| `speeding` | speed > `speedLimit` durante ≥5s |
| `phoneUsageWhileDriving` | `IN_VEHICLE` + screen on + interaction events (heurístico) |
| `possibleCrash` | impact > 3g + Δspeed > 25 km/h en <1s + activity == IN_VEHICLE → confirmación user |

---

## Configuración propuesta

```ts
BackgroundGeolocation.configure({
  drivingEvents: {
    enabled: true,
    speedLimit: 80,                    // km/h
    hardBrakeThreshold: 3.5,           // m/s²
    rapidAccelThreshold: 3.5,          // m/s²
    sharpTurnGyroZ: 0.6,               // rad/s
    crashImpactG: 3.0,                 // g
    minTripSpeed: 10,                  // km/h
    minTripDuration: 30                // s
  }
});

BackgroundGeolocation.on('drivingEvent', (event) => {
  // event.type:        'hardBrake' | 'rapidAcceleration' | 'sharpTurn' | 'speeding' | 'phoneUsageWhileDriving' | 'possibleCrash' | 'tripStart' | 'tripEnd'
  // event.value:       valor numérico (delta speed, gyro, g-force, etc.)
  // event.location:    { latitude, longitude, time, speed, bearing, accuracy }
  // event.confidence:  0..1
  // event.timestamp:   ms
});
```

---

## Consideraciones

- Sensor fusion debe correr en el FGS existente para no impactar batería.
- Sampling rate: 50 Hz acelerómetro/giroscopio (suficiente y frugal).
- Filtros: low-pass para acelerómetro (eliminar gravity), high-pass para detectar impactos.
- `possibleCrash` NO debe etiquetarse como "detección exacta". Pedir confirmación al usuario antes de alertar contactos.
- Falsos positivos típicos: caída del teléfono, baches fuertes, frenadas en bajada. Filtrar con activity recognition + duración del evento.

---

## Driving events vs backends server-side

Backends como Traccar tienen reglas server-side (`overspeed`, `deviceMoving`, `deviceStopped`), pero **no** pueden detectar hardBrake / sharpTurn / crash: eso requiere acelerómetro y giroscopio del cliente, datos que no llegan al servidor.

Por eso esta lógica vive obligatoriamente en el plugin (cliente). Una vez detectado, el evento puede enviarse a cualquier backend usando el HTTP transport personalizable.

---

## Relación con `mockLocationPolicy`

Driving events deben ignorar (o marcar) posiciones con `isFromMockProvider == true` (Android) o `simulated == true` (iOS). Si la política activa es `'drop'`, los driving events no se calculan sobre esas muestras.
