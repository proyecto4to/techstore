# Fase 11 — Entrega de la versión 1.0.22

Fecha: 2026-10-19. Rama: `rediseno-monokai`.

## Qué se entrega

| Archivo | Para qué | SHA-256 |
|---|---|---|
| `TechStore-1.0.22+23.apk` | Instalar a mano en teléfonos (ARM de 32 y 64 bits) | `df9deaf951b0bc6b0ba056817df32baaf906b77e09adcdd41120672bcd5e2fae` |
| `TechStore-1.0.22+23.aab` | Subir a Google Play (incluye x86_64; Play arma la descarga de cada equipo) | `36f1d383d52baab4f89a9738c58d81b687339f55f139f34e39557781f9d412f6` |

- Versión `1.0.22`, código `23`. El `pubspec.yaml` sigue en `1.0.0+1` a propósito: la versión real viaja por línea de comandos.
- Sabor `production` (`py.com.techstore.app`) contra `https://closure-unrigged-postcard.ngrok-free.dev`, el mismo backend que la 1.0.21.
- Firmada con la clave de producción de siempre (`android/key.properties`, fuera de git). El certificado coincide con el de la 1.0.21, así que se instala encima sin perder datos.
- Código Dart ofuscado; los símbolos para leer un error de producción quedan en `artifacts/mobile/symbols/1.0.22+23` (fuera de git, no se distribuyen).
- **Sin notificaciones push**, igual que la 1.0.21: no hay claves de Firebase en este equipo (`%LOCALAPPDATA%\TechStore\mobile-signing\firebase-android.json`).
- Copias en el escritorio (`TechStore 1.0.22`, con un LEEME) y en `artifacts/mobile/release`, junto a las versiones anteriores. Copiarlo ahí no publica nada: el backend ofrece el APK que nombra la metadata, que sigue en la 1.0.21.

## Verificación de lo entregado

| Comprobación | Resultado |
|---|---|
| `apksigner verify` | Firma v2 válida; certificado SHA-256 `c4a0…f0a2`, el mismo de la 1.0.21 |
| `aapt2 dump badging` | `py.com.techstore.app`, versión 1.0.22 (23), SDK mínimo 24 y objetivo 36; permisos idénticos a los de la 1.0.21 |
| `ci-security-check.mjs --archive` | 514 entradas, sin secretos ni avisos |
| Instalación y arranque | En el emulador, forzando ARM64 (Android 15 traduce ARM): se instaló encima de una versión anterior, cargó el motor y abrió el ingreso con la marca nueva; sin el backend público, mostró el aviso de sin conexión, como corresponde |

Una aclaración sobre el APK: además de ARM trae dos bibliotecas mínimas de plugins para `x86_64`, sin el motor de Flutter, igual que la 1.0.21. En un teléfono no importa; en un equipo x86_64 (un emulador, una Chromebook) Android elegiría esa arquitectura y la app no arrancaría. Ahí corresponde la versión de Play, cuyo AAB sí lleva `x86_64` completo.

## Lo que no se hizo sin autorización

**La actualización dentro de la app no se publicó.** El backend ofrece a cada teléfono la versión que dice `artifacts/mobile/release/release-metadata.json`; reescribirlo es publicar la 1.0.22 para todos los clientes. Por eso no se usó `scripts/build-mobile-release.ps1`, que además de compilar escribe ese archivo y borra el `changelog.json` si no recibe novedades: se repitieron sus mismas opciones a mano. Para publicar, cuando se decida:

```powershell
.\scripts\build-mobile-release.ps1 -ApiBaseUrl https://closure-unrigged-postcard.ngrok-free.dev `
  -VersionName 1.0.22 -VersionCode 23 -SinPush -Novedades @(
    'Nuevo diseño: tienda en negro y azul, con la marca nueva.',
    'Inicio con vidriera: lo último que entró, lo que queda poco y las marcas.',
    'Catálogo más rápido: las fotos se guardan y no se vuelven a bajar.',
    'Ordená por precio y compartí productos desde su ficha.',
    'Mejor con letra grande y con lector de pantalla.'
  )
```

El script vuelve a compilar desde el commit en el que esté la rama, corre el análisis y las pruebas, y recién ahí escribe la metadata.

## Cómo se compiló

Requisitos: Flutter 3.47.1, el JDK 21 de `tools/jdk-21`, el SDK de Android en `C:/Android/Sdk` y `android/key.properties`.

```bash
cd techstore-mobile
export JAVA_HOME=../tools/jdk-21
COMUN="--release --flavor production -t lib/main_production.dart \
  --build-name 1.0.22 --build-number 23 \
  --dart-define=API_BASE_URL=https://closure-unrigged-postcard.ngrok-free.dev \
  --obfuscate --split-debug-info=../artifacts/mobile/symbols/1.0.22+23"
flutter build appbundle $COMUN
flutter build apk $COMUN --target-platform android-arm,android-arm64
(cd android && ./gradlew --stop)   # el daemon retiene ~2,8 GB de RAM
```

Verificación de lo entregado: `apksigner verify --print-certs` (certificado igual al de la 1.0.21, no de depuración) y `node scripts/ci-security-check.mjs --archive <apk>`.

## Instalación

**En un teléfono, a mano:**

1. Copiar el APK al teléfono o bajarlo.
2. Abrirlo. Android pide permiso para instalar desde esa app (el explorador o el navegador): aceptar. En HyperOS puede correr además su análisis de seguridad.
3. Si ya está la 1.0.21, se actualiza encima: la sesión, el carrito y los favoritos se conservan.

**En Google Play:** subir el AAB a una pista de prueba interna antes de producción.

## Novedades de la versión

Para quien compra:

- Marca nueva y diseño Monokai Black & Blue en toda la app, la web y la factura.
- Inicio con vidriera: carrusel de campañas, colecciones por marca, recién llegados y «queda poco».
- Catálogo editorial con colecciones por marca, orden por precio y búsqueda.
- Cada foto se presenta según lo que es: recortada, de estudio o de escena.
- Ficha inmersiva con zoom, compartir y relacionados.
- Fotos guardadas en el teléfono: el catálogo no las vuelve a bajar en cada visita.
- Funciona con letra grande, lector de pantalla y «Quitar animaciones».

Técnicas:

- Sistema de diseño (`lib/core/diseno`) y de movimiento con duraciones y curvas únicas.
- Motor de presentación de productos (`lib/features/catalog/presentation/vitrina`).
- Análisis de fotos con el decodificador de Android (canal `py.com.techstore.app/miniaturas`).
- Backend: cabeceras de caché propias para las fotos de producto.
- Correcciones de navegación, avisos, contraste y desbordes (fases 7, 8 y 10).

## Arquitectura de lo nuevo

| Pieza | Dónde | Qué hace |
|---|---|---|
| Sistema de diseño | `lib/core/diseno/` | Paleta, tipografía, medidas, movimiento, `MarcaTema` y la forma con corte |
| Marca | `lib/core/widgets/marca/`, `docs/marca/` | Isotipo, logotipo y rótulos; el generador produce ícono, splash, favicon y el logo de la factura |
| Movimiento | `lib/core/animacion/` | Aparición en cascada, respuesta al toque, texto tecleado, vuelo al carrito |
| Vitrina | `lib/features/catalog/presentation/vitrina/` | Rasgos de cada foto, estilo por colección, escenario, tarjetas y el ritmo de la vidriera |
| Fotos | `foto_de_producto.dart`, `MainActivity.kt` | Caché en disco de una semana, miniatura nativa para el análisis, precarga ociosa |
| Banco de capturas | `tool/capturas/` | Dibuja pantallas con datos y fotos reales, sin compilar la app |

## Mantenimiento

- **Cambiar la marca:** editar `docs/marca/generador/generar-marca.mjs` y correrlo; regenera los recursos, el ícono y el splash. Después, `dart run flutter_launcher_icons` y `dart run flutter_native_splash:create`.
- **Cambiar un color:** en `lib/core/diseno/paleta.dart`. `test/core/paleta_test.dart` avisa si un par queda por debajo del contraste mínimo.
- **Revisar pantallas sin compilar:** `TECHSTORE_CAPTURAS=<carpeta> flutter test --update-goldens tool/capturas`. La carpeta lleva `productos.json` (lo que devuelve `/api/v1/productos`) y `fotos/`.
- **Fotos de producto:** subir JPEG de hasta unos 1600 px y 500 KB. Las de 1,5 MB funcionan, pero se bajan más lento la primera vez.
- **Medir fluidez:** ver `docs/transformacion/09-rendimiento-y-seguridad.md`.
