# Preparación iOS

**Estado:** configuración técnica preparada; build y distribución pendientes de credenciales Apple autorizadas.

La configuración validada declara:

- bundle identifier `com.techstore.mobile`;
- `usesNonExemptEncryption: false`;
- versionado remoto y autoincremento mediante EAS;
- perfil `production` sin secretos ni URL inventada en el repositorio;
- guardas que exigen API HTTPS pública terminada en `/api/v1`.

La resolución de producción se validó con una URL HTTPS ficticia usada sólo durante la comprobación. No se generó ni guardó certificado, provisioning profile, `.p8`, `.p12` ni `.mobileprovision`.

## Para generar iOS

1. Contar con Apple Developer Program y acceso autorizado al equipo Apple.
2. Configurar la URL HTTPS real en el entorno `production` de EAS.
3. Ejecutar `npm run check`, `npm run build:validate` y `npm run doctor`.
4. Ejecutar `npx eas-cli@21.8.0 build --platform ios --profile production`.

EAS puede administrar credenciales fuera del repositorio. La compilación nativa local de iOS y el simulador requieren macOS; desde Windows se puede preparar y enviar el build remoto.

Después se debe probar en dispositivo/TestFlight y completar privacidad y metadatos de App Store Connect.

Referencias: [credenciales iOS](https://docs.expo.dev/app-signing/app-credentials/), [envío iOS](https://docs.expo.dev/submit/ios/) y [distribución interna](https://docs.expo.dev/build/internal-distribution/).
