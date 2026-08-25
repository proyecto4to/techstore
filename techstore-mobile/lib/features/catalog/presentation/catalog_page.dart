import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/widgets/async_states.dart';
import '../../auth/application/session_controller.dart';
import '../../cart/application/cart_controller.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';

class CatalogPage extends ConsumerStatefulWidget {
  const CatalogPage({super.key});

  @override
  ConsumerState<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends ConsumerState<CatalogPage> {
  String _search = '';
  int? _brandId;

  @override
  Widget build(BuildContext context) {
    final query = (search: _search, brandId: _brandId);
    final catalog = ref.watch(catalogProvider(query));
    final brands = ref.watch(brandsProvider).value ?? const [];
    final cartCount = ref.watch(
      cartProvider.select(
        (items) => items.fold(0, (sum, item) => sum + item.quantity),
      ),
    );
    return Scaffold(
      appBar: AppBar(
        title: const Text('Catálogo'),
        actions: [
          Badge(
            isLabelVisible: cartCount > 0,
            label: Text('$cartCount'),
            child: IconButton(
              tooltip: 'Carrito',
              onPressed: () => context.push('/cart'),
              icon: const Icon(Icons.shopping_cart_outlined),
            ),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(catalogProvider(query).future),
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SearchBar(
                  hintText: 'Buscar productos',
                  leading: const Icon(Icons.search),
                  onSubmitted: (value) => setState(() => _search = value),
                  trailing: _search.isEmpty
                      ? null
                      : [
                          IconButton(
                            tooltip: 'Limpiar búsqueda',
                            onPressed: () => setState(() => _search = ''),
                            icon: const Icon(Icons.clear),
                          ),
                        ],
                ),
              ),
            ),
            if (brands.isNotEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: DropdownButtonFormField<int?>(
                    initialValue: _brandId,
                    decoration: const InputDecoration(labelText: 'Marca'),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('Todas')),
                      for (final brand in brands)
                        DropdownMenuItem(
                          value: (brand['id'] as num).toInt(),
                          child: Text(brand['nombre']?.toString() ?? 'Marca'),
                        ),
                    ],
                    onChanged: (value) => setState(() => _brandId = value),
                  ),
                ),
              ),
            catalog.when(
              loading: () => const SliverFillRemaining(
                child: AppLoading(label: 'Cargando catálogo'),
              ),
              error: (error, _) => SliverFillRemaining(
                child: AppErrorState(
                  message: 'No se pudo cargar el catálogo',
                  onRetry: () => ref.invalidate(catalogProvider(query)),
                ),
              ),
              data: (products) => products.isEmpty
                  ? const SliverFillRemaining(
                      child: AppEmptyState(
                        message: 'No hay productos disponibles',
                        icon: Icons.inventory_2_outlined,
                      ),
                    )
                  : SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                      sliver: SliverGrid.builder(
                        gridDelegate:
                            const SliverGridDelegateWithMaxCrossAxisExtent(
                              maxCrossAxisExtent: 360,
                              childAspectRatio: .72,
                              crossAxisSpacing: 12,
                              mainAxisSpacing: 12,
                            ),
                        itemCount: products.length,
                        itemBuilder: (context, index) => _ProductCard(
                          product: products[index],
                          onOpen: () =>
                              context.push('/products/${products[index].id}'),
                          onAdd: products[index].available
                              ? () => ref
                                    .read(cartProvider.notifier)
                                    .add(products[index])
                              : null,
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class ProductDetailPage extends ConsumerWidget {
  const ProductDetailPage({required this.productId, super.key});

  final int productId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final product = ref.watch(productProvider(productId));
    final authenticated = ref.watch(authSessionProvider).isAuthenticated;
    final favorites = authenticated ? ref.watch(favoritesProvider).value : null;
    return Scaffold(
      appBar: AppBar(title: const Text('Producto')),
      body: product.when(
        loading: () => const AppLoading(label: 'Cargando producto'),
        error: (error, _) => AppErrorState(
          message: 'No se pudo cargar el producto',
          onRetry: () => ref.invalidate(productProvider(productId)),
        ),
        data: (item) => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            _ProductImage(product: item, height: 280),
            const SizedBox(height: 20),
            Text(item.name, style: Theme.of(context).textTheme.headlineSmall),
            if (item.brand != null) Text(item.brand!),
            const SizedBox(height: 12),
            Text(_money(item), style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            Text(item.description ?? 'Sin descripción'),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: item.available
                  ? () {
                      ref.read(cartProvider.notifier).add(item);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Producto agregado')),
                      );
                    }
                  : null,
              icon: const Icon(Icons.add_shopping_cart),
              label: Text(
                item.available ? 'Agregar al carrito' : 'Sin disponibilidad',
              ),
            ),
            if (authenticated) ...[
              const SizedBox(height: 10),
              OutlinedButton.icon(
                onPressed: () async {
                  final isFavorite =
                      favorites?.any((value) => value.id == item.id) ?? false;
                  final repository = ref.read(favoritesRepositoryProvider);
                  if (isFavorite) {
                    await repository.remove(item.id);
                  } else {
                    await repository.add(item.id);
                  }
                  ref.invalidate(favoritesProvider);
                },
                icon: Icon(
                  favorites?.any((value) => value.id == item.id) == true
                      ? Icons.favorite
                      : Icons.favorite_outline,
                ),
                label: const Text('Favorito'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ProductCard extends ConsumerWidget {
  const _ProductCard({required this.product, required this.onOpen, this.onAdd});

  final Product product;
  final VoidCallback onOpen;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context, WidgetRef ref) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onOpen,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: _ProductImage(product: product)),
            const SizedBox(height: 8),
            Text(product.name, maxLines: 2, overflow: TextOverflow.ellipsis),
            Text(
              _money(product),
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Align(
              alignment: Alignment.centerRight,
              child: IconButton.filledTonal(
                tooltip: 'Agregar',
                onPressed: onAdd,
                icon: const Icon(Icons.add_shopping_cart),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _ProductImage extends ConsumerWidget {
  const _ProductImage({required this.product, this.height});
  final Product product;
  final double? height;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final raw = product.imageUrl;
    final base = ref.watch(environmentProvider).apiBaseUrl;
    final url = raw == null || raw.isEmpty
        ? null
        : Uri.parse(base).resolve(raw).toString();
    return Semantics(
      image: true,
      label: 'Imagen de ${product.name}',
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: url == null
            ? const ColoredBox(
                color: Color(0xFFECEFF1),
                child: Icon(Icons.inventory_2_outlined, size: 56),
              )
            : LayoutBuilder(
                builder: (context, constraints) {
                  final logicalWidth = constraints.maxWidth.isFinite
                      ? constraints.maxWidth
                      : 720.0;
                  final cacheWidth =
                      (logicalWidth * MediaQuery.devicePixelRatioOf(context))
                          .round()
                          .clamp(1, 2048);
                  return Image.network(
                    url,
                    fit: BoxFit.contain,
                    cacheWidth: cacheWidth,
                    filterQuality: FilterQuality.medium,
                    loadingBuilder: (context, child, progress) =>
                        progress == null
                        ? child
                        : const AppLoading(label: 'Cargando imagen'),
                    errorBuilder: (_, _, _) =>
                        const Icon(Icons.broken_image_outlined),
                  );
                },
              ),
      ),
    );
  }
}

String _money(Product product) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: product.currency == 'PYG' ? '₲' : product.currency,
  decimalDigits: product.currency == 'PYG' ? 0 : 2,
).format(product.price);
