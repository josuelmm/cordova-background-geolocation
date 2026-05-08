# Traccar como capa backend opcional (ejemplo de integración)

> **Aviso de diseño.** Este plugin es **global y backend-agnóstico**. Traccar **no** es una dependencia ni un modo interno: no existe `traccarMode`, `osmandMode` ni similar. La compatibilidad con Traccar se logra **únicamente** mediante el [HTTP transport personalizable](http-transport.md). Este documento es solo un ejemplo de integración; cualquier otro backend (GPSWox, Laravel, Node, Firebase, n8n, API propia) se conecta de la misma manera.

---

## Por qué Traccar como ejemplo

[Traccar](https://www.traccar.org/) es una opción razonable cuando se necesita una capa backend que cubra:

| Área | Quién lo resuelve |
|---|---|
| GPS fiable en móvil | Plugin |
| Auto-start Android | Plugin |
| Cola offline | Plugin |
| HTTP personalizable | Plugin |
| Geocercas | Backend (Traccar / propio) |
| Eventos entrada/salida | Backend (Traccar / propio) |
| Historial de rutas | Backend (Traccar / propio) |
| Viajes / paradas | Backend (Traccar / propio) |
| Reportes | Backend (Traccar / propio) |
| Usuarios / dispositivos | Traccar o tu SaaS |
| Círculos / familias | Tu SaaS |
| Notificaciones avanzadas | Traccar / tu SaaS |

División correcta:

- **Plugin** = motor móvil global y agnóstico.
- **Traccar** (o equivalente) = motor GPS backend opcional.
- **Tu SaaS** = experiencia tipo Life360 encima.

---

## Lo que cubre Traccar (NO desarrollar en plugin)

| Feature | Módulo Traccar | Detalle |
|---|---|---|
| Persistencia historial de rutas | Reports | Tabla `tc_positions`, retención configurable |
| Geocercas (alta/baja/listado) | Geofences | Polígono, círculo, polilínea. API REST `/api/geofences` |
| Eventos `geofenceEnter` / `geofenceExit` | Notifications | Server evalúa al recibir cada posición |
| Reglas overspeed, idle, deviceMoving, deviceStopped | Notifications | Configurables por device/grupo |
| Eventos de viaje (Trips) | Reports → Trips | Calcula start/end automáticamente desde positions |
| Stops / paradas | Reports → Stops | Calcula automáticamente |
| Resumen diario (Summary) | Reports → Summary | Distancia, tiempo en movimiento, speed promedio/máx |
| Compartir posición temporal | Sharing | URL pública con expiración |
| Históricos por rango | Web / API | `/api/positions?from=...&to=...` |
| Dashboard / mapa multi-device | Web (Manager / Modern) | UI lista para usar |
| Push a familia | Notifications + webhook → FCM | Server-side |
| Audit log | tc_log | Tabla nativa |

---

## Lo que sí debe hacer el plugin

- Tracking background fiable Android/iOS (ya existe).
- Cola offline con `forceSync` / `clearSync` (ya existe).
- HTTP genérico GET/POST/PUT/PATCH + URL templating (v3.4) — habilita cualquier backend, Traccar incluido.
- Limpieza Android moderno: `foregroundServiceType="location"` simple, sin `dataSync` ni `org.apache.http.legacy`, `mavenCentral` (v3.3).
- `ACCESS_BACKGROUND_LOCATION` + flujo runtime (v3.3).
- Auto-start tras reinicio Android completo (v3.3).
- `getDiagnostics()` (v3.5).
- Heartbeat y sync events (v3.5).
- `mockLocationPolicy` (v3.5) — la detección **ya existe** (`isFromMockProvider` Android, `simulated` iOS).
- Battery / OEM helpers (v3.6).
- Crash detection y SOS local (v4.0).

---

## Ejemplo de integración con Traccar (tras v3.4)

Traccar acepta posiciones en múltiples protocolos. El más simple desde móvil es el **protocolo OsmAnd** (HTTP GET al puerto 5055 por defecto). El plugin lo cubre **sin código específico**, usando `httpMethod: 'GET'` y URL templating.

> El plugin **no implementa** "modo OsmAnd". OsmAnd es solo el nombre del protocolo HTTP que Traccar acepta para recibir posiciones.

```ts
import BackgroundGeolocation from '@josuelmm/cordova-background-geolocation';

BackgroundGeolocation.configure({
  url: 'https://gps.midominio.com:5055/?id={uid}&lat={latitude}&lon={longitude}&timestamp={timestamp_iso}&speed={speed}&altitude={altitude}&bearing={bearing}&accuracy={accuracy}',
  httpMethod: 'GET',
  httpMode: 'single',
  queryParams: { uid: 'USER_DEVICE_123' },

  // Cola offline al mismo endpoint
  syncUrl: 'https://gps.midominio.com:5055/?id={uid}&lat={latitude}&lon={longitude}&timestamp={timestamp_iso}&speed={speed}&altitude={altitude}&bearing={bearing}&accuracy={accuracy}',
  syncHttpMethod: 'GET',
  syncMode: 'single',

  // Comportamiento de tracking
  locationProvider: BackgroundGeolocation.DISTANCE_FILTER_PROVIDER,
  desiredAccuracy: BackgroundGeolocation.HIGH_ACCURACY,
  stationaryRadius: 50,
  distanceFilter: 50,
  interval: 10000,

  // Persistencia
  startOnBoot: true,
  stopOnTerminate: false,
  notificationsEnabled: true,
  notificationTitle: 'Rastreo activo',
  notificationText: 'La ubicación se está enviando',

  maxLocations: 10000
});

BackgroundGeolocation.start();
```

**Importante:** el `id` debe coincidir con el `Unique ID` del dispositivo creado en Traccar. Si no existe, Traccar descarta la posición.

---

## Workaround con v3.2.0 (sin GET nativo)

Hasta que v3.4 esté disponible, hay dos opciones:

### Opción A: gateway intermedio

Levantar un proxy ligero (Cloudflare Worker, Vercel function, n8n, FastAPI) que reciba **POST JSON** del plugin y haga **GET OsmAnd** a Traccar. El plugin como está hoy.

```ts
configure({
  url: 'https://miproxy.workers.dev/track',
  httpHeaders: { 'Content-Type': 'application/json', 'X-Device-Id': 'USER_DEVICE_123' },
  postTemplate: { lat: '@latitude', lng: '@longitude', t: '@time', spd: '@speed', alt: '@altitude' }
});
```

### Opción B: protocolo Traccar JSON HTTP

Algunos despliegues de Traccar exponen un endpoint JSON HTTP custom (puerto 5013). Configurar `postTemplate` para que coincida con el contrato del protocolo.

---

## Geocercas en Traccar (server-side)

No se gestionan desde el plugin. Crear vía API REST de Traccar:

```bash
# Crear geocerca circular
curl -u admin:admin -X POST https://gps.midominio.com/api/geofences \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Casa",
    "area": "CIRCLE (3.4516 -76.5320, 150)"
  }'

# Asignar al dispositivo
curl -u admin:admin -X POST https://gps.midominio.com/api/permissions \
  -H "Content-Type: application/json" \
  -d '{ "deviceId": 1, "geofenceId": 1 }'
```

Eventos `geofenceEnter` / `geofenceExit` se entregan vía:

- Notifications module → push, email, SMS, webhook.
- WebSocket `/api/socket` para apps en tiempo real.
- Polling `/api/events?from=...&to=...`.

---

## Notificaciones a la app familiar

Recomendado: webhook Traccar → FCM (Firebase Cloud Messaging) → app.

1. Configurar Notifier en Traccar (`traccar.xml`).
2. Crear notificación en Traccar Web: tipo `geofenceEnter`, canal `Firebase`, dispositivos asignados.
3. La app suscribe topics FCM por `userId` o `deviceId`.

---

## Mismo plugin con otros backends

Idéntico patrón sirve para:

- **APIs Laravel:** `httpMethod: 'POST'`, `bodyTemplate` con campos de tu modelo.
- **APIs Node.js:** lo mismo.
- **GPSWox:** GET o POST según endpoint del proveedor.
- **Firebase Realtime DB:** `httpMethod: 'PUT'` con URL `.json?auth={token}`.
- **n8n / Make webhooks:** POST JSON.
- **GraphQL gateways:** POST con `bodyTemplate` que incluya el query.

> Resumen: el plugin **no se enfoca en Traccar**. Se mantiene global mediante HTTP transport personalizable, y Traccar es solo un ejemplo de qué backend puede recibir las posiciones.
