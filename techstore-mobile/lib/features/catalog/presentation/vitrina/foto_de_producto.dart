import 'dart:async';
import 'dart:ui' as ui;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/di/providers.dart';
import 'rasgos_de_imagen.dart';

/// Las fotos de producto se guardan en disco.
///
/// Antes se bajaban con `Image.network`, que solo guarda en memoria: cada vez
/// que se abría la app, el catálogo entero volvía a bajar por el túnel, que es
/// lo más lento y lo que más datos gasta. Una semana de vigencia alcanza: si
/// alguien cambia la foto de un producto, el nombre del archivo nuevo es otro.
final gestorDeFotos = CacheManager(
  Config(
    'techstore-fotos',
    stalePeriod: const Duration(days: 7),
    maxNrOfCacheObjects: 400,
  ),
);

/// De dónde sale cada foto. Está en un provider para que las pruebas, donde no
/// hay disco ni red, lo cambien por fotos en memoria.
final fabricaDeFotosProvider = Provider<ImageProvider Function(String url)>(
  (ref) =>
      (url) => CachedNetworkImageProvider(url, cacheManager: gestorDeFotos),
);

/// La dirección completa de una foto, o nula si el producto no tiene.
///
/// El servidor manda rutas relativas (`/img/productos/gpu.jpg`); se resuelven
/// contra la dirección de la API del sabor que esté corriendo.
final urlDeFotoProvider = Provider.family<String?, String?>((ref, relativa) {
  if (relativa == null || relativa.trim().isEmpty) return null;
  final base = ref.watch(environmentProvider).apiBaseUrl;
  return Uri.parse(base).resolve(relativa.trim()).toString();
});

/// Lo que dice cada foto de sí misma, mirado una vez por sesión.
///
/// Se decodifica una copia de 48 píxeles de ancho desde el mismo archivo en
/// disco: cuesta un par de milisegundos y no vuelve a bajar nada.
final rasgosDeFotoProvider = FutureProvider.family<RasgosDeImagen, String>((
  ref,
  url,
) async {
  final fabrica = ref.watch(fabricaDeFotosProvider);
  try {
    final imagen = await _decodificar(ResizeImage(fabrica(url), width: 48));
    try {
      final datos = await imagen.toByteData(format: ui.ImageByteFormat.rawRgba);
      if (datos == null) return RasgosDeImagen.ausente;
      return AnalizadorDeImagen.analizar(
        datos.buffer.asUint8List(),
        imagen.width,
        imagen.height,
      );
    } finally {
      imagen.dispose();
    }
  } on Object {
    // Una foto que no baja no es un error de la pantalla: la vitrina la
    // presenta como «sin foto» y sigue.
    return RasgosDeImagen.ausente;
  }
});

Future<ui.Image> _decodificar(ImageProvider proveedor) {
  final listo = Completer<ui.Image>();
  final flujo = proveedor.resolve(ImageConfiguration.empty);
  late final ImageStreamListener oyente;
  oyente = ImageStreamListener(
    (info, _) {
      if (!listo.isCompleted) listo.complete(info.image.clone());
      info.dispose();
      flujo.removeListener(oyente);
    },
    onError: (error, pila) {
      if (!listo.isCompleted) listo.completeError(error, pila);
      flujo.removeListener(oyente);
    },
  );
  flujo.addListener(oyente);
  return listo.future;
}
