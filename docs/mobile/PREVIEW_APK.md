# APK Android — candidato de Fase 15

**Estado:** generado, descargado y verificado técnicamente; pendiente aceptación manual  
**Fecha:** 2026-08-13  
**Dispositivo objetivo:** Xiaomi Redmi Note 8 Pro  
**Código incluido:** Fases 0 a 15.

## APK entregado

| Dato | Valor |
| --- | --- |
| Proyecto EAS | `@proyecto4toano/techstore-mobile` |
| Build ID | `a759bc63-77c9-4e19-a66b-f06c49971b81` |
| Estado EAS | `FINISHED` |
| Perfil | `preview` / distribución interna / APK autónomo |
| Versión | `1.0.0` |
| versionCode | `11` |
| Expo SDK | `57.0.0` |
| Android package | `com.techstore.mobile` |
| Tamaño | 124.892.480 bytes (119,10 MiB) |
| SHA-256 | `3E74FEC80AFC3E1FDE35CDDDDC16A8EDD0982D1450976BD844430ECF4F10D627` |
| Expiración del enlace directo | `2026-08-28 00:18 UTC` |

Descarga directa:

<https://expo.dev/artifacts/eas/PGkTUX82E6mqyBFqCbDNus-a9fLuMsNK61fb-cnDwA0.apk>

Página persistente del build:

<https://expo.dev/accounts/proyecto4toano/projects/techstore-mobile/builds/a759bc63-77c9-4e19-a66b-f06c49971b81>

La URL directa es temporal. Si expira, la página conserva el registro; si Expo ya retiró el archivo, se debe generar otro APK con `eas build --platform android --profile preview`.

## Verificación técnica

- Descarga completa y ZIP/APK legible: 1.413 entradas.
- Contiene manifiesto y bibliotecas `arm64-v8a`, `armeabi-v7a` y `x86_64`; el Redmi queda cubierto por ARM64.
- Firma Android v2 verificada con la herramienta oficial `apksig`: cero errores y cero advertencias. El certificado SHA-256 coincide exactamente con el APK v8 que sí se instalaba.
- Escaneo de secretos aprobado. `assets/expo-root.pem` es el certificado raíz público de Expo, no una clave privada.
- Copia local ignorada por Git: `artifacts/phase15/techstore-mobile-1.0.0-preview-v11.apk`.

## Red configurada

- API: `http://192.168.16.125:8090/api/v1`
- WebSocket: `ws://192.168.16.125:8090/ws`

Antes de probar, iniciar TechStore y conectar teléfono y PC a la misma Wi-Fi. La API usa `8090`; `8080` y `8181` quedan reservados para GeneXus/Tomcat. Este APK no necesita Metro ni Expo Go. Si cambia la IP de la PC, se debe reservar esa IP o generar otro APK con la nueva URL.

## Instalación en Xiaomi

1. Abrir el enlace directo desde el teléfono y descargar el APK.
2. Abrirlo desde Descargas o el gestor de archivos.
3. Si MIUI lo solicita, permitir temporalmente **Instalar apps desconocidas** para esa aplicación.
4. Pulsar **Instalar** y luego **Abrir**.
5. Revocar el permiso después de instalar si ya no se necesita.

No requiere root, bootloader desbloqueado, ROM personalizada, Expo Go ni Metro.

## Checklist de aceptación manual

- [ ] Splash, icono, registro, login, persistencia y cierre de sesión.
- [ ] Home, catálogo, búsqueda, filtros, detalle, favoritos e importes en guaraníes.
- [ ] Carrito persistente y revalidación de precio/stock.
- [ ] Direcciones, cotización, entrega, pago y checkout idempotente.
- [ ] Pedidos, detalle y timeline de tracking.
- [ ] Chat móvil ↔ Admin Web, no leídos, lectura y reconexión.
- [ ] Permiso push, centro de notificaciones y navegación desde una notificación.
- [ ] Bloqueo biométrico opcional y fallback seguro.
- [ ] Safe areas, teclado, scroll, tamaño de texto, fluidez y ausencia de cortes.

La Fase 15 sólo podrá marcarse aceptada después de completar este checklist en el dispositivo físico.

## Si Android muestra “No se instaló la app”

Los APK v8 y v9 fueron comprobados con el mismo certificado de firma. Android acepta una actualización sólo si coinciden package/firma y el `versionCode` no disminuye. Para descartar una descarga parcial o una variante más nueva ya instalada:

1. Eliminar únicamente el archivo APK fallido de la carpeta **Descargas**; no desinstalar todavía la app que funciona.
2. Descargar el APK correctivo v11 desde el enlace documentado en esta página cuando esté disponible.
3. Confirmar que la descarga terminó por completo antes de abrirla y disponer de al menos 500 MB libres durante la instalación.
4. Abrirlo desde **Archivos/Descargas**, aceptar la actualización y el aviso de fuente desconocida.
5. Si v11 también falla, no borrar datos: conectar el teléfono por USB y obtener el código exacto con `adb install -r archivo.apk`. El mensaje genérico de MIUI no permite distinguir falta de espacio, política del instalador o conflicto residual.
