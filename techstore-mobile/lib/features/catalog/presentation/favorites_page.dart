import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../cart/application/cart_controller.dart';
import '../application/catalog_providers.dart';

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
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: [
              const SizedBox(height: 160),
              Center(
                child: FilledButton(
                  onPressed: () => ref.invalidate(favoritesProvider),
                  child: const Text('Reintentar'),
                ),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    SizedBox(height: 160),
                    Center(child: Text('Todavía no tenés favoritos')),
                  ],
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final product = items[index];
                    return Card(
                      child: ListTile(
                        title: Text(product.name),
                        subtitle: Text('${product.price} ${product.currency}'),
                        onTap: () => context.push('/products/${product.id}'),
                        trailing: Wrap(
                          children: [
                            IconButton(
                              tooltip: 'Agregar al carrito',
                              onPressed: product.available
                                  ? () => ref
                                        .read(cartProvider.notifier)
                                        .add(product)
                                  : null,
                              icon: const Icon(Icons.add_shopping_cart),
                            ),
                            IconButton(
                              tooltip: 'Quitar favorito',
                              onPressed: () async {
                                await ref
                                    .read(favoritesRepositoryProvider)
                                    .remove(product.id);
                                ref.invalidate(favoritesProvider);
                              },
                              icon: const Icon(Icons.favorite),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
      ),
    );
  }
}
