# NOTIFICACIONES PUSH (FCM)

ESTADO (24/10/2026): las apps Android `py.com.techstore.app` y `py.com.techstore.app.dev` están registradas en `techstore-mobile-513c4`. Las configuraciones móviles y la credencial del backend están guardadas fuera del repositorio, en `%LOCALAPPDATA%\TechStore\mobile-signing` (`firebase-android.json`, `firebase-android-dev.json` y `firebase-admin.json`). El APK de producción con Firebase compiló, pasó firma, manifiesto y seguridad y abrió la pantalla de ingreso en el emulador; reemplaza al del escritorio conservando un respaldo del anterior. La versión development recibió avisos reales por FCM con el proceso cerrado y abrió el pedido correcto al tocar la notificación. Evidencias: `artifacts/qa-push-20261024/`. El 25/10 se sumó el ícono propio del aviso (`ic_stat_techstore`, verificado en el emulador) y se **publicó la 1.0.23 con Firebase** (commit `a34b241`, ver `docs/transformacion/14-prueba-integral-y-apk-celular.md`). El acceso «TechStore - En internet» arranca el servidor con push si existe `firebase-admin.json`. Falta la prueba en un teléfono físico y con el servidor público.

Sin eso, la app arranca igual y no se queja: `FirebaseConfiguration.configured` da falso, `PushService.start()` anota «FCM no configurado para este flavor» y los avisos llegan solo por WebSocket, es decir, **solo con la app abierta**.

## Qué ya está hecho

| Pieza | Dónde | Estado |
| --- | --- | --- |
| Registro del equipo | `POST /api/v1/dispositivos/actual`, tabla de la migración V40 | listo |
| Token FCM, refresco y baja al cerrar sesión | `lib/core/push/push_service.dart` | listo |
| Apertura desde la notificación (deep links `techstore://…`) | `PushService.deepLinks` | listo |
| Envío desde el servidor, con reintentos | `NotificationOutboxDispatcher` + `FcmPushProvider` | listo |
| Claves al compilar | `scripts/firebase-mobile-defines.ps1` | listo |
| Proyecto Firebase y credencial del backend | consola de Firebase y carpeta local de credenciales | configurados; entrega real y apertura verificadas en QA |

El escaneo de APK/AAB acepta la clave pública exacta de una configuración Android validada mediante `--firebase-config <archivo>` antes de `--archive`. No permite otras claves Google ni credenciales privadas. Los scripts de compilación pasan esa opción cuando incluyen Firebase; el escaneo del repositorio conserva sus reglas originales. Véase [Firebase — API keys](https://firebase.google.com/docs/projects/api-keys).

## Lo que hay que hacer una sola vez

### 1. La app Android en Firebase

1. Entrar a <https://console.firebase.google.com> con la cuenta del proyecto.
2. Crear un proyecto (o abrir el que ya exista) y agregar una app **Android**.
3. Nombre del paquete, exactamente: `py.com.techstore.app`.
   Para FCM no hace falta la huella SHA-1; se pide para otras cosas de Google, no para esto.
4. Firebase muestra `google-services.json`. **No hace falta bajarlo al repositorio**: la app no lo usa, arma su configuración con los valores que recibe al compilar. Lo que sí hay que copiar son cinco datos de *Configuración del proyecto → Tus apps → Android*.

Crear con ellos este archivo, fuera del repositorio, al lado del keystore:

`%LOCALAPPDATA%\TechStore\mobile-signing\firebase-android.json`

```json
{
  "apiKey": "AIza...",
  "appId": "1:000000000000:android:abc123",
  "messagingSenderId": "000000000000",
  "projectId": "techstore-xxxxx",
  "storageBucket": "techstore-xxxxx.firebasestorage.app"
}
```

Esa clave viaja dentro del APK y Google la considera pública; aun así conviene restringirla en Google Cloud a este paquete. Lo que nunca entra en la app es la credencial del paso siguiente.

### 2. La credencial del backend

1. En *Configuración del proyecto → Cuentas de servicio*, generar una clave privada nueva. Baja un JSON.
2. Guardarlo fuera del repositorio, por ejemplo en `%LOCALAPPDATA%\TechStore\mobile-signing\firebase-admin.json`.
3. Apuntar la variable antes de levantar el sistema:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "$env:LOCALAPPDATA\TechStore\mobile-signing\firebase-admin.json"
.\iniciar-techstore-publico.ps1
```

**Ese JSON es un secreto de verdad**: permite mandarle notificaciones a todos los teléfonos con la app. No se versiona, no se comparte y si se filtra hay que revocarlo desde la consola.

El lanzador público ya pide push por omisión y **falla si la variable no está o apunta a un archivo que no existe**. Para arrancar a propósito sin push: `.\iniciar-techstore-publico.ps1 -SinPush`.

### 3. Compilar la app con las claves

No hay paso extra: `build-mobile-release.ps1` lee el archivo del punto 1 y agrega los `--dart-define`. Si falta, **corta la compilación** en vez de publicar una versión muda; para compilar igual, `-SinPush`. El APK privado solo avisa.

## Cómo comprobar que quedó andando

1. Al arrancar el backend tiene que decir `[push] Firebase Cloud Messaging activo.`
2. Al compilar tiene que decir `[push] Firebase: proyecto techstore-xxxxx.`
3. Instalar el APK, iniciar sesión y aceptar el permiso de notificaciones que pide Android 13 o superior.
4. El teléfono queda registrado: la app llama a `PUT /api/v1/dispositivos/actual` con su token. En la base, `dispositivos_moviles` tiene que sumar una fila para ese usuario.
5. Cerrar la app y provocar un aviso real: cambiar el estado de un pedido de esa persona. Para simular un proceso desalojado se puede usar `am kill` después de ir al inicio; no usar `am force-stop`, que bloquea la recepción hasta abrir nuevamente la app. Tiene que aparecer la notificación y, al tocarla, abrir el pedido.

Si el paso 5 no llega pero el 4 sí, el problema está del lado del servidor: mirar el log del worker del outbox, que anota el error que devuelve FCM (token vencido, credencial de otro proyecto, etc.).

## Detalles que evitan perder una tarde

- **El paquete tiene que coincidir.** El APK de Expo (`com.techstore.mobile`) es otra app; su configuración de Firebase no sirve acá.
- **La credencial y las claves tienen que ser del mismo proyecto.** Si el backend manda con la cuenta de servicio de un proyecto y la app se registró en otro, FCM rechaza cada envío con un error de token.
- **Un token es de una instalación.** Desinstalar y reinstalar cambia el token; la app lo vuelve a registrar sola al iniciar sesión.
- **Android 13 o superior pide permiso** y el usuario lo puede rechazar. Si lo rechaza, `PushService` no vuelve a insistir: los avisos siguen llegando solo con la app abierta.
- **Sin Google Play Services no hay FCM.** En un teléfono sin los servicios de Google el push no funciona, y el resto de la app sí.
- **En QA comprobar que Google Play Services esté habilitado.** La imagen puede incluirlo pero tener `com.google.android.gms` deshabilitado: en ese caso el registro falla con `SERVICE_DISABLED`. La prueba del 24/10 requirió habilitarlo antes de registrar el token.
