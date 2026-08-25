import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/realtime/realtime_event.dart';
import 'package:techstore_mobile/features/cart/application/cart_controller.dart';
import 'package:techstore_mobile/features/catalog/data/catalog_repository.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/checkout/data/checkout_repository.dart';
import 'package:techstore_mobile/features/orders/domain/order.dart';

void main() {
  test('catálogo usa los filtros reales y convierte la página', () async {
    late RequestOptions captured;
    final api = _api((options) {
      captured = options;
      return {
        'content': [
          {
            'id': 7,
            'nombre': 'Notebook',
            'precio': 4500000,
            'moneda': 'PYG',
            'disponible': true,
          },
        ],
      };
    });

    final products = await CatalogRepository(api)
        .list(search: 'note', brandId: 3);

    expect(captured.path, '/api/v1/productos');
    expect(captured.queryParameters['search'], 'note');
    expect(captured.queryParameters['marcaId'], 3);
    expect(products.single.name, 'Notebook');
  });

  test('checkout envía carrito e Idempotency-Key', () async {
    late RequestOptions captured;
    final api = _api((options) {
      captured = options;
      return {'id': 91};
    });
    const product = Product(
      id: 4,
      name: 'Mouse',
      price: 100000,
      currency: 'PYG',
      available: true,
    );

    final id = await CheckoutRepository(api).checkout(
      const [CartLine(product: product, quantity: 2)],
      delivery: false,
      paymentMethod: 'PAGO_EN_LOCAL',
    );

    expect(id, 91);
    expect(captured.path, '/api/v1/pedidos');
    expect(captured.headers['Idempotency-Key'], isNotEmpty);
    final body = Map<String, Object?>.from(captured.data as Map);
    final items = List<Map<String, Object?>>.from(body['items']! as List);
    expect(body['metodoEntrega'], 'RETIRO_TIENDA');
    expect(items.single['cantidad'], 2);
  });

  test('modelos de pedido y evento preservan identidad y estado', () {
    final order = OrderSummary.fromJson({
      'id': 8,
      'numero': 15,
      'estado': 'Pendiente',
      'fecha': '2026-08-22T12:30:00Z',
      'total': 900000,
      'moneda': 'PYG',
    });
    final event = RealtimeEvent.fromJson({
      'id': 31,
      'eventType': 'STOCK_UPDATED',
      'aggregateType': 'PRODUCT',
      'aggregateId': 4,
    });

    expect(order.id, 8);
    expect(order.status, 'Pendiente');
    expect(event.id, 31);
    expect(event.aggregateId, 4);
  });
}

ApiClient _api(Object? Function(RequestOptions options) response) {
  final dio = Dio();
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) => handler.resolve(
        Response<Object?>(
          requestOptions: options,
          statusCode: 200,
          data: response(options),
        ),
      ),
    ),
  );
  return ApiClient(
    baseUrl: 'https://api.techstore.test',
    logger: const SafeAppLogger(),
    dio: dio,
  );
}
