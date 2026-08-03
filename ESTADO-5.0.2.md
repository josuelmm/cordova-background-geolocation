# Estado de v5.0.2 — hecho, pendiente y por qué

**Fecha:** 2026-08-03 · **Rama:** `main` · **Versión:** 5.0.2

Índice corto. Detalle técnico: `CHANGELOG.md`. Regresiones v4→v5: `MEJORAS.md`. Gates: `COMPATIBILIDAD.md` §6.

---

## 1. Hecho en 5.0.2 (sobre lo publicado como 5.0.1)

| ✅ | Qué |
|---|---|
| ✅ | Timeout **120 s** en **todas** las rutas HTTP (no solo lote JSON) |
| ✅ | Default `httpMode: 'single'` (= payload v4 `{...}`) |
| ✅ | Soft-validate Android + iOS (`GET` sync → POST; no mata `configure()`) |
| ✅ | `maxLocations` prioriza `DELETED` (Android + iOS) |
| ✅ | Docs / `.d.ts` / javadoc alineados |
| ✅ | Resto de 5.0.1: soft-delete 4xx, form-urlencoded plano, 285, Angular, etc. |

## 2. Validación local

```
./gradlew :common:testDebugUnitTest :CDVBackgroundGeolocation:testDebugUnitTest
  → BUILD SUCCESSFUL — 84 tests, 0 fallos
npm run test:js
  → 239 assertions OK
```

## 3. Pendiente (gates de release)

| ❌ | Qué |
|---|---|
| ❌ | Build Xcode / dispositivo iOS |
| ❌ | QA Android real (logcat: sin timeout 30s; sí Batch sync / partially completed) |
| ❌ | Job iOS CI verde sin `continue-on-error` |

## 4. Cambios deliberados vs v4 (no son bugs)

- `checkStatus()` OR de permisos
- Sync periódico 15 min (`syncEnabled: false` para opt-out)
- `mockLocationsEnabled` null; usar `@mocked`
- Semántica `tripEnd` / `speeding`
- `.d.ts`: `on()` → `EventSubscription`
- minSdk 24 · cordova-android ≥ 13 · cordova-ios ≥ 7
