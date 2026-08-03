# Changelog

## [5.0.1](https://github.com/josuelmm/cordova-background-geolocation/tree/5.0.1) (2026-08-01)

> **Correccion de regresiones de v5.0.0.** Un despliegue real destapo que varias de las ~132
> correcciones de v5.0.0 se habian verificado por separado pero nunca en COMBINACION, y que otras
> eliminaron comportamiento de v4 del que dependian consumidores reales. Actualiza cuanto antes.

### Fixed — perdida de datos (critico)

- **Un 4xx borraba la posicion en vez de encolarla.** v5.0.0 trataba los 4xx "permanentes" como
  consumidos (`return true`), y `post()` interpreta eso como entregada: `deleteLocationById()`.
  Un backend devolviendo 400 durante un despliegue borraba cada posicion del turno, sin cola y sin
  rastro. v4 devolvia `false` para cualquier no-2xx y la posicion caia a `updateLocationForSync()`.
  Restaurado. Perder datos del cliente nunca es preferible a una cola que crece, y `maxLocations`
  ya acota la tabla.
- **`httpMode:'batch'` + `x-www-form-urlencoded` enviaba `locations=<json>`.** v4 desenrollaba los
  arrays de un elemento y salia plano (`lat=..&lon=..`); v5.0.0 quito el desenrollado y ningun
  decoder OsmAnd/Traccar entendia el cuerpo -> **HTTP 400 en todas las posiciones**, que combinado
  con el punto anterior las borraba. Ahora un array bajo form-urlencoded se envia como una peticion
  por elemento con parametros planos, igual que ya hacia la ruta de sync. iOS ya lo hacia bien
  (`MAURPostLocationTask.m`), asi que ademas se restaura la paridad entre plataformas.
- **`Content-Type: application/x-www-form-urlencoded; charset=UTF-8` desactivaba todo el aplanado.**
  Las comparaciones eran por igualdad de la cabecera completa, asi que el parametro `charset`
  —escritura habitual— reabria el mismo 400 por otra puerta. Ahora se normaliza el media type.
- **`postTemplate` de tipo array + form-urlencoded producia 0 peticiones y HTTP 200.** Los
  elementos no-objeto no se pueden aplanar a `clave=valor`; se saltaban en silencio y el caller
  borraba la posicion. Ahora se detecta antes de enviar, se loguea el motivo y se envia el cuerpo
  sin aplanar para que el error sea visible.
- **Corte de red a mitad de lote reenviaba los items ya aceptados.** El `throw` que propagaba la
  `IOException` estaba dentro de un `catch (Exception)` que la tragaba, cayendo a un POST unico con
  `locations=<json>`: duplicados en el servidor y, si respondia 2xx, borrado del lote entero.
  Acotado a `catch (JSONException)`.

### Fixed — funcionalidad silenciada

- **`HTTP 285` (abort updates) se enmascaraba** en los envios por-peticion: el bucle lo trataba
  como 2xx y devolvia 200, asi que `onRequestedAbortUpdates` no se emitia nunca y el tracking
  seguia pese a que el servidor pedia parar.
- **Angular: `on(evt, a).subscribe(b)` ignoraba `b`.** Compilaba y no llamaba nunca al segundo
  callback. v4 si lo registraba. Restaurado, con NgZone tambien para los callbacks extra.
- **iOS: la cabecera `Content-Type` del sync salia duplicada.** Se fijaba a `application/json` y
  luego se hacia `addValue:` de todas las claves de `httpHeaders`, que CONCATENA: al cable salia
  `application/json,application/x-www-form-urlencoded`. Ahora se respeta la configurada.
- **iOS: el aplanado se saltaba con `charset` o con la cabecera en otra capitalizacion.**
  `MAURPostLocationTask.m` buscaba la clave literal `Content-Type` y comparaba la cabecera entera
  contra `application/x-www-form-urlencoded`. Con `content-type` en minusculas, o con
  `...; charset=UTF-8`, el array salia como JSON contra un decoder OsmAnd -> mismo 400 que en
  Android, por otra puerta. Ahora la clave se busca sin distinguir mayusculas y se normaliza el
  media type antes de comparar.

### Fixed — configuraciones que v4 aceptaba y v5.0.0 rechazaba

- `syncThreshold: 0` (v4: sincronizar en cada posicion) y `maxLocations: 0` (v4: no persistir)
  hacian fallar `configure()` al arrancar. Vuelven a aceptarse.

### Validacion

```
./gradlew :common:testDebugUnitTest :CDVBackgroundGeolocation:testDebugUnitTest
-> BUILD SUCCESSFUL — 82 tests, 0 fallos   (68 en v5.0.0; +14 de regresion para lo de abajo)
clang -fsyntax-only con stubs sobre los .m modificados -> sin errores
```

Los 5 tests de `HttpPostServicePerItemSyncTest` levantan un servidor HTTP real en localhost y
ejercitan la ruta POR-ELEMENTO completa (285, corte de red a mitad de lote, aplanado
form-urlencoded con `charset`). Es la combinacion que nunca se habia probado y por la que rompio
produccion: el mock de `HttpURLConnection` no la alcanza porque esa ruta abre una conexion por item.

**Aviso sobre iOS: nada esta compilado ni ejecutado.** `clang -fsyntax-only` contra stubs de Linux
detecta sintaxis y selectores inexistentes; no es un build ni una prueba de comportamiento.

### Fixed — regresiones v4 -> v5 cerradas (auditoria del diff completo)

- **R1 — `getLocations()` volvia vacio tras cada sync.** v5.0.0 paso el lote sincronizado a borrado
  fisico; `getAllLocations()` no filtra por estado, asi que el historico desaparecia. Vuelve el
  soft-delete de v4: `maxLocations` (10000 por defecto) es lo que acota la tabla, para eso existe.
- **R2 — `mocked` se habia añadido al template POR DEFECTO** en Android e iOS: todos los backends
  empezaban a recibir un campo nuevo sin pedirlo. Revertido en ambas plataformas; quien lo quiera
  lo pone en su `postTemplate` (`@mocked` sigue resolviendose).
- **R11 — Android ignoraba `syncMode` por completo.** Se parseaba, validaba y persistia, pero
  `SyncAdapter` no lo leia nunca: el modo real lo decidia el Content-Type. iOS si lo respetaba, asi
  que `syncMode:'single'` funcionaba en iOS y no hacia nada en Android. Ahora se honra.
  *Precision:* esto NO lo rompio v5.0.0 — el `SyncAdapter` de v4 tampoco leia `getSyncMode()` (0
  ocurrencias). Es una feature a medias desde v4 y una paridad nueva, no una regresion.
- **R13 — `<engine cordova-android ">=12.0.0">` con androidx que exige compileSdk 34.**
  cordova-android 12 compila con SDK 33, asi que el build fallaba con "requires ... version 34 or
  later" en un motor que el propio plugin declaraba soportar. Sube a `>=13.0.0`.
- **`<engine cordova-ios>` contradecia al propio CHANGELOG.** v5.0.0 documentaba requisito
  cordova-ios >= 7, pero `plugin.xml` seguia declarando `>=6.2.0`: un proyecto en cordova-ios 6
  instalaba el plugin sin aviso y fallaba despues. Alineado a `>=7.0.0`.
- **R14 — `syncHttpMethod:'GET'` borraba el lote con 200 y cero datos.** La URL de sync se resuelve
  con `location = null`, asi que ningun placeholder por posicion se sustituye. Rechazado en
  `validate()`.
- **R15 — `url` sin `syncUrl` no reintentaba nunca.** Los POST fallidos se marcaban SYNC_PENDING y
  se quedaban ahi: el unico lector es el SyncAdapter, que abortaba por no haber syncUrl. Se
  acumulaban hasta que `maxLocations` los reciclaba, justo lo contrario de lo que promete el
  Javadoc de `PostLocationTask`. Ahora `url` actua de destino de reserva.
  *Precision:* tampoco es regresion de v5.0.0 — v4 ya tenia `if (mConfig.hasValidSyncUrl())` en
  `PostLocationTask.java:157`. Es un agujero heredado que el Javadoc contradecia desde v4.
- **R6 — `READ_TIMEOUT` 120s -> 30s rompia los lotes grandes.** 30 s es correcto para un POST de una
  posicion (un servidor colgado retenia el wake lock dos minutos por intento), pero un backend que
  importa 100 filas de forma sincrona tardaba mas y fallaba donde v4 funcionaba. El envio por lote
  conserva los 120 s; el de una posicion se queda en 30 s.
- **R10 — `radio.js` descartaba `[fn, contextoB]`** si ya existia `[fn, contextoA]`: el dedupe
  comparaba solo la funcion. La identidad es el par (callback, contexto).

### Cambios de comportamiento que se MANTIENEN (con justificacion)

- **`mockLocationsEnabled` sigue siendo `null` en Android.** `Settings.Secure.ALLOW_MOCK_LOCATION`
  se retiro en API 23 y el minSdk es 24, asi que el codigo de v4 solo podia devolver `false`
  siempre: era una falsa confianza para un backend antifraude. La señal real por posicion es
  `isFromMockProvider()`, que `mockLocationPolicy` ya usa y `@mocked` expone.
- **`checkStatus()` usa OR de permisos** (antes AND). El servicio arranca con COARSE o con FINE, asi
  que reportar DENIED con solo aproximada describia mal lo que el plugin hace. Para distinguir
  precisa de aproximada esta `getDiagnostics().fineLocationGranted`.
- **Sync periodico de 15 min.** Sin el, un conductor que terminaba turno con `syncThreshold - 1`
  posiciones pendientes no las subia nunca. Se desactiva con `syncEnabled: false`.
- **`tripEnd` y `speeding` con la semantica nueva** (duracion sin la cola de parada, distancia
  filtrada por accuracy, histeresis del 95%): son correcciones de falsos positivos, no regresiones.
  Documentadas en `MEJORAS.md` por si tu facturacion por km dependia del calculo anterior.
- **`.d.ts` de `on()` y la forma de `radio`**: el tipo de v4 no describia el runtime. El cambio
  rompe compilacion, no ejecucion, y la migracion esta en `COMPATIBILIDAD.md`.

- **R12 — iOS no aplanaba en la ruta de sync.** `MAURBackgroundSync` serializaba SIEMPRE un array
  JSON, tambien cuando `httpHeaders` declaraba `Content-Type: application/x-www-form-urlencoded`:
  al cable salia `[{...}]` etiquetado como formulario y ningun decoder OsmAnd/Traccar lo entiende
  -> HTTP 400 en cada ventana de sync, con las filas restauradas y reintentadas indefinidamente.
  Ahora el cuerpo se construye segun el Content-Type configurado (normalizado, `charset` incluido,
  clave case-insensitive): con form-urlencoded se fuerza la ruta por-posicion — una peticion plana
  `lat=..&lon=..` por ubicacion, exactamente lo que hace Android — reutilizando el mismo aplanado
  que `MAURPostLocationTask` (omite `NSNull`, percent-encoding de clave y valor). La contabilidad
  por fila (`locationIds`) ya existia en esa ruta, asi que un fallo parcial solo restaura sus
  propias filas. Un `postTemplate` de tipo array se detecta antes de enviar, se loguea y se manda
  sin aplanar para que el error sea visible en vez de un 200 falso (misma guarda que en Android).
  **Aviso:** verificado con clang + stubs, no compilado en Xcode ni probado en dispositivo.

### Fixed — segunda auditoria (02-08), fuera de la tabla R1-R15

> La tabla R1-R15 cubria el diff v4 -> v5. Esta ronda audito el arbol COMPLETO (Android a fondo +
> paridad Android/iOS campo a campo) y encontro 15 fallos mas, varios de ellos del mismo tipo que
> el incidente de produccion: rutas que solo se ejercitan al fallar algo. Ninguno estaba en R1-R15.

**Android — perdida o duplicacion de datos**

- **`setBatchPartiallyCompleted` seguia en borrado FISICO** mientras `setBatchCompleted` ya habia
  vuelto al soft-delete (R1). Un lote que falla a mitad — el caso normal de form-urlencoded y de
  `syncMode:'single'` — vaciaba `getLocations()` exactamente igual que el bug original, solo que
  por el camino mas dificil de reproducir. R1 estaba a medias.
- **El contador de aceptados se perdia al cortarse la red.** La sobrecarga de 7 argumentos de
  `postJSONFile` (la unica que usa `SyncAdapter`) asignaba `acceptedOut[0]` DESPUES de la llamada,
  sin `try/finally`; al relanzarse la `IOException` la asignacion no ocurria, quedaba en `-1`, no
  se llamaba a `setBatchPartiallyCompleted` y las posiciones ya aceptadas por el servidor se
  reenviaban en el siguiente lote. Duplicados en cada corte de red.
- **`maxLocations: 0` significaba ILIMITADO.** `PostLocationTask` enrutaba `<= 0` a la sobrecarga
  sin limite, asi que quien lo configuraba para no almacenar obtenia crecimiento sin tope — lo
  contrario de lo que pide `docs/api.md` ("the total count never exceeds maxLocations") y de lo
  que esta misma version dice haber restaurado. Ahora 0 = no persistir.
- **`persistLocation(location, maxRows)` borraba la fila que luego actualizaba.** El `applyBatch`
  encolaba un DELETE de las N filas mas antiguas y, a continuacion, un UPDATE sobre
  `getOldestLocationUri()` — una de las que el DELETE acababa de eliminar. El UPDATE afectaba a 0
  filas: la posicion no se guardaba y se devolvia el id de una fila inexistente. Alcanzable
  siempre que la tabla supere `maxLocations`. Ahora se recorta a `maxRows - 1` y se inserta.
- **`deleteLocationById(-1)` lanzaba `IllegalArgumentException`** en el hilo del executor: la URI
  con id negativo no casa con el `UriMatcher` del provider. Guarda en el DAO y en el llamante.

**Android — funcionalidad silenciada**

- **`HTTP 285` se tragaba tambien en el bucle por-elemento de sync.** El fix H8 solo se aplico a
  `postFormUrlEncodedArray`; el bucle de `postJSONFile` (`syncMode:'single'`) seguia tratando 285
  como 2xx, seguia enviando y devolvia 200 -> `abort_requested` no se emitia nunca.
- **El sync periodico de 15 min no subia nada.** Se registraba con un `Bundle` vacio, asi que el
  `SyncAdapter` le aplicaba el `syncThreshold` normal (100) y `createBatch` devolvia `null`. La
  funcion existe justo para drenar la cola que se quedo POR DEBAJO del umbral, o sea que era
  inerte para su unico caso de uso. Extra `PERIODIC_DRAIN` -> umbral 0 en esas ejecuciones.
- **Un placeholder sin resolver salia como el literal `"@heading"`** en el POST en tiempo real.
  La ruta de sync (`BatchManager`) e iOS ya emitian `null`, que es lo que documenta `docs/api.md`.
  Traccar/OsmAnd responden 400 (`NumberFormatException`) ante `speed=@speed`: el mismo 400 del
  incidente, y la MISMA posicion reintentada por sync salia bien.
- **`maxAcceptedAccuracy: null` no podia desactivar el filtro.** El `null` explicito se descartaba
  en el parseo y en el merge, asi que quien activaba el filtro y luego intentaba apagarlo (tunel,
  urbano denso) seguia descartando TODOS los fixes sin forma de recuperarse salvo reinstalando.
  iOS ya tenia el reset explicito (`resetMaxAcceptedAccuracy`); Android ahora tambien.
- **El `charset` del `Content-Type` se perdia** en la ruta form-urlencoded: se enviaba el literal
  sin parametros, justo en el camino que se acababa de arreglar para reconocerlo.

**iOS — paridad**

- **Se subia un lote vacio `[]`** con la cola vacia (patron habitual de un boton "sincronizar
  ahora"). Android nunca crea un lote vacio. Si el backend contestaba 285 a ese `[]`, el tracking
  se detenia solo; con 4xx se emitia un `syncError` espurio.
- **Los eventos `foreground` y `background` no se emitian.** Estan declarados en la API publica
  (`www/BackgroundGeolocation.js` y `.d.ts`) y solo los mandaba Android.
- **El placeholder de URL `{is_moving}`** (documentado en `docs/api.md`) salia literal en iOS.
- **`stationaryRadius` se truncaba a entero al releerse de SQLite** (`12.5` -> `12`), pese a que
  la columna es REAL: tras reiniciar la app se monitorizaba una region mas pequena que la pedida.
- **Faltaba la cabecera `x-batch-id`** que Android envia en cada subida de sync. Un backend que
  deduplica lotes por esa cabecera funcionaba en Android y duplicaba en iOS al reintentar.

### Fixed — tercera pasada (02-08): las 14 deudas catalogadas, cerradas

> `COMPATIBILIDAD.md` §6 listaba 14 deudas conocidas con archivo y linea. Ninguna era del tipo que
> provoco el incidente, pero dejarlas abiertas y a la vez publicar la version no encaja. Cerradas.

**Android**

- **`deleteLocationById` y `deleteAllLocations` borraban FISICAMENTE** en
  `ContentProviderLocationDAO`, mientras `SQLiteLocationDAO` (mismo interfaz, mismo javadoc:
  *"location is not actually deleted only flagged as non valid"*) marcaba DELETED. Consecuencia: un
  POST correcto en tiempo real hacia desaparecer la fila, o sea el mismo vaciado de
  `getLocations()` de R1 pero por la ruta de tiempo real; y `getValidLocationsAndDelete()`
  destruia tambien filas SYNC_PENDING nunca enviadas. Ahora ambos son logicos.
- **Cursores sin comprobar `null`** en `getLocations`, `getLocationById`, `getFirstUnpostedLocation`,
  `getNextUnpostedLocation` y `getOldestLocationUri`. `ContentResolver.query()` devuelve `null` si
  el proceso del provider muere — y aqui el proceso `:sync` compite con el principal, asi que no es
  teorico. El endurecimiento de v5 solo habia cubierto los `*Count`. `getOldestLocationUri` ademas
  llamaba a `getLong(0)` tras un `moveToFirst()` que podia devolver false (tabla vacia).
- **`mConfig` y `mProvider` no eran `volatile`** en `LocationServiceImpl`. Los runnables del
  watchdog y del heartbeat corren en el main looper y los leen mientras `configure()`/`start()` los
  escriben desde el hilo del binder.
- **El fallback de R15 usaba `syncMode`/`syncHttpMethod`.** Cuando no hay `syncUrl` el destino
  efectivo es `url`, cuyo contrato es el de TIEMPO REAL: mandarle un array JSON porque `syncMode`
  es `batch` daba 400 permanente y reintento en bucle sobre el mismo payload. Ahora ese caso hereda
  `httpMode`/`httpMethod`.
- **El bucle per-item JSON no prevalidaba los elementos.** `postFormUrlEncodedArray` ya lo hacia
  (guarda H1); aqui un `postTemplate` de tipo array lanzaba `JSONException` a mitad de bucle, con
  parte del lote YA enviado, y el catch caia al POST unico reenviando todo (duplicados).
- **`RuntimeException` del `ContentResolver` escapaba de `onPerformSync`.** Solo se capturaba
  `IOException`, asi que "database is locked", un provider muerto o `SQLiteFullException` tumbaban
  el proceso `:sync` entero. Ahora se contabiliza como error de I/O y el SyncManager reintenta.
- **Al manifest del modulo Gradle le faltaban permisos** (`ACCESS_BACKGROUND_LOCATION`,
  `FOREGROUND_SERVICE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACTIVITY_RECOGNITION`,
  `uses-feature`). En Cordova/Capacitor manda `plugin.xml`, por eso no se veia; el build standalone
  del modulo y sus tests instrumentados corrian sin ellos.

**iOS**

- **No habia NINGUNA validacion de configuracion.** Android rechaza en el borde JS->nativo
  (`ConfigMapper.validate`); iOS aceptaba `locationProvider: 3` o `httpMode: 'batched'`, los
  PERSISTIA en SQLite y fallaba mucho despues — o caia a un default sin avisar — repitiendose en
  cada arranque porque el valor malo ya estaba guardado. Nuevo `-[MAURConfig validate:]` con los
  mismos rangos y conjuntos que Android, invocado en `configure:`.
- **`battery`, `isCharging` y `events` llegaban `nil` a JS.** El facade emitia el evento
  sincronamente tras `[postLocationTask add:]`, en carrera con el enriquecimiento que ocurre dentro
  del `dispatch_async`. Nuevo `-add:onReady:`: se emite ya enriquecido, en el mismo orden que
  Android (`transform -> eventos -> bateria -> broadcast -> persistir/postear`), y un transform que
  devuelve `nil` no emite, igual que Android.
- **El sync `single` lanzaba las N subidas en paralelo.** Android es secuencial y corta en el primer
  fallo: con 500 posiciones en cola y el servidor caido, Android hacia 1 peticion y iOS 500. Ahora
  se encolan y la siguiente sale solo tras un 2xx; un fallo (o un 285) vacia la cadena y el resto
  espera a la proxima ventana.
- **`headlessTask()` no tenia selector**, asi que Cordova rechazaba la promesa con "Invalid action"
  aunque el `.d.ts` documenta que en iOS es un no-op. Ahora cumple ese contrato: resuelve OK y deja
  rastro en el log.
- **Timeout del POST en tiempo real alineado a 30 s** (regia el default de 60 s de `NSURLSession`).
  Ante un backend que tarda 45 s, Android encolaba la posicion y iOS la daba por entregada y la
  borraba.
- **El escapado de placeholders de URL dejaba pasar `/ = ; : ? @ , $`.** Android usa `URLEncoder`,
  que escapa todo lo que no sea alfanumerico o `-_.*`. `queryParams: {id: 'flota/AB-12'}` viajaba
  como `id=flota/AB-12` y algunos proxies lo leian como cambio de ruta.
- **`getConfig()` devolvia formas distintas** para `includeBattery` (iOS lo omitia si nunca se
  fijo) y `postTemplate` (iOS devolvia siempre el default materializado; Android emite `null`
  cuando el usuario no configuro ninguno). El comportamiento efectivo coincidia; lo que veia el JS
  no. Alineado.

**Ambas plataformas**

- **`@timestamp_iso` estaba documentado y no existia.** `docs/api.md` y `README.md` lo listan como
  placeholder de `postTemplate` desde 3.3.0, pero ninguna plataforma lo resolvia: Android posteaba
  el literal `"@timestamp_iso"` y iOS `null`. Implementado en ambas con el mismo formato que ya
  usaban los resolvers de URL (`yyyy-MM-dd'T'HH:mm:ss'Z'` en UTC).

### Fixed — revisión adversarial del propio diff (02-08)

> Las tres auditorías anteriores miraban el código *existente*. Esta miró **los cambios de 5.0.1**,
> partiendo de que quien los escribió ya había introducido bugs al corregir (el parche del HTTP 400
> que provocaba pérdida silenciosa, R1 cerrado a medias). Encontró 9 defectos **introducidos por
> esta misma versión**. Ninguno estaba en el árbol antes.

**Regresiones que 5.0.1 introdujo y ya no están**

- **El fix del HTTP 285 borraba el resto del lote sin enviarlo.** Al cortar el bucle per-item en el
  285, `uploadLocations` seguía calculando `isStatusOkay = 2xx` — y 285 es 2xx —, así que
  `onPerformSync` llamaba a `setBatchCompleted()` y marcaba DELETED también las posiciones que
  nunca salieron del dispositivo. Con un lote de 100 y un 285 en la tercera se perdían 97. Ahora un
  2xx con `acceptedOut` menor que el total se degrada a "parcial" y solo se confirma el prefijo
  realmente aceptado.
- **`syncHttpMethod: 'GET'` volvía a entrar por el fallback de R15.** Al no haber `syncUrl`, el
  reintento hereda `httpMethod` — que sí admite GET — y la URL del lote se resuelve con
  `location = null`: salía un GET literal a `?lat={latitude}&lon={longitude}`, y un 2xx a esa
  petición habría borrado el lote. Es exactamente R14 por la puerta de atrás. Ahora ese caso omite
  el sync con un log explícito y las filas se quedan en cola.
- **`addPeriodicSync` con el extra nuevo creaba un SEGUNDO sync periódico.** El framework identifica
  un periodic sync por `(cuenta, autoridad, extras)`: al cambiar el Bundle de vacío al que lleva
  `PERIODIC_DRAIN`, no encontraba el de 5.0.0 y añadía otro. Todo dispositivo que actualizara se
  quedaba con dos despertando el proceso `:sync` cada 15 min. Se retira el anterior antes de añadir.
- **`syncThreshold: 0`, que 5.0.1 vuelve a aceptar, pedía un sync en CADA posición** aunque no
  hubiera nada pendiente (`count >= 0` siempre cierto): ~8600 arranques del proceso `:sync` al día
  sin enviar nada. Ahora exige además `count > 0`.
- **Los permisos añadidos a los manifests de los módulos Gradle se revierten.** El manifest merger
  los inyecta en TODA app consumidora del AAR sin forma sencilla de quitarlos, y dos de ellos
  (`ACCESS_BACKGROUND_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) obligan a declaraciones en
  Play Console o son motivo habitual de rechazo. `plugin.xml` sigue siendo la ruta correcta, donde
  la app elige. El problema original (build standalone del módulo) no justifica ese coste.

**iOS — defectos introducidos en esta versión**

- **`urlEncode:` provocaba un crash con cualquier texto no ASCII.** El conjunto nuevo se construyó
  con `+alphanumericCharacterSet`, que NO es `[A-Za-z0-9]` sino las categorías Unicode L\*, M\*, N\*:
  `é ñ á 中` viajaban sin escapar, `+[NSURL URLWithString:]` devolvía **nil** y
  `requestWithURL:nil` lanza `NSInvalidArgumentException` — crash del proceso en cada posición con
  `queryParams: {driver: 'José Pérez'}`. Ahora el conjunto permitido se declara carácter a carácter,
  que es la paridad real con `URLEncoder`. Se añade además una guarda de URL nil en la ruta de sync.
- **La cadena secuencial de sync `single` podía quedarse colgada para siempre.** Si `-upload:` salía
  antes de crear la tarea (serialización fallida, URL inválida) no había ningún
  `didCompleteWithError` que continuara: `singleChainActive` se quedaba en `YES` de por vida y todos
  los `-sync:` posteriores encolaban y salían sin enviar nada — el sync moría hasta reiniciar la
  app, con la cola creciendo sin límite. `-upload:` ahora informa de si llegó a arrancar la tarea y
  la cadena continúa con la siguiente entrada.
- **Abortar la cadena dejaba las filas restantes congeladas 15 minutos.** `getLocationsForSync`
  marca TODAS las filas `PostPending → SyncPending` al empezar; al abortar, solo la tarea fallida
  restauraba las suyas. Con 500 posiciones y un 502 en la primera, 499 quedaban invisibles hasta
  que el rescate de filas obsoletas las recuperaba. Ahora se devuelven a la cola al momento, como
  hace Android.
- **El evento a JS podía perderse y salir desordenado.** El `onReady` se despachaba a la main queue
  desde una cola **concurrente**, así que dos fixes seguidos podían entregarse fuera de orden
  (antes la llamada era síncrona y estrictamente ordenada), y el background task se cerraba sin
  esperar a la entrega. Ahora el pipeline corre en una cola **serie** —paridad exacta con el
  `newSingleThreadExecutor()` de Android— y la entrega es síncrona contra la main queue.

**Además, corregidos en la misma pasada**

- **`postTemplate: null` en `getConfig()` dependía del timing.** Se decidía leyendo el ivar, pero el
  getter materializa el template por defecto en cuanto alguien lo lee — y lo lee `-description`, o
  sea el propio log de `configure()`. Ahora hay un flag explícito, que además sobrevive al `copy`
  de `+merge:` y al viaje por SQLite.
- **`@mocked` no existía en iOS.** Tras revertir R2, el CHANGELOG afirmaba que "`@mocked` sigue
  resolviéndose": cierto en Android y falso en iOS, que solo entendía `@simulated`. Añadido como
  alias.
- **`docs/traccar.md` recomendaba una configuración que pierde datos** (`syncUrl` +
  `syncHttpMethod: 'GET'`) y que además `configure()` ahora rechaza. Reescrita con la explicación y
  las dos alternativas. `docs/api.md`, `README.md` y el `.d.ts` ya no listan `GET` como válido para
  `syncHttpMethod`.
- `-[MAURConfig validate:]` se declaraba en la `@interface` principal e implementaba en una
  categoría: clang avisaba con `-Wincomplete-implementation` en cada build. Declaración movida.
- `NSDateFormatter` de `@timestamp_iso` cacheado (se creaba uno por posición y por elemento de cada
  lote — el objeto más caro de Foundation, en el hot path).
- El servidor stub del test nuevo moría ante una `RuntimeException`, dejando colgados 30 s los
  tests siguientes.

### Conocido, sin corregir

- Nada a nivel de codigo: las tres auditorias (diff v4->v5, arbol completo, y las 14 deudas
  catalogadas) estan cerradas.
- Lo que queda son **gates de release**, no bugs: build de Xcode, QA en dispositivo (Android e
  iOS), primer run verde de los tests instrumentados y la matriz completa
  `httpMode` x `syncMode` x Content-Type x metodo x ruta x plataforma. Detalle en
  `COMPATIBILIDAD.md` §6. **Nada de iOS se ha compilado ni ejecutado**: solo clang + stubs.
