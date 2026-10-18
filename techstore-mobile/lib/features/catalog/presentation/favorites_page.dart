import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/money.dart';
import '../../../core/widgets/add_to_cart_button.dart';
import '../../../core/widgets/async_states.dart';
import '../../cart/application/cart_controller.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';
import 'catalog_page.dart';

class FavoritesPage extends ConsumerWidget {
  const FavoritesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final favorites = ref.watch(favoritesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Favoritos')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(favoritesProvider.future),
        child: favorites.when(
          loading: () => const AppLoading(label: 'Cargando favoritos'),
          error: (error, _) => AppErrorState(
            message:
                'No pudimos cargar tus favoritos. '
                'Revisá tu conexión e intentá de nuevo.',
            onRetry: () => ref.invalidate(favoritesProvider),
          ),
          data: (items) => items.isEmpty
              ? _NoFavorites(onBrowse: () => context.go('/catalog'))
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: items.length,
                  itemBuilder: (context, index) =>
                      _FavoriteTile(product: items[index]),
                ),
        ),
      ),
    );
  }
}

class _FavoriteTile extends ConsumerWidget {
  const _FavoriteTile({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final inCart = ref.watch(
      cartProvider.select((items) {
        for (final line in items) {
          if (line.product.id == product.id) return line.quantity;
        }
        return 0;
      }),
    );
    final room = math.max(0, product.maxPerOrder - inCart);

    return Card(
      child: ListTile(
        title: Text(product.name),
        subtitle: Text(
          [
            formatMoney(product.price, product.currency),
            // La rebaja se anuncia con el porcentaje y no con el precio
            // anterior tachado: en un subtítulo de una línea, el tachado se
            // lee como parte del mismo número.
            if (product.hasPromotion)
              '−${product.promotionPercentage!.round()}%',
            if (product.maxPerOrder <= 0) 'sin stock',
          ].join(' · '),
          style: product.maxPerOrder <= 0
              ? theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.error,
                )
              : null,
        ),
        onTap: () => context.push('/products/${product.id}'),
        trailing: Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            AddToCartButton(
              compact: true,
              disabledLabel: product.maxPerOrder <= 0
                  ? 'Sin stock disponible'
                  : 'Ya tenés todo el stock en el carrito',
              onPressed: room <= 0
                  ? null
                  : () => addProductToCart(context, ref, product),
            ),
            IconButton(
              tooltip: 'Quitar de favoritos',
              onPressed: () async {
                await ref.read(favoritesRepositoryProvider).remove(product.id);
                ref.invalidate(favoritesProvider);
              },
              icon: const Icon(Icons.favorite),
            ),
          ],
        ),
      ),
    );
  }
}

class _NoFavorites extends StatelessWidget {
  const _NoFavorites({required this.onBrowse});

  final VoidCallback onBrowse;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const SizedBox(height: 120),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                ExcludeSemantics(
                  child: Icon(
                    Icons.favorite_outline,
                    size: 56,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  'Todavía no tenés favoritos',
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'Guardá productos con el corazón para encontrarlos rápido.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 20),
                FilledButton.icon(
                  onPressed: onBrowse,
                  icon: const Icon(Icons.storefront_outlined),
                  label: const Text('Ver productos'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
