# Arquitectura de TechStore Mobile

## Propósito y límite actual

`techstore-mobile` es el cliente universal de TechStore para Android, iOS y web. Hasta la Fase 7 incorpora autenticación segura, catálogo, búsqueda, favoritos, carrito revalidado, libreta de direcciones, checkout idempotente, cotización, tracking e historial real de pedidos.

La arquitectura busca que las rutas sean delgadas, que cada dominio mantenga sus pantallas en una feature y que el sistema visual sea independiente de los flujos de negocio.

## Plataforma

- Expo SDK `~57.0.12` y Expo Router `~57.0.12`.
- React Native `0.86.2`, React/React DOM `19.2.3` y React Native Web `~0.21.0`.
- TypeScript `~6.0.3` en modo estricto.
- Rutas tipadas y React Compiler habilitados en `app.config.ts`.
- Identificador Android `com.techstore.mobile`, bundle iOS `com.techstore.mobile` y esquema profundo `techstore://`.
- Export web estático mediante `web.output = "static"`.

La aplicación usa únicamente las primitivas de navegación expuestas por Expo Router. No importa paquetes externos de `@react-navigation/*` desde el código de la aplicación.

## Capas y estructura

```text
techstore-mobile/
├── app.config.ts          Configuración Expo por ambiente
├── eas.json               Perfiles development, preview y production
├── src/
│   ├── app/               Rutas y layouts de Expo Router
│   ├── features/          Pantallas agrupadas por dominio
│   ├── components/
│   │   ├── common/        Contenedores y placeholders compartidos
│   │   └── ui/            Sistema de componentes TechStore
│   ├── providers/         Providers de alcance global
│   ├── api/               Cliente HTTP, errores, contrato generado y bóveda de token
│   ├── config/            Variables de ambiente validadas
│   ├── localization/      Locale, zona horaria, formatos y copy extensible
│   ├── store/             Sesión, carrito mínimo e intento de checkout
│   └── theme/             Tokens, temas y contexto visual
├── tests/                 Pruebas unitarias de tema y componentes
└── assets/                Icono y recursos gráficos
```

Reglas de dependencia previstas:

1. `src/app` declara navegación y delega la representación a `features`.
2. Las features consumen `components`, `theme` y servicios/estado de su dominio.
3. Los componentes UI no dependen de una feature ni del backend.
4. Los datos recibidos del servidor deberán pasar por una capa de contrato/validación antes de llegar a una pantalla.

## Árbol de providers

```text
GestureHandlerRootView
└── SafeAreaProvider
    └── AppThemeProvider
        └── QueryClientProvider
            ├── AuthBootstrap
            └── Expo Router
```

El `QueryClient` define reintentos, `staleTime`, `gcTime` y el comportamiento de mutaciones. TanStack Query es dueño del estado remoto y no reintenta mutaciones. Zustand conserva sesión, carrito mínimo e intento idempotente de checkout. `AuthBootstrap` restaura el refresh token y mantiene el splash hasta resolver la sesión.

## Navegación

El stack raíz contiene:

- `/` redirige al grupo principal.
- `(tabs)` contiene `Inicio`, `Buscar`, `Carrito`, `Pedidos` y `Cuenta`.
- `(auth)` contiene el shell de login y placeholders para registro/recuperación.
- `/product/[id]` muestra el detalle público del producto.
- `/addresses`, `/addresses/new` y `/addresses/[id]` gestionan la libreta propia.
- `/checkout/address`, `/shipping`, `/payment`, `/review` y `/success` implementan el checkout ordenado.
- `/tracking/[pedidoId]` consulta el envío propio y representa sus eventos.
- `/orders/[pedidoId]` presenta el detalle comercial y enlaza su tracking.

Los nombres entre paréntesis son grupos organizativos de Expo Router y no forman parte de la URL pública.

## Sistema visual TechStore

`src/theme/` concentra la paleta oficial del frontend web, tema oscuro único, espaciado, radios, Inter 400/500/600/700, tamaños y grosor de iconos, capas, movimiento, breakpoints, sombras y dimensiones de layout. El área táctil mínima declarada es de 48 puntos.

`AppThemeProvider` aplica siempre dark mode y adapta el tema de navegación. `TechStoreBackground` reproduce el gradiente y los halos del frontend; `TechStoreBrand` reutiliza la geometría del isotipo oficial. La iconografía funcional usa Lucide mediante `AppIcon` e imports individuales.

Los componentes exportados por `src/components/ui` incluyen:

- Texto, botones e inputs: `AppText`, `Button`, `LoadingButton`, `IconButton`, `Input`, `PasswordInput` y `SearchInput`.
- Superficies y datos: `Card`, `Divider`, `Avatar`, `SectionHeader`, `Price`, `Badge`, `Chip`, `UnreadBadge`, `ProductCard` y `ProductHorizontalCard`.
- Feedback: `Skeleton`, `EmptyState`, `ErrorState`, `OfflineBanner`, `AppModal`, `BottomSheet` y `Toast`.
- Comercio: `QuantitySelector`, `OrderStatusBadge`, `ShippingTimeline` y `ChatBubble`.

`BottomSheet` es actualmente un alias visual de `AppModal`, no una hoja nativa completa.

## Configuración nativa y distribución

`app.config.ts` configura icono, splash, tema oscuro, esquema profundo y plugins para Router, SecureStore, notificaciones, autenticación local y splash. Tener el plugin o paquete instalado no significa que su flujo funcional esté terminado.

`eas.json` define:

- `development`: development client con distribución interna.
- `preview`: APK Android para distribución interna.
- `production`: Android App Bundle con incremento automático.

No se considera completada una publicación, firma de producción ni entrega a stores en Fase 1.

## Variables de entorno

`app.config.ts` expone actualmente:

- `EXPO_PUBLIC_APP_ENV` como `extra.environment`, con fallback `development` solo cuando la variable no existe.
- `EXPO_PUBLIC_API_URL` como `extra.apiUrl`, con fallback vacío.

`.env.example` también declara `EXPO_PUBLIC_TENANT_SLUG`, reservado para una fase de integración; todavía no se consume. Todas las variables con prefijo `EXPO_PUBLIC_` son información pública del bundle y no pueden contener secretos.

## API y sesión

`docs/api/mobile-auth.openapi.yaml` 1.5 define autenticación, catálogo, favoritos, carrito, direcciones, checkout, cotización, tracking e historial de pedidos; `npm run api:generate` produce tipos en `src/api/generated`. Las respuestas se validan además en runtime con Zod estricto, que rechaza si un DTO público incluye campos internos.

Hay dos instancias Axios: `apiClient` adjunta el access token y reintenta una vez tras un 401; `sessionClient` renueva o revoca sin atravesar el interceptor. Las renovaciones concurrentes comparten una única promesa. El timeout es de 15 segundos y los errores se normalizan en `ApiError`.

El access token vive exclusivamente en memoria. El refresh token usa Expo SecureStore con `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`; en web existe solo en memoria durante la pestaña y nunca se escribe en localStorage, sessionStorage ni AsyncStorage. Logout intenta revocar en servidor y siempre limpia el dispositivo.

## Catálogo y favoritos

TanStack Query mantiene productos, marcas, detalle y favoritos. La búsqueda usa paginación incremental y filtros de texto, marca, disponibilidad y orden. Las imágenes absolutas y `/uploads/` se resuelven contra la API; cualquier ruta web no portable usa una de las tres fotografías locales como fallback.

El backend separa `ProductoPublicResponse` de `ProductoResponse`: costo, margen, stocks exactos, reposición, flags y timestamps internos quedan solo en endpoints Admin protegidos. El detalle público aplica la misma regla de activo/visibilidad que el listado.

Favoritos se asocia a usuario+producto+tenant en V36, con unicidad, RLS y ownership derivado de `Authentication`; el cliente nunca envía un usuario o tenant elegible.

## Carrito, direcciones y checkout

El carrito persiste solo `{ productoId, cantidad }` bajo una clave versionada por tenant configurado y se revalida contra `/carrito/validar`. Ningún precio, IVA, stock o total local se considera autoridad.

Las direcciones pertenecen al usuario autenticado y al tenant de seguridad. V37 agrega RLS, una dirección principal por usuario y campos paraguayos con código postal, número y GPS opcionales. El teléfono se acepta en formato local y el backend lo normaliza a `+595`.

El checkout registra dirección, entrega, pago y notas, vuelve a bloquear/revalidar productos y almacena una copia histórica de la dirección. `Idempotency-Key` se controla por usuario con hash del request y bloqueo pesimista. La app persiste solamente clave+firma del intento para que una recuperación tras timeout use la misma identidad; cualquier cambio del contenido invalida esa clave. El carrito se limpia únicamente tras recibir el pedido.

## Envíos y tracking

`ShippingProvider` desacopla la cotización de proveedores futuros. La implementación inicial consulta `tarifas_envio`: ubicación, zona, peso, subtotal, transportista, promociones, vigencia, umbral gratis y prioridad viven en backend y pueden administrarse por tenant. Mobile envía únicamente dirección e items y persiste el identificador de la opción elegida; el checkout vuelve a evaluarla transaccionalmente.

`Envio` pertenece a un pedido y conserva costo, IVA, proveedor, transportista, código, ETA y estado. `EnvioEvento` forma una línea temporal append-only. Confirmación y cancelación del pedido propagan su efecto logístico. El costo forma parte del total y se representa como servicio en factura PDF, XML SIFEN y KuDE.

## Pedidos

TanStack Query pagina `/pedidos` y separa sus cachés de resumen, detalle y envío. El backend deriva ownership de la sesión; Mobile nunca elige usuario o tenant. La pantalla distingue el ciclo comercial `P/C/G/N/R/X` de `EstadoEnvio`, muestra el snapshot histórico de dirección y solo permite cancelar mientras el estado comercial es Pendiente. Después de cancelar, invalida historial y tracking sin mutación optimista.

`src/localization` fija `es-PY`, `America/Asuncion`, PYG sin decimales innecesarios y formatos `dd/MM/yyyy` / `dd/MM/yyyy HH:mm`. La estructura de copy permite sumar Guaraní o inglés sin replicar lógica fiscal.

Decisiones de seguridad para las fases siguientes:

- Contraseñas y tokens nunca se guardarán en AsyncStorage.
- El backend seguirá siendo la autoridad sobre precios, stock, totales y permisos.
- Los DTO públicos no exponen costo, margen ni inventario interno; existe una prueba de regresión en backend y móvil.
- La sesión y sus renovaciones deben diseñarse antes de habilitar el botón de login.

## Estado visible de Fase 7

- `Inicio`: productos/marcas reales, búsqueda, fallback fotográfico y favoritos del usuario.
- `Buscar`: catálogo paginado con búsqueda, marca, disponibilidad, orden y favoritos.
- `Carrito`: persistencia mínima, revalidación y entrada al checkout autenticado.
- `Pedidos`: historial real paginado, refrescable y con detalle navegable.
- `Cuenta`: sesión, logout y acceso a la libreta de direcciones.
- `Login`: formulario funcional cuando la API está configurada.
- `Registro`: alta funcional y sesión inmediata.
- `Detalle`: producto público visible, precio/IVA, disponibilidad y favorito.
- `Checkout`: dirección, entrega, pago, resumen, creación idempotente y éxito.
- `Tracking`: envío propio, código, costo, ETA, transportista y eventos reales.
- `Detalle pedido`: productos, importes, dirección, métodos, notas, timeline y cancelación segura.

Recuperación de contraseña e historial real de pedidos siguen pendientes. `EXPO_PUBLIC_TENANT_SLUG` separa claves locales pero nunca se envía como autoridad: el backend resuelve tenant desde su contexto seguro.
