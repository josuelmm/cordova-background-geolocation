# MEJORAS — cordova-background-geolocation-plugin

> ## Estado real — v5.0.0, 2026-07-30
>
> **Corrección de una versión anterior de este documento.** Decía "132 de 132 corregidos". Era
> falso: los `[x]` se pusieron con un script de regex sobre una lista escrita a mano, no leyendo
> el código. **D3, D4 y D5 nunca se tocaron** y aparecían marcados. Este bloque sustituye ese
> resumen y solo afirma lo que se ha comprobado leyendo el código en esta pasada.
>
> ### Verificado leyendo el código (esta pasada)
>
> | Sección | Items | Resultado |
> |---|---|---|
> | 1. Android (A1–A21) | 21 | **21 cerrados.** A8 y A16 estaban parciales y se cerraron; A5 tenía una regresión introducida al arreglarlo (ver abajo) y también se cerró |
> | 2. HTTP/sync/persistencia (B1–B23) | 23 | 23 cerrados (verificado símbolo por símbolo) |
> | 3. Config/puente/build (C1–C17) | 17 | 17 cerrados. **C5 revisado en 5.0.1:** se había cerrado añadiendo `mocked` al template por defecto; eso cambiaba el payload de todos los backends sin pedirlo y se revirtió (R2). La afirmación "iOS ya lo tenía" era falsa: v4 iOS tenía 0 ocurrencias de `mocked`. Ahora `@mocked` se resuelve pero hay que declararlo en el `postTemplate` |
> | 4. iOS (D1–D30) | 30 | 30 cerrados a nivel de código |
> | 5. JS/Angular (E1–E19) | 19 | 19 cerrados |
> | 6. Driving events (F1–F19) | 19 | 19 cerrados. F4 requirió una segunda pasada (ver abajo) |
> | 8. Tests/CI (G1–G5) | 5 | 5 cerrados. G4 queda con `continue-on-error` hasta su primer run verde en macOS |
>
> ### Lo que estaba parcial y se cerró
>
> - **A5** `provider/../LocationServiceProxy.java:167-215` — el `try/catch` ya evitaba que
>   `startService()` matara el proceso, pero el reintento por `startForegroundService()` que se
>   añadió era peor que el problema: para un comando como `REGISTER_HEADLESS_TASK`,
>   `onStartCommand` toma la rama `processCommand()` y nunca promociona el servicio, así que la
>   promesa de 5 s se rompe y el sistema lanza `ForegroundServiceDidNotStartInTimeException`.
>   Ahora el reintento se limita a `START`, `START_FOREGROUND_SERVICE` y `START_FOREGROUND`.
> - **A8** `provider/RawLocationProvider.java` — con la localización apagada al arrancar nunca
>   quedábamos registrados en `LocationManager`, así que `onProviderEnabled()` (callback de
>   `LocationListener`) no podía llegar y el tracking no se recuperaba nunca. Ahora, mientras no
>   haya ningún proveedor, se arma un receptor dinámico de `PROVIDERS_CHANGED` (con
>   `RECEIVER_NOT_EXPORTED` en API 33+ vía el override del servicio) que reintenta la suscripción;
>   se desarma al conseguirla y en `onStop()`/`onDestroy()`, de forma idempotente.
> - **A16** `provider/DistanceFilterLocationProvider.java` — `volatile` solo daba visibilidad.
>   Nuevo `mStateLock` dedicado (no `this`) que hace atómicos `setPace()`, `onStart()`, `onStop()`
>   y la máquina de estados de `handleNewLocation()`. **Invariante documentada en el campo: nunca
>   se llama al delegate con el lock tomado** — `handleLocation`, `handleStationary`,
>   `handleServiceError` y `handleSecurityException` se aplazan a después de liberarlo, porque
>   `LocationServiceImpl.stop()`/`configure()` son `synchronized` sobre el servicio y vuelven a
>   entrar en el provider: tomarlos en orden inverso es una inversión de bloqueo clásica.
>   `onConfigure()` y `onExitStationaryRegion()` se dejaron **sin** el lock a propósito, y el
>   motivo está en un comentario en cada uno.
> - **G1** — los 72 tests instrumentados no compilaban (80 errores por
>   `InstrumentationRegistry.getTargetContext()`, retirado en androidx.test 1.4+). Migrados a
>   `getInstrumentation().getTargetContext()` / `ApplicationProvider.getApplicationContext()` en
>   10 ficheros; **compilan**. Nuevo job de CI con emulador (`android-emulator-runner`, API 30)
>   que los ejecuta, y nuevo job de tests JS con `npm run test:js` (script `test` añadido).
> - **G4** — scheme compartido commiteado en
>   `ios/common/BackgroundGeolocation.xcodeproj/xcshareddata/xcschemes/BackgroundGeolocationTests.xcscheme`
>   (el que generaba Xcode vivía en `xcuserdata/` y nunca llegaba al CI). Los pasos del job ya no
>   son `continue-on-error`; el job sí lo sigue siendo hasta su primer run verde en macOS.
>
> ### Segunda revisión (residuales detectados por el usuario, cerrados)
>
> - **F4 estaba a medias.** El `tick()` de Android colgaba del runnable del watchdog, que solo se
>   posteaba con `enableWatchdog: true` — y el default es `false`. Con `drivingEvents` activo y el
>   watchdog apagado (el caso normal) el tick nunca corría y el viaje seguía sin cerrarse. El
>   runnable se postea ahora **siempre** que el servicio está corriendo; el bloque del watchdog
>   conserva su propia guarda de `enableWatchdog`, y `configure()` ya no lo cancela al apagarlo.
> - **iOS no tenía tick.** Nuevo `NSTimer` de 60 s (`-scheduleDrivingTick`), independiente de
>   `heartbeatInterval` (que es opt-in y por defecto está apagado, así que reutilizarlo dejaba el
>   mismo agujero). Emite `stopped` + `tripEnd` con la duración medida hasta que el vehículo dejó
>   de moverse, igual que Android. Se arma en `start()`, se cancela en `stop()` y se re-evalúa en
>   `configure()`.
> - **`getPluginVersion` devolvía 4.5.5** en ambas plataformas
>   (`BackgroundGeolocationPlugin.PLUGIN_VERSION`, `CDVBackgroundGeolocation.m`). Ahora `5.0.0`.
> - **D4: el fallback global seguía existiendo.** La rama sin `locationIds` ni `uploadCutoff` (task
>   adoptado tras un relaunch: los associated objects mueren con el proceso) hacía un
>   `SyncPending → PostPending` sobre toda la tabla, revirtiendo también las filas de un upload
>   hermano en vuelo. Eliminada: esas filas huérfanas ya las rescata
>   `restoreStaleSyncLocationsOlderThan:` al inicio de cada ventana de sync. Ahora solo se loguea.
>
> ### Regresiones v4 → v5 (auditoría del 2026-08-01)
>
> Un fallo en producción (HTTP 400 en el 100% de las posiciones) destapó que las ~132 correcciones
> de v5.0.0 se verificaron **por separado pero nunca en combinación**, y que varias eliminaron
> comportamiento del que dependían consumidores reales. Se auditó el diff `2486d97..HEAD` buscando
> específicamente comportamiento **eliminado**.
>
> **Corregido en 5.0.1** (detalle en `CHANGELOG.md`): 4xx borraba la posición en vez de encolarla ·
> `batch`+form-urlencoded enviaba `locations=<json>` · `charset=UTF-8` desactivaba el aplanado ·
> `postTemplate` array daba 200 sin enviar nada · corte de red duplicaba items · HTTP 285
> enmascarado · Angular `subscribe(cb)` ignorado · iOS `Content-Type` duplicado en sync ·
> `syncThreshold:0` / `maxLocations:0` rechazados.
>
> **Estado tras 5.0.1: 15 de 15 revisadas.** 9 corregidas, 6 mantenidas con justificación
> explícita, 0 sin corregir.
>
> **Corrección de clasificación (verificada contra el árbol de v4):** de esas 8, **R11 y R15 no eran
> regresiones de v5.0.0**. Ambas ya se comportaban igual en v4 — `SyncAdapter` de v4 nunca leyó
> `getSyncMode()`, y `PostLocationTask` de v4 ya abortaba sin `syncUrl`. Están corregidas, pero
> como deuda heredada y paridad nueva, no como algo que v5.0.0 rompiera. Regresiones reales de
> v5.0.0 en esta tabla: 6 (R1, R2, R6, R10, R13, R14).
>
> | # | Regresión | Estado |
> |---|---|---|
> | R1 | `getLocations()` vaciado por el borrado físico del lote | **Corregida** — vuelve el soft-delete; `maxLocations` acota |
> | R2 | `mocked` añadido al template por defecto (ambas plataformas) | **Corregida** — revertido |
> | R6 | `READ_TIMEOUT` 120 s → 30 s rompía los envíos | **Corregida (en dos intentos)** — el primero dejó 30 s por posición y 120 s por lote, pero la ruta form-urlencoded SIEMPRE va por posición, así que en la práctica todo seguía en 30 s y rompió un despliegue real. Ahora 120 s (valor de v4) en todas las rutas, y las peticiones por elemento heredan el timeout |
> | R10 | `radio.js` descartaba `[fn, contextoB]` | **Corregida** — identidad = par (callback, contexto) |
> | R11 | Android ignoraba `syncMode` | **Corregida, pero mal clasificada aquí** — v4 tampoco leía `getSyncMode()` (0 ocurrencias en `SyncAdapter` v4). No es regresión de v5.0.0: es *feature a medias desde v4* + paridad nueva con iOS. Ahora se honra `single` |
> | R13 | `androidx` exige compileSdk 34 con `<engine>` en 12 | **Corregida** — motor mínimo 13 |
> | R14 | `syncHttpMethod:'GET'` borraba el lote con 200 | **Corregida** — rechazado en `validate()` |
> | R15 | `url` sin `syncUrl` no reintentaba nunca | **Corregida, pero mal clasificada aquí** — v4 ya exigía `hasValidSyncUrl()` (`PostLocationTask.java:157` en v4). No es regresión de v5.0.0: es *agujero heredado de v4* que el Javadoc contradecía. Ahora `url` actúa de destino de reserva |
> | R3 | `mockLocationsEnabled` siempre null | **Se mantiene** — v4 devolvía `false` siempre (API 23 retiró el ajuste): era una falsa confianza. Usa `@mocked` |
> | R4 | Sync periódico de 15 min | **Se mantiene** — sin él se perdían los pendientes al fin de turno. Opt-out: `syncEnabled: false` |
> | R5 | `checkStatus()` pasa a OR de permisos | **Se mantiene** — el servicio arranca con COARSE. Para distinguir: `getDiagnostics().fineLocationGranted` |
> | R7 | `tripEnd`: duración y distancia | **Se mantiene** — corrige falsos positivos. Revisa si facturas por km |
> | R8 | `speeding` con histéresis y cooldown | **Se mantiene** — evita ráfagas al cruzar el límite |
> | R9 | `.d.ts`: `on()` devuelve `EventSubscription` | **Se mantiene** — el tipo de v4 no describía el runtime. Rompe compilación, no ejecución |
> | R12 | iOS no aplana en la ruta de sync | **Corregida** — `MAURBackgroundSync` construye el cuerpo según el `Content-Type` configurado: con form-urlencoded fuerza la ruta por-posición (una petición plana por ubicación, igual que Android) y aplana `clave=valor` reutilizando la lógica de `MAURPostLocationTask`. **Sin compilar en Xcode**: verificado solo con clang + stubs |
>
> ### Segunda auditoría (02-08): 15 fallos más, fuera de la tabla R1–R15
>
> La tabla de arriba cubre el diff v4 → v5. Auditando el **árbol completo** (Android a fondo +
> paridad Android/iOS campo a campo) aparecieron 15 fallos que R1–R15 no podía ver porque no eran
> diferencias contra v4. Todos corregidos; detalle en `CHANGELOG.md`.
>
> | # | Qué | Plataforma |
> |---|---|---|
> | A1 | `setBatchPartiallyCompleted` seguía en borrado físico (R1 estaba a medias) | Android |
> | A2 | `acceptedOut` sin `try/finally`: duplicados en cada corte de red | Android |
> | A3 | `HTTP 285` tragado también en el bucle per-item de sync | Android |
> | A4 | `maxLocations: 0` significaba ilimitado, no "no persistir" | Android |
> | A5 | `persistLocation(maxRows)` borraba la fila que después actualizaba | Android |
> | A6 | `deleteLocationById(-1)` lanzaba `IllegalArgumentException` | Android |
> | A7 | El sync periódico de 15 min nunca drenaba (le aplicaba `syncThreshold`) | Android |
> | A8 | Placeholder sin resolver salía como literal `"@heading"` en tiempo real | Android |
> | A9 | `maxAcceptedAccuracy: null` no podía desactivar el filtro | Android |
> | A10 | El `charset` del `Content-Type` se perdía en form-urlencoded | Android |
> | B1 | Se subía un lote vacío `[]` con la cola vacía | iOS |
> | B2 | Los eventos `foreground` / `background` no se emitían | iOS |
> | B3 | El placeholder de URL `{is_moving}` salía literal | iOS |
> | B4 | `stationaryRadius` se truncaba a entero al releerse de SQLite | iOS |
> | B5 | Faltaba la cabecera `x-batch-id` en las subidas de sync | iOS |
>
> ### Cuarta auditoría (02-08): las 14 deudas catalogadas, cerradas
>
> La tercera auditoría dejó 14 deudas abiertas, documentadas con archivo y línea. Están cerradas.
> Detalle en `CHANGELOG.md`.
>
> | # | Qué | Plataforma |
> |---|---|---|
> | A1 | `deleteLocationById` / `deleteAllLocations` borraban físicamente | Android |
> | A2 | Cinco cursores sin comprobar `null` (+ `getOldestLocationUri` con tabla vacía) | Android |
> | A3 | `mConfig` / `mProvider` no eran `volatile` | Android |
> | A4 | El fallback a `url` usaba `syncMode`/`syncHttpMethod` en vez de `httpMode`/`httpMethod` | Android |
> | A5 | El bucle per-item JSON no prevalidaba los elementos | Android |
> | A6 | `RuntimeException` del `ContentResolver` tumbaba el proceso `:sync` | Android |
> | A7 | Faltaban 5 permisos en los manifests de los módulos Gradle | Android |
> | B1 | Cero validación de configuración | iOS |
> | B2 | `battery` / `isCharging` / `events` llegaban `nil` al evento JS | iOS |
> | B3 | Sync `single` lanzaba las N subidas en paralelo sin cortar al fallar | iOS |
> | B4 | `headlessTask()` sin selector: promesa rechazada con "Invalid action" | iOS |
> | B5 | Timeout del POST en tiempo real: 60 s por defecto, no 30 s | iOS |
> | B6 | El escapado de URL dejaba pasar `/ = ; : ? @ , $` | iOS |
> | B7 | `getConfig()` con formas distintas para `includeBattery` y `postTemplate` | iOS |
> | — | `@timestamp_iso`: documentado desde 3.3.0, nunca implementado | **ambas** |
>
> **No queda ningún hallazgo de código abierto.** Lo que queda son gates de release —Xcode, QA en
> dispositivo, CI instrumentado— listados en `COMPATIBILIDAD.md` §6. Este documento afirmó dos veces
> "cero hallazgos abiertos" cuando no lo era; la diferencia ahora es que las cuatro auditorías están
> escritas con su alcance, y ese alcance dice explícitamente qué NO se ha probado: nada de iOS se ha
> compilado ni ejecutado.
>
> **Lección de método, no de código:** cada corrección de v5.0.0 se validó aislada. Lo que faltó
> fue la matriz — `httpMode` × `syncMode` × `Content-Type` × método × ruta × plataforma — y un
> diff de v4 buscando qué se había **quitado**. Los 5 tests de regresión añadidos en 5.0.1 cubren
> ya las celdas del incidente.
>
> ### Validación ejecutada
>
> ```
> cd android && ./gradlew :common:testDebugUnitTest :CDVBackgroundGeolocation:testDebugUnitTest
> → BUILD SUCCESSFUL — 84 tests, 0 fallos          (68 en v5.0.0; +16 de regresión en 5.0.1)
> cd android && ./gradlew :common:compileDebugAndroidTestJavaWithJavac \
>                        :CDVBackgroundGeolocation:compileDebugAndroidTestJavaWithJavac
> → BUILD SUCCESSFUL — los 72 tests instrumentados compilan (antes: 80 errores)
> npm run test:js → 239 aserciones OK
> clang -fsyntax-only con stubs sobre los .m modificados → sin errores nuevos
> python -c 'yaml.safe_load(ci.yml)' → YAML válido
> ```
>
> *(Este bloque decía «68 tests» hasta el 02-08; estaba desfasado respecto al CHANGELOG.)*
>
> ### Lo que NO está verificado — leer antes de publicar
>
> 1. **iOS nunca se ha compilado.** No hay macOS ni Xcode en este entorno. El chequeo con clang
>    contra stubs detecta sintaxis y selectores inexistentes, **no** sustituye a un build real.
>    Ábrelo en Xcode antes de dar por buenos los 30 puntos D*.
> 2. **Cero pruebas en dispositivo.** Ni Android ni iOS. Nada de lo de aquí se ha visto funcionar
>    en un teléfono real: ni el tracking en background, ni el sync, ni los eventos de conducción.
> 3. **Tests instrumentados: 60 ejecutados, 4 fallos en el primer run** (CI #29/#30), los cuatro
>    diagnosticados y corregidos. Ninguno era un bug del plugin; los cuatro eran los tests
>    quedándose atrás respecto a cambios de v5:
>    - `LocationServiceTest.testStartStop`, `LocationServiceProxyTest.testStart` y `.testStop`
>      (timeout de 5 s). `start()` ahora se niega a arrancar sin `ACCESS_FINE/COARSE_LOCATION`
>      (antes levantaba un servicio incapaz de producir un fix, y en Android 8+ eso además es un
>      crash inmediato del FGS). En el emulador la app de instrumentación no tiene el permiso en
>      runtime, así que `start()` volvía temprano: `sIsRunning` se quedaba en false y
>      `MSG_ON_SERVICE_STARTED/STOPPED` no se emitían nunca. Añadido `GrantPermissionRule`.
>    - `LocationContentProviderTest.testShouldDeleteMultipleLocations` (`expected:<2> but
>      was:<3>`). Efecto de B6: el provider toma la conexión de
>      `SQLiteOpenHelper.getHelper()`, que llama a `getApplicationContext()` y por tanto desenvuelve
>      el `RenamingDelegatingContext` con el que este harness aísla la BD. El provider escribía en
>      `cordova_bg_geolocation.db` mientras `setUp()` borraba `test.cordova_bg_geolocation.db`, así
>      que las filas sobrevivían entre tests. El `deleteDatabase()` del test usa ahora el mismo
>      helper que el provider.
>    Gradle solo imprime "There were failing tests. See the report at: file:///...", que en CI no
>    sirve; se añadió `scripts/print-failing-androidtests.py`, invocado por el job cuando falla.
>    **Los arreglos no se han podido ejecutar aquí** (no hay emulador): se validan en el próximo
>    push.
> 3b. **El job de iOS ha fallado dos veces por errores del propio workflow, no del código.**
>    CI #29: `xcodebuild` exige `-scheme` cuando se pasa `-derivedDataPath`, y el paso de build
>    usaba `-target` → corregido a `build-for-testing -scheme` + `test-without-building`.
>    CI #30: el `.xcscheme` escrito a mano llevaba un `<TestPlans>` vacío; Xcode toma la sola
>    presencia de ese nodo como "este scheme va por test plans" y aborta con "uses test plans but
>    has no test plan(s) associated with it" → nodo eliminado (el proyecto no tiene `.xctestplan`;
>    la acción de test la dirige `<Testables>`). **iOS sigue sin haberse compilado nunca**, así que
>    cada fallo de esta cadena solo se descubre al empujar.
> 4. **D10 depende de la app anfitriona.** No hay forwarding de
>    `application:handleEventsForBackgroundURLSession:` en todas las versiones de cordova-ios. El
>    snippet para el `AppDelegate`, con cómo comprobar que quedó bien, está en
>    **`COMPATIBILIDAD.md` §5** (y en `CDVBackgroundGeolocation.m:81-122`).
> 5. **El tick de iOS no corre con el proceso suspendido.** `NSTimer` no se dispara mientras iOS
>    tiene la app suspendida, así que `tripEnd` puede llegar en el siguiente despertar en vez de
>    exactamente al cumplirse `stoppedDuration`. El evento llega; el instante exacto no se
>    garantiza. En Android el `Handler` corre mientras viva el foreground service.
>
> **No publiques en npm sin (1) un build de Xcode limpio y (2) una prueba en dispositivo real de
> Android e iOS.** La versión ya es 5.0.0 por el volumen de cambios de comportamiento; ver
> `COMPATIBILIDAD.md`.
>
> Los pendientes conservan `- [ ]`. Los bloques 0 y 8 describen el plan original y no se han reescrito.

Auditoría v4.5.5. Fecha: 2026-07-29.
Alcance: Android (servicio, providers, HTTP, sync, persistencia, config, puente Cordova), iOS, capa JS/www, wrapper Angular, driving events, tests y CI.
Método: lectura directa del código. Cada hallazgo incluye archivo:línea y evidencia verificada.

Marcar `[x]` al corregir.

---

## 0. Bloqueantes — corregir primero

Estos rompen el requisito central (no dejar de emitir posiciones, no perder posiciones, no duplicar).

| # | Área | Defecto |
|---|------|---------|
| A1 | Android provider | `DISTANCE_FILTER` deja de emitir posiciones para siempre al entrar en estacionario |
| A2 | Android provider | `ACTIVITY_PROVIDER` nunca recibe actividad: funcionalidad muerta |
| B1 | Android sync | Duplicados garantizados en cada lote que falla a mitad |
| B2 | Android sync | Carrera BatchManager: posiciones marcadas como enviadas sin haberse enviado |
| C1 | Android config | `enableWatchdog` nunca llega a persistirse: el watchdog jamás arranca |
| C2 | Android sync | Eventos `sync*` emitidos desde el proceso `:sync` nunca llegan a JS |
| D1 | iOS | POST en background sin `beginBackgroundTask`: huecos sistemáticos de tracking |
| D2 | iOS | Nunca se pide upgrade `WhenInUse` → `Always`: el tracking muere en background |
| D3 | iOS | Precisión reducida iOS 14+ nunca detectada: trayectos inventados de ~3 km |
| D4 | iOS | `restoreFailedSyncLocations` revierte todas las filas: duplicados masivos |
| E1 | JS | Fuga de listeners: `unsubscribe()` desregistra del canal equivocado |
| F1 | Driving | Entrega en lote produce Δt≈1 ms → `hardBrake`/`crash` falsos en cada despertar |
| F2 | Driving | Fix sin velocidad se trata como 0 → alertas de accidente falsas |

---

## 1. Android — servicio, providers y ciclo de vida

### CRÍTICA

- [x] **A1. `DISTANCE_FILTER` deja de emitir posiciones para siempre**
  `android/common/.../provider/DistanceFilterLocationProvider.java:168-186`
  Los `PendingIntent` de alarma usan intents **explícitos** hacia receivers registrados dinámicamente (`StationaryAlarmReceiver`, `StationaryLocationMonitorReceiver`, `SingleUpdateReceiver` — clases internas privadas, no declaradas en ningún manifest).
  ```java
  Intent stationaryAlarmIntent = new Intent(mContext, StationaryAlarmReceiver.class);
  stationaryAlarmPI = PendingIntent.getBroadcast(mContext, 9000, stationaryAlarmIntent, updateCurrentFlag);
  registerReceiver(stationaryAlarmReceiver, new IntentFilter(STATIONARY_ALARM_ACTION));
  ```
  Android solo consulta receivers dinámicos cuando `intent.getComponent() == null`. Con componente explícito inexistente en el manifest, el broadcast se descarta en silencio.
  Secuencia: `onStart()` → `setPace(false)` → tras 5 fixes → `enterStationary()` → `unsubscribeLocationUpdates()` + `startPollingStationaryLocation()`. La alarma nunca llega ⇒ **no se vuelve a pedir ninguna localización**. Con `enableWatchdog=false` (default) no hay recuperación.
  **Fix:** intent implícito `new Intent(STATIONARY_ALARM_ACTION).setPackage(mContext.getPackageName())`, o declarar los tres receivers como `public static` en el manifest.

- [x] **A2. `ACTIVITY_PROVIDER`: las actualizaciones de actividad nunca llegan**
  `android/common/.../provider/ActivityRecognitionLocationProvider.java:96-103`
  Mismo defecto: `DetectedActivitiesReceiver` (privado, línea 351) no está en el manifest.
  `lastActivity` queda permanentemente en `UNKNOWN`: el evento JS `activity` nunca se dispara y `stopOnStillActivity` no funciona.
  Bonus: `requestCode 9002` colisiona con el de `DistanceFilterLocationProvider` con `FLAG_UPDATE_CURRENT`.
  **Fix:** igual que A1. Cambiar el requestCode a 9004.

- [x] **A3. `onDestroy()` no libera WakeLock, heartbeat ni sensores**
  `android/common/.../service/LocationServiceImpl.java:347-373`
  No hay `mWakeLock.release()`, `cancelHeartbeat()`, `mSensorFusion.stop()` ni `removeCallbacks`. Todo eso solo existe en `stop()` (833-864).
  Camino real: `onTaskRemoved()` con `stopOnTerminate=true` → `stopSelf()` → `onDestroy()` **sin pasar por `stop()`**. Queda el `PARTIAL_WAKE_LOCK` retenido, el `ScheduledExecutorService` del heartbeat vivo reteniendo el Service, y sensores a 50 Hz sin desregistrar.
  **Fix:** extraer a `releaseResources()` idempotente y llamarlo desde `stop()` y `onDestroy()`.

- [x] **A4. `onDestroy()` bloquea el hilo principal hasta 60 s → ANR**
  `LocationServiceImpl.java:365` + `PostLocationTask.java:126-137` + `HttpPostService.java:29-31`
  ```java
  public void shutdown() { shutdown(60); }
  ...
  if (!mExecutor.awaitTermination(waitSeconds, TimeUnit.SECONDS)) { ... }
  ```
  Con `READ_TIMEOUT_MS = 120_000`, un POST en vuelo bloquea `onDestroy()` (main thread, ~20 s antes de ANR).
  Al morir por ANR no se ejecuta `deleteUnpostedLocations()` y las filas quedan en `POST_PENDING`, estado que el batch **nunca** selecciona.
  **Fix:** `shutdown(2)` o moverlo fuera del main thread. Bajar `READ_TIMEOUT_MS` a ~30 s.

### ALTA

- [x] **A5. `startService()` sin protección → excepción no capturada mata el proceso**
  `LocationServiceProxy.java:153-155` (y `:40` `registerHeadlessTask` sin guarda `isStarted()`)
  ```java
  private void executeIntentCommand(Intent intent) { mContext.startService(intent); }
  ```
  `startForegroundService()` (90-99) sí tiene `try/catch`; este no. En Android 8+ con la app en background lanza `IllegalStateException`; al correr en un hilo del pool de Cordova mata el proceso completo.
  **Fix:** `try/catch(Exception)` + `ContextCompat.startForegroundService()` cuando el comando implique arrancar.

- [x] **A6. El watchdog se auto-desactiva de forma permanente**
  `LocationServiceImpl.java:192-220`
  Cualquier `return` temprano rompe la cadena de `postDelayed`: el watchdog muere hasta reiniciar el servicio. `configure()` (1104-1140) re-evalúa heartbeat, drivingEvents y wakeLockMode pero **no** el watchdog.
  **Fix:** mover el `postDelayed` a un `finally`; añadir (re)programación en `configure()`.

- [x] **A7. El watchdog no cuenta las posiciones estacionarias**
  `LocationServiceImpl.java:1298-1330` vs `:1209`
  `onLocation()` hace `mLastLocationTime = System.currentTimeMillis()`; `onStationary()` no.
  Con DISTANCE_FILTER parado, el watchdog ejecuta `mProvider.onStop(); onStart();` cada 60 s indefinidamente → GPS a `PRIORITY_HIGH_ACCURACY` con el vehículo aparcado.
  **Fix:** actualizar `mLastLocationTime` también en `onStationary()`.

- [x] **A8. `RawLocationProvider` no se recupera si el GPS está apagado al arrancar**
  `android/common/.../provider/RawLocationProvider.java:47-51, 145-149`
  ```java
  if (providers.isEmpty()) { logger.warn(...); return; }   // sin reintento
  public void onProviderEnabled(String provider) { logger.debug(...); }  // no re-suscribe
  ```
  Es el provider por defecto de la app cliente. Si se arranca con ubicación desactivada, nunca reintenta, pero `MSG_ON_SERVICE_STARTED` ya se publicó: la app cree que rastrea.
  **Fix:** emitir `handleServiceError` + reintento programado; implementar `onProviderEnabled()` → `onStop(); onStart();`.

- [x] **A9. `PERMISSIONS` exige FINE **y** COARSE**
  `BackgroundGeolocationFacade.java:58-61, 328-348`
  Incoherente con `LocationServiceImpl.hasLocationPermission()` (938) y `LocationServiceProxy.hasLocationPermission()` (101), que aceptan cualquiera de las dos.
  En Android 12+ con "Ubicación aproximada" concedida, `checkPermissions` cae siempre en `onPermissionDenied` y el servicio no arranca nunca. Fallo silencioso.
  **Fix:** alinear los tres puntos de comprobación, o devolver un error explícito indicando que se requiere precisión exacta.

- [x] **A10. Si `startForeground()` falla, el servicio queda zombi**
  `LocationServiceImpl.java:948-971`
  Tras el `return` del catch final, `sIsRunning` sigue `true` y el JS ya recibió `MSG_ON_SERVICE_STARTED`, pero es un servicio de fondo que el sistema mata en minutos. Causas: `ForegroundServiceStartNotAllowedException`, `smallIcon` inexistente (`getDrawable` → 0), `POST_NOTIFICATIONS` denegado.
  **Fix:** emitir `MSG_ON_ERROR`, reintentar con backoff o `stopSelf()`. Validar el icono con fallback a `android.R.drawable.ic_menu_mylocation`.

### MEDIA

- [x] **A11. Excepciones de `configure()` tragadas + NPE seguro sobre `mProvider`**
  `LocationServiceImpl.java:1061-1102` + `org/chromium/content/browser/ThreadUtils.java:100-108`
  `runOnUiThread(Runnable)` envuelve en `FutureTask` y nadie llama `get()`: cualquier excepción se pierde. Si el servicio está "started" vía `REGISTER_HEADLESS_TASK` pero `start()` no corrió, `mProvider` es `null` → NPE invisible y el resto del runnable (heartbeat, drivingEvents, wakeLockMode) no se ejecuta. El JS recibe `success`.
  **Fix:** guarda `if (mProvider == null) return;` + `try/catch(Throwable)` con `MSG_ON_ERROR`.

- [x] **A12. Comparación de `Integer` por identidad**
  `LocationServiceImpl.java:1089` — `if (currentConfig.getLocationProvider() != mConfig.getLocationProvider())`
  Funciona por accidente (caché de `Integer` -128..127). Fuera de caché provoca destrucción+recreación del provider en cada `configure()`.
  **Fix:** `Objects.equals(...)`.

- [x] **A13. `requestIgnoreBatteryOptimizations()` no hace nada: falta el permiso**
  `android/common/.../oem/BatteryOemHelper.java:46-56`
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` no está en `plugin.xml` ni en ningún `AndroidManifest.xml` del plugin. Sin él, la actividad del sistema se cierra sin diálogo y **sin excepción** (ni entra el fallback del catch).
  *Nota:* GPSMasterClient sí lo declara en su propio manifest, así que allí funciona. Cualquier otro consumidor del plugin, no.
  **Fix:** declararlo en `plugin.xml` (justificar ante Play Store) o redirigir siempre a `openBatterySettings()`.

- [x] **A14. Sensor fusion muestrea a ~100 callbacks/s en el hilo principal**
  `android/common/.../sensor/SensorFusionDetector.java:83-84, 96-103`
  `new Handler(Looper.getMainLooper())` + `SENSOR_DELAY_GAME` × 2 sensores, activo toda la sesión (no solo en viaje). Ver también F7.
  **Fix:** `HandlerThread` propio; registrar/desregistrar según `setTripActive()`.

- [x] **A15. `getCurrentLocation()` puede auto-bloquearse**
  `android/common/.../LocationManager.java:47-62, 95-100`
  `requestSingleUpdate(..., Looper.getMainLooper())` + `mCountDownLatch.await(timeout)`. Si `onPermissionGranted()` se invoca en el main thread, deadlock hasta agotar timeout → ANR.
  **Fix:** pasar el `Looper` de un `HandlerThread` propio.

- [x] **A16. Receivers del provider entregados en el HandlerThread, estado tocado desde main**
  `LocationServiceImpl.java:1408-1418` + `AbstractLocationProvider.java:77-79`
  Al corregir A1, `setPace(false)` correrá en `LocationServiceImpl.Thread` mientras `onStart/onStop/handleNewLocation` corren en main. `isMoving`, `isAcquiringStationaryLocation`, `stationaryLocation`, `scaledDistanceFilter`, `lastLocation`, `isStarted` no son `volatile` ni están sincronizados.
  **Fix:** registrar los receivers con un `Handler` del main looper, o sincronizar y marcar `volatile`.

- [x] **A17. `isStarted()` depende de `ActivityManager.getRunningServices()`**
  `LocationServiceInfoImpl.java:32-39` — API deprecada desde API 26; algunos OEM devuelven listas incompletas. Todos los comandos del proxy están guardados por este método: un falso negativo deja el servicio corriendo indefinidamente; un falso positivo provoca A5.
  **Fix:** flag estático/`SharedPreferences` mantenido por el propio servicio; `getRunningServices` solo como respaldo.

### BAJA

- [x] **A18.** `sIsRunning` (`LocationServiceImpl.java:155`) y `mIsInForeground` (`:157`) estáticos sin `volatile`, leídos desde otros hilos → `isRunning()` puede devolver valor obsoleto.
- [x] **A19.** `android/common/src/main/AndroidManifest.xml:57-58`: `<uses-library android:name="org.apache.http.legacy" android:required="true" />` innecesaria (el HTTP usa `HttpURLConnection`). Con `required="true"` puede provocar `INSTALL_FAILED_MISSING_SHARED_LIBRARY`. *Solo afecta al build AAR standalone; `plugin.xml` no la inyecta.*
- [x] **A20.** `BootCompletedReceiver.java:73` envía un extra `config` que `onStartCommand` (390-419) nunca lee. Código muerto.
- [x] **A21.** `AbstractLocationProvider.java:190-196`: `hasMockLocationsEnabled()` usa `Settings.Secure.ALLOW_MOCK_LOCATION`, eliminado en Android 6 → siempre `false`. Campo engañoso reportado al backend.

**Verificado y NO es bug:** `registerReceiver` de `CONNECTIVITY_ACTION`/`MODE_CHANGED_ACTION` sin flag exportado (broadcasts protegidos del sistema, exentos en Android 14); los `PendingIntent` de notificación ya usan `FLAG_IMMUTABLE` en S+; el canal de notificación se crea en `onCreate` antes de `startForeground`; `deleteUnpostedLocations()` no borra, solo re-marca; arrancar un FGS `location` desde `BOOT_COMPLETED` está permitido en Android 14/15 y el receiver valida `ACCESS_BACKGROUND_LOCATION`.

---

## 2. Android — HTTP, sync y persistencia

### CRÍTICA

- [x] **B1. Duplicados garantizados en cada lote fallido a mitad**
  `HttpPostService.java:275-289` + `SyncAdapter.java:148-159`
  ```java
  for (int i = 0; i < len; i++) {
      int code = perRequest.postJSON(item, headers);
      if (code < 200 || code >= 300) return code;   // los i anteriores YA fueron aceptados
  }
  ```
  El `SyncAdapter` trata el retorno como todo-o-nada. Lote de 100, la nº 60 devuelve 500: las 59 primeras ya están en el servidor pero siguen `SYNC_PENDING` y se reenvían. Con red inestable ocurre en casi todos los lotes.
  **Fix:** devolver la lista de ids aceptados (o el índice del primer fallo) y marcar `DELETED` solo esas filas. Alternativa: borrar cada fila justo tras su 2xx dentro del bucle.

- [x] **B2. Carrera BatchManager: posiciones marcadas como enviadas sin haberse escrito al lote**
  `sync/BatchManager.java:80-123`
  ```java
  cursor = resolver.query(contentUri, null, whereClause, whereArgs, ...);   // L80
  while (cursor.moveToNext()) { writer.write(...); }                        // L109
  resolver.update(contentUri, values, whereClause, whereArgs);              // L123  MISMO whereClause
  ```
  Sin transacción, y en procesos distintos. Mientras `:sync` escribe el archivo, `PostLocationTask.post()` falla un POST y ejecuta `updateLocationForSync(id)` → esa fila pasa a `SYNC_PENDING`, cumple el `whereClause` del `update`, recibe `batchStartMillis` y `setBatchCompleted` la marca `DELETED` **sin haberse enviado nunca**.
  Agravante: el cursor pagina por `CursorWindow`; con miles de filas se re-ejecuta la query y las inserciones concurrentes desplazan filas → saltos y repeticiones dentro del propio archivo.
  **Fix:** recolectar los `_id` realmente escritos y hacer `update`/`setBatchCompleted` con `_ID IN (...)`. O estampar `batch_start` **antes** de leer y consultar por `batch_start = ?`.

### ALTA

- [x] **B3. `HttpURLConnection` nunca se cierra ni se consume la respuesta**
  `HttpPostService.java:168`, `:353` — cero ocurrencias de `disconnect`, `getInputStream`, `getErrorStream` en todo el archivo.
  Un POST por posición, 24/7, por vehículo. Cada respuesta deja el socket sin drenar → la conexión no vuelve al pool, se acumulan descriptores hasta `EMFILE`.
  **Fix:** `finally` que drene y cierre `getInputStream()`/`getErrorStream()` y llame `disconnect()`.

- [x] **B4. `maxLocations` no se aplica nunca: la tabla crece sin límite**
  `PostLocationTask.java:107` + `BatchManager.java:177`
  `persistLocation(location, maxRows)` **solo se llama desde tests**. `setBatchCompleted` hace borrado lógico (`STATUS = DELETED`), nunca físico.
  Un fix cada 10 s = ~8.600 filas/día que nunca se eliminan. Cientos de MB y, con B5, degradación hasta el ANR.
  **Fix:** usar `persistLocation(location, config.getMaxLocations())` y purgar físicamente en `setBatchCompleted` (`DELETE FROM location WHERE batch_start = ?`).

- [x] **B5. Sin índice en la columna de estado + conteo por cursor completo en cada fix**
  `SQLiteLocationContract.java:76-80` (solo `time_idx` y `batch_id_idx`) + `ContentProviderLocationDAO.java:237-246`
  ```java
  Cursor cursor = mResolver.query(mContentUri, null, whereClause, whereArgs, null);
  int count = cursor.getCount();
  ```
  `projection = null` → todas las columnas de todas las filas, solo para un `getCount()`. `PostLocationTask.post():160` lo invoca en **cada** posición. Con 20.000 filas pendientes: 20.000 filas serializadas por Binder por fix.
  **Fix:** `CREATE INDEX status_idx ON location(valid, batch_start)` + migración; usar `projection = {"count(*)"}` o `DatabaseUtils.queryNumEntries`.

- [x] **B6. Dos conexiones SQLite al mismo fichero: el provider no usa el singleton**
  `data/provider/LocationContentProvider.java:86`
  ```java
  mDatabaseHelper = new SQLiteOpenHelper(context);   // el javadoc del helper dice "only for testing"
  ```
  Los DAO sí usan `SQLiteOpenHelper.getHelper(context)`. Además `SyncAdapter.java:73` abre el mismo fichero desde el proceso `:sync`. Sin WAL → `SQLiteDatabaseLockedException` y pérdida del `insertOrThrow` del fix.
  **Fix:** usar `getHelper(context)` en el provider + `setWriteAheadLoggingEnabled(true)`.

- [x] **B7. Transacciones sin `try/finally`: la BD queda bloqueada ante cualquier excepción**
  `data/sqlite/SQLiteLocationDAO.java:79-86` y `:243-338`
  Si `hydrate()` lanza o el cursor de 262-275 viene vacío (`CursorIndexOutOfBounds` en L275), `endTransaction()` nunca se ejecuta: toda escritura posterior de posiciones falla hasta reiniciar el proceso.
  **Fix:** `try { ... setTransactionSuccessful(); } finally { endTransaction(); }` en ambos métodos.

- [x] **B8.** *(= A4)* `shutdown()` de 60 s en `onDestroy()` → ANR y filas atascadas en `POST_PENDING`.

- [x] **B9. `syncHttpMethod` se ignora en el envío por lote form-urlencoded**
  `HttpPostService.java:277` — `new HttpPostService(mUrl)` (constructor de 1 arg → `mMethod = "POST"`). El método resuelto en `SyncAdapter.java:147` se pierde.
  Backend con `syncHttpMethod: "PUT"` recibe POST → 405 → reintentos infinitos. Con `GET`, el bloque 265-290 hace POSTs igualmente, ignorando la rama `isBodyless()` de L310.
  **Fix:** `new HttpPostService(mUrl, mMethod)`.

### MEDIA

- [x] **B10.** `SyncAdapter.java:151` — `file.delete()` solo en la rama de éxito. Cada sync fallido deja un `locations*.json` huérfano. **Fix:** borrar en `finally`.
- [x] **B11.** `HttpPostService.java:247-261` + `SyncAdapter.java:183-189` — el lote se carga entero en memoria dos veces (3-4 copias simultáneas del JSON) → `OutOfMemoryError` en `:sync`. **Fix:** `JsonReader` en streaming; obtener el conteo del `BatchManager`.
- [x] **B12.** `HttpPostService.java:156` (`setFixedLengthStreamingMode` sin `setInstanceFollowRedirects`) + `PostLocationTask.java:229`. Un 301/302 lanza `HttpRetryException` ("Cannot retry streamed HTTP body") indefinidamente. Un 400 permanente se reintenta igual que un 503, sin backoff. **Fix:** resolver 3xx por la cabecera `Location`; diferenciar 4xx permanentes de 5xx transitorios.
- [x] **B13.** `HttpPostService.java:87-102` — con `httpMode: "batch"` y una sola posición se desenvuelve el array y se manda un objeto. Un servidor que espere `[{...}]` devuelve 400. **Fix:** el formato debe depender de `httpMode`, no de la longitud.
- [x] **B14.** Sin sincronización periódica: `LocationServiceImpl.java:315` solo hace `setSyncAutomatically`, sin `addPeriodicSync`. Con `syncThreshold` 100, un turno que acaba con 99 pendientes no las envía nunca. **Fix:** `ContentResolver.addPeriodicSync(...)` al arrancar.
- [x] **B15.** `ContentProviderLocationDAO.java:334` — `persistLocation(location, maxRows)` devuelve `0` como id. Latente hoy; al corregir B4 provocaría `deleteLocationById(0)` → borra la fila equivocada y reenvía la correcta.
- [x] **B16.** `SQLiteOpenHelper.java:162-164, 172-179` — `onUpgrade` cae en `default: onDowngrade(db,0,0)` que hace `DROP TABLE`. Cualquier `oldVersion` no contemplado destruye la tabla con las posiciones pendientes. `execAndLogSql` (181-188) traga las `SQLException`. **Fix:** no dropear en downgrade; propagar errores de migración.
- [x] **B17.** `HttpPostService.java:323` — `setDoInput(false)` en el envío de lote impide leer el cuerpo de error. Diagnóstico imposible. **Fix:** eliminarlo y loguear el `ErrorStream`.
- [x] **B18.** `ContentProviderLocationDAO.java:57, 119-131, 209-223, 226-247` — cursores sin `finally` ni chequeo de null. `ContentResolver.query` devuelve `null` si el provider muere. **Fix:** `try/finally` con chequeo, como ya hace `getLocations()`.

### BAJA

- [x] **B19.** `LocationContentProvider.java:166, 227, 261` — SQL concatenado con el segmento de URI. Mitigado por el `UriMatcher` (`#`) y `exported="false"`. **Fix:** `selectionArgs`.
- [x] **B20.** `ContentProviderLocationDAO.java:150, 188` — subconsulta cruda como `selection`. No explotable (`fromId` es `long`) pero frágil.
- [x] **B21.** `SQLiteLocationDAO.java:365-370` y `ContentProviderLocationDAO.java:373-378` — NPE en `deleteFirstUnpostedLocation()` si no hay filas.
- [x] **B22.** `HttpPostService.java:214` — `if ("null".equals(value)) continue;` descarta valores string legítimos `"null"`.
- [x] **B23.** `ContentProviderLocationDAO.java:285` — `Integer.valueOf(...)` sobre un `_ID` de 64 bits.

---

## 3. Android — config, modelo y puente Cordova

### CRÍTICA

- [x] **C1. `Config.merge()` no propaga `enableWatchdog`**
  `android/common/.../Config.java:1013-1147`
  El método comprueba 40+ campos pero nunca `hasEnableWatchdog()`, pese a que el getter existe (`Config.java:804`).
  `BackgroundGeolocationFacade.configure()` (`:493`) hace `Config.merge(getStoredConfig(), config)`: `configure({enableWatchdog:true})` se pierde, se persiste `false`, y `LocationServiceImpl:196/506` nunca arranca el watchdog. **El bug es permanente: no hay forma de que el valor llegue a la BD.**
  **Fix:**
  ```java
  if (config2.hasEnableWatchdog()) { merger.setEnableWatchdog(config2.getEnableWatchdog()); }
  ```

- [x] **C2. Eventos de sync emitidos con `LocalBroadcastManager` desde el proceso `:sync`**
  `sync/SyncAdapter.java:293-300` + `android/common/src/main/AndroidManifest.xml:23-25` + `plugin.xml:~140`
  `LocalBroadcastManager` es intra-proceso; el receptor (`BackgroundGeolocationFacade.java:178-205, 574`) vive en el proceso principal.
  `syncStart`, `syncProgress`, `syncSuccess`, `syncError`, `abort_requested` (HTTP 285) y `http_authorization` (HTTP 401) **nunca llegan al JS** cuando vienen de la cola de sync. Los mismos MSG_* sí funcionan emitidos por `LocationServiceImpl` (mismo proceso), lo que enmascara el fallo en pruebas foreground.
  **Fix:** (a) quitar `android:process=":sync"` del `SyncService`; o (b) broadcast explícito con `setPackage(...)` + receiver con `RECEIVER_NOT_EXPORTED`.

### ALTA

- [x] **C3. Cuatro acciones nunca invocan `callbackContext`: promesas JS colgadas**
  `android/CDVBackgroundGeolocation/.../BackgroundGeolocationPlugin.java:193-202, 231-238, 381-389`
  `ACTION_SWITCH_MODE`, `ACTION_SHOW_LOCATION_SETTINGS`, `ACTION_SHOW_APP_SETTINGS`, `ACTION_REGISTER_HEADLESS_TASK`.
  `await BackgroundGeolocation.showAppSettings()` / `openSettings()` / `showLocationSettings()` / `switchMode()` / `headlessTask()` cuelgan para siempre y dejan un `CallbackContext` retenido (fuga). En `switchMode`, un argumento inválido se reporta como evento `error` global en vez de rechazar su promesa.
  **Fix:** `callbackContext.success();` en cada rama de éxito; en `switchMode`, `callbackContext.sendPluginResult(ErrorPluginResult.from(...))`.

- [x] **C4. Ninguna validación de rango en el borde JS→nativo**
  `ConfigMapper.java:24-207` → `LocationServiceImpl.java:485`
  `configure({locationProvider: 3})` → `IllegalArgumentException("Provider not found")` y el servicio no arranca. `interval: -1` → `LocationRequest.Builder` lanza con `ACTIVITY_PROVIDER`. **La config queda grabada en SQLite, así que el crash se repite en cada arranque, incluido `startOnBoot`.**
  **Fix:** validar en `fromJSONObject`: `locationProvider ∈ {0,1,2}`; `interval/fastestInterval/activitiesInterval/heartbeatInterval ≥ 0`; `distanceFilter/stationaryRadius ≥ 0`; `syncThreshold/maxLocations ≥ 1`; `activityConfidenceThreshold ∈ [0,100]`; `httpMethod ∈ {POST,GET,PUT,PATCH}`; `httpMode/syncMode ∈ {batch,single}`; `mockLocationPolicy ∈ {allow,flag,drop}`; `wakeLockMode ∈ {none,posting,always}`. Lanzar `JSONException` con mensaje claro.

- [x] **C5. El campo `mocked` documentado no existe en ninguna parte del código**
  `www/BackgroundGeolocation.d.ts:503` vs `data/BackgroundLocation.java:931-955`
  El `.d.ts` promete `mocked: true` con `mockLocationPolicy:'flag'`. `toJSONObject()` solo emite `isFromMockProvider` y `mockLocationsEnabled`, y solo si `has*()`. Por defecto no van en el `postTemplate`, así que **`'flag'` es indistinguible de `'allow'`**. La interfaz `Location` del `.d.ts` (660-746) tampoco declara `mocked`.
  **Fix:** emitir `json.put("mocked", true)` cuando la política sea `'flag'` y el fix esté marcado; o corregir `.d.ts:503`, `README.md:296/692` y `docs/api.md:74`.

### MEDIA

- [x] **C6.** `Config.java:316` — `startForeground = true` por defecto, pero `.d.ts:214-216` y `docs/api.md:42` dicen `@default false`. **Fix:** mantener `true` y corregir la doc.
- [x] **C7.** `Config.java:200-226, 352-379` — el round-trip por `Parcel` destruye la nulabilidad de los campos boxed (`readFloat`/`readInt` → nunca null) y la identidad del sentinel `Config.NullString`, que se compara **por referencia** en `ConfigMapper.java:219` y `ConfigJsonMapper.java:202`. Tras cualquier paso por `Parcel`, todos los `has*()` devuelven `true` y se rompe el merge parcial. `writeToParcel` puede NPE por auto-unboxing con un `Config` parcial. **Fix:** `writeValue`/`readValue(null)` para todos los campos boxed; sustituir las comparaciones de referencia.
- [x] **C8.** `data/BackgroundLocation.java:258-286` — `writeToParcel` desreferencia `locationId`, `locationProvider` y `batchStartMillis`, que son `null` por defecto (`:23-25`). Hoy no explota porque el reparto es intra-proceso, pero al cruzar a `:sync` o bajo presión de memoria se cae **antes** de persistir el fix. **Fix:** `writeValue`/`readValue`. *(Los campos v4.3-v4.4 `events`/`battery`/`isCharging` sí están correctos en las cuatro rutas.)*
- [x] **C9.** `Config.java:1064-1072, 1143-1144` — opciones no reseteables vía `configure()`: los tres `notificationIcon*` (usan `has*()` que exige no-vacío), `maxAcceptedAccuracy` y `drivingEvents` (no pueden volver a `null`). **Fix:** usar `!= null` para los iconos; soportar `JSONObject.NULL` como reset.
- [x] **C10.** `plugin.xml:34-37` / `android/dependencies.gradle` — `androidx.localbroadcastmanager` se importa en 3 clases pero no se declara; llega solo transitivamente. **Fix:** declararla (o migrar a `BroadcastReceiver` con `setPackage()`, que resuelve también C2).
- [x] **C11.** `plugin.xml:35-36` — `androidx.core:core:1.1.0` y `androidx.appcompat:appcompat:1.7.0`→`1.1.0` pinneadas de 2019 con compileSdk 36. **Fix:** subir o eliminar y dejar que la plataforma las fije.
- [x] **C12.** `android/common/VERSIONS.gradle:7` — `DEFAULT_PLAY_SERVICES_VERSION = "17+"` (rango flotante) vs `plugin.xml:26` que fija `21.0.1`. `LocationRequest.Builder` requiere ≥21.0.0. Y `:145` usa `com.intentfilter:android-permissions:0.1.7` vs `dependencies.gradle:6` con `io.github.nishkarsh:android-permissions:2.1.8`. **Fix:** alinear ambas.
- [x] **C13.** `android/common/VERSIONS.gradle:3-6` — `DEFAULT_COMPILE_SDK_VERSION = 28` pero el código usa `PackageManager.ComponentInfoFlags.of(0)` (API 33, `LocationServiceImpl.java:892`) y `Context.RECEIVER_NOT_EXPORTED` (API 33, `:1411`). **Fix:** subir a 34+.
- [x] **C14.** Build Gradle standalone muerto: `android/common/build.gradle:34,37,49` usa `jcenter()` (apagado, 403) y AGP 3.4.1; `android/CDVBackgroundGeolocation/build.gradle:28-33` usa `testCompile`/`compile` (eliminados en Gradle 7). Ver sección 7.

### BAJA

- [x] **C15.** `package.json:63-140` — `engines.cordovaDependencies` se detiene en `4.5.1` mientras la versión es `4.5.5`. El `CHANGELOG.md` además no documenta `4.5.3`. *(Versión sí consistente en `package.json:3`, `plugin.xml:5` y `BackgroundGeolocationPlugin.java:99`.)*
- [x] **C16.** `angular/dist/.npmignore:2` — `**/package.json` elimina `angular/dist/package.json` del tarball. Verificado con `npm pack --dry-run`. Bajo impacto (el `exports` de la raíz apunta directo a los ficheros), pero rompe la resolución clásica.
- [x] **C17.** `BackgroundGeolocationPlugin.java:481-503, 578, 717-723` — `cordova.getActivity()` sin chequeo de null. Además `ACTION_IS_IGNORING_BATTERY_OPT`, `ACTION_OPEN_BATTERY_SETTINGS`, `ACTION_OPEN_AUTOSTART_SETTINGS`, `ACTION_TRIGGER_SOS` y `ACTION_GET_STATIONARY` corren síncronos en el hilo del bridge (el último toca la BD), a diferencia del resto. **Fix:** guardar el `Context` de aplicación en `pluginInitialize()`; mover `ACTION_GET_STATIONARY` a `runOnWebViewThread`.

### Cobertura de mapeo de config (verificada)

Todas las claves de `ConfigureOptions` están mapeadas y persistidas en Android. Las 4 no mapeadas son iOS-only y sí están implementadas en `MAURConfig.m`: `activityType`, `pauseLocationUpdates`, `saveBatteryOnBackground`, `showsBackgroundLocationIndicator`.
**La única fuga real es `enableWatchdog` en `merge()` (C1).**

---

## 4. iOS

### CRÍTICA

- [x] **D1. POST en background sin `beginBackgroundTaskWithExpirationHandler`**
  `MAURPostLocationTask.m:81, 292`
  Todo el trabajo (persistencia + HTTP) va a una cola global background sin aserción de tiempo de fondo y bloquea hasta 120 s con un semáforo. Los únicos `beginBackgroundTask` del proyecto están en `MAURBackgroundTaskManager.m` (expuesto a JS) y en `MAURBackgroundSync.start`, que nunca se invoca (ver D10).
  iOS despierta ~10 s por callback. Si el POST tarda más, el proceso se suspende con el hilo bloqueado; la petición muere y la posición queda solo en SQLite. Con `url` y sin `syncUrl`, no se recupera nunca.
  **Fix:** envolver cada `add:` en un background task con `endBackgroundTask` en **todas** las salidas; o mover el POST real-time a `NSURLSession` de configuración background.

- [x] **D2. Nunca se pide upgrade `WhenInUse` → `Always`**
  `MAURDistanceFilterLocationProvider.m:143-148`, idéntico en `MAURLocationManager.m:81-86`
  ```objc
  if (authStatus == kCLAuthorizationStatusNotDetermined) { [locationManager requestAlwaysAuthorization]; }
  ```
  `kCLAuthorizationStatusAuthorizedWhenInUse` no se trata. Desde iOS 13 el flujo normal es que el usuario conceda "Al usar la app": el plugin arranca, `isRunning=true`, y al pasar a background iOS corta las actualizaciones. Sin error, sin evento. **Es el fallo más común de tracking de flota en iOS.**
  **Fix:** rama para `AuthorizedWhenInUse` → `requestAlwaysAuthorization`; si tras el callback sigue igual, emitir `onError`/evento `authorization`.

- [x] **D3. Precisión reducida iOS 14+ nunca detectada ni solicitada**
  Todo `ios/` — `accuracyAuthorization` solo se loguea (`MAURDistanceFilterLocationProvider.m:392`) y se reporta en `getDiagnostics` (`CDVBackgroundGeolocation.m:415`). Nunca se llama `requestTemporaryFullAccuracyAuthorizationWithPurposeKey:`. No existe `NSLocationTemporaryUsageDescriptionDictionary` en `plugin.xml:235-270`.
  Con "Ubicación precisa" desactivada iOS entrega fixes de ~1-3 km: `desiredAccuracy`, `distanceFilter` y `stationaryRadius` dejan de tener sentido y el servidor recibe trayectos inventados.
  **Fix:** detectar `CLAccuracyAuthorizationReducedAccuracy` en `handleAuthorizationStatusChange:` y en `onStart`, pedir precisión temporal, añadir la clave al `plugin.xml`, emitir evento si el usuario rechaza.

- [x] **D4. `restoreFailedSyncLocations` revierte TODAS las filas `SyncPending`**
  `MAURSQLiteLocationDAO.m:394-412`, invocado desde `MAURBackgroundSync.m:226, 238`
  ```objc
  @"UPDATE ... SET status = ? WHERE status = ?", @(MAURLocationPostPending), @(MAURLocationSyncPending)
  ```
  Sin discriminar qué upload falló, y `sync:` no tiene guarda de reentrada (`MAURBackgroundSync.m:72`). Si `forceSync()` coincide con un sync automático, el fallo de uno devuelve a `PostPending` las filas del otro → se reenvían enteras. Igual si un upload dura >15 min (`restoreStaleSyncLocationsOlderThan`, `MAURPostLocationTask.m:345`).
  **Fix:** columna `sync_batch_id` con el UUID del upload; restore/delete filtrando por ese id. Añadir flag de "sync en curso".

### ALTA

- [x] **D5.** `MAURBackgroundGeolocationFacade.m:835-839` — `getValidLocationsAndDelete` no borra: llama `getLocationsForSync` (marca `PostPending → SyncPending`). `getPendingSyncCount` pasa a 0 y 15 min después las filas resucitan → duplicados. Divergencia con Android. **Fix:** `SELECT` + `UPDATE status = Deleted` en la misma transacción.
- [x] **D6.** `MAURLocation.m:88-110` — `fromCLLocation:` nunca asigna `provider` ni `locationProvider`. El template por defecto envía `null` en ambos (`MAURConfig.m:541-542`), el evento `providerChange` nunca se emite (`MAURBackgroundGeolocationFacade.m:536-543`) y el `.d.ts` los declara **no opcionales**. **Fix:** `instance.provider = @"gps"` y que cada provider asigne su `locationProvider`.
- [x] **D7.** `MAURLocation.m:355-359` — `hasAccuracy` compara un puntero: `if (accuracy == nil || accuracy < 0)`. CoreLocation devuelve `horizontalAccuracy = -1` para coordenadas inválidas; ese fix pasa el guard de `MAURDistanceFilterLocationProvider.m:238` y en `:264` `-1 <= 100` lo considera "el más preciso", cortando la adquisición. `maxAcceptedAccuracy` tampoco lo descarta. **Fix:** `[accuracy doubleValue] < 0` y guard `< 0` en el chequeo de `maxAcceptedAccuracy`.
- [x] **D8.** `MAURSQLiteConfigurationDAO.m:255-290` — `maxAcceptedAccuracy` y `activityConfidenceThreshold` no se serializan ni se restauran, y `retrieveConfiguration` usa `[[MAURConfig alloc] init]` en vez de `initWithDefaults`. En el arranque por `UIApplicationLaunchOptionsLocationKey` (`CDVBackgroundGeolocation.m:890-896`) el filtro de precisión está desactivado. **Fix:** añadir ambas claves y usar `initWithDefaults`.
- [x] **D9.** `MAURBackgroundSync.m:109` vs `:189` — `tasks` (`NSMutableArray`) mutado desde la cola background (`addObject:`) y desde `mainQueue` (`removeObject:`) sin sincronizar → corrupción / `EXC_BAD_ACCESS`. **Fix:** `@synchronized` o cola propia.
- [x] **D10.** `MAURBackgroundSync.m:43-58` — `start` nunca se llama (`MAURPostLocationTask.start` solo hace `[reach startNotifier]`). Tras un kill/relaunch los uploads en vuelo quedan fuera de `tasks`; sus filas siguen `SyncPending` hasta el rescate de 15 min → reenvío duplicado. Y si llega `didCompleteWithError`, `uploadCutoff` es nil y se usa `now`, borrando filas no subidas. **Fix:** llamar `[uploader start]` desde `MAURPostLocationTask.start` + `handleEventsForBackgroundURLSession`.
- [x] **D11.** `MAURBackgroundGeolocationFacade.m:880-916` — `dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER)` con `timeout = INT_MAX` (`CDVBackgroundGeolocation.m:624`, cuando `www/BackgroundGeolocation.js:192` pasa `undefined`). `getCurrentLocation()` sin opciones y permiso `NotDetermined` bloquea un hilo del pool de Cordova ~24 días. **Fix:** deadline acotado (tope 60 s) y default de 30000 ms.
- [x] **D12.** `MAURSensorFusionDetector.m:123-135` — `dispatch_sync` al main queue a 50 Hz para leer `applicationState`. Ver F8.
- [x] **D13.** `MAURPostLocationTask.m:50-61` — ciclo de retención en los bloques de Reachability (capturan `self` por el ivar `hasConnectivity`) **y** `[_reach stopNotifier]` dentro del `reachableBlock`: tras la primera transición el monitor queda apagado y `hasConnectivity` se queda en `YES` permanentemente. Al perder cobertura, cada `add:` bloquea un hilo hasta el timeout → inanición de GCD (límite 64). **Fix:** `__weak self`, quitar el `stopNotifier`, hacer `hasConnectivity` atómico.
- [x] **D14.** `MAURLocationManager.h:18-19` declara `onLocationPause:`/`onLocationResume:` pero `MAURRawLocationProvider.m:108,113` y `MAURActivityLocationProvider.m:315,320` implementan `onPause:`/`onResume:`. El `respondsToSelector:` los descarta en silencio. Con `pauseLocationUpdates: true` en RAW o ACTIVITY, la app no recibe ningún evento. `onRegionExit:` no está implementado en ninguno. **Fix:** renombrar.
- [x] **D15.** `MAURBackgroundGeolocationFacade.m:334-357` — el heartbeat se programa en `dispatch_async(main)` pero `cancelHeartbeat` corre en el hilo del llamante (`stop:` viene de `runInBackground`). `invalidate` desde otro hilo puede no invalidar; el timer retiene `self` fuertemente. Además dos `scheduleHeartbeat` seguidos dejan un timer huérfano. **Fix:** programar y cancelar siempre en main queue; `__weak self`.
- [x] **D16.** `MAURSQLiteLocationDAO.m:200-287` — con `maxRows <= 0` ninguna rama inserta y se cae al `UPDATE ... WHERE id = 0` (0 filas), pero `executeUpdate` devuelve `YES`. Explica el `// TODO: investigate location id always 0` de `MAURPostLocationTask.m:157`. Agravado por `config` declarada `weak` (ver D27). **Fix:** guard `if (maxRows <= 0) return [self persistLocation:...]`; verificar `[database changes] > 0`.
- [x] **D17.** `CDVBackgroundGeolocation.m:402-449, 466` — `getDiagnostics` crea un `CLLocationManager` y lee `UIApplication.backgroundRefreshStatus` desde `runInBackground`. Main Thread Checker lo marca; `authorizationStatus` sobre una instancia creada en background no es fiable. **Fix:** `dispatch_sync(main)` o reutilizar `[MAURLocationManager sharedInstance]`.
- [x] **D18.** `CDVBackgroundGeolocation.m:358-365, 664-668` — `switchMode` y `endTask` no llaman `sendPluginResult:`. Promesas JS colgadas. Divergencia con Android (donde también fallan, ver C3). **Fix:** añadir el `CDVPluginResult`.

### MEDIA

- [x] **D19.** `MAURBackgroundSync.m:36-38` — `backgroundSessionConfiguration:` deprecado desde iOS 8; identificador fijo creado en `init`. Cordova recrea los plugins al recargar la WebView → crash de Foundation ("A background URLSession with identifier … already exists"). No hay `dealloc` ni `invalidateAndCancel`. **Fix:** `backgroundSessionConfigurationWithIdentifier:` + singleton + `finishTasksAndInvalidate`.
- [x] **D20.** `showsBackgroundLocationIndicator` solo se aplica en `DISTANCE_FILTER` (`MAURDistanceFilterLocationProvider.m:91-95`); RAW y ACTIVITY lo ignoran. El `.d.ts:483` no restringe por provider.
- [x] **D21.** `+[CLLocationManager authorizationStatus]` (deprecado en iOS 14) en `MAURBackgroundGeolocationFacade.m:753`, `MAURDistanceFilterLocationProvider.m:113`, `MAURLocationManager.m:51`. `checkStatus` y `getDiagnostics` pueden reportar valores distintos.
- [x] **D22.** `MAURDistanceFilterLocationProvider.m:513-524` — con `stationaryRegion == nil`, `containsCoordinate` sobre nil devuelve `NO` → el método devuelve `YES` siempre. Cada fix en background dispara `switchMode` subiendo el GPS a `BestForNavigation` y reiniciando la adquisición: ciclo continuo de drenaje. **Fix:** `if (stationaryRegion == nil) return NO;`.
- [x] **D23.** `CDVBackgroundGeolocation.m:38-65` — 19 `addObserver:` en `pluginInitialize` y ningún `dealloc`/`dispose` con `removeObserver:`. Al recargar la WebView el facade antiguo sigue vivo (retenido por el heartbeat, D15) y emite a un `callbackId` obsoleto → eventos duplicados. **Fix:** implementar `- (void)dispose`.
- [x] **D24.** `mockLocationPolicy: 'flag'` no produce ningún campo por defecto en iOS: `getDefaultTemplate` no incluye `@simulated` (`MAURConfig.m:530-551`). iOS usa la clave `simulated`, Android `isFromMockProvider`. Ver C5.
- [x] **D25.** `MAURAbstractLocationProvider.m:30, 36-41` y `MAURBackgroundGeolocationFacade.m:138, 988-993` — `UILocalNotification`/`scheduleLocalNotification:` deprecados desde iOS 10. Solo afecta a `debug: true`.
- [x] **D26.** `syncMode` se parsea (`MAURConfig.m:106-108`) y se persiste, pero no se lee en ningún sitio: el uploader siempre construye un array JSON. El `.d.ts:430` lo documenta como "Android, iOS". **Fix:** implementarlo o corregir la doc.
- [x] **D27.** `MAURPostLocationTask.h:119` — `@property (nonatomic, weak) MAURConfig *config`. Los bloques de `add:` leen `self.config.url`/`maxLocations`/`_template` mucho después. Si `configure:` reemplaza `_config`, la referencia débil puede quedar nil entre lecturas → comportamiento no determinista. **Fix:** `strong`, o pasar una copia inmutable al bloque.

### BAJA

- [x] **D28.** `CDVBackgroundGeolocation.m:310-326, 335-351` — `start:`/`stop:` reutilizan la variable `error`; `start()` devuelve OK aunque `facade start:` haya fallado.
- [x] **D29.** `MAURBackgroundTaskManager.m:59-67` — escritura del diccionario fuera de `@synchronized`, mientras `endTaskWithKey:` (`:89`) sí lo protege.
- [x] **D30.** `MAURConfig.m:131-133, 238-240` — `maxAcceptedAccuracy` no se puede resetear pasando `null`, contra lo que dice `.d.ts:580`.

### Tabla de paridad Android / iOS

| Opción | Android | iOS | Doc dice | Nota |
|---|---|---|---|---|
| `locationProvider`, `desiredAccuracy`, `stationaryRadius`, `distanceFilter`, `stopOnTerminate` | Sí | Sí | all | — |
| `debug` | Sí | Sí | all | iOS usa API deprecada (D25) |
| `startOnBoot`, `interval`, `fastestInterval`, `enableWatchdog`, `notificationsEnabled`, `startForeground`, `notification*`, `showTime`, `showDistance`, `notificationSync*`, `wakeLockMode`, `stationaryTimeout/PollInterval/PollFast` | Sí | No | Android | Correcto |
| `activitiesInterval` | Sí | **Parsea, no usa** | Android | Se persiste sin efecto |
| `activityType` | n/a | Sí | iOS | — |
| `pauseLocationUpdates` | n/a | Sí | iOS | **Eventos pause/resume rotos en RAW y ACTIVITY (D14)** |
| `saveBatteryOnBackground` | n/a | Solo DISTANCE_FILTER | iOS | ACTIVITY y RAW ignoran `switchMode` |
| `url`, `syncUrl`, `syncThreshold`, `sync`, `httpHeaders`/`headers`, `httpMethod`, `syncHttpMethod`, `httpMode`, `queryParams` | Sí | Sí | all | — |
| `syncMode` | Sí | **Parsea y persiste, no aplica** | Android, iOS | **Divergencia (D26)** |
| `maxLocations` | Sí | Sí | all | Pérdida silenciosa si `<= 0` (D16); no aplicado en Android (B4) |
| `postTemplate`/`bodyTemplate` | Sí | Sí | all | Faltan `@provider` reales (D6) y `mocked` (C5/D24) |
| `showsBackgroundLocationIndicator` | n/a | **Solo DISTANCE_FILTER** | iOS | **Divergencia (D20)** |
| `heartbeatInterval` | Sí | Sí | Android, iOS | Problemas de hilo (D15) |
| `mockLocationPolicy` | `drop` sí, `flag` sin campo | ídem | Android, iOS | **Divergencia (C5/D24)** |
| `includeBattery` | Sí | Sí | Android, iOS | iOS: `dispatch_sync` a main por fix |
| `activityConfidenceThreshold` | Sí | Sí, **no se persiste** | Android, iOS | **Se pierde al reinicio (D8)** |
| `maxAcceptedAccuracy` | Sí | Sí, **no se persiste** | Android, iOS | **D8 + roto con `accuracy = -1` (D7)** |
| `drivingEvents.*` | Sí | Sí | Android, iOS | `providerChange` nunca se emite en iOS (D6) |
| `drivingEvents.sensorFusion` | Sí | Sí | Android, iOS | 50 Hz en main (A14 / D12) |

**Permisos runtime en iOS:** `requestBackgroundLocationPermission`, `requestActivityRecognitionPermission` y `requestNotificationPermission` son no-ops que devuelven `{granted:true, notRequired:true}` (`CDVBackgroundGeolocation.m:141-158`). Dados D2 y D3, `requestBackgroundLocationPermission` **debería** hacer el upgrade real en lugar de mentir.

---

## 5. Capa JS / www / wrapper Angular

### CRÍTICA

- [x] **E1. Fuga de listeners: `unsubscribe()` desregistra del canal equivocado**
  `angular/background-geolocation.service.ts:319-326` (+ `angular/dist/esm2022/background-geolocation.service.mjs:182-188`, `www/radio.js:45, 149-151`, `www/BackgroundGeolocation.js:20, 322-324`)
  `on(event)` sin callback devuelve el **singleton** `radio.$`, cuyo `channelName` es global y muta en cada `radio(x)` y en cada `broadcast`.
  ```ts
  const channel = plugin.on(eventName) as {...};
  return { subscribe(cb) { channel.subscribe(cb); return { unsubscribe() { channel.unsubscribe(cb); } }; } };
  ```
  Reproducción ejecutada sobre el código real:
  ```
  counts after 1st init: { loc: 1, err: 1, stop: 1 }
  counts after 2nd init: { loc: 1, err: 2, stop: 2 }
  counts after 3rd init: { loc: 2, err: 3, stop: 1 }
  ```
  Es exactamente el patrón de `GPSMasterClient/src/app/tab1/tab1.page.ts:1709-1766`. Cada ciclo stop→start acumula listeners: un solo evento `error` nativo dispara N reinicios del servicio.
  **Fix (`www/BackgroundGeolocation.js:322-324`):**
  ```js
  if (!callbackFn) {
    return {
      subscribe: function (cb) { radio(event).subscribe(cb); return { unsubscribe: function () { radio(event).unsubscribe(cb); } }; },
      unsubscribe: function (cb) { radio(event).unsubscribe(cb); }
    };
  }
  ```
  **Workaround seguro mientras tanto:** usar `removeAllListeners(event)` (verificado correcto) o la rama con callback `on(event, cb).remove()` (también correcta).

- [x] **E2. `finish()` y `changePace()` no existen en el plugin**
  `angular/background-geolocation.service.ts:55-62`
  `rg "finish|changePace" www/BackgroundGeolocation.js` → sin resultados. Tampoco hay `ACTION_FINISH`/`ACTION_CHANGE_PACE` en el puente Android.
  La app cliente lo llama en iOS (`tab1.page.ts:1731`): lanza `TypeError`, capturado por el `.catch()` de la línea 1733 y enmascarado como "Error starting background task". El background task de iOS **nunca se finaliza**.
  **Fix:** eliminarlos del servicio y del `dist`, o implementarlos. Actualizar el cliente.

- [x] **E3-E5.** *(= C3)* `switchMode()`, `showAppSettings()`/`openSettings()`/`showLocationSettings()` y `headlessTask()` nunca resuelven. Declarados `Promise<void>` en `.d.ts:1113,1120,1127` y `docs/angular.md:100-102`; `headlessTask` está además mal tipado como `void` en `.d.ts:1408-1410`.

### ALTA

- [x] **E6.** `www/BackgroundGeolocation.js:35-42` — `execWithPromise` devuelve `undefined` si se pasa **solo uno** de los dos callbacks, pero el `.d.ts` declara `Promise<T>` siempre. `bg.getLocations(cb).then(...)` → `TypeError`. Contradice `README.md:513`.
  **Fix:**
  ```js
  var p = new Promise(function (res, rej) { exec(res, rej, 'BackgroundGeolocation', method, data || []); });
  if (suceess || failure) p.then(suceess || function(){}, failure || function(){});
  return p;
  ```
  *(Nota: la rama Promise actual pasa `data` sin `|| []`.)*
- [x] **E7.** `www/BackgroundGeolocation.d.ts:9, 1772-1831` — 10 `export enum` en un fichero de declaración: compilan pero **no emiten runtime**. `www/BackgroundGeolocation.js:356` solo exporta el objeto plugin. `import { BackgroundGeolocationEvents } from '...'` type-checkea y en runtime es `undefined`. Solo funciona desde `/angular`. **Fix:** exportarlos como objetos reales desde el JS, o cambiarlos a `export type` / `export declare const`.
- [x] **E8.** `.d.ts:56-61, 874-876` vs `www/BackgroundGeolocation.js:320-331` — `on()` con callback devuelve `{ remove }` pero el `.d.ts` declara `Subscribable` (`{ subscribe }`). `EventSubscription` está declarado y nunca referenciado. El patrón de `docs/events.md:64` y `README.md:517` (`.remove()`) **no compila**; el que sí compila (`.subscribe()`) lanza en runtime. Los 28 overloads (1417-1738) están mal tipados. **Fix:** sobrecargas separadas.
- [x] **E9.** `www/radio.js:149-155` — `unsubscribe()` sin argumentos es un no-op (`l === 0`, el bucle no se ejecuta), pero el `.d.ts:56-60` declara exactamente esa firma. El consumidor cree haber desregistrado. **Fix:** ver E1.
- [x] **E10.** `www/radio.js:121-135, 149-173` — `subscribe` hace `c.push(p)` sin dedup; `unsubscribe` elimina **todas** las coincidencias. Dos suscripciones con la misma referencia → N invocaciones por fix; un `remove()` mata las dos. **Fix:** deduplicar en `subscribe`; `break` tras el primer `splice`.
- [x] **E11.** `www/cordova-channel-stub.js:6-27` — `module.exports = getChannel()` se evalúa **en import time** y cachea la decisión; asimétrico con `cordova-exec-stub.js`, que comprueba en cada llamada. Si `window.cordova` aún no existe, queda fijado el stub incluso en dispositivo: `deviceready` se emula con `load`, `exec` cae al stub, llama `fail(...)` y **el listener nativo nunca se registra, sin reintento**. Cero eventos durante toda la vida de la app. El `catch (e) {}` vacío oculta el fallo de `require`. `docs/angular.md:34` afirma lo contrario. **Fix:** diferir la resolución; loguear el catch.
- [x] **E12.** `angular/background-geolocation.service.ts` — sin `NgZone` en ninguna línea. Los callbacks de Cordova se invocan desde `evaluateJavascript` nativo, fuera de la zona Angular. Explica coordenadas congeladas en pantalla mientras el backend sí recibe posiciones (`tab1.page.ts:1714-1723` asigna sin `detectChanges`). **Fix:** inyectar `NgZone` y envolver `this.zone.run(() => cb(value))` en ambas ramas de `on()`.

### MEDIA / BAJA

- [x] **E13.** `www/cordova-exec-stub.js:10-12` — rechaza con `{ message: ... }` sin `code` y sin ser `Error`. `err.code` es `undefined`; zone.js registra `Uncaught (in promise): [object Object]` sin stack. En web, el registro global de `deviceready` emite además un evento `error` espurio al arrancar. **Fix:** `fail(Object.assign(new Error('...'), { code: 0 }))`.
- [x] **E14.** `angular/background-geolocation.service.ts:310-317` — `on(event, cb).subscribe(cb2)` registra un **segundo** listener. Combinado con E10, un `unsubscribe()` externo elimina ambos.
- [x] **E15.** `docs/angular.md:94-147` — la tabla "Service API" omite 7 métodos que el servicio expone (`getDiagnostics`, `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`, `openBatterySettings`, `openAutoStartSettings`, `getManufacturerHelp`, `triggerSOS`) y la de eventos omite 17 (todo v3.5-v4.2).
- [x] **E16.** `.d.ts:51` — `type AccuracyLevel = 0 | 100 | 1000 | 10000 | number;` colapsa a `number`. **Fix:** quitar `| number`.
- [x] **E17.** `.d.ts:953` — `fail?: (error: LocationError) => void | null,` — el `| null` aplica al retorno, no al parámetro. **Fix:** `fail?: ((error: LocationError) => void) | null,`.
- [x] **E18.** `www/BackgroundGeolocation.js:199` — defaults posicionales inútiles en `getLogEntries`; el `.d.ts:1330-1336` los declara obligatorios.
- [x] **E19.** `www/BackgroundGeolocation.js:322-324` — devolver `radio.$` expone `reset()`: un consumidor puede borrar **todos** los canales de la app.

**Verificado sin hallazgo:** `removeAllListeners(event?)` funciona correctamente; `on(event, cb).remove()` funciona; los alias `headers`/`bodyTemplate` (v3.3) están implementados en ambas plataformas; todas las opciones del CHANGELOG v3.5-v4.5.4 están en el `.d.ts` y mapeadas; los 28 eventos coinciden entre `www`, `.d.ts` y `angular`; `angular/dist` está alineado con `angular/*.ts`; la versión 4.5.5 es consistente en los tres sitios; `checkStatus()` en web rechaza correctamente (no miente).

---

## 6. Driving events y sensor fusion

### CRÍTICA

- [x] **F1. Entrega en lote → Δt ≈ 1 ms → aceleraciones absurdas**
  `driving/DrivingEventsDetector.java:196-214` (iOS: `MAURBackgroundGeolocationFacade.m:636`)
  ```java
  long dtMs = now - prevSpeedAt;      // now = System.currentTimeMillis(), no loc.getTime()
  if (dtMs > 0 && dtMs <= 5_000) { double accel = (speed - prevSpeedMps) / (dtMs/1000.0); }
  ```
  Los providers entregan lotes en bucle cerrado (`ActivityRecognitionLocationProvider.java:63-67`, `DistanceFilterLocationProvider.java:114`). Al salir de Doze, 5 fixes llegan en el mismo milisegundo: Δv de 0,5 m/s da `accel = 500 m/s²` → `hardBrake` y `rapidAcceleration` falsos **garantizados en cada despertar**. Igual en `sharpTurn` (línea 237).
  **Fix:** usar `loc.getTime()` y exigir `dtMs >= 500`.

- [x] **F2. Fix sin velocidad se trata como 0**
  `DrivingEventsDetector.java:120` — `double speed = loc.hasSpeed() ? loc.getSpeed() : 0.0;` (iOS: `MAURBackgroundGeolocationFacade.m:532-533`, clamp de `-1` a 0)
  Con el vehículo a 90 km/h y un fix sin speed (NETWORK, PASSIVE, primer fix tras readquisición):
  - `accel = -25 m/s²` → **`hardBrake` falso**
  - `dropKmh = 90`, `speed < 1.5` ✓, `prevSpeedMps*3.6 >= 25` ✓ → **`possibleCrash` falso**, que en flota dispara alertas de accidente y llamadas al conductor
  - `nowMoving = false` → arranca el temporizador de `stopped` en autopista
  **Fix:** saltar los cálculos derivados cuando `!loc.hasSpeed()` (sin actualizar `prevSpeedMps`/`prevSpeedAt`), o derivar de haversine/Δt. En iOS tratar `speed < 0` y `course < 0` como ausentes.

- [x] **F3. Desactivar `drivingEvents` en caliente deja el sensor fusion registrado**
  `service/LocationServiceImpl.java:544-548`
  ```java
  if (opts == null || !opts.enabled) {
      if (mDrivingDetector != null) mDrivingDetector.reset();
      mDrivingDetector = null;
      return;                       // nunca llega a configureSensorFusion()
  }
  ```
  `configureSensorFusion()` solo se invoca en la línea 669 (rama "enabled"). Tras `configure({drivingEvents:{enabled:false}})`, `mSensorFusion` sigue vivo con acelerómetro y giroscopio a `SENSOR_DELAY_GAME` y emitiendo `possibleCrash`/`phoneUsageWhileDriving` de una función ya desactivada.
  **Fix:** llamar `configureSensorFusion()` también en la rama de desactivación.

### ALTA

- [x] **F4.** `DrivingEventsDetector.java:166-179` + `LocationServiceImpl.java:205` — el cierre de viaje solo se evalúa **dentro de `onLocation`**. Si dejan de llegar fixes (aparcado en garaje, Doze profundo), `tripEnd` nunca se emite: el viaje queda abierto para siempre en el backend, `mDrivingTripActive` sigue `true` y el watchdog reinicia el provider cada 60 s toda la noche — justo lo que la optimización de v4.5.1 pretendía evitar. **Fix:** `tick(now)` periódico que evalúe el vencimiento de `stoppedDurationMs` sin nueva localización.
- [x] **F5.** `LocationServiceImpl.java:558, 731` — cualquier `configure()` reconstruye el detector desde cero (`new DrivingEventsDetector(...)`) con `tripActive=false`, `tripDistanceMeters=0`. Cambiar `speedLimit` a mitad de viaje pierde distancia y duración, el `tripEnd` nunca se emite y `mDrivingTripActive` queda pegado en `true` (solo se limpia en `onTripEnd` o `stop()`). **Fix:** `setConfig` que preserve el estado; o forzar `tripEnd` sintético antes de recrear.
- [x] **F6.** `DrivingEventsDetector.java:119` — toda la lógica temporal usa `System.currentTimeMillis()` (reloj de pared de llegada), no `loc.getTime()` ni `SystemClock.elapsedRealtime()`. Un salto NTP hacia atrás puede dejar `lastHardBrakeAt` en el futuro y **bloquear el cooldown para siempre**. Con el dispositivo dormido, `belowMovingSinceMs` y `aboveTripSpeedSince` miden tiempo de reloj, no de conducción. **Fix:** cooldowns con reloj monótono, Δv/Δbearing con `loc.getTime()`; inyectar el reloj como parámetro (hace la clase testeable).
- [x] **F7.** `sensor/SensorFusionDetector.java:83, 98, 101` + `LocationServiceImpl.java:733` — `SENSOR_DELAY_GAME` × 2 sensores ≈ 100 callbacks/s **en el UI thread de la app host**, registrados durante toda la vida del servicio (no solo `tripActive`). **Fix:** `HandlerThread` dedicado; registrar en `setTripActive(true)`, desregistrar en `false`; `SENSOR_DELAY_NORMAL/UI` para el giroscopio.
- [x] **F8.** `ios/.../MAURSensorFusionDetector.m:122-133` — `dispatch_sync(main)` desde `processMotion:` a 50 Hz. Contención severa de la UI y riesgo de deadlock. Además `isScreenOnApprox` solo detecta "app en foreground", no "pantalla encendida". **Fix:** cachear con observadores de `UIApplicationDidBecomeActive/WillResignActive`.
- [x] **F9.** `SensorFusionDetector.java:73-74, 174-175` — `JITTER_ACCEL_MPS2 = 0.5` es inferior a la aceleración normal de un coche (baches, cambios de marcha, giros), y la condición es `OR` con el giroscopio. Con la app en foreground durante un viaje, `phoneUsageWhileDriving` se emite **cada 60 s (el cooldown) sin que nadie toque el teléfono**. Invalida la métrica y genera sanciones injustas. **Fix:** subir el umbral, exigir correlación de ambos sensores con filtro paso-alto, o usar la señal real de pantalla encendida.
- [x] **F10.** `SensorFusionDetector.java:68-69, 149-153, 171-186` — `lastCrashAt`, `lastPhoneUsageAt` y `jitterAboveSince` se **escriben fuera** del `synchronized`, mientras `stop()` (:111) y `setTripActive()` (:117) sí lo usan. `jitterAboveSince = 0L` de `setTripActive(false)` puede no ser visible → `phoneUsageWhileDriving` tras terminar el viaje; el cooldown de crash puede saltarse y emitir dos alertas. **Fix:** `volatile` o un único `synchronized`.

### MEDIA / BAJA

- [x] **F11.** `DrivingEventsDetector.java:217` — `possibleCrash` por GPS exige que dos fixes **consecutivos** estén separados ≤ `crashWindowMs` (2 s). Con fixes cada 10-60 s (configuración típica de flota) la detección **nunca se activa**, pese a documentarse como operativa (`.d.ts:622-625`, `README.md:311-312`). **Fix:** ventana deslizante real con buffer circular `(tiempo, velocidad)`.
- [x] **F12.** `DrivingEventsDetector.java:134-136, 173` — la distancia acumula ruido GPS (parado en semáforo con `accuracy` 50 m suma decenas de metros por fix) y saltos post-túnel/Doze en línea recta; la duración incluye los `stoppedDurationMs` (todos los viajes reportan 1 min de más). **Fix:** filtrar por `accuracy` y por Δt máximo; usar `belowMovingSinceMs - tripStartedAt` para la duración.
- [x] **F13.** `SensorFusionDetector.java:149-153` — el `possibleCrash` por sensor dispara con 3 g sin corroboración de GPS, contradiciendo su propio javadoc (22-25). Un teléfono que cae del soporte al asiento supera 3 g. **Fix:** exigir caída de velocidad GPS en los ~3 s siguientes; publicar `confidence`.
- [x] **F14.** `ios/.../MAURLocation.m:96` — `instance.heading = [NSNumber numberWithDouble:location.course]` envuelve siempre, así que el guard `loc.heading != nil` de `MAURBackgroundGeolocationFacade.m:674` nunca filtra el sentinel `-1`. Rumbos basura en `sharpTurn`. Divergencia con Android, que sí usa `hasBearing()`.
- [x] **F15.** `docs/driving-events.md:1-60` — se declara **"planificado, no implementado"** y documenta una API distinta: `hardBrakeThreshold`, `sharpTurnGyroZ`, `minTripSpeed` en km/h. La real usa `hardBrakeMps2`, `sharpTurnDegPerSec`, `minTripSpeed` en **m/s** y `minTripDuration` en **ms**. Quien siga el doc configura `minTripSpeed: 10` creyendo km/h y el plugin lo lee como 36 km/h: los viajes urbanos nunca arrancan. Las claves desconocidas se ignoran sin aviso. Promete `event.confidence` y un evento `'drivingEvent'` inexistentes. **Fix:** reescribir el doc; añadir log de advertencia para claves desconocidas en `ConfigMapper`.
- [x] **F16.** `.d.ts:622-625` vs `DrivingEventsDetector.java:217-226, 231, 87` — condiciones no documentadas: `possibleCrash` exige además `speed < 1.5` y `prevSpeedMps*3.6 >= crashImpactKmh` (un impacto a 60 km/h que deja el vehículo rodando a 20 no se detecta); `sharpTurn` tiene un gate de 5 m/s hardcodeado; `DRIVING_EVENT_COOLDOWN_MS = 4_000` no es configurable. **Fix:** documentar y exponer `drivingEventCooldownMs` y `sharpTurnMinSpeedMps`.
- [x] **F17.** `DrivingEventsDetector.java:67-68, 157-159` — `tripStartLat`, `tripStartLon`, `hasTripStartCoord`: asignados y nunca leídos. Código muerto.
- [x] **F18.** `DrivingEventsDetector.java:182-193` — `speeding` sin cooldown ni histéresis (rearme inmediato al bajar del límite) → ráfaga de eventos conduciendo justo en el límite. Además no requiere `tripActive`, a diferencia de los eventos v4.1. **Fix:** banda muerta de rearme (`limit * 0.95`) + el cooldown común.
- [x] **F19.** `DrivingEventsDetector.java:231-250` — `hasPrevBearing` solo se invalida en `reset()`.

**Verificado correcto:** normalización del wrap 0°/360° (`diff > 180 → 360 - diff`, ambas plataformas); protección de división por cero (`dtMs > 0`); signo de la aceleración (`accel <= -hardBrakeMps2`); unidades m/s internas con `* 3.6` solo en `speeding` y `dropKmh`; conversión a g (Android divide entre 9,80665; iOS usa `userAcceleration` ya en g); mapeo de las 16 opciones de `drivingEvents` completo y consistente entre `ConfigMapper` ↔ `Config.DrivingEventsOptions` ↔ `DrivingEventsDetector.Config` ↔ `.d.ts:601-640` ↔ `README.md:302-317`.

---

## 7. Tests y CI

### Cobertura actual

| Área | ¿Testeado? | Fichero(s) |
|---|---|---|
| Driving events (detector GPS) | **NO** | — |
| Sensor fusion (Android e iOS) | **NO** | — |
| Driving events (iOS facade) | **NO** | — |
| Mapeo `drivingEvents` (bridge) | **NO** | `ConfigMapperTest.java` no lo menciona |
| **Providers de localización** | **NO** | solo andamiaje (`MockLocationProvider`, `LocationProviderTestCase`) |
| **Capa JS / TypeScript (`www/`)** | **NO** | sin jest/karma, sin script `test` |
| **Wrapper Angular** | **NO** | — |
| Config (modelo Java) | Parcial | `ConfigTest.java` (8 + 2) — sin `drivingEvents`, `wakeLockMode`, `maxAcceptedAccuracy` |
| Config (bridge Cordova) | Parcial | `ConfigMapperTest.java` (10) — solo opciones legacy |
| Config (iOS) | Parcial | `MAURConfigTest.m` (2) |
| HTTP transport | Sí | `HttpPostServiceTest.java` (12), `PostLocationTaskTest.java` (4) |
| Plantillas de post | Sí | `HashMapLocationTemplateTest` (6), `ArrayListLocationTemplateTest` (3), `LocationTemplateFactoryTest` (2) — sin `@events`/`@battery` |
| Modelo de localización | Sí | `BackgroundLocationTest` (8), `MAURLocationTest.m` — sin `addDrivingEvent` |
| DAO / SQLite | Sí (instrumentado) | 6 ficheros `androidTest` + equivalentes iOS |
| Sync / batch | Sí (instrumentado) | `BatchManagerTest.java` (7) |
| Servicio / puente | Parcial (instrumentado) | `LocationServiceTest` (6), `LocationServiceProxyTest` (9), `BackgroundGeolocationFacadeTest` (1) |

### Estado del CI: **no ejecuta ni un solo test**

`.github/workflows/ci.yml` es solo un smoke test de compilación:
```yaml
- run: npm pack
- run: npm init @capacitor/app temp -- ... && cd android && ./gradlew assembleDebug
```

- [x] **G1.** No hay tarea `test` en ninguna parte: ni `./gradlew test`, ni `connectedAndroidTest`, ni `xcodebuild test`, ni `npm test`. Los 46 `@Test` de `src/test` y los ~70 de `src/androidTest` **nunca se ejecutan**. Pueden llevar meses rotos sin que nadie lo note.
- [x] **G2.** Se compila el proyecto equivocado: una app Capacitor efímera, no el proyecto Gradle del plugin (`:common`, `:CDVBackgroundGeolocation`, `:CordovaLib`).
- [x] **G3.** El proyecto del plugin es incompatible con el toolchain del CI: `gradle-wrapper.properties` fija **Gradle 5.1.1** (2019) y `android/build.gradle` usa **AGP 3.4.1**; el workflow provisiona **Java 21**, que Gradle 5.x no soporta. `android/CDVBackgroundGeolocation/build.gradle:28-31` usa `testCompile`/`compile`, eliminados en Gradle 7+.
- [x] **G4.** iOS sin CI: existe el target `BackgroundGeolocationTests` con 11 ficheros y ningún workflow lo ejecuta.
- [x] **G5.** `publish-npm.yml` publica sin verificación previa: `npm ci` → `npm publish`. Un fallo de compilación en Android puede publicarse a npm.

*Infraestructura ya disponible sin añadir dependencias:* `android/common/build.gradle:14-17` declara `testImplementation` con junit + robolectric + mockito-core (`VERSIONS.gradle:129-140`).

### Los 5 tests de mayor valor que faltan

1. **`DrivingEventsDetectorTest` — falsos positivos por entrega en lote** (cubre F1, F6). JUnit puro. Inyectar reloj falso; 5 fixes con timestamps separados 1 ms y velocidades 20,0/20,5/21,0/20,2/20,8 m/s en viaje activo. Aserción: **cero** `onHardBrake` y **cero** `onRapidAcceleration`. Variante: dos fixes con timestamp idéntico no producen ningún evento.
2. **`DrivingEventsDetectorTest` — fix sin velocidad** (cubre F2). Viaje a 25 m/s; llega un `BackgroundLocation` con `hasSpeed() == false` 1,5 s después. Aserciones: sin `onHardBrake`, sin `onPossibleCrash`, `isMoving` no transiciona. **El test que impide alertas de accidente falsas — el fallo de mayor coste operativo del módulo.**
3. **`DrivingEventsDetectorTest` — ciclo de vida del viaje y aislamiento entre viajes** (cubre F4, F12). Secuencia scriptada: parado 60 s → 15 m/s durante 35 s (`onMoving`, `onTripStart`) → polilínea de ~2 km → parar 65 s (`onStopped`, `onTripEnd`) → segundo viaje de 1 km. Aserciones: exactamente un `tripStart`/`tripEnd` por viaje; distancia del segundo ≈1000 m (prueba que no arrastra los 2 km); `durationMs` sin los 60 s de `stoppedDuration`.
4. **`LocationServiceImplDrivingConfigTest` (Robolectric) — hot-reload de `drivingEvents`** (cubre F3, F5). A: reconfigurar `enabled=false` con sensorFusion activo → aserción `unregisterListener` invocado y `mSensorFusion == null` (hoy falla). B: reconfigurar `speedLimit` a mitad de viaje → aserción de que se emite `tripEnd` o se conserva `tripActive`, y `mDrivingTripActive` no queda pegado.
5. **`ConfigMapperTest` — round-trip completo por clave** (cubre C1, C7, C8, F15). Test paramétrico que para **cada** clave de `ConfigureOptions` verifique `JSON → ConfigMapper → merge(default, cfg) → ConfigJsonMapper → fromJSONObject → Parcel → toJSONObject` y compare el valor. **Un único test que cierra los cuatro puntos de fuga a la vez** — es la causa raíz de que C1 haya sobrevivido varias releases. Añadir un caso con las claves obsoletas de `docs/driving-events.md` verificando que se ignoran.

**Cambio de CI mínimo para que esto valga algo:** actualizar el wrapper a Gradle 8.x / AGP 8.x, migrar `testCompile`/`compile` a `testImplementation`/`implementation`, y añadir un job con `cd android && ./gradlew :common:testDebugUnitTest :CDVBackgroundGeolocation:testDebugUnitTest`. Sin esto, cualquier test nuevo es decorativo.

---

## 8. Orden de corrección propuesto

**Fase 1 — el plugin deja de perder y duplicar posiciones**
A1, A2 (el provider vuelve a emitir) · B1, B2 (duplicados y pérdida silenciosa) · C1 (watchdog) · C2 (eventos de sync) · E1 (fuga de listeners) · F1, F2 (falsos positivos de conducción)

**Fase 2 — el plugin deja de morirse**
A3, A4/B8 (fugas y ANR) · A5, A6, A7, A8, A10 · B3, B7 · D1, D2, D3, D4 (iOS en background) · F3

**Fase 3 — degradación a largo plazo en flota**
B4, B5, B6, B9 · A13, A14 · D5-D18 · F4-F10

**Fase 4 — contratos y API**
C3, C4, C5 · E2-E12 · D19-D27 · paridad Android/iOS

**Fase 5 — infraestructura**
G1-G5 + los 5 tests · C10-C14 (build) · F15, E15 (documentación)
