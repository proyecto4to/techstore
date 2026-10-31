import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../cart/application/cart_controller.dart';
import '../../catalog/domain/product.dart';
import '../domain/checkout_models.dart';

/// Disponibilidad vigente según el servidor, por producto.
final class CartAvailability {
  const CartAvailability({required this.valid, required this.maxByProduct});

  /// Si el carrito, tal como está, se puede comprar sin tocar nada.
  final bool valid;

  /// Cuántas unidades acepta hoy el servidor de cada producto.
  final Map<int, int> maxByProduct;
}

final class CheckoutRepository {
  const CheckoutRepository(this._api);

  final ApiClient _api;

  List<Map<String, int>> _items(List<CartLine> lines) => [
    for (final line in lines)
      {'productoId': line.product.id, 'cantidad': line.quantity},
  ];

  Future<Map<String, Object?>> validate(List<CartLine> lines) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/carrito/validar',
      data: {'items': _items(lines)},
    );
    return response.data!;
  }

  /// Revalida el carrito y traduce la respuesta a cuántas unidades acepta hoy
  /// el servidor de cada producto.
  ///
  /// Los nombres de estado del backend se consumen acá y no salen de este
  /// método: la interfaz recibe números y nombres de producto, nunca un
  /// identificador técnico que tendría que interpretar el cliente.
  Future<CartAvailability> availability(List<CartLine> lines) async {
    if (lines.isEmpty) {
      return const CartAvailability(valid: true, maxByProduct: {});
    }
    final data = await validate(lines);
    final maxByProduct = <int, int>{};

    for (final raw in data['items'] as List? ?? const []) {
      final item = Map<String, Object?>.from(raw as Map);
      final id = (item['productoId'] as num?)?.toInt();
      if (id == null) continue;

      final producto = item['producto'] as Map?;
      if (producto != null) {
        maxByProduct[id] = (producto['maxPorPedido'] as num?)?.toInt() ?? 0;
      } else if (item['estado'] == 'CANTIDAD_EXCESIVA') {
        // El producto existe y hay stock: lo que sobra es la cantidad pedida.
        maxByProduct[id] = Product.maximoPorPedido;
      } else {
        // Sin producto en la respuesta ya no se puede comprar.
        maxByProduct[id] = 0;
      }
    }

    return CartAvailability(
      valid: data['valido'] == true,
      maxByProduct: maxByProduct,
    );
  }

  Future<List<DeliveryAddress>> addresses() async {
    final response = await _api.get<List<dynamic>>('/api/v1/direcciones');
    return (response.data ?? const [])
        .map(
          (item) =>
              DeliveryAddress.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  Future<DeliveryAddress> createAddress(Map<String, Object?> data) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/direcciones',
      data: data,
    );
    return DeliveryAddress.fromJson(response.data!);
  }

  Future<DeliveryAddress> updateAddress(
    int id,
    Map<String, Object?> data,
  ) async {
    final response = await _api.put<Map<String, Object?>>(
      '/api/v1/direcciones/$id',
      data: data,
    );
    return DeliveryAddress.fromJson(response.data!);
  }

  Future<void> deleteAddress(int id) =>
      _api.delete<void>('/api/v1/direcciones/$id');

  Future<List<ShippingQuote>> quotes(
    List<CartLine> lines, {
    required int addressId,
  }) async {
    final response = await _api.post<List<dynamic>>(
      '/api/v1/envios/cotizar',
      data: {'direccionEntregaId': addressId, 'items': _items(lines)},
    );
    return (response.data ?? const [])
        .map(
          (item) =>
              ShippingQuote.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  /// Crea el pedido.
  ///
  /// [idempotencyKey] tiene que ser la misma en cada reintento de la misma
  /// compra. Antes se generaba una nueva en cada llamada, así que si la red se
  /// cortaba justo después de que el servidor creara el pedido, tocar de nuevo
  /// «Confirmar» creaba otro igual.
  ///
  /// [expectedTotal] es lo que la pantalla mostró y la persona transfirió: si
  /// el servidor calcula otro total, rechaza la compra en vez de crear un
  /// pedido que el comprobante no cubre.
  Future<int> checkout(
    List<CartLine> lines, {
    required bool delivery,
    int? addressId,
    int? tariffId,
    required String paymentMethod,
    required String idempotencyKey,
    String? notes,
    List<int> receiptIds = const [],
    double? expectedTotal,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/pedidos',
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
      data: {
        'items': _items(lines),
        'metodoEntrega': delivery ? 'ENVIO_DOMICILIO' : 'RETIRO_TIENDA',
        'metodoPago': paymentMethod,
        if (delivery) 'direccionEntregaId': addressId,
        if (delivery) 'tarifaEnvioId': tariffId,
        if (notes != null && notes.trim().isNotEmpty) 'notas': notes.trim(),
        if (receiptIds.isNotEmpty) 'comprobanteIds': receiptIds,
        'totalEsperado': ?expectedTotal,
      },
    );
    return (response.data!['id'] as num).toInt();
  }
}
