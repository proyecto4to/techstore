# Correcciones y validación después del recorrido con Claude Code

Revisión: 2026-10-20; cierre de compilación y verificación: 2026-10-21. Rama: `rediseno-monokai`.

## Qué se corrigió

- El detalle de un pedido abierto sin historial ofrece una flecha y vuelve al inicio con el botón del sistema. Si se abrió desde otra pantalla, conserva el regreso normal.
- Los estados vacíos de pedidos, favoritos, carrito y checkout abren el catálogo conservando la pantalla anterior. Si el stock se agota al confirmar, se vuelve al carrito existente.
- Las páginas del PDF se dibujan sobre papel blanco para que el texto negro siga siendo legible con el tema oscuro.
- Los controles de Material y Cupertino usan español de Paraguay; el botón Atrás y los textos de accesibilidad dejan de aparecer en inglés.
- Un acceso rechazado por credenciales incorrectas explica que hay que revisar correo o contraseña, en lugar de anunciar una sesión inválida.
- El checkout conserva el tipo de tarifa que entrega el servidor y excluye `RETIRO_TIENDA` de las opciones de envío a domicilio. También descarta respuestas de una dirección que ya no está seleccionada.
- El pie del checkout usa todo el ancho y apila sus botones en pantallas angostas o con texto ampliado. El resumen y los importes pueden ocupar varias líneas sin desbordar.
- La confirmación de compra tiene una guarda inmediata contra dos toques anteriores al siguiente dibujo del botón deshabilitado.
- Los pedidos tienen separación entre tarjetas; los estados positivos usan el color de éxito y las advertencias su color correspondiente.
- Los paneles de error y estados vacíos dejan pasar el arrastre a la lista exterior cuando se insertan dentro de ella. Esto permite llegar a las acciones del mostrador aun si falla la búsqueda de productos.
- El acceso «Pedidos» del inicio de cajero y administrador abre `/resources/all-orders`, donde se puede confirmar y registrar pagos. Antes abría `/orders`, la pantalla de seguimiento del cliente.
- Los resúmenes de las listas administrativas usan las etiquetas y formatos de la ficha: fechas legibles, importes con su moneda y booleanos en español. Las tarjetas tienen 10 px de separación.

## Pruebas añadidas

`test/app/recorrido_regresion_test.dart` recorre pantallas reales con GoRouter, repositorios con respuestas HTTP controladas y una base local en memoria. Comprueba regreso desde estados vacíos y pedidos, perfil, direcciones, permisos visibles de administración, caja abierta/cerrada, entrada al mostrador, confirmación de compra, doble toque y agotamiento del stock. Las comprobaciones de diseño cargan las fuentes reales y cubren 411 dp y 320 dp con texto al 130 %.

También se ampliaron las pruebas de la aplicación completa para el idioma, del controlador de sesión para credenciales incorrectas y del visor para el fondo de papel.

`test/features/administration/resource_summary_test.dart` cubre fechas, importes en PYG y USD y separación entre tarjetas, reutilizando el formato de las fichas.

- **Análisis de Flutter: sin errores, advertencias ni observaciones.**
- **40 pruebas focales finales: aprobadas**, de recorridos, desplazamiento y formato de fichas y resúmenes (`revision-codex-mobile-final-20261020.log`).
- **Suite completa de la app antes del último ajuste de formato: 258 aprobadas y 2 E2E optativas omitidas**; sin fallos. El ajuste y su nueva prueba quedaron cubiertos por la tanda focal final. Se mantuvo desactivada la prueba que registra una cuenta contra el backend real.

## Servidor y cuentas existentes

- **379 pruebas unitarias del backend: aprobadas.**
- **95 pruebas de integración: aprobadas**, contra PostgreSQL 15 embebido y separado de la base del negocio. Incluyen venta completa, mostrador, comprobantes, permisos, caja, reparto y concurrencia de stock.
- Ingreso real correcto con los tres roles facilitados en la conversación anterior. Se consultaron direcciones, pedidos, facturas, favoritos, conversaciones y notificaciones del cliente; pedidos, apertura/movimientos de caja, facturas y conversaciones del cajero; dashboard, usuarios, productos, pedidos, resumen de caja, auditoría, conversaciones y promociones del administrador.
- El acceso a `/usuarios` devolvió **403** para cliente y cajero, como corresponde. Las sesiones creadas para estas comprobaciones se cerraron al finalizar.
- Las operaciones de venta y caja de esta revisión se ejecutaron en la base de integración. No se procesó el pedido real 78 ni se restauró el respaldo de Claude.

## Alcance de esta entrega

Se recorrió la app instalada en el emulador Android 15 con los tres roles:

- Cliente: inicio, Mi cuenta, Direcciones y facturas. La factura se abrió en el visor interno, se amplió al 160 % y conservó el papel blanco y el texto legible. El regreso entre pantallas funcionó.
- Cajero: inicio, gestión de pedidos y su menú de acciones, caja abierta y venta directa. La búsqueda de «Mochila» cargó el producto y permitió añadirlo al borrador de venta; se salió sin confirmar.
- Administrador: inicio, Administración, Usuarios, Productos y el estado vacío de Promociones.

El 21/10 se reinstaló el APK final conservando los datos del emulador. Desde el inicio del administrador, Pedidos mostró «Fecha: 19 oct 2026 22:32 · Total: 140.000 ₲», con separación visible entre tarjetas y el menú de confirmar y registrar pago disponible. El regreso al inicio y el cierre de sesión funcionaron. La captura final es `artifacts/qa-android-20261020/admin-pedidos-final-20261021.png`.

Estas comprobaciones visuales no confirmaron pedidos, ventas ni pagos, no cerraron caja y no modificaron perfiles ni direcciones. Las capturas están en `artifacts/qa-android-20261020/`. No se registraron errores de Flutter o AndroidRuntime del proceso de la app durante el recorrido.

APK de desarrollo para el emulador x86_64, compilado en modo release el 21/10: `artifacts/qa-android-20261020/TechStore-Dev-1.0.22-correcciones.apk` (1.0.22-dev, código 23, 30.059.806 bytes). SHA-256: `6985d268c09961e98ad385604d8fb12d45245b6af788a56329f5c7e0ac39817e`.

Los cambios permanecen disponibles para revisión, sin commit ni publicación de una actualización. Esta revisión no valida equipos físicos, TalkBack, Firebase push ni una pasarela externa de tarjetas. Los resultados anteriores del APK 1.0.22 del escritorio no sustituyen la validación de estos cambios.

Los registros de las comprobaciones están en `artifacts/revision-codex-*-20261020.log`; la compilación final está en `artifacts/revision-codex-build-final-20261021.log`.
