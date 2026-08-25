import 'dart:async';
import 'dart:convert';

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
  Map<String, Object?> toJson() => {
    'product': product.toJson(),
    'quantity': quantity,
  };
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

  void add(Product product) {
    final index = state.indexWhere((line) => line.product.id == product.id);
    if (index < 0) {
      state = [...state, CartLine(product: product, quantity: 1)];
    } else {
      setQuantity(product.id, state[index].quantity + 1);
      return;
    }
    unawaited(_persist());
  }

  void setQuantity(int productId, int quantity) {
    if (quantity <= 0) {
      state = state.where((line) => line.product.id != productId).toList();
    } else {
      state = [
        for (final line in state)
          if (line.product.id == productId)
            CartLine(product: line.product, quantity: quantity)
          else
            line,
      ];
    }
    unawaited(_persist());
  }

  void clear() {
    state = const [];
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
