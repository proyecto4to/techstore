# NOTIFICACIONES PUSH (FCM)

ESTADO: el código está entero de los dos lados y probado; **falta crear la app en la consola de Firebase**, que es lo único que no se puede hacer desde acá porque necesita tu cuenta de Google.

Sin eso, la app arranca igual y no se queja: `FirebaseConfiguration.configured` da falso, `PushService.start()` anota «FCM no configurado para este flavor» y los avisos llegan solo por WebSocket, es decir, **solo con la app abierta**.

## Qué ya está hecho

| Pieza | Dónde | Estado |
| --- | --- | --- |
| Registro del equipo | `POST /api/v1/dispositivos/actual`, tabla de la migración V40 | listo |
| Token FCM, refresco y baja al cerrar sesión | `lib/core/push/push_service.dart` | listo |
| Apertura desde la notificación (deep links `techstore://…`) | `PushService.deepLinks` | listo |
| Envío desde el servidor, con reintentos | `NotificationOutboxDispatcher` + `FcmPushProvider` | listo |
| Claves al compilar | `scripts/firebase-mobile-defines.ps1` | listo |
| Proyecto Firebase y credencial del backend | consola de Firebase | **falta** |

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
4. El teléfono queda registrado: la app llama a `POST /api/v1/dispositivos/actual` con su token. En la base, `dispositivos_moviles` tiene que sumar una fila para ese usuario.
5. **Cerrar la app del todo** y provocar un aviso real: cambiar el estado de un pedido de esa persona. Tiene que aparecer la notificación, y al tocarla la app abre el pedido.

Si el paso 5 no llega pero el 4 sí, el problema está del lado del servidor: mirar el log del worker del outbox, que anota el error que devuelve FCM (token vencido, credencial de otro proyecto, etc.).

## Detalles que evitan perder una tarde

- **El paquete tiene que coincidir.** El APK de Expo (`com.techstore.mobile`) es otra app; su configuración de Firebase no sirve acá.
- **La credencial y las claves tienen que ser del mismo proyecto.** Si el backend manda con la cuenta de servicio de un proyecto y la app se registró en otro, FCM rechaza cada envío con un error de token.
- **Un token es de una instalación.** Desinstalar y reinstalar cambia el token; la app lo vuelve a registrar sola al iniciar sesión.
- **Android 13 o superior pide permiso** y el usuario lo puede rechazar. Si lo rechaza, `PushService` no vuelve a insistir: los avisos siguen llegando solo con la app abierta.
- **Sin Google Play Services no hay FCM.** En un teléfono sin los servicios de Google el push no funciona, y el resto de la app sí.
