# Prueba integral y APK para celular

Revisión iniciada el 21/10/2026, retomada el 22/10 y continuada por Claude Code entre el 23/10 y la madrugada del 24/10/2026. Rama: `rediseno-monokai`.

**Estado al 24/10, 11:45: entregado.** Se probaron en la interfaz los cuatro roles (administrador, cajero, cliente y repartidor), verificando cada paso contra el servidor de QA. Se corrigieron dos errores y pasaron el análisis y 268 pruebas. El APK final, recompilado con esas correcciones, quedó en `Escritorio\TechStore 1.0.23`. No se publicó para los clientes ni se probó en un teléfono físico; ver [Pendientes fuera de esta revisión](#pendientes-fuera-de-esta-revisión).

## Objetivo y entorno

El usuario pidió probar los flujos completos, incluyendo confirmar y cobrar pedidos desde administrador y cajero, corregir los errores encontrados y dejar el APK final instalable en una carpeta nueva del escritorio.

La revisión anterior se documentó en [13-correcciones-y-validacion.md](13-correcciones-y-validacion.md). Allí las comprobaciones visuales de las cuentas existentes no confirmaron pedidos ni cobraron ventas. Esta segunda revisión incorpora operaciones transaccionales en un entorno de QA separado.

- Backend de QA en `http://127.0.0.1:8091`, con PostgreSQL embebido independiente. Tanto el datasource como Flyway apuntan a esa instancia.
- Cuentas, productos, pedidos y documentos de prueba. Los identificadores de este informe pertenecen a esa base descartable, no a la base habitual del negocio.
- SMTP sin credenciales y apuntando a un puerto local sin servicio; proveedor push `none`; entrega de notificaciones externas desactivada. El outbox de sincronización permanece activo para probar eventos entre clientes.
- SIFEN en modo `TEST`, con certificado demostrativo. Los PDF/XML generados no equivalen a una validación fiscal ni a una transmisión a la DNIT.
- Uploads y directorio de actualizaciones propios de QA. La configuración está en `artifacts/qa-completa-20261021/QaBackend.java`.
- App de QA para el emulador con `API_BASE_URL=http://10.0.2.2:8091`. Este APK de prueba no es la entrega para un celular físico.

## Pruebas de API completadas el 21/10

`artifacts/qa-completa-20261021/api-tests-status.json` registra `state: success` para las cinco herramientas siguientes. Los logs contienen sus marcadores de éxito; los avisos de PowerShell sobre la salida informativa «Running build hooks» no corresponden a fallos de estas ejecuciones.

| Herramienta | Evidencia obtenida |
| --- | --- |
| `auth_e2e.dart` | Registro e ingreso de cliente, rotación del refresh y cierre de sesión. |
| `business_e2e.dart` | Catálogo, filtro por marca, favoritos, validación del carrito y alta/edición/baja de direcciones. |
| `realtime_e2e.dart` | Conexión STOMP, evento `PRODUCT_UPDATED` recibido por cliente y aislamiento de tenant. Una ejecución posterior correlaciona el evento con el producto modificado y espera el resultado de la mutación. |
| `integration_total_e2e.dart` | Doce áreas de negocio completadas, detalladas debajo. |
| `cross_client_sync_e2e.dart` | Creación desde un cliente y edición desde el otro, recarga del estado, recuperación de eventos, actualización de stock y aislamiento de tenant. |

Las doce áreas registradas por `INTEGRATION_TOTAL_E2E_OK domains=12` son:

1. Usuarios, permisos y ciclo de activación.
2. Empresa y puntos de expedición.
3. Productos y stock.
4. Proveedores, ofertas y comparación.
5. Tarifas de envío.
6. Direcciones, favoritos, carrito, envío y registro del dispositivo del cliente.
7. Mensajería e idempotencia.
8. Checkout idempotente y restitución del stock al cancelar.
9. Caja, venta directa, factura y reportes.
10. Compras, recepción, comprobante privado y pago al proveedor.
11. Dashboard, contabilidad, notificaciones, auditoría, sincronización y versión.
12. Cierre de caja y limpieza funcional del cliente.

Los nombres «web» y «mobile» de los clientes de estas herramientas identifican sesiones HTTP/STOMP independientes. Su éxito no sustituye un recorrido de las pantallas del navegador o de Android.

Logs: `auth_e2e.log`, `business_e2e.log`, `realtime_e2e.log`, `realtime-correlated-e2e.log`, `integration_total_e2e.log` y `cross_client_sync_e2e.log`, dentro de `artifacts/qa-completa-20261021/`.

## Recorrido transaccional realizado por Claude Code

Se contrastó el chat `a194ee6e-c670-40d9-b15f-3729a2dced9b` con las capturas y las lecturas del backend anteriores y posteriores. El recorrido corresponde a la noche del 21/10 y el inicio del 22/10, hora de Paraguay.

- Ingreso como cajero de QA y apertura de caja con 500.000 ₲.
- Confirmación del pedido 4: `P` (Pendiente) → `C` (Confirmado).
- Cobro del pedido 4 por 100.000 ₲ en efectivo: `C` → `G` (Pagado), con factura `001-001-0000002`.
- Venta directa de una unidad del producto 41 por 100.000 ₲: nuevo pedido 6 en estado `G`, con factura `001-001-0000003`.
- Cancelación del pedido 5: `P` → `X` (Cancelado).
- Recorrido del cierre de caja; el snapshot posterior registra la caja cerrada. La captura conservada muestra el diálogo de conteo, por lo que no prueba por sí sola el importe finalmente contado ni la diferencia del cierre.
- Ingreso como cliente de QA y comprobación visual de que el pedido 4 figura Pagado y el 5 Cancelado.

La comparación de `snapshot-antes-claude.json` y `snapshot-despues-claude.json` confirma:

| Dato | Antes | Después |
| --- | --- | --- |
| Pedido 4 | Pendiente, sin factura | Pagado, con factura |
| Pedido 5 | Pendiente | Cancelado |
| Pedido 6 | No existía | Venta directa pagada por 100.000 ₲ |
| Producto 41: stock real | 50 | 48 |
| Producto 41: stock comprometido | 2 | 0 |
| Producto 41: stock disponible | 48 | 48 |
| Ingresos acumulados de caja | 130.000 ₲ | 330.000 ₲ |
| Facturas | 1 | 3 |
| Notas de crédito | 0 | 0 |

El incremento de ingresos coincide con el cobro del pedido 4 y la venta directa 6. La cancelación liberó la reserva del pedido 5; las dos ventas consumieron dos unidades reales.

Capturas conservadas en `artifacts/qa-completa-20261021/claude/`: `01-cajero-inicio.png`, `02-cajero-pedidos.png`, `03-registrar-pago.png`, `04-cierre-caja.png` y `05-cliente-pedidos.png`. Estas capturas corresponden a la interfaz anterior a los últimos ajustes de presentación descritos abajo.

Los snapshots de Claude de esa noche no demuestran una nota de crédito ni una entrega de repartidor. La nota de crédito se cubrió en el recorrido del 24/10, y la entrega del repartidor en la verificación final de ese día.

## Recorrido de la interfaz del 24/10

Realizado por Claude Code entre las 00:13 y las 00:45 (hora de Paraguay) en el emulador descartable `TechStore_QA_20261022`. Se usó `emulator-5556`, con renderizado por software y las animaciones del sistema desactivadas. El backend de QA se levantó con una base nueva, cargada con `node api.cjs seed` (`seed-24.log`).

- **APK de QA:** se usó `TechStore-QA-1.0.23.apk`. El SHA-256 de lo instalado coincide con el del archivo (`0c96f6ef…29f2b6`), y sus datos se borraron antes de empezar. Su kernel contiene, bloque por bloque, el texto actual de los 16 archivos de `lib/` modificados en esta revisión (520 de 520 bloques), así que corresponde al código probado por las 267 pruebas.
- **Tipeo en el emulador:** `control.cjs` ahora exige `QA_SERIAL`, para no tocar `emulator-5554`, que usaba otra sesión. El tipeo se hace con `escribir.cjs`, en tramos de cinco caracteres. Con la PC sin RAM libre, un `input text` largo superaba el tiempo máximo y perdía caracteres. Además, Ctrl+A no selecciona en los campos de Flutter.

| Rol | Operación en la app | Lo que mostró la app | Verificación en el servidor |
| --- | --- | --- | --- |
| — | Primer inicio tras borrar datos | Pantalla de ingreso en ~42 s | — |
| Admin | Lista de pedidos | «Pedido N.º 1/2», total en ₲ y chip «Pendiente de confirmación» | — |
| Admin | Confirmar pedido 1 | Diálogo «Pedido N.º 1 por 100.000 ₲.»; luego «Confirmado» | `P` → `C` |
| Admin | Registrar pago del pedido 1 | Monto precargado «₲ 100.000»; medios: solo Efectivo y Transferencia; «Pagado» | `C` → `G`, factura `001-001-0000001` |
| Admin | Cancelar pedido 2 | Diálogo con «Los productos reservados vuelven a quedar disponibles.»; «Cancelado» (la fila tardó unos segundos en refrescar) | `P` → `X`; stock comprometido 2 → 0 |
| Admin | Nota de crédito del pedido 1 (producto 37, 1 unidad) | «Devuelto» | Estado `R`, nota `001-001-0000001`, stock real 49 → 50 |
| Cajero | Abrir caja con 500.000 ₲ | «Caja abierta. Empezaste con 500.000 ₲» | Caja 1 abierta |
| Cajero | Venta directa de 1 unidad, efectivo | Productos vaciados y una sola línea de pago vacía; **aviso «Venta #null registrada»** | Pedido 3 en `G`, segunda factura, stock 50 → 49 |
| Cajero | Cierre con 6 billetes de 100.000 | «Caja cerrada. Registramos lo que declaraste.» | Teórico 600.000 = 500.000 + 200.000 − 100.000; contado 600.000; diferencia 0 |
| Cliente | Mis pedidos | N.º 2 «Cancelado», N.º 1 «Devuelto» | — |
| Cliente | Compra con envío a domicilio («Entrega en Central», 35.000 ₲) y efectivo al recibir | Resumen 100.000 + 35.000 = 135.000 ₲; **aviso «¡Listo! Tu pedido quedó confirmado.»** sobre un detalle que dice «Pendiente de confirmación» | Pedido 4 en `P`, total 135.000, envío 35.000, `ENVIO_DOMICILIO`, `PAGO_EFECTIVO_REPARTIDOR`, 1 unidad reservada |

El administrador pudo registrar el pago sin una caja abierta; el backend lo imputó al cierre del cajero junto con la devolución. Se deja anotado como comportamiento observado, no como error.

Capturas en `artifacts/qa-final-20261022/`: de `c01-inicio-limpio.png` a `c36-detalle-pedido.png`. Snapshots: `snapshot-antes-ui-24.json`, `snapshot-tras-admin-24.json`, `snapshot-tras-nota-credito-24.json`, `snapshot-tras-venta-24.json` y `snapshot-tras-compra-cliente-24.json`.

### Errores encontrados y corregidos el 24/10

1. **«Venta #null registrada».** `POST /api/v1/ventas` responde un `PagoResponse` con `pedidoNumero` y `factura.numero`, pero la app leía `numero`. La prueba de widget pasaba porque simulaba una respuesta inventada `{'numero': 77}`. Ahora el aviso dice «Venta N.º 3 registrada · Factura 001-001-0000002», y «Venta registrada» si faltan los datos. Las simulaciones de `direct_sale_submission_test.dart` usan la forma real y hay un caso nuevo que comprueba que no aparezca «null».
2. **Aviso contradictorio al comprar.** El checkout anunciaba «¡Listo! Tu pedido quedó confirmado.» y abría el detalle con «Pendiente de confirmación — Te avisamos apenas lo confirmemos». Ahora dice «¡Listo! Recibimos tu pedido.»

Archivos: `lib/features/sales/presentation/direct_sale_page.dart`, `lib/features/checkout/presentation/checkout_page.dart` y `test/features/sales/direct_sale_submission_test.dart`. Quedaron validados por el análisis y la suite del 24/10 a las 10:40, y verificados en la interfaz en la [verificación final](#verificación-final-del-2410-10301145).

## Correcciones incorporadas durante esta revisión

- Los medios de pago ofrecidos en mostrador y en el diálogo administrativo se limitan a Efectivo y Transferencia, que son los habilitados por el backend.
- La venta directa bloquea nuevos envíos inmediatamente, incluso mientras se valida la identidad. Cancelar esa validación o recibir un error permite continuar usando el formulario.
- La lista de pedidos administrativos identifica cada fila como «Pedido N.º …» y muestra el estado traducido mediante el mismo `StatusChip` del cliente. El detalle expandido también utiliza la etiqueta en palabras.
- Confirmar y cancelar muestran el número y el importe del pedido. Cancelar explica que los productos reservados vuelven a quedar disponibles.
- Registrar el pago propone el total del pedido. La precarga respeta la coma decimal y evita que un valor como `100000.0` se interprete como un millón al eliminar el punto de agrupación.
- Después de una venta directa exitosa se vacían los productos y se reemplazan los pagos por una única línea vacía. Cada editor conserva la identidad de su línea para evitar que muestre el medio de una línea anterior.

Los cambios se encuentran principalmente en `ResourceDefinition`, `ResourcePage` y `DirectSalePage`. La presencia del código no se cuenta como validación visual del APK final.

## Estado de la verificación de Flutter

- `flutter-analyze.log` de la corrida del 21/10 terminó con **No issues found**. Esa corrida precede a los últimos ajustes de Claude.
- La suite de ese día terminó con **263 pruebas aprobadas y 1 fallo**, en el reintento de venta directa después de un error de red. No se presenta como suite aprobada.
- El 22/10, `direct-sale-retry-fixed.log` registra **3 pruebas focales aprobadas**: doble pulsación, cancelación de identidad y reintento después de error de red.
- La ejecución final del 22/10 en `artifacts/qa-final-20261022/` terminó con **267 pruebas aprobadas, sin fallos ni omisiones** (`flutter-test.log`, 2 min 57 s de ejecución de pruebas). Incluye las dos E2E optativas contra el nuevo PostgreSQL aislado: reconexión sin duplicación y política real de actualización (mínima 1, última 24, no obligatoria), además de dos casos nuevos de respuesta tardía después de salir de la venta directa. La tanda anterior de 265 pruebas se conserva en `265-flutter-test.log`.
- El análisis completo sobre ese código terminó con **No issues found** (`flutter-analyze.log`, 148,8 s). La primera ejecución del día incluía el tiempo con la PC suspendida y se conserva en `265-flutter-analyze.log`.
- El estado visible, el monto precargado y la limpieza de pagos tras una venta se comprobaron en la interfaz el 24/10 (sección anterior).
- El 24/10 a las 10:40 se repitieron el análisis y la suite completa con las dos correcciones (`test-mobile.ps1`, mismo entorno que el 22/10, con el backend de QA levantado para las dos E2E). **No issues found** (398,1 s) y **268 pruebas aprobadas, sin fallos ni omisiones**: las 267 anteriores más «una respuesta sin número no muestra "null" en el aviso». Los logs anteriores se conservan como `267-flutter-analyze.log` y `267-flutter-test.log`.

Las 379 pruebas unitarias y 95 de integración del backend documentadas en la revisión anterior son antecedentes. No se atribuyen a una nueva ejecución del 22/10 sin su registro correspondiente.

## APK para el celular

**Entregado el 24/10/2026 a las 11:44 en `C:\Users\Dell Vostro\OneDrive\Escritorio\TechStore 1.0.23`**, con `TechStore-1.0.23+24.apk`, `SHA256SUMS.txt` (verificado con `sha256sum -c`) y `LEEME.txt`.

Lo compiló Claude Code el 24/10 con `artifacts/qa-final-20261022/build-phone-final.ps1`, después de las dos correcciones y de la suite de 268 pruebas. Usa las mismas opciones que la 1.0.22 (`11-entrega.md`), más `x86_64` para poder instalar el mismo archivo en un emulador. Gradle tardó 627 s con el heap limitado a 1,5 GB.

La primera compilación, del 23/10, no tenía esas correcciones (SHA-256 `2c142a85…e2342`, 1209 s). Nunca se distribuyó y se conserva como `TechStore-1.0.23+24-sin-correcciones-20261023.apk`, con sus logs prefijados `20261023-`.

| Dato | Valor |
| --- | --- |
| Archivo | `TechStore-1.0.23+24.apk` |
| Versión | 1.0.23, código 24 |
| Flavor y paquete | `production`, `py.com.techstore.app` |
| Arquitecturas | `armeabi-v7a`, `arm64-v8a`, `x86_64` |
| Android | mínimo 7.0 (API 24), target API 36 |
| Backend | `https://closure-unrigged-postcard.ngrok-free.dev`, comprobado dentro de `lib/arm64-v8a/libapp.so`; no contiene `10.0.2.2`, `127.0.0.1:8091` ni la clave JWT de QA |
| Tamaño | 72.511.182 bytes (69,2 MB) |
| SHA-256 | `51ec443c722aa062831d4145544eaddd3fc3289ede9bc38490532faab84c0abb` |
| Textos corregidos | `libapp.so` contiene «Recibimos tu pedido», «Venta registrada» y `pedidoNumero`, y no contiene «quedó confirmado» |
| Firma | `CN=TechStore Private`, certificado SHA-256 `c4a098390fc9f299a68dd51b79741ad931485ea012828e613afc7f801aabf0a2`, el mismo de la 1.0.22: se instala encima sin desinstalar |
| Depuración | deshabilitada (`aapt2 dump badging` sin `application-debuggable`) |
| Seguridad | `ci-security-check.mjs --archive`: `ARCHIVE_SECURITY_OK entries=517`, `SECURITY_OK warnings=0` |
| Símbolos de ofuscación | `artifacts/mobile/symbols/1.0.23+24` |

Logs: `build-phone.log`, `phone-signature.log`, `phone-badging.log`, `phone-security.log` y `phone-build-status.json`.

**No se publicó.** `artifacts/mobile/release/release-metadata.json` conserva su SHA-256 (`04dfe733…63a41`): el backend sigue ofreciendo la 1.0.21 (código 22) y los teléfonos que ya tienen la app no ven esta versión. Publicarla es la decisión descrita en `11-entrega.md`.

El túnel público estaba apagado durante la compilación (ngrok devolvía su página de endpoint fuera de línea). No afecta al APK, pero para comprar desde un teléfono hay que levantar el servidor público.

Este mismo archivo se instaló en el emulador de QA (SHA-256 del instalado igual al del archivo). Abrió en modo release y mostró la pantalla de ingreso con el aviso «Sin conexión con el servidor», que es lo esperable con el túnel apagado. El registro no tiene errores fatales del proceso `py.com.techstore.app`; el único fatal fue de `uiautomator`, que no logró conectarse a tiempo. **No se instaló en un teléfono físico.**

## Verificación final del 24/10 (10:30–11:45)

Se usó una base de QA nueva (`seed-24b.log`) y el APK de QA recompilado con las correcciones (`TechStore-QA-1.0.23.apk`, SHA-256 `c2ceb3d6…84895`). Su kernel contiene el texto actual de `direct_sale_page.dart` y `checkout_page.dart`, y lo instalado coincide con el archivo. Las capturas son las que empiezan con `d` y `p` en `artifacts/qa-final-20261022/`; los snapshots son `snapshot-antes-ui-24b.json`, `snapshot-tras-reparto-24b.json` y `snapshot-final-24b.json`.

| Rol | Operación en la app | Lo que mostró la app | Verificación en el servidor |
| --- | --- | --- | --- |
| — | APK de producción | Pantalla de ingreso con «Sin conexión con el servidor» | — |
| Cliente | Compra con envío («Entrega en Central») y efectivo al recibir | **«¡Listo! Recibimos tu pedido.»** sobre «Pedido N.º 3 — Pendiente de confirmación» | Pedido 3 en `P`, 135.000, envío 35.000 |
| Admin | Confirmar pedido 3 | «Pedido N.º 3 por 135.000 ₲.» | `P` → `C` |
| Admin (API) | Asignar el pedido 3 al repartidor | La app móvil no tiene esta acción: se hace desde la web (`POST /api/v1/repartos/pedidos/{id}/asignar`) | Reparto 1 creado |
| Repartidor | Tablero, Mis entregas y detalle | 1 pendiente; «Pedido #3», 135.000 ₲, «Efectivo al recibir · Falta cobrar» | — |
| Repartidor | Aceptar, iniciar entrega y llegar al domicilio | «Aceptaste la entrega», «Entrega iniciada. Buen viaje.», «Llegada registrada» | — |
| Repartidor | Registrar cobro de 135.000 ₲ | Diálogo «Confirmá que recibiste 135.000 ₲ en efectivo»; «Cobro registrado» | `cobrado: true`; caja del repartidor 135.000 |
| Repartidor | Marcar como entregado | «Entregada — Esta entrega ya finalizó» | Reparto `ENTREGADO`; pedido 3 en `N` (Entregado) con factura |
| Repartidor | Vaciar caja para «Cajero QA» | «Solicitud enviada. El saldo sigue en tu caja hasta la aceptación.» | Transferencia `PENDIENTE_ACEPTACION` por 135.000 |
| Cajero (API) | Aceptar el vaciado | La app móvil no tiene esta pantalla: está en la web (`repartoService.js`) | `ACEPTADO`; caja del repartidor en 0 |
| Cajero | Abrir caja con 500.000 ₲ | «Caja abierta» | Caja abierta |
| Cajero | Venta directa de 1 unidad, efectivo | **«Venta N.º 4 registrada · Factura 001-001-0000002»**; productos y pagos en blanco | Pedido 4 en `G` con factura |
| Cajero | Cierre con 735.000 ₲ contados | «Caja cerrada. Registramos lo que declaraste.» | Teórico 735.000 = 500.000 + 235.000 (venta + vaciado); diferencia 0 |

Observaciones que no son errores de la app móvil: asignar repartidor y aceptar o rechazar el vaciado de caja solo existen en la web. En el emulador, un toque por coordenadas en un diálogo con el teclado abierto puede caer fuera de él y descartarlo; por eso los botones se tocan por su nombre.

## Pendientes fuera de esta revisión

- Instalar el APK en un teléfono físico (idealmente un Redmi con HyperOS) y hacer una compra real con el servidor público levantado.
- Decidir si se publica: `scripts/build-mobile-release.ps1`, descrito en `11-entrega.md`, reescribe `release-metadata.json`.
- Commitear los cambios de la rama `rediseno-monokai`, que siguen sin commit. No se hizo porque el usuario no lo pidió.

Los APK de QA (`TechStore-QA-1.0.23*.apk`) son artefactos en modo debug que se conectan al servidor aislado. No deben confundirse con el instalador final.

## Límites de las evidencias

Esta revisión comprueba los casos ejecutados; no garantiza ausencia de todo error posible. Quedan fuera de los resultados anteriores la entrega real de Firebase push, SMTP externo, transmisión y habilitación fiscal SIFEN, pasarela bancaria externa, equipos físicos y TalkBack. El registro de dispositivos, la generación local de documentos y los pagos de prueba no demuestran esas integraciones externas.

Las comprobaciones en emulador no equivalen a una instalación en un teléfono físico.

## Activación de Firebase del 24/10

Después de la entrega sin push descrita arriba se recompiló producción con Firebase. El archivo del escritorio ahora tiene SHA-256 `a447202807b16454b7ef0834e728f64b68d6b3f96d6b622b769123a77cbefda8` (72.511.182 bytes), conserva el paquete, la versión y el certificado anteriores. El instalador sin Firebase y sus instrucciones quedaron en `Escritorio\TechStore 1.0.23\Respaldo sin push`. No se modificó la metadata publicada.

La compilación terminó correctamente; el primer escaneo rechazó la API key pública de Firebase. Se corrigió el escáner para admitir solo la clave exacta de una configuración Android validada, sin exceptuar credenciales de servidor ni otras claves. Pasaron siete escenarios de regresión del escáner y el análisis del APK (`SECURITY_OK warnings=0`). El escaneo del repositorio pasó con su única advertencia SIFEN heredada. No se repitió la suite móvil de 268 pruebas porque estos ajustes afectan a los scripts de empaquetado, no al código Flutter.

La app de pruebas `py.com.techstore.app.dev` fue registrada con autorización del usuario. Compiló correctamente en 789,6 s. Contra PostgreSQL descartable y el backend QA con FCM en 8091 se verificó:

- Cliente QA autenticado; `GET /api/v1/dispositivos` pasó de vacío a una instalación Android activa.
- Después de ir al inicio se cerró el proceso de la app sin `force-stop`; `pidof` confirmó que no seguía ejecutándose.
- Confirmar el pedido 1 por API (`PUT /api/v1/pedidos/1/confirmar`, estado `C`) generó avisos reales en Android: «Pedido confirmado» y «Estamos preparando tu pedido».
- Tocar el aviso abrió **Detalle del pedido → Pedido N.º 1 → Confirmado**, con una unidad de QA Producto Movil y total de 100.000 ₲.
- El APK exacto de producción con Firebase también se instaló y abrió la pantalla de ingreso. El aviso de servidor sin conexión corresponde al túnel público apagado; no se inició sesión contra el negocio.

Evidencias en `artifacts/qa-push-20261024`: `push-notification.png`, `push-opened-order.png`, `production-firebase-start.png` y `push-validation.json`. La primera parte de la prueba estuvo afectada por poca memoria y avisos ANR de System UI, Mensajes y la app debug; se completó con el emulador de QA, renderizado por software y 2 GB asignados. Google Play Services estaba deshabilitado y se habilitó para que FCM pudiera registrar el token. Esto no reemplaza la prueba en un teléfono físico ni comprueba el servidor público.
