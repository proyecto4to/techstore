# Preparación del entorno móvil

## Requisitos

- Git.
- Node.js `22.13` o posterior, mínimo de Expo SDK 57.
- npm compatible con el lockfile; el entorno validado usa Node `24.18.0` y npm `11.16.0`.
- Para Android local: Android Studio, Android SDK, JDK y un emulador o dispositivo con depuración USB.
- Para un development build remoto: cuenta de Expo y acceso a Internet.

No hace falta instalar Expo globalmente: los scripts de npm y `npx` usan las versiones compatibles del proyecto.

## Instalación reproducible

Desde PowerShell:

```powershell
cd "C:\Users\Dell Vostro\OneDrive\Documentos\GitHub\techstore\techstore-mobile"
npm ci
Copy-Item .env.example .env.local
```

`npm ci` respeta `package-lock.json` y es la opción recomendada para una instalación limpia. `.env.local` está ignorado por Git.

Configuración mínima de desarrollo:

```dotenv
EXPO_PUBLIC_APP_ENV=development
EXPO_PUBLIC_API_URL=http://192.168.1.50:8090/api/v1
EXPO_PUBLIC_TENANT_SLUG=mi-tienda
```

Login, registro, refresh y logout consumen esta URL en Fase 2. Si queda vacía, la aplicación sigue siendo navegable pero deshabilita el envío del formulario de acceso.

## Variables y conectividad

| Variable | Estado en Fase 2 | Uso |
| --- | --- | --- |
| `EXPO_PUBLIC_APP_ENV` | Validada al iniciar | `development`, `preview` o `production`. |
| `EXPO_PUBLIC_API_URL` | Consumida por el cliente Axios | Base absoluta, incluida `/api/v1`; producción exige HTTPS público. |
| `EXPO_PUBLIC_TENANT_SLUG` | Validada y expuesta | Reservada para resolver tienda; el backend aún usa el tenant anónimo predeterminado. |

Todo valor `EXPO_PUBLIC_*` puede inspeccionarse en la aplicación compilada. No agregues JWT, refresh tokens, contraseñas, claves privadas ni credenciales de proveedores.

Cuando se conecte la API:

- Un teléfono físico debe usar la IP LAN de la computadora, no `localhost`.
- El emulador Android estándar accede al host mediante `10.0.2.2`.
- El backend debe escuchar en una interfaz accesible y permitir el origen de desarrollo correspondiente.
- Producción debe usar HTTPS.

Después de cambiar variables, reiniciá Metro. Si conserva una configuración anterior:

```powershell
npx expo start --clear
```

## Verificación inicial

```powershell
npm run check
npm run doctor
npm run build:web
```

`check` ejecuta TypeScript, ESLint sin warnings y Jest. `doctor` comprueba la coherencia de Expo y `build:web` verifica el export estático.

## Android con development build

Este es el entorno recomendado para revisar la aplicación en sí, porque incluye la configuración nativa del proyecto.

### Opción A: EAS remoto

```powershell
npx eas-cli@latest build --platform android --profile development
```

EAS pedirá autenticación. Si el proyecto todavía no está asociado, también puede solicitar crear/vincular el proyecto y agregar su identificador a la configuración. Revisá esos cambios antes de versionarlos. Al finalizar, instalá el APK interno desde el enlace entregado.

Con el development build instalado:

```powershell
npx expo start --dev-client
```

Escaneá el QR desde el teléfono. Ambos equipos deben compartir red o contar con una ruta de red entre sí.

### Opción B: compilación local

Con Android Studio, SDK, JDK y `adb` operativos:

```powershell
npx expo run:android
```

El comando genera el proyecto nativo local (las carpetas `/android` y `/ios` están ignoradas) y compila el development build. Una vez instalado puede reutilizarse con `npm run android` mientras no cambien dependencias o plugins nativos.

## Expo Go

Para una inspección provisional de la UI:

```powershell
npx expo start --go
```

Expo Go es un sandbox limitado. No valida adecuadamente todos los plugins de configuración, capacidades nativas ni el comportamiento de una build distribuible, por lo que no es el criterio de aceptación de la fase.

## Web

```powershell
npm run web
```

La terminal muestra la URL local. El export de producción se valida con:

```powershell
npm run build:web
```

El resultado aparece en `techstore-mobile/dist/` y no se versiona.

## Perfiles EAS disponibles

| Perfil | Resultado esperado | Uso |
| --- | --- | --- |
| `development` | Development client, distribución interna | Desarrollo y QA nativo. |
| `preview` | APK Android interno | Demostración/QA sin Metro. |
| `production` | AAB Android, versión autoincremental | Publicación futura. |

La existencia de estos perfiles no significa que credenciales, store listing, firma final o despliegue ya estén completados.

## Auditoría de dependencias

La auditoría vigente al cierre de Fase 2 registró `0 critical`, `15 high` y `8 moderate` en 23 nodos afectados. Los hallazgos provienen de dependencias transitivas del tooling de Expo/Metro (`image-size`) y del tooling iOS (`xcode` → `uuid`); no se demostró una ruta de explotación dentro del runtime de la aplicación, pero eso no equivale a riesgo cero.

En el árbol compatible actual de Expo SDK 57 no hay parche directo disponible. No ejecutes:

```text
npm audit fix --force
```

La corrección forzada propone cambios incompatibles de versiones mayores/menores y puede romper Expo 57. La política es mantener assets de build confiables, limitar tiempos de CI, actualizar parches compatibles de Expo cuando aparezcan y repetir `npm audit`/`npm run doctor` después de cada actualización.
