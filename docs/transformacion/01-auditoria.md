# Fase 1 — Auditoría de la app Android de TechStore

Fecha: 2026-10-18. Rama: `rediseno-monokai`, sobre `9b39b14` (`expansion-mobile-flutter`).

Este documento fija el punto de partida antes de tocar nada estructural: qué hay, qué funciona, qué falta y en qué orden conviene transformarlo. Lo que se afirma acá se comprobó en el código, contra el backend local con los datos reales o corriendo las pruebas. Donde no se pudo comprobar, se dice.

## 1. Línea de base

| Verificación | Resultado |
|---|---|
| `flutter analyze` | Sin observaciones |
| `flutter test` | 187 pasan, 2 salteados (46 archivos de prueba) |
| Backend (última corrida registrada, 2026-09-22) | 379 unitarias + 22 de integración contra PostgreSQL real |
| Web (misma fecha) | 54 pruebas, lint limpio |
| Backend local | Levanta con `iniciar-techstore.ps1 -SinNavegador`; responde catálogo y marcas |

## 2. Tecnología y arquitectura

**App móvil** (`techstore-mobile`): Flutter 3.47.1 / Dart 3.13.1, Material 3. Estado con Riverpod 3, navegación con go_router 17 (redirecciones por sesión, rol y permiso), HTTP con dio, caché y cola sin conexión con drift (SQLite), secretos en `flutter_secure_storage`, tiempo real por STOMP/WebSocket, mapas con flutter_map + OpenStreetMap, push con Firebase Messaging (código listo, falta registrar la app en la consola).

Cuatro sabores: `development`, `private`, `staging`, `production` (`py.com.techstore.app`). Android mínimo 7.0 (SDK 24), objetivo SDK 36. La versión real va por línea de comandos (`--build-name`/`--build-number`); el `pubspec.yaml` queda en `1.0.0+1` a propósito.

**Backend** (`techstore-backend`): Spring Boot sobre PostgreSQL con Flyway (V1 a V57), multiempresa por `tenant_id`, permisos por rol y por persona aplicados por un interceptor antes de cada controlador, facturación con timbrado y KuDE en PDF.

**Web** (`techstore-frontend`): React + Vite + Tailwind; comparte el isotipo con el PDF.

Organización de la app: `lib/app` (arranque, router, tema, coordinadores), `lib/core` (red, auth, caché, formato, widgets compartidos, servicios nativos) y `lib/features/<módulo>/{data,domain,application,presentation}`. Son 126 archivos Dart y unas 24.000 líneas sin contar lo generado.

## 3. Datos reales del negocio

Medido contra el backend local el 2026-10-18:

- **36 productos activos de 23 marcas**: procesadores, placas de video, memorias, discos, monitores, periféricos, audio, redes, impresión, energía, sillas y accesorios.
- **No hay categorías.** El modelo tiene `Marca` y `Modelo` (hoy ningún producto tiene modelo cargado). El rediseño no puede inventar categorías: las «colecciones» son las marcas, que es la agrupación que el negocio sí tiene.
- **Una sola imagen por producto** (`imagenUrl`). No hay galería ni variantes de color o talle en el modelo de datos.
- **Fotos de calidad dispar**: fotos de estudio con fondo blanco (monitor, UPS), escenas con colores fuertes (la RTX dentro de un gabinete), fondos grises (la caja del mouse). Varias son genéricas y se comparten: `mouse.jpg` la usan tres productos y `teclado.jpg` otros tres.
- **Ninguna promoción activa** en este momento (el módulo existe desde la V57).
- **Pagos**: en el local, en efectivo contra entrega y por transferencia con comprobante. **No hay pasarela de tarjeta**: requeriría un proveedor y credenciales que el proyecto no tiene.

## 4. Pantallas y rutas

36 rutas en `app_router.dart`. Las públicas (sin sesión) son ingreso, registro, recuperación, catálogo, ficha de producto y carrito.

| Rol | Inicio | Qué usa |
|---|---|---|
| Cliente | Grilla de módulos «Comprar» + «Tu cuenta» | Catálogo, ficha, carrito, checkout en pasos, direcciones con mapa, pedidos y su detalle, comprobantes de pago, facturas y PDF, favoritos, notificaciones, Atención al Cliente, Mi cuenta |
| Cajero | Grilla «En el mostrador» | Venta directa, caja, pedidos, facturas, buscar producto, operaciones |
| Administración | Grilla «Gestión» | Hub de administración (recursos genéricos), seguridad y permisos, promociones, SMTP, ubicación, auditoría, documentos, compras |
| Repartidor | Panel propio | Entregas y su detalle, ruta en el mapa, caja del repartidor, vehículos, chat con administración |

## 5. Inventario de controles

Se extrajeron del código **320 controles interactivos en 45 archivos** (botones, filas tocables, campos, menús, interruptores, gestos de arrastre para refrescar). La tabla completa está en [inventario-de-controles.md](inventario-de-controles.md), y el script que la genera, en `herramientas/`.

Revisión de los 47 que no tienen manejador directo: son filas informativas sin `onTap` que no se presentan como tocables, campos que trabajan con su controlador y un ítem de menú cuya acción vive en el menú padre. **No se encontró ningún botón decorativo ni ninguna acción falsa.** Tampoco hay `TODO`, `UnimplementedError` ni textos de «próximamente».

Que el código no tenga botones vacíos no prueba que cada flujo funcione de punta a punta. Eso lo cubren las pruebas de la sección 1 y las pasadas en teléfono registradas hasta septiembre, y se vuelve a comprobar en la fase 10.

## 6. Matriz de trazabilidad

Estados: **operativa** (comprobada), **parcial**, **defectuosa**, **inexistente requerida**, **pendiente de validación**.

| Funcionalidad | Estado | Evidencia o motivo |
|---|---|---|
| Catálogo paginado con búsqueda y filtro por marca | Operativa | `CatalogoPaginado`; recorrido real hasta el producto 37 |
| Búsqueda por voz | Operativa | `voice_dictation_button_test` |
| Ordenamiento del catálogo | Parcial | El backend ya ordena (`sort=precio,asc`, `precio,desc`, `id,desc` probados contra los datos reales), pero la app no lo ofrece. Ordena por el precio de lista, no por el que tiene la promoción aplicada |
| Categorías y subcategorías | No aplica | El modelo no tiene categorías; las colecciones son marcas |
| Variantes (color, talle) | No aplica | El modelo no tiene variantes; no se inventan |
| Galería de fotos | No aplica | Una imagen por producto; sí se agrega zoom |
| Precio con promoción, vencimiento | Operativa | `producto_con_promocion_test`; backend aplica el descuento |
| Disponibilidad y tope por pedido | Operativa | `maxPorPedido` techado; carrito revalida al abrir |
| Carrito persistente | Operativa | drift + `cart_controller` |
| Favoritos | **Defectuosa (parcial)** | Si falla la red al guardar o quitar, el error se pierde sin aviso |
| Compartir producto | Inexistente requerida | No hay botón de compartir en la ficha |
| Registro, ingreso, recuperación | Operativa | Pruebas de auth; enlace profundo `/password/reset` |
| Perfil y biometría | Operativa | `biometric_gate_test`, `operation_confirm` |
| Direcciones con mapa | Operativa | `direccion_en_el_mapa_test`; Nominatim y GPS probados en el Moto G42 |
| Checkout (entrega, pago, confirmación) | Operativa | Pruebas de checkout; flujo de cliente probado de punta a punta en el teléfono |
| Pago con tarjeta | Bloqueada por dependencia externa | Sin proveedor ni credenciales |
| Pedidos, detalle, cancelación, comprobante | Operativa | `payment_receipts`, pruebas de órdenes |
| Facturas y PDF | Operativa | Visor probado en el teléfono |
| Notificaciones en la app | Operativa | `campana_pendientes_test`, tiempo real |
| Notificaciones push | Bloqueada por dependencia externa | Falta registrar la app en la consola de Firebase (`docs/mobile-expansion/PUSH.md`) |
| Atención al Cliente (chat) | Operativa | Pruebas de chat; recorrido real |
| Sin conexión y reconexión | Operativa | `offline_reconnect_e2e_test`, `session_sin_conexion_test` |
| Actualización dentro de la app | Operativa | `update_policy_e2e_test` |
| Módulos de cajero, administración y repartidor | Operativa | Pruebas de cada módulo; algunos flujos con dinero solo por integración (ver pendientes de septiembre) |
| Identidad de marca | Inexistente requerida | Ícono hecho con IA (T, bolsa y circuitos), seis propuestas de julio sin elegir, la web con otro isotipo («Rayo») |
| Diseño visual propio | Inexistente requerida | Tema estándar de Material derivado de un azul, claro u oscuro según el sistema |
| Vidriera comercial (inicio de cliente) | Inexistente requerida | El inicio del cliente es una grilla de módulos igual a la del personal |
| Animaciones de navegación y comerciales | Inexistente requerida | Solo dos microanimaciones (tilde del carrito, contador) |
| Caché de imágenes en disco | Inexistente requerida | `Image.network` sin disco: cada apertura vuelve a bajar todo por el túnel |
| Lector de pantalla real y tablets | Pendiente de validación | Sin recorrido con TalkBack ni prueba en tablet (pendiente desde septiembre) |

## 7. Deuda técnica y defectos encontrados

1. **Favoritos sin manejo de error** (lista y ficha): una falla de red al guardar o quitar dispara una excepción no atendida y la persona no se entera.
2. **Carga de imágenes copiada en tres lugares** (catálogo, carrito, favoritos), con un relleno gris claro `#ECEFF1` que en tema oscuro quedaría como un parche blanco.
3. **Sin caché de imágenes en disco**: con el backend detrás de un túnel, es el mayor consumo de datos y de tiempo de la app.
4. **Ventana de Android blanca en modo claro**: detrás de Flutter quedaba `?android:colorBackground`, con destellos blancos al abrir la app o el teclado. El recortador de fotos (uCrop) usaba un tema claro.
5. **El inicio del cliente no es una tienda**: el comprador ve la misma grilla de baldosas que el personal.
6. **24 colores escritos a mano en 8 archivos**: son pocos, porque el resto usa el tema, y eso hace viable el rediseño sin reescribir las pantallas de trabajo.
7. **Montar la app con sesión en un test deja temporizadores andando** (anotado en septiembre): obliga a probar la navegación autenticada leyendo fuentes o con pantallas sueltas.

## 8. Plan de transformación

Orden por dependencias. Cada fase termina en un commit propio en `rediseno-monokai`.

| Fase | Trabajo | Depende de | Riesgo principal |
|---|---|---|---|
| 2 | Marca: isotipo, logotipo, ícono adaptativo, splash, web y PDF; manual de marca | — | Que el ícono se recorte en alguna máscara: se diseña dentro del círculo seguro de 66 dp |
| 3 | Sistema de diseño: tokens, tipografías, tema oscuro único, componentes base; migración de los 24 colores fijos | 2 | Pantallas de trabajo con poco contraste: se valida contraste AA en cada par de colores |
| 4–5 | Vidriera de cliente, catálogo editorial, ficha inmersiva, motor de presentación que lee cada foto (fondo, transparencia, orientación) | 3 | Fotos dispares: el motor se decide por lo que la foto es, no por una categoría que no existe |
| 6 | Movimiento: transiciones, elemento compartido catálogo→ficha, vuelo al carrito, microinteracciones; respeto de «quitar animaciones» del sistema | 3–5 | Que `pumpAndSettle` no termine o queden temporizadores: nada de animaciones infinitas |
| 7 | Defectos funcionales (favoritos), ordenamiento en la app, compartir producto, zoom de foto | 4 | Ninguno estructural |
| 8–9 | Compatibilidad (recortes, gestos, teclado, densidad 500 dpi del usuario), caché de imágenes, revisión de seguridad | 3–7 | Xiaomi/HyperOS sin equipo para probar: se documenta qué se validó y qué no |
| 10–11 | QA, compilación 1.0.22+23 firmada con la clave existente, APK y AAB, documentación | todo | No publicar la versión dentro de la app sin autorización |

**Decisión estructural:** la navegación sigue siendo por pantallas apiladas, sin barra inferior. Pasar a una barra inferior obligaría a rearmar el router con `StatefulShellRoute`, y eso toca las redirecciones por rol y permiso, los enlaces profundos y el comportamiento del botón atrás, que hoy sale a Android y no a una pantalla autenticada. El inicio nuevo del cliente da acceso directo a todo lo que daba la grilla, sin ese riesgo.
