import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:techstore_mobile/core/network/api_client.dart';

import '../../test/support/sesion_de_prueba.dart';
import 'banco.dart';

/// Un servidor de mentira que contesta con el catálogo real del banco:
/// filtra por marca y búsqueda, ordena y pagina como el de verdad.
ApiClient apiDeBanco({String rol = 'Cliente', int noLeidas = 2}) => ApiClient(
  baseUrl: 'http://10.0.2.2:8090',
  logger: const LoggerMudo(),
  dio: Dio()..httpClientAdapter = _Catalogo(rol, noLeidas),
);

final class _Catalogo implements HttpClientAdapter {
  _Catalogo(this.rol, this.noLeidas);

  final String rol;
  final int noLeidas;

  late final List<Map<String, Object?>> _productos = [
    for (final p
        in (jsonDecode(File('$carpeta/productos.json').readAsStringSync())
                as Map)['content']
            as List)
      Map<String, Object?>.from(p as Map),
  ];

  late final List<Object?> _marcas = File('$carpeta/marcas.json').existsSync()
      ? jsonDecode(File('$carpeta/marcas.json').readAsStringSync()) as List
      : const [];

  ResponseBody _json(Object? cuerpo) => ResponseBody.fromString(
    jsonEncode(cuerpo),
    200,
    headers: {
      Headers.contentTypeHeader: ['application/json'],
    },
  );

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final ruta = options.path;
    final q = options.queryParameters;
    if (ruta.endsWith('/auth/refresh')) {
      return _json({
        'accessToken': 'access',
        'refreshToken': 'refresh',
        'tokenType': 'Bearer',
        'expiresInMs': 900000,
        'usuario': {
          'id': 1,
          'email': 'ana@techstore.py',
          'nombre': 'Ana',
          'apellido': 'Benítez',
          'rol': rol,
          'ruc': null,
          'razonSocial': null,
        },
      });
    }
    if (ruta.endsWith('/api/v1/marcas')) return _json(_marcas);
    final ficha = RegExp(r'/api/v1/productos/(\d+)$').firstMatch(ruta);
    if (ficha != null) {
      final id = int.parse(ficha.group(1)!);
      return _json(_productos.firstWhere((p) => p['id'] == id));
    }
    if (ruta.endsWith('/api/v1/productos')) {
      var lista = [..._productos];
      final marca = q['marcaId'];
      if (marca != null) {
        lista = lista
            .where((p) => (p['marca'] as Map?)?['id'] == marca)
            .toList();
      }
      final buscar = (q['search'] as String?)?.toLowerCase();
      if (buscar != null) {
        lista = lista
            .where((p) => '${p['nombre']}'.toLowerCase().contains(buscar))
            .toList();
      }
      final orden = q['sort'] as String?;
      final int Function(Map<String, Object?>, Map<String, Object?>) comparar =
          switch (orden) {
            'id,desc' => (a, b) => (b['id'] as int).compareTo(a['id'] as int),
            'precio,asc' => (a, b) =>
                (a['precio'] as num).compareTo(b['precio'] as num),
            'precio,desc' => (a, b) =>
                (b['precio'] as num).compareTo(a['precio'] as num),
            _ => (a, b) => '${a['nombre']}'.compareTo('${b['nombre']}'),
          };
      lista.sort(comparar);
      final pagina = q['page'] as int? ?? 0;
      final tamano = q['size'] as int? ?? 30;
      final desde = (pagina * tamano).clamp(0, lista.length);
      final hasta = (desde + tamano).clamp(0, lista.length);
      return _json({
        'content': lista.sublist(desde, hasta),
        'page': pagina,
        'size': tamano,
        'totalElements': lista.length,
        'totalPages': (lista.length / tamano).ceil(),
      });
    }
    if (ruta.contains('no-leidas')) return _json({'noLeidas': noLeidas});
    if (ruta.endsWith('/api/v1/favoritos')) {
      return _json([
        for (final p in _productos.where((p) => [2, 36, 20].contains(p['id'])))
          {'producto': p},
      ]);
    }
    return _json(const <String, Object?>{});
  }

  @override
  void close({bool force = false}) {}
}
