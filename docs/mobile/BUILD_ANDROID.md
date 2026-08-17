# Builds Android

## Perfiles

| Perfil | Salida | Uso |
| --- | --- | --- |
| `development` | APK con dev client | Depuración con Metro en `8081`. |
| `preview` | APK interno autónomo | Instalación y aceptación en dispositivos. |
| `store-preview` | AAB técnico | Validar empaquetado; no publicar con la URL LAN. |
| `production` | AAB | Publicación real, con API HTTPS configurada en EAS. |

Validar y generar:

```powershell
npm ci
npm run check
npm run e2e:validate
npm run build:validate
npm run doctor
npx eas-cli@21.8.0 build --platform android --profile development
npx eas-cli@21.8.0 build --platform android --profile preview
npx eas-cli@21.8.0 build --platform android --profile store-preview
```

Producción exige `EXPO_PUBLIC_API_URL` pública, HTTPS y terminada en `/api/v1` dentro del entorno `production` de EAS. Las variables `EXPO_PUBLIC_*` nunca deben contener secretos.

## Evidencia de Fase 15

- APK preview correctivo v11: `a759bc63-77c9-4e19-a66b-f06c49971b81` — `FINISHED`.
- AAB store-preview v10: `764a7083-ffd9-49b2-a39b-ec6f167e337e` — `FINISHED`.
- Development build v10: `c4fd27ea-216a-4e5c-bcb0-4e31a36c7729` — `FINISHED`.

El AAB técnico está registrado en:

<https://expo.dev/accounts/proyecto4toano/projects/techstore-mobile/builds/764a7083-ffd9-49b2-a39b-ec6f167e337e>

Su copia local verificada mide 88.681.551 bytes, tiene SHA-256 `DC50B9208717C95AFE7CFAD25C72AC5B78A85001C3843BDD5F012C27531C1FDA` y queda ignorada en `artifacts/phase15/`.

El development build está registrado en:

<https://expo.dev/accounts/proyecto4toano/projects/techstore-mobile/builds/c4fd27ea-216a-4e5c-bcb0-4e31a36c7729>

Antes de Google Play se debe configurar una API HTTPS real, completar Play Console y aceptación interna, y usar el perfil `production`. El AAB técnico de esta fase apunta a la red LAN y no debe publicarse.

Referencias: [EAS Build](https://docs.expo.dev/build/introduction/), [APK para instalación](https://docs.expo.dev/build-reference/apk/) y [eas.json](https://docs.expo.dev/build/eas-json/).
