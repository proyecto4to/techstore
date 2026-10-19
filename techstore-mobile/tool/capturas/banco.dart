// Banco de capturas: dibuja pantallas y componentes con las fuentes de la
// marca y fotos reales del catálogo, y los guarda como PNG para revisarlos a
// ojo sin compilar la app.
//
// No es una prueba de regresión ni corre con la suite: necesita una carpeta
// con las fotos y el JSON de productos, que se indica con la variable de
// entorno TECHSTORE_CAPTURAS. Sin ella, cada caso se saltea.
//
//   TECHSTORE_CAPTURAS=/ruta/a/carpeta flutter test --update-goldens tool/capturas
//
// La carpeta lleva `productos.json` (lo que devuelve /api/v1/productos) y una
// subcarpeta `fotos/` con cada imagen por su nombre de archivo.

import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/app_theme.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/foto_de_producto.dart';

final carpeta = Platform.environment['TECHSTORE_CAPTURAS'];

bool get hayBanco => carpeta != null && Directory(carpeta!).existsSync();

/// Los productos reales del JSON.
List<Product> productosReales() {
  final json =
      jsonDecode(File('$carpeta/productos.json').readAsStringSync()) as Map;
  return [
    for (final item in json['content'] as List)
      Product.fromJson(Map<String, Object?>.from(item as Map)),
  ];
}

Future<void> cargarFuentes() async {
  const familias = {
    'Inter': ['Inter-400', 'Inter-500', 'Inter-600', 'Inter-700'],
    'SpaceGrotesk': [
      'SpaceGrotesk-500',
      'SpaceGrotesk-600',
      'SpaceGrotesk-700',
    ],
    'JetBrainsMono': ['JetBrainsMono-500', 'JetBrainsMono-700'],
  };
  for (final MapEntry(key: familia, value: archivos) in familias.entries) {
    final cargador = FontLoader(familia);
    for (final archivo in archivos) {
      cargador.addFont(rootBundle.load('assets/fonts/$archivo.ttf'));
    }
    await cargador.load();
  }
  final iconos = FontLoader('MaterialIcons')
    ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'));
  await iconos.load();
}

File _foto(String url) =>
    File('$carpeta/fotos/${Uri.parse(url).pathSegments.last}');

/// Un contenedor de Riverpod con las fotos servidas desde disco y la base
/// local en memoria.
ProviderContainer contenedorDeBanco({List<Override> extra = const []}) =>
    ProviderContainer(
      overrides: [
        environmentProvider.overrideWithValue(AppEnvironment.development()),
        fabricaDeFotosProvider.overrideWithValue((url) => FileImage(_foto(url))),
        archivoDeFotoProvider.overrideWithValue((url) async => _foto(url)),
        databaseProvider.overrideWithValue(
          AppDatabase(NativeDatabase.memory()),
        ),
        ...extra,
      ],
    );

/// Baja (de disco) y analiza las fotos de estos productos antes de dibujar,
/// para que la captura salga con cada foto ya en su tratamiento.
Future<void> prepararFotos(
  WidgetTester tester,
  ProviderContainer contenedor,
  Iterable<Product> productos,
) async {
  await tester.runAsync(() async {
    for (final p in productos) {
      final url = contenedor.read(urlDeFotoProvider(p.imageUrl));
      if (url == null) continue;
      await contenedor.read(rasgosDeFotoProvider(url).future);
    }
  });
}

/// Espera a que las imágenes del árbol terminen de decodificarse.
///
/// No sirve esperar cada una con `precacheImage` adentro de `runAsync`: la
/// carga ya empezó en el tiempo falso de la prueba, y su continuación solo
/// corre cuando ese reloj avanza. Se alterna una pausa real —para que el disco
/// y el decodificador terminen— con un `pump` que procesa lo que quedó.
Future<void> esperarImagenes(WidgetTester tester, {int vueltas = 30}) async {
  for (var i = 0; i < vueltas; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 60)),
    );
    await tester.pump(const Duration(milliseconds: 50));
  }
  await tester.pump(const Duration(seconds: 1));
}

/// Monta [hijo] en un teléfono de [ancho] × [alto] con el tema de la marca.
/// 412 es un teléfono común; 346 es el Moto G42 con la pantalla agrandada y
/// 393 un Redmi Note. [escalaDeTexto] imita el tamaño de letra del sistema:
/// en HyperOS es común encontrarlo agrandado.
Future<void> montar(
  WidgetTester tester,
  ProviderContainer contenedor,
  Widget hijo, {
  double alto = 915,
  double ancho = 412,
  double escalaDeTexto = 1,
}) async {
  tester.view
    ..physicalSize = Size(ancho * 2.625, alto * 2.625)
    ..devicePixelRatio = 2.625;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: contenedor,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.marca(),
        builder: (context, pantalla) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(escalaDeTexto)),
          child: pantalla!,
        ),
        home: hijo,
      ),
    ),
  );
}
