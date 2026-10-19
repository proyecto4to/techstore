# Fase 7 — Funcionalidades de e-commerce

Fecha: 2026-10-19. Rama: `rediseno-monokai`.

Esta fase comprueba que cada función de compra esté completa, con datos reales y sin acciones de adorno, y deja escrito qué depende de terceros. Las pruebas en el emulador se hicieron contra el backend local con la base real del negocio (36 productos activos, 23 marcas), comparando cada resultado con la respuesta de la API.

## Recorrido en el emulador (sin sesión)

| Paso | Resultado en la app | Comparación con la API |
|---|---|---|
| Abrir la app sin sesión | Pantalla de ingreso con «Ver la tienda sin iniciar sesión» | — |
| Ver la tienda | Catálogo con colecciones por marca, orden y búsqueda | — |
| Colección Corsair | Corsair CV550 (480.000 ₲, 17 en stock) y Corsair RM850x (950.000 ₲, 8 en stock) | Igual: los mismos dos productos |
| Buscar «logitech» | Logitech C920 HD Pro, G502 HERO y G Pro Teclado | Igual: `totalElements` 3 |
| Ordenar por menor precio | 350.000 → 620.000 → 780.000 | Igual a `sort=precio,asc` |
| Ficha del RM850x | Precio, stock, «Fuente 850W 80+ Gold», código #0005, «Hasta 8 unidades» | Igual |
| Cantidad 2 y «Agregar al carrito» | Aviso con «Ver carrito»; el contador del carrito pasa a 2 | — |
| Carrito | 2 × 950.000 = 1.900.000 ₲ | Precio revalidado con `POST /carrito/validar` |
| «Continuar con la compra» sin sesión | Pide ingresar y vuelve al checkout después | — |
| Favorito sin sesión | «Ingresá para guardar favoritos», con «Ingresar» | — |
| Backend apagado | Catálogo desde lo guardado y el aviso «Sin conexión · se muestran datos guardados» | — |

## Defectos encontrados y corregidos en el recorrido

1. **Volver atrás desde el ingreso cerraba la app.** El carrito, el registro y la recuperación de contraseña abrían su pantalla reemplazando toda la pila de navegación: no había flecha para volver y el «atrás» del teléfono sacaba a la persona de la app, con su carrito a medio armar. Ahora se abren encima de la pantalla anterior; «atrás» vuelve a ella. Prueba: `test/app/techstore_app_test.dart`.
2. **Franja negra debajo del aviso de «Sin conexión».** El aviso ya se corría debajo de la barra de estado y la pantalla de abajo volvía a dejar ese lugar. Prueba: `test/app/connectivity_banner_test.dart`.
3. **La foto de la ficha quedaba debajo del reloj y de los botones.** La barra flota sobre la escena y el producto se ubicaba desde el borde de la pantalla. Ahora el producto se ubica debajo de la barra y la escena crece lo mismo. El banco de capturas no lo podía ver: ahí no hay barra de estado.
4. **Cada foto se volvía a bajar entera cada vez que se mostraba** (ver la fase 9): el servidor manda `Cache-Control: no-cache` y la caché de la app lo obedecía.

## Matriz de funciones

Estados: **verificada** (en el emulador con datos reales, en esta fase), **cubierta por pruebas** (automatizadas, sin recorrido manual en esta fase), **pendiente de validación** y **bloqueada por dependencia externa**.

| Función | Estado | Evidencia o motivo |
|---|---|---|
| Catálogo, colecciones por marca, búsqueda | Verificada | Recorrido de arriba |
| Búsqueda por voz | Cubierta por pruebas | `voice_dictation_button_test` |
| Orden (más nuevos, A–Z, precio) | Verificada | Antes la app no lo ofrecía; ordena por precio de lista, no por el que tiene la promoción aplicada (lo resuelve el backend) |
| Ficha: precio, stock, tope por pedido, relacionados, compartir, zoom | Verificada | Compartir y zoom son nuevos de este rediseño |
| Disponibilidad y escasez | Verificada | «Queda 1 unidad» solo con 5 o menos; el resto, «N en stock» |
| Carrito persistente con revalidación | Verificada | drift + `POST /carrito/validar` |
| Favoritos | Verificada sin sesión; cubierta por pruebas con sesión | La falla de red ya no se pierde: se revierte y avisa (`favoritos_test`) |
| Registro, ingreso, recuperación | Verificada la navegación; cubierta por pruebas | No se envió ningún formulario: crear cuentas en la base real requiere autorización |
| Checkout (entrega con mapa, pago, confirmación) | Cubierta por pruebas; **pendiente de validación** en el emulador | Requiere una cuenta autorizada |
| Pedidos, comprobantes de pago, facturas y PDF | Cubierta por pruebas; **pendiente de validación** | Ídem |
| Notificaciones en la app y Atención al Cliente | Cubierta por pruebas; **pendiente de validación** | Ídem |
| Sin conexión y reconexión | Verificada la lectura sin conexión; la cola, cubierta por pruebas | `offline_queue_service_test`; la prueba E2E de reconexión registra una cuenta en el backend y no se corrió sin autorización |
| Promociones | Cubierta por pruebas | No hay ninguna activa en la base real; la app no inventa descuentos |
| Pago con tarjeta | **Bloqueada por dependencia externa** | Requiere contratar una pasarela (por ejemplo Bancard o Pagopar en Paraguay) y sus credenciales |
| Notificaciones push | **Bloqueada por dependencia externa** | Falta registrar la app en la consola de Firebase; el código está listo (`docs/mobile-expansion/PUSH.md`) |
| Categorías, variantes, galería | No aplica | El modelo de datos no los tiene; no se inventan |

## Dependencias externas

| Dependencia | Para qué | Qué hace falta |
|---|---|---|
| Firebase Cloud Messaging | Push con la app cerrada | Proyecto de Firebase, `google-services` y las claves en `scripts/firebase-mobile-defines.ps1` |
| Pasarela de pago con tarjeta | Cobrar con tarjeta | Contrato con un proveedor, credenciales y su integración en el backend |
| Servidor SMTP | Correos de recuperación y avisos | Ya configurable desde la app (Administración → SMTP) |
| Túnel o dominio público | Que la app llegue al backend fuera de la red local | Hoy, ngrok con dominio fijo |
| OpenStreetMap y Nominatim | Mapa y búsqueda de calles | Uso gratuito con sus políticas; para mucho volumen, un servidor propio de tiles |
