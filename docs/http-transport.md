# Transporte HTTP personalizable

> **Plugin global y backend-agnóstico.** El plugin no conoce ni depende de Traccar, GPSWox, OsmAnd, Laravel, Node ni ningún backend específico. El transporte HTTP es totalmente personalizable y permite enviar posiciones a cualquier servidor, en cualquier formato (JSON, query params, form-urlencoded, body templating).

Backends compatibles vía configuración (ejemplos de uso, no integración hardcodeada): Traccar, GPSWox, APIs Laravel, APIs Node.js, n8n, Make, Firebase, GraphQL gateways, APIs propias.

---

## Comportamiento en v3.2.0 y anteriores

| Aspecto | Estado |
|---|---|
| Método HTTP | Hardcoded **POST** |
| `httpHeaders` | Sí |
| `Content-Type` flexible | `application/json` (default) y `application/x-www-form-urlencoded` (auto-conversión Android) |
| `postTemplate` (formato body custom) | Sí |
| URL templating | NO |
| GET / PUT / PATCH | NO |
| Modo single-location vs batch | Solo batch (array) |
| Query string dinámico | NO |

Hardcodes confirmados:

- Android `HttpPostService.java` líneas 112 y 261: `setRequestMethod("POST")`.
- iOS `MAURPostLocationTask.m` línea 141 y `MAURBackgroundSync.m` línea 80: `setHTTPMethod:@"POST"`.

---

## API de configuración (v3.3.0, Fase 2 — entregada)

```ts
BackgroundGeolocation.configure({
  // ===== Endpoint principal =====
  url: string,                                          // soporta URL templating
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH',       // default 'POST'
  httpMode?: 'single' | 'batch',                       // default 'single' (since 5.0.1)

  // Headers (alias retrocompatible: httpHeaders)
  headers?: { [key: string]: string },

  // Body (alias retrocompatible: postTemplate). Solo se usa con POST/PUT/PATCH.
  bodyTemplate?: object,

  // Valores estáticos que rellenan placeholders en url / syncUrl / bodyTemplate
  queryParams?: { [key: string]: string | number },

  // ===== Endpoint de la cola (locations fallidas) =====
  syncUrl?: string,
  syncHttpMethod?: 'POST' | 'PUT' | 'PATCH',           // GET no válido: se coerge a POST desde 5.0.1
  syncMode?: 'single' | 'batch',
});
```

### Placeholders disponibles

Aplicables a `url`, `syncUrl` y a los valores string dentro de `bodyTemplate`:

`{latitude}`, `{longitude}`, `{lat}` (alias), `{lon}` (alias), `{time}` (ms), `{timestamp}` (ms), `{timestamp_iso}` (ISO 8601 UTC), `{speed}`, `{altitude}`, `{bearing}`, `{accuracy}`, `{provider}`, `{is_moving}`, `{activity}`, `{battery}` + cualquier clave de `queryParams`.

---

## Ejemplos por backend

### REST JSON batch (comportamiento por defecto)

```ts
configure({
  url: 'https://api.miapp.com/locations',
  httpMethod: 'POST',
  httpMode: 'batch',
  headers: {
    'Authorization': 'Bearer eyJhbGc...',
    'Content-Type': 'application/json'
  },
  bodyTemplate: { lat: '{latitude}', lon: '{longitude}', t: '{time}', acc: '{accuracy}' }
});
```

### REST JSON single

```ts
configure({
  url: 'https://api.miapp.com/locations',
  httpMethod: 'POST',
  httpMode: 'single',
  headers: { 'Authorization': 'Bearer TOKEN', 'Content-Type': 'application/json' },
  bodyTemplate: {
    user_id: '{uid}',
    lat: '{latitude}',
    lng: '{longitude}',
    speed: '{speed}',
    accuracy: '{accuracy}',
    timestamp: '{timestamp_iso}'
  },
  queryParams: { uid: 'USER_123' }
});
```

### Form-urlencoded (legacy)

```ts
configure({
  url: 'https://legacy.midominio.com/track.php',
  httpMethod: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  bodyTemplate: { lat: '{latitude}', lng: '{longitude}', t: '{time}' }
});
```

### GET con query params

```ts
configure({
  url: 'https://api.midominio.com/location?uid={uid}&lat={latitude}&lng={longitude}&speed={speed}&time={timestamp_iso}',
  httpMethod: 'GET',
  httpMode: 'single',
  queryParams: { uid: 'USER_123' }
});
```

### Webhook n8n / Make

```ts
configure({
  url: 'https://n8n.midominio.com/webhook/abc123',
  httpMethod: 'POST',
  headers: { 'Content-Type': 'application/json' },
  bodyTemplate: {
    user: 'USER_123',
    coords: { lat: '{latitude}', lng: '{longitude}' },
    ts: '{timestamp_iso}'
  }
});
```

### Firebase Realtime Database vía PUT

```ts
configure({
  url: 'https://miapp.firebaseio.com/users/{uid}/last.json?auth={token}',
  httpMethod: 'PUT',
  httpMode: 'single',
  queryParams: { uid: 'USER_123', token: 'FIREBASE_AUTH_TOKEN' },
  bodyTemplate: { lat: '{latitude}', lng: '{longitude}', t: '{time}' }
});
```

### Cola de sync separada

```ts
configure({
  // Tiempo real, una por una
  url: 'https://api.miapp.com/live',
  httpMethod: 'POST',
  httpMode: 'single',
  // Cola fallidas, en lotes
  syncUrl: 'https://api.miapp.com/locations/batch',
  syncHttpMethod: 'POST',
  syncMode: 'batch'
});
```

---

## Cambios técnicos requeridos

### Android

- `Config.java`: añadir `httpMethod`, `syncHttpMethod`, `httpMode`, `syncMode`, `queryParams`, `bodyTemplate` (mantener `httpHeaders` y `postTemplate` como alias).
- `ConfigMapper.java`: mapear nuevas keys.
- `HttpPostService.java`:
  - Aceptar `method` parámetro; eliminar `setRequestMethod("POST")` hardcoded.
  - Resolver placeholders en URL antes de abrir conexión.
  - Si método es GET → no enviar body. POST/PUT/PATCH envían body si aplica.
  - Si `httpMode == 'single'` → iterar locations y emitir una request por cada una.
- `PostLocationTask.java` y `SyncAdapter.java`: respetar `httpMode` y `syncMode`.

### iOS

- `MAURConfig`: mismas propiedades.
- `MAURPostLocationTask.m`: parametrizar `setHTTPMethod`, URL templating y single/batch.
- `MAURBackgroundSync.m`: idem.

### TypeScript / Angular

- `www/BackgroundGeolocation.d.ts`: añadir tipos. Mantener `httpHeaders` y `postTemplate` como alias deprecated pero funcionales.
- `angular/background-geolocation.service.ts`: re-exportar.

---

## Compatibilidad

- Apps existentes con solo `url` + `httpHeaders` + `postTemplate` siguen funcionando idénticas. Defaults: `httpMethod: 'POST'`, `httpMode: 'single'` (desde 5.0.1; forma de payload de v4).
- `headers` es alias nuevo de `httpHeaders`. Si ambos vienen, gana `headers`.
- `bodyTemplate` es alias nuevo de `postTemplate`. Si ambos vienen, gana `bodyTemplate`.
- Cambio totalmente aditivo, no breaking.

---

## Limpieza adicional asociada (v3.3)

- `plugin.xml` línea 173: `<uses-library android:name="org.apache.http.legacy" android:required="true" />`. `grep` no encuentra imports `org.apache.http.*` en `android/common/src/main/java` ni en `android/CDVBackgroundGeolocation`. El cliente HTTP usa `HttpURLConnection`. Eliminar.
- `android/dependencies.gradle` línea 12: `useLibrary 'org.apache.http.legacy'`. Eliminar.
- `android/build.gradle` líneas 9 y 25: `jcenter()` → `mavenCentral()`.
