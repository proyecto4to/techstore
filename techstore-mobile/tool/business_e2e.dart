import 'dart:io';

import 'package:dio/dio.dart';

Future<void> main() async {
  final baseUrl =
      Platform.environment['TECHSTORE_E2E_BASE_URL'] ?? 'http://localhost:8090';
  final email = Platform.environment['TECHSTORE_E2E_EMAIL'];
  final password = Platform.environment['TECHSTORE_E2E_PASSWORD'];
  if (email == null || password == null) {
    stderr.writeln('Defini TECHSTORE_E2E_EMAIL y TECHSTORE_E2E_PASSWORD.');
    exitCode = 2;
    return;
  }

  final api = Dio(BaseOptions(baseUrl: baseUrl));
  final registration = await api.post<Map<String, dynamic>>(
    '/api/v1/auth/registro',
    data: {
      'nombre': 'Business',
      'apellido': 'E2E',
      'email': email,
      'password': password,
    },
  );
  final accessToken = registration.data!['accessToken'] as String;
  final refreshToken = registration.data!['refreshToken'] as String;
  api.options.headers['Authorization'] = 'Bearer $accessToken';
  int? productId;
  int? addressId;
  try {
    final brandsResponse = await api.get<List<dynamic>>('/api/v1/marcas');
    final brands = brandsResponse.data ?? const [];
    _check(brands.isNotEmpty, 'No existen marcas para probar el filtro.');
    final brandId = ((brands.first as Map)['id'] as num).toInt();
    final productsResponse = await api.get<Map<String, dynamic>>(
      '/api/v1/productos',
      queryParameters: {'marcaId': brandId, 'page': 0, 'size': 20},
    );
    final products =
        productsResponse.data?['content'] as List<dynamic>? ?? const [];
    _check(products.isNotEmpty, 'El filtro por marca no devolvio productos.');
    productId = ((products.first as Map)['id'] as num).toInt();
    final productResponse = await api.get<Map<String, dynamic>>(
      '/api/v1/productos/$productId',
    );
    _check(
      productResponse.data?['id'] == productId,
      'Producto no consultable.',
    );

    await api.post<void>('/api/v1/favoritos/$productId');
    final savedResponse = await api.get<List<dynamic>>('/api/v1/favoritos');
    final saved = savedResponse.data ?? const [];
    _check(
      saved.any(
        (item) => ((item as Map)['producto'] as Map)['id'] == productId,
      ),
      'Favorito no persistido.',
    );

    final validation = await api.post<Map<String, Object?>>(
      '/api/v1/carrito/validar',
      data: {
        'items': [
          {'productoId': productId, 'cantidad': 1},
        ],
      },
    );
    _check(validation.data!.isNotEmpty, 'El carrito no genero respuesta.');

    final addressData = <String, Object?>{
      'nombreDestinatario': 'Business E2E',
      'telefono': '0981123456',
      'departamento': 'Central',
      'ciudad': 'Asuncion',
      'barrio': 'Centro',
      'direccionLinea1': 'Calle E2E 100',
      'referencia': 'Prueba automatizada',
      'latitud': -25.2867,
      'longitud': -57.647,
      'principal': true,
    };
    final created = await api.post<Map<String, Object?>>(
      '/api/v1/direcciones',
      data: addressData,
    );
    addressId = (created.data!['id'] as num).toInt();
    final updated = await api.put<Map<String, Object?>>(
      '/api/v1/direcciones/$addressId',
      data: {...addressData, 'direccionLinea1': 'Calle E2E 101'},
    );
    _check(
      (updated.data!['direccionLinea1'] as String).endsWith('101'),
      'La direccion no fue actualizada.',
    );
    final response = await api.get<List<dynamic>>('/api/v1/direcciones');
    final addresses = response.data ?? const [];
    _check(
      addresses.any((item) => (item as Map)['id'] == addressId),
      'La direccion no aparece en el listado.',
    );

    stdout.writeln(
      'BUSINESS_E2E_OK catalog=true brand_filter=true favorites_crud=true '
      'cart_validation=true addresses_crud=true',
    );
  } finally {
    if (addressId != null) {
      await api.delete<void>('/api/v1/direcciones/$addressId');
    }
    if (productId != null) {
      await api.delete<void>('/api/v1/favoritos/$productId');
    }
    await api.post<void>(
      '/api/v1/auth/logout',
      data: {'refreshToken': refreshToken},
    );
  }
}

void _check(bool condition, String message) {
  if (!condition) throw StateError(message);
}
