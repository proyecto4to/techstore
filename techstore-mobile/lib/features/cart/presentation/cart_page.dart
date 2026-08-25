import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../auth/application/session_controller.dart';
import '../application/cart_controller.dart';

class CartPage extends ConsumerWidget {
  const CartPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final items = ref.watch(cartProvider);
    final total = items.fold<double>(0, (sum, line) => sum + line.subtotal);
    return Scaffold(
      appBar: AppBar(title: const Text('Carrito')),
      body: items.isEmpty
          ? const Center(child: Text('Tu carrito está vacío'))
          : ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: items.length,
              separatorBuilder: (_, _) => const Divider(),
              itemBuilder: (context, index) {
                final line = items[index];
                return ListTile(
                  title: Text(line.product.name),
                  subtitle: Text(_money(line.subtotal, line.product.currency)),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Disminuir cantidad',
                        onPressed: () => ref
                            .read(cartProvider.notifier)
                            .setQuantity(line.product.id, line.quantity - 1),
                        icon: const Icon(Icons.remove_circle_outline),
                      ),
                      Text('${line.quantity}'),
                      IconButton(
                        tooltip: 'Aumentar cantidad',
                        onPressed: () => ref
                            .read(cartProvider.notifier)
                            .setQuantity(line.product.id, line.quantity + 1),
                        icon: const Icon(Icons.add_circle_outline),
                      ),
                    ],
                  ),
                );
              },
            ),
      bottomNavigationBar: items.isEmpty
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: FilledButton(
                  onPressed: () {
                    if (ref.read(authSessionProvider).isAuthenticated) {
                      context.push('/checkout');
                    } else {
                      context.go('/login?returnTo=/checkout');
                    }
                  },
                  child: Text('Continuar · ${_money(total, 'PYG')}'),
                ),
              ),
            ),
    );
  }
}

String _money(double value, String currency) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: currency == 'PYG' ? '₲' : currency,
  decimalDigits: currency == 'PYG' ? 0 : 2,
).format(value);
