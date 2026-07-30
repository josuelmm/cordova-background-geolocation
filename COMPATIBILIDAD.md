# Compatibilidad con GPSMasterClient

Qué cambió en la superficie pública del plugin y cómo afecta a la app. Fecha: 2026-07-29.

Verificado cruzando **todo** el uso real de la app (`rg` sobre `src/`) contra el plugin modificado, no de memoria.

---

## 0. BLOQUEANTE para poder probar: hay que construir `angular/dist`

`package.json` expone `./angular` así:

```json
"./angular": {
  "types": "./angular/dist/index.d.ts",
  "default": "./angular/dist/fesm2022/josuelmm-cordova-background-geolocation.mjs"
}
```

`angular/dist` está en `.gitignore` y **no existe en el repo**. Se genera con `npm run build:angular`,
y **no hay script `prepare` ni `prepublishOnly`** que lo dispare automáticamente, así que
`npm install file:...` / `pnpm add file:...` instalaría un paquete cuyo subpath `./angular` apunta a
ficheros inexistentes.

La app importa de `/angular` en **4 sitios** (`main.ts`, `tab1.page.ts`, `login.page.ts`,
`disconnect.page.ts`), así que sin ese paso el build de la app falla al resolver el módulo.

Secuencia correcta:

```bash
cd D:\xampp\htdocs\cordova-background-geolocation-plugin
npm install            # devDeps: ng-packagr + @angular/core 18
npm run build:angular  # genera angular/dist  <-- IMPRESCINDIBLE

cd D:\xampp\htdocs\GPSMasterClient
pnpm add file:../cordova-background-geolocation-plugin
npx cap sync           # copia los .java vía plugin.xml
```

Verificado: `npm run build:angular` compila sin errores con los cambios actuales
(ng-packagr 18.2, @angular/core 18.2.14) y el `dist` resultante declara
`constructor(zone: NgZone)` y contiene 3 `zone.run(...)`.

> Recomendación aparte: añadir `"prepare": "npm run build:angular"` a `package.json` para que una
> instalación local no pueda quedar a medias. No lo he hecho porque cambia el comportamiento de
> publicación y es tu decisión.

---

## 1. Rotura viva en la app: `finish()`

`GPSMasterClient/src/app/tab1/tab1.page.ts:1732`

```ts
if (Capacitor.getPlatform() === 'ios') {
  this.backgroundGeolocation.finish();   // <-- ya no existe
}
```

`finish()` y `changePace()` estaban declarados en el servicio Angular pero **no existen** en
`www/BackgroundGeolocation.js` ni como acción nativa en Android o iOS. Comprobado con grep sobre
las tres capas. Antes lanzaba `TypeError`, lo capturaba el `.catch()` de la línea 1734 y lo
reportaba como *"Error starting background task"* — es decir, en iOS el background task **nunca
se cerraba**. Los eliminé del servicio, así que ahora falla en compilación en vez de en silencio.

Confirmado en el `dist` recién generado: 0 ocurrencias de `finish()` / `changePace`.

**Arreglo en la app** — la API correcta es `endTask(taskKey)`, que sí existe y ya tienes el
`taskKey` a mano en ese `.then()`:

```ts
if (Capacitor.getPlatform() === 'ios') {
  this.backgroundGeolocation.endTask(taskKey);
}
```

Esto además cierra el bug real de iOS.

---

## 2. Rotura ya corregida: `AccuracyLevel`

`tab1.page.ts:1649` pasa `desiredAccuracy: 10` para 'Media'. Yo había estrechado el tipo a
`0 | 100 | 1000 | 10000`, que rechaza el `10`. Reproducido: `error TS2322: Type '10' is not
assignable to type 'AccuracyLevel | undefined'`.

Restaurado como `0 | 100 | 1000 | 10000 | (number & {})`: mantiene el autocompletado de los cuatro
presets y acepta cualquier número, que es lo que el nativo realmente admite (lo reenvía como
metros). Verificado con `tsc --strict` sobre 0, 10, 100 y 37.

---

## 3. Superficie que la app usa: verificación completa

**Símbolos importados (5/5 siguen exportados):**
`BackgroundGeolocationService`, `BackgroundGeolocationAccuracy`, `BackgroundGeolocationConfig`,
`BackgroundGeolocationEvents`, `BackgroundGeolocationResponse`.

**Métodos del servicio que la app llama (10 de 11 existen):**

| Método | Estado |
|---|---|
| `on` (×3), `configure`, `start`, `stop` (×2), `checkStatus` (×2) | OK |
| `startTask`, `endTask`, `getPendingSyncCount`, `forceSync`, `clearSync` | OK |
| `finish` | **eliminado** → ver punto 1 |

**`configBackground` completo** (los 24 campos que arma `startService()`, incluido `postTemplate`
con `@events` y `@battery`): compilado contra el `.d.ts` modificado con `tsc --strict` → exit 0.

**`on()` del `.d.ts`** pasó de 29 a 58 sobrecargas (separadas en «con callback» → `EventSubscription`
y «sin callback» → `Subscribable`). No afecta a la app: la app usa el `on()` del **servicio Angular**,
cuya firma `on(eventName: string, callback?)` no cambió.

---

## 4. Cambios de comportamiento (no rompen la compilación, pero notarás la diferencia)

- **`unsubscribe()` ahora funciona de verdad.** Antes `radio.js` desuscribía del canal equivocado y
  los listeners se acumulaban en cada ciclo stop→start. El `bgSubs.forEach(s => s?.unsubscribe?.())`
  de `tab1.page.ts:1766` era un no-op silencioso. Ahora sí libera, así que dejarás de ver reinicios
  múltiples del servicio tras varios ciclos.
- **`NgZone` en el servicio.** Los eventos nativos ahora entran en la zona Angular. Tu handler de
  `location` (`tab1.page.ts:1714-1723`) asigna `myLatitude`/`myLongitude` sin `detectChanges()`;
  con esto la vista debería refrescarse sola. Tu `main.ts` provee el servicio como clase, así que
  Angular inyecta `NgZone` automáticamente — no hay que tocar nada.
- **`checkStatus().authorization`** puede devolver AUTHORIZED donde antes decía DENIED: `hasPermissions()`
  pasó de exigir FINE **y** COARSE a aceptar cualquiera de los dos, que es lo que el servicio
  siempre aceptó.
- **Orden de claves del `postTemplate`** ahora es determinista (el que declaras). GPSWox parsea por
  nombre, así que es indiferente, pero los logs del servidor se ven distintos.
- **Sin impacto:** los cambios de driving events (la app no activa `drivingEvents`), el borrado
  físico del lote de sync (`getPendingSyncCount` cuenta `SYNC_PENDING`, no las borradas) y la
  validación de `ConfigMapper` (todos los valores que manda la app son válidos).

---

## 5. iOS: hay que cablear un callback en tu `AppDelegate` (D10)

**Esto es lo único que la app tiene que tocar para que v5 funcione entera en iOS.** El sync de
fondo usa una `NSURLSession` de tipo *background*: iOS puede terminar la subida con la app
suspendida o ya matada, y en ese momento relanza el proceso y llama a
`-application:handleEventsForBackgroundURLSession:completionHandler:` en el **AppDelegate**.
El plugin ya implementa ese método (`CDVBackgroundGeolocation.m`), pero según la versión de
cordova-ios el `AppDelegate` **no lo reenvía a los plugins**. Si no llega, el handler de UIKit
nunca se invoca y iOS deja de relanzar la app para terminar subidas: las posiciones se quedan en
la cola hasta que el usuario abre la app.

Añade esto a `platforms/ios/<TuApp>/Classes/AppDelegate.m` (o al hook que uses para no perderlo en
cada `cordova platform add`):

```objc
- (void)application:(UIApplication *)application
        handleEventsForBackgroundURLSession:(NSString *)identifier
        completionHandler:(void (^)(void))completionHandler
{
    CDVViewController *vc = (CDVViewController *)self.viewController;
    id plugin = [vc getCommandInstance:@"BackgroundGeolocation"];
    if ([plugin respondsToSelector:@selector(application:handleEventsForBackgroundURLSession:completionHandler:)]) {
        [plugin application:application
                handleEventsForBackgroundURLSession:identifier
                completionHandler:completionHandler];
        return;
    }
    completionHandler();
}
```

El plugin compara `identifier` contra `+[MAURBackgroundSync sessionIdentifier]`
(`com.marianhello.session`) e ignora sesiones ajenas, así que es seguro reenviarle todo.

**Cómo comprobar que quedó bien:** con `syncUrl` configurado, mata la app desde el selector de
apps con posiciones pendientes y espera. Si al volver a abrirla la cola está vacía, el reenvío
funciona; si sigue llena hasta que abres la app a mano, no está llegando.

---

## 6. Qué falta en el plugin

Ninguno de los ~132 hallazgos de `MEJORAS.md` queda abierto a nivel de código. Lo que queda son
gates de release, no bugs:

| # | Qué | Por qué sigue abierto |
|---|---|---|
| 1 | Build de Xcode | No hay macOS ni Xcode donde se hicieron los cambios |
| 2 | QA en dispositivo (Android + iOS) | Ni un solo minuto de tracking real |
| 3 | Tests instrumentados Android | Compilan y hay job de CI, pero nadie los ha ejecutado todavía |
| 4 | D10 (sección 5) | Depende del `AppDelegate` de tu app |
| 5 | Job de iOS en CI | Sigue en `continue-on-error` hasta su primer run verde |

---

## 7. Lo que no puedo garantizar

- **iOS no está compilado.** El chequeo con clang contra stubs detecta sintaxis y selectores
  inexistentes; no es un build. Ábrelo en Xcode antes de dar por buenos los 30 puntos D*.
- **Nada probado en dispositivo.** 68 tests unitarios verdes demuestran lógica, no comportamiento
  real: foreground service, Doze, revocación de permisos en caliente, sync con red intermitente.
- **Los 72 tests instrumentados compilan pero no se han ejecutado.** Cubren la capa
  DAO/BatchManager/servicio, que es la más modificada — espera fallos reales en el primer run.
- **`tick()` de iOS no corre con el proceso suspendido.** `NSTimer` no se dispara mientras iOS
  tiene la app suspendida, así que `tripEnd` puede llegar en el siguiente despertar (una
  localización nueva, un `significant location change` o el propio sync) en vez de exactamente al
  cumplirse `stoppedDuration`. En Android el `Handler` sí corre mientras vive el foreground
  service, con la latencia normal de Doze. En ambos casos el evento **llega**; lo que no se
  garantiza es el instante exacto.
