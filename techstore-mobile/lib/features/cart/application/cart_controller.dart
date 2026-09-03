import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../auth/application/session_controller.dart';
import '../../catalog/domain/product.dart';

final cartProvider = NotifierProvider<CartController, List<CartLine>>(
  CartController.new,
);

final class CartLine {
  const CartLine({required this.product, required this.quantity});

  factory CartLine.fromJson(Map<String, Object?> json) => CartLine(
    product: Product.fromJson(
      Map<String, Object?>.from(json['product']! as Map),
    ),
    quantity: (json['quantity'] as num).toInt(),
  );

  final Product product;
  final int quantity;

  double get subtotal => product.price * quantity;

  /// Cuántas unidades más admite esta línea antes de tocar el tope.
  int get room => math.max(0, product.maxPerOrder - quantity);

  /// Si la línea quedó pidiendo más de lo que el servidor acepta hoy.
  bool get exceedsStock => quantity > product.maxPerOrder;

  CartLine copyWith({int? quantity, Product? product}) => CartLine(
    product: product ?? this.product,
    quantity: quantity ?? this.quantity,
  );

  Map<String, Object?> toJson() => {
    'product': product.toJson(),
    'quantity': quantity,
  };
}

/// Qué pasó al intentar agregar algo al carrito. La interfaz necesita
/// distinguir estos casos para explicarle al cliente por qué no se llevó lo que
/// pidió, en lugar de dejar el botón sin respuesta.
enum CartOutcome {
  /// Entró la cantidad completa.
  added,

  /// Entró solo una parte: el resto no cabía contra el tope disponible.
  capped,

  /// No entró nada porque el carrito ya tiene el máximo de ese producto.
  alreadyAtMax,

  /// No entró nada porque el producto no tiene unidades disponibles.
  unavailable,
}

final class CartResult {
  const CartResult(
    this.outcome, {
    required this.productName,
    this.added = 0,
    this.inCart = 0,
    this.max = 0,
  });

  final CartOutcome outcome;
  final String productName;

  /// Unidades que efectivamente entraron.
  final int added;

  /// Unidades del producto que quedaron en el carrito.
  final int inCart;

  /// Tope que informó el servidor.
  final int max;

  bool get isSuccess => outcome == CartOutcome.added;

  /// Texto para mostrarle al cliente. Vive acá y no en cada pantalla para que
  /// el catálogo, el detalle y el carrito digan exactamente lo mismo.
  String get message => switch (outcome) {
    CartOutcome.added =>
      added == 1 ? 'Agregado al carrito' : 'Agregaste $added unidades',
    CartOutcome.capped => _cappedMessage,
    CartOutcome.alreadyAtMax => _atMaxMessage,
    CartOutcome.unavailable => 'Este producto se quedó sin unidades.',
  };

  String get _cappedMessage {
    final entraron = '$added ${_units(added)}';
    return 'Solo quedaban $entraron disponibles. Agregamos '
        '${added == 1 ? 'esa' : 'esas'} y tu carrito quedó en $inCart.';
  }

  String get _atMaxMessage =>
      'Ya tenés $inCart ${_units(inCart)} en el carrito, que es todo lo '
      'disponible de este producto.';

  static String _units(int value) => value == 1 ? 'unidad' : 'unidades';
}

/// Una línea que hubo que recortar contra la disponibilidad del servidor.
final class CartAdjustment {
  const CartAdjustment({
    required this.name,
    required this.from,
    required this.to,
  });

  final String name;
  final int from;
  final int to;
}

/// Qué cambió al revalidar el carrito contra el servidor.
final class CartSyncReport {
  const CartSyncReport({this.adjusted = const [], this.removed = const []});

  /// Líneas cuya cantidad hubo que bajar.
  final List<CartAdjustment> adjusted;

  /// Productos que ya no se pueden comprar y salieron del carrito.
  final List<String> removed;

  bool get hasChanges => adjusted.isNotEmpty || removed.isNotEmpty;

  /// Una sola explicación de qué pasó y de que ya está resuelto, para que el
  /// cliente pueda seguir comprando sin tener que interpretar nada.
  String get message {
    final partes = <String>[];
    for (final linea in adjusted) {
      final quedan = '${linea.to} ${linea.to == 1 ? 'unidad' : 'unidades'}';
      partes.add(
        'De ${linea.name} quedan $quedan, así que bajamos tu cantidad de '
        '${linea.from} a ${linea.to}.',
      );
    }
    for (final nombre in removed) {
      partes.add('$nombre se quedó sin stock y lo sacamos del carrito.');
    }
    return '${partes.join(' ')} Podés continuar con la compra.';
  }
}

final class CartController extends Notifier<List<CartLine>> {
  String get _scope {
    final user = ref.read(authSessionProvider).user;
    return 'cart:${user?.id ?? 'guest'}';
  }

  @override
  List<CartLine> build() {
    unawaited(_restore());
    return const [];
  }

  /// Cuántas unidades de un producto ya hay en el carrito.
  int quantityOf(int productId) {
    for (final line in state) {
      if (line.product.id == productId) return line.quantity;
    }
    return 0;
  }

  /// Cuántas unidades más se pueden agregar de un producto, contando lo que ya
  /// está en el carrito. Es lo que necesita el selector de cantidad para no
  /// ofrecer un número que después va a ser rechazado.
  int roomFor(Product product) =>
      math.max(0, product.maxPerOrder - quantityOf(product.id));

  /// Agrega respetando el tope disponible y lo que ya hay en el carrito. Nunca
  /// deja el carrito por encima de lo que el servidor acepta.
  CartResult add(Product product, {int quantity = 1}) {
    final requested = math.max(1, quantity);
    final current = quantityOf(product.id);
    final max = product.maxPerOrder;

    if (max <= 0) {
      return CartResult(
        CartOutcome.unavailable,
        productName: product.name,
        inCart: current,
      );
    }
    if (current >= max) {
      return CartResult(
        CartOutcome.alreadyAtMax,
        productName: product.name,
        inCart: current,
        max: max,
      );
    }

    final toAdd = math.min(requested, max - current);
    _write(product, current + toAdd);

    return CartResult(
      toAdd < requested ? CartOutcome.capped : CartOutcome.added,
      productName: product.name,
      added: toAdd,
      inCart: current + toAdd,
      max: max,
    );
  }

  /// Fija la cantidad de una línea, recortando al tope disponible en vez de
  /// aceptar un número imposible: si el cliente escribe 15 y hay 8, queda 8.
  /// Devuelve la cantidad que realmente quedó.
  int setQuantity(int productId, int quantity) {
    final index = state.indexWhere((line) => line.product.id == productId);
    if (index < 0) return 0;

    final product = state[index].product;
    final capped = math.min(quantity, math.max(0, product.maxPerOrder));
    if (capped <= 0) {
      state = state.where((line) => line.product.id != productId).toList();
      unawaited(_persist());
      return 0;
    }
    _write(product, capped);
    return capped;
  }

  void remove(int productId) => setQuantity(productId, 0);

  void clear() {
    state = const [];
    unawaited(_persist());
  }

  /// Aplica la disponibilidad que acaba de informar el servidor: recorta las
  /// líneas que quedaron largas y saca las que ya no se pueden comprar.
  /// Devuelve el detalle para poder explicárselo al cliente.
  CartSyncReport applyAvailability(Map<int, int> maxByProduct) {
    final adjusted = <CartAdjustment>[];
    final removed = <String>[];
    final next = <CartLine>[];

    for (final line in state) {
      final max = maxByProduct[line.product.id];
      if (max == null) {
        next.add(line);
        continue;
      }
      if (max <= 0) {
        removed.add(line.product.name);
        continue;
      }
      final product = line.product.copyWith(maxPerOrder: max, available: true);
      if (line.quantity > max) {
        adjusted.add(
          CartAdjustment(name: line.product.name, from: line.quantity, to: max),
        );
        next.add(line.copyWith(quantity: max, product: product));
      } else {
        next.add(line.copyWith(product: product));
      }
    }

    state = List.unmodifiable(next);
    unawaited(_persist());
    return CartSyncReport(adjusted: adjusted, removed: removed);
  }

  void _write(Product product, int quantity) {
    final index = state.indexWhere((line) => line.product.id == product.id);
    if (index < 0) {
      state = [...state, CartLine(product: product, quantity: quantity)];
    } else {
      state = [
        for (final line in state)
          if (line.product.id == product.id)
            // Se guarda el producto recién traído del catálogo: trae el tope
            // actualizado, que es justamente lo que hay que respetar.
            CartLine(product: product, quantity: quantity)
          else
            line,
      ];
    }
    unawaited(_persist());
  }

  Future<void> _restore() async {
    final raw = await ref
        .read(cacheStoreProvider)
        .read(scope: _scope, key: 'items');
    if (raw == null) return;
    final decoded = jsonDecode(raw) as List;
    state = decoded
        .map(
          (item) => CartLine.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  Future<void> _persist() => ref
      .read(cacheStoreProvider)
      .write(
        scope: _scope,
        key: 'items',
        json: jsonEncode(state.map((line) => line.toJson()).toList()),
        ttl: const Duration(days: 30),
      );
}
