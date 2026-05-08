# Auto-start tras reinicio del dispositivo (Fase 1, v3.3)

Comportamiento del plugin cuando el usuario apaga y enciende el celular o cuando el sistema reinicia.

> **Esta es la Fase 1 del roadmap.** Sin auto-start fiable, el resto (HTTP transport, diagnóstico, OEM helpers, driving) tiene poco valor en producción.

---

## Android

### Soporte actual (v3.2.0)

El plugin **sí** reinicia el servicio tras `BOOT_COMPLETED` si:

1. El usuario llamó `configure({ startOnBoot: true })` antes del apagado.
2. La app tiene permiso `ACCESS_FINE_LOCATION` o `ACCESS_COARSE_LOCATION` concedido.
3. El servicio estaba siendo usado (la `Config` quedó persistida en SQLite).

Componentes:

- `android/common/src/main/java/com/marianhello/bgloc/BootCompletedReceiver.java` — broadcast receiver.
- `plugin.xml` líneas 165-172 — registro del receiver.
- `plugin.xml` línea 180 — permiso `RECEIVE_BOOT_COMPLETED`.

Flujo interno:

1. Sistema emite `android.intent.action.BOOT_COMPLETED`.
2. `BootCompletedReceiver.onReceive` lee la última `Config` vía `ConfigurationDAO`.
3. Si `config.getStartOnBoot() == true` y hay permiso de ubicación:
   - Android 8+ → `context.startForegroundService(LocationServiceImpl)`.
   - Android <8 → `context.startService(LocationServiceImpl)`.

Configuración:

```ts
BackgroundGeolocation.configure({
  startOnBoot: true,
  stopOnTerminate: false,
  // ...resto de config
});
```

### Limitaciones actuales y plan v3.3

| Limitación | Impacto | Plan v3.3 |
|---|---|---|
| Solo escucha `BOOT_COMPLETED` | HTC y MIUI (Xiaomi/Redmi) no disparan ese broadcast; sí `QUICKBOOT_POWERON` | Añadir `QUICKBOOT_POWERON` y `com.htc.intent.action.QUICKBOOT_POWERON` |
| No escucha `MY_PACKAGE_REPLACED` | Tras actualizar la app por Play Store el servicio no se relanza hasta que el usuario abre la app | Añadir action |
| No valida `ACCESS_BACKGROUND_LOCATION` | Android 10+: FGS de tipo location iniciado desde background sin este permiso falla silencioso | Validar antes de `startForegroundService` (solo en Android 10+) y loggear |
| No maneja `ForegroundServiceStartNotAllowedException` | Android 12+: si el sistema bloquea, crash silencioso | Try/catch + log + evento `serviceStartBlocked`. **NO** WorkManager para tracking continuo (ver §Notas). |
| `LocationServiceImpl.java` L586: hardcode `0x8` | Ignora `getManifestForegroundServiceType()` ya definido en L528. Si el manifest cambia, el código no se entera. | Usar el valor leído del manifest; abortar con log si retorna 0. |
| `LocationServiceImpl.java` L519-520: comentario y constante muerta | Dice "TYPE_LOCATION = 4". El valor real es `8` (4 es `PHONE_CALL`). | Eliminar constante L520 y corregir comentario L519. |
| `plugin.xml` declara `foregroundServiceType="location\|dataSync"` y permiso `FOREGROUND_SERVICE_DATA_SYNC` | El servicio NO usa dataSync; añade escrutinio innecesario en Play Console | Dejar solo `location` y eliminar el permiso |
| `LocationServiceProxy.java`: si falta permiso de ubicación cae a `startService(...)` silencioso | El servicio queda zombie sin foreground real | No iniciar foreground service, log claro y salir. |
| OEM con políticas agresivas (Xiaomi, Huawei, Oppo, Vivo, Samsung One UI) | Matan el FGS aunque arranque | Helpers de batería + AutoStart settings (v3.6) |

### Receiver propuesto v3.3

```xml
<receiver android:name="com.marianhello.bgloc.BootCompletedReceiver"
          android:enabled="true"
          android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

**No** se incluye `android.intent.action.LOCKED_BOOT_COMPLETED` ni `android:directBootAware="true"` en v3.3. Motivo: la `Config` vive en almacenamiento credential-encrypted (SQLite normal), no disponible antes del primer desbloqueo. Soportarlo requiere migrar `ConfigurationDAO` a Device Protected Storage, lo cual queda fuera de scope.

### Cambios concretos en `BootCompletedReceiver.java`

Antes:

```java
if (!hasLocationPermission(context)) {
    Log.w(TAG, "Skipping start on boot: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION not granted");
    return;
}
// ...
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(locationServiceIntent);
} else {
    context.startService(locationServiceIntent);
}
```

Después (v3.3):

```java
if (!hasLocationPermission(context)) {
    Log.w(TAG, "Skipping start on boot: fine/coarse location not granted");
    return;
}
if (Build.VERSION.SDK_INT >= 29 && !hasBackgroundLocationPermission(context)) {
    Log.w(TAG, "Skipping start on boot: ACCESS_BACKGROUND_LOCATION not granted");
    return;
}
try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(locationServiceIntent);
    } else {
        context.startService(locationServiceIntent);
    }
} catch (Exception e) {
    Log.e(TAG, "Start on boot blocked: " + e.getClass().getSimpleName(), e);
    // TODO v3.5: emit serviceStartBlocked event + diagnostics snapshot
}
```

### Cambios concretos en `LocationServiceImpl.java`

L519-520 actuales:

```java
/** FOREGROUND_SERVICE_TYPE_LOCATION = 4 when compileSdk >= 34. */
private static final int FOREGROUND_SERVICE_TYPE_LOCATION = 4;
```

Eliminar ambas líneas. El valor correcto es 8, ya implícito en el call.

L586 actual:

```java
super.startForeground(NOTIFICATION_ID, notification, 0x8);
```

Reemplazar por:

```java
int type = getManifestForegroundServiceType();
if (type == 0) {
    logger.error("Cannot start foreground: manifest foregroundServiceType missing or unreadable");
    return;
}
super.startForeground(NOTIFICATION_ID, notification, type);
```

### Recomendaciones de uso

- Pedir `ACCESS_BACKGROUND_LOCATION` con flujo correcto: foreground → explicación → "Permitir todo el tiempo".
- Pedir al usuario desactivar optimización de batería para la app (helpers v3.6).
- En Xiaomi/Huawei/Oppo/Vivo, indicar que activen "AutoStart" o "Inicio en segundo plano" (helpers v3.6).
- `stopOnTerminate: false` para que el servicio sobreviva al swipe.

### Notas sobre WorkManager

WorkManager **no** es solución para mantener tracking GPS continuo. Sus restricciones (Doze, batch scheduling, mínimo 15 min para periodic work) lo hacen inadecuado para Life360-like.

Uso válido de WorkManager en este plugin: **sólo** para reintentos diferidos de la cola de sync cuando la app no está activa y el dispositivo está offline. Tracking GPS sigue siendo responsabilidad del Foreground Service (`LocationServiceImpl`).

---

## iOS

### Limitación de plataforma

iOS **no permite** auto-iniciar al encender el dispositivo. Apple bloquea ejecución de la app hasta su primera apertura post-boot. No hay equivalente a `BOOT_COMPLETED`.

### Mitigaciones (ya implementadas)

- `stopOnTerminate: false` + `UIBackgroundModes: location` → si la app pasa a background y el usuario la termina, iOS puede relanzarla cuando detecta movimiento significativo.
- Significant Location Changes ya activo en `MAURDistanceFilterLocationProvider.m` (L191, L427, L431-433) e `INTULocationManager.m` (L546).
- `allowsBackgroundLocationUpdates = YES` en `MAURDistanceFilterLocationProvider.m` L73 e `INTULocationManager.m` L140, L1011.
- Region monitoring (geofence) → relanza al cruzar regiones registradas.
- Background App Refresh activado por el usuario (System Settings → Background App Refresh → app).

### Recomendaciones

- Documentar al usuario que en iOS, tras un reinicio del dispositivo, debe abrir la app al menos una vez para que el tracking se reactive.
- Tras esa primera apertura, con `stopOnTerminate: false` el sistema lo mantendrá vivo.
