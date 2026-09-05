import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/format/money.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/add_to_cart_button.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/quantity_stepper.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../auth/application/session_controller.dart';
import '../../cart/application/cart_controller.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';

/// Cuántas unidades de un producto hay en el carrito. Observa solo ese número,
/// así la pantalla se reconstruye cuando cambia esa línea y no ante cualquier
/// movimiento del carrito.
int _quantityInCart(WidgetRef ref, int productId) => ref.watch(
  cartProvider.select((items) {
    for (final line in items) {
      if (line.product.id == productId) return line.quantity;
    }
    return 0;
  }),
);

/// Agrega al carrito y le cuenta al cliente qué pasó.
///
/// Está suelto para que el catálogo, el detalle y los favoritos den exactamente
/// la misma respuesta ante el mismo caso.
Future<bool> addProductToCart(
  BuildContext context,
  WidgetRef ref,
  Product product, {
  int quantity = 1,
}) async {
  final result = ref
      .read(cartProvider.notifier)
      .add(product, quantity: quantity);

  unawaited(
    ref
        .read(hapticsServiceProvider)
        .play(result.isSuccess ? HapticCue.success : HapticCue.tap),
  );

  if (context.mounted) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(result.message),
          duration: Duration(seconds: result.isSuccess ? 2 : 4),
          action: result.isSuccess
              ? SnackBarAction(
                  label: 'Ver carrito',
                  onPressed: () => context.push('/cart'),
                )
              : null,
        ),
      );
  }
  return result.added > 0;
}

class CatalogPage extends ConsumerStatefulWidget {
  const CatalogPage({super.key});

  @override
  ConsumerState<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends ConsumerState<CatalogPage> {
  final _searchController = TextEditingController();
  String _search = '';
  int? _brandId;
  ScaffoldMessengerState? _messenger;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Se guarda acá porque en dispose() ya no se puede buscar por el árbol.
    _messenger = ScaffoldMessenger.of(context);
  }

  @override
  void dispose() {
    // El aviso «Agregado al carrito» lo emite el ScaffoldMessenger de la app,
    // que vive por encima del Navigator: al salir del catálogo seguía pegado
    // abajo en las pantallas siguientes, tapando lo que hubiera ahí. Muere con
    // la pantalla que lo mostró.
    _messenger?.hideCurrentSnackBar();
    _searchController.dispose();
    super.dispose();
  }

  void _applySearch(String value) {
    if (value == _search) return;
    setState(() => _search = value);
  }

  void _clearFilters() {
    _searchController.clear();
    setState(() {
      _search = '';
      _brandId = null;
    });
  }

  bool get _hasFilters => _search.isNotEmpty || _brandId != null;

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
        actions: [_CartButton(count: cartCount)],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(catalogProvider(query).future),
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SearchBar(
                  controller: _searchController,
                  hintText: 'Buscar productos',
                  leading: const Icon(Icons.search),
                  onSubmitted: _applySearch,
                  trailing: [
                    if (_search.isNotEmpty)
                      IconButton(
                        tooltip: 'Limpiar búsqueda',
                        onPressed: () {
                          _searchController.clear();
                          _applySearch('');
                        },
                        icon: const Icon(Icons.clear),
                      ),
                    VoiceDictationButton(
                      controller: _searchController,
                      tooltip: 'Buscar por voz',
                      onFinalResult: _applySearch,
                    ),
                  ],
                ),
              ),
            ),
            if (brands.isNotEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<int?>(
                          initialValue: _brandId,
                          decoration: const InputDecoration(labelText: 'Marca'),
                          items: [
                            const DropdownMenuItem(child: Text('Todas')),
                            for (final brand in brands)
                              DropdownMenuItem(
                                value: (brand['id'] as num).toInt(),
                                child: Text(
                                  brand['nombre']?.toString() ?? 'Marca',
                                ),
                              ),
                          ],
                          onChanged: (value) =>
                              setState(() => _brandId = value),
                        ),
                      ),
                      if (_hasFilters) ...[
                        const SizedBox(width: 8),
                        TextButton(
                          onPressed: _clearFilters,
                          child: const Text('Limpiar'),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            catalog.when(
              loading: () => const SliverFillRemaining(
                child: AppLoading(label: 'Cargando catálogo'),
              ),
              error: (error, _) => SliverFillRemaining(
                child: AppErrorState(
                  message:
                      'No pudimos cargar el catálogo. '
                      'Revisá tu conexión e intentá de nuevo.',
                  onRetry: () => ref.invalidate(catalogProvider(query)),
                ),
              ),
              data: (products) => products.isEmpty
                  ? SliverFillRemaining(
                      child: _NoResults(
                        filtered: _hasFilters,
                        onClear: _clearFilters,
                      ),
                    )
                  : SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                      sliver: SliverGrid.builder(
                        gridDelegate:
                            const SliverGridDelegateWithMaxCrossAxisExtent(
                              maxCrossAxisExtent: 360,
                              childAspectRatio: .68,
                              crossAxisSpacing: 12,
                              mainAxisSpacing: 12,
                            ),
                        itemCount: products.length,
                        itemBuilder: (context, index) {
                          // Al asomar las últimas fichas se pide el bloque
                          // siguiente, así la grilla no corta en treinta ni
                          // baja el catálogo entero de una.
                          if (index >= products.length - 4) {
                            WidgetsBinding.instance.addPostFrameCallback((_) {
                              ref
                                  .read(catalogProvider(query).notifier)
                                  .cargarMas();
                            });
                          }
                          return _ProductCard(
                            product: products[index],
                            onOpen: () =>
                                context.push('/products/${products[index].id}'),
                          );
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Icono del carrito con el contador animado: el número se agranda al cambiar,
/// que es la segunda mitad de la respuesta al agregar algo.
class _CartButton extends StatelessWidget {
  const _CartButton({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) => Badge(
    isLabelVisible: count > 0,
    label: AnimatedSwitcher(
      duration: const Duration(milliseconds: 220),
      transitionBuilder: (child, animation) =>
          ScaleTransition(scale: animation, child: child),
      child: Text('$count', key: ValueKey(count)),
    ),
    child: IconButton(
      tooltip: count == 0
          ? 'Carrito vacío'
          : 'Carrito, $count ${count == 1 ? 'unidad' : 'unidades'}',
      onPressed: () => context.push('/cart'),
      icon: const Icon(Icons.shopping_cart_outlined),
    ),
  );
}

class _NoResults extends StatelessWidget {
  const _NoResults({required this.filtered, required this.onClear});

  final bool filtered;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Icon(
                filtered ? Icons.search_off : Icons.inventory_2_outlined,
                size: 56,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              filtered
                  ? 'No encontramos productos con esos filtros'
                  : 'Todavía no hay productos publicados',
              textAlign: TextAlign.center,
              style: theme.textTheme.titleSmall,
            ),
            if (filtered) ...[
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: onClear,
                child: const Text('Limpiar filtros'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class ProductDetailPage extends ConsumerStatefulWidget {
  const ProductDetailPage({required this.productId, super.key});

  final int productId;

  @override
  ConsumerState<ProductDetailPage> createState() => _ProductDetailPageState();
}

class _ProductDetailPageState extends ConsumerState<ProductDetailPage> {
  int _quantity = 1;

  @override
  Widget build(BuildContext context) {
    final product = ref.watch(productProvider(widget.productId));
    final authenticated = ref.watch(authSessionProvider).isAuthenticated;
    final favorites = authenticated ? ref.watch(favoritesProvider).value : null;
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Producto'),
        actions: [
          _CartButton(
            count: ref.watch(
              cartProvider.select(
                (items) => items.fold(0, (sum, item) => sum + item.quantity),
              ),
            ),
          ),
        ],
      ),
      body: product.when(
        loading: () => const AppLoading(label: 'Cargando producto'),
        error: (error, _) => AppErrorState(
          message: 'No pudimos cargar este producto. Intentá de nuevo.',
          onRetry: () => ref.invalidate(productProvider(widget.productId)),
        ),
        data: (item) {
          // Lo que ya está en el carrito descuenta de lo que se puede agregar:
          // sin esto el cliente podría elegir 5, tener 7 y terminar pidiendo
          // 12 de un producto que solo tiene 10.
          final inCart = _quantityInCart(ref, item.id);
          final room = math.max(0, item.maxPerOrder - inCart);
          final quantity = _quantity.clamp(1, room > 0 ? room : 1).toInt();

          return ListView(
            padding: const EdgeInsets.all(20),
            children: [
              _ProductImage(product: item, height: 280),
              const SizedBox(height: 20),
              Text(item.name, style: theme.textTheme.headlineSmall),
              if (item.brand != null)
                Text(
                  item.brand!,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              const SizedBox(height: 12),
              Text(
                formatMoney(item.price, item.currency),
                style: theme.textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              _Availability(product: item, inCart: inCart),
              const SizedBox(height: 16),
              if (item.description != null && item.description!.isNotEmpty)
                Text(item.description!, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 24),
              if (room > 0) ...[
                Text('Cantidad', style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                QuantityStepper(
                  value: quantity,
                  max: room,
                  limitMeansScarcity: item.showsRemainingUnits,
                  onChanged: (value) => setState(() => _quantity = value),
                ),
                const SizedBox(height: 20),
              ],
              AddToCartButton(
                disabledLabel: item.maxPerOrder <= 0
                    ? 'Sin stock disponible'
                    : 'Ya tenés todo el stock en el carrito',
                onPressed: room <= 0
                    ? null
                    : () async {
                        final added = await addProductToCart(
                          context,
                          ref,
                          item,
                          quantity: quantity,
                        );
                        if (added && mounted) setState(() => _quantity = 1);
                        return added;
                      },
              ),
              if (authenticated) ...[
                const SizedBox(height: 10),
                _FavoriteButton(product: item, favorites: favorites),
              ],
            ],
          );
        },
      ),
    );
  }
}

/// Disponibilidad en palabras. Solo se dice cuánto queda cuando el número
/// significa escasez real; con stock alto alcanza con decir que hay.
class _Availability extends StatelessWidget {
  const _Availability({required this.product, required this.inCart});

  final Product product;
  final int inCart;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (IconData icon, String text, Color color) = switch (product) {
      final p when p.maxPerOrder <= 0 => (
        Icons.remove_shopping_cart_outlined,
        'Sin stock por ahora',
        theme.colorScheme.error,
      ),
      final p when p.showsRemainingUnits => (
        Icons.inventory_2_outlined,
        p.maxPerOrder == 1
            ? 'Queda 1 unidad'
            : 'Quedan ${p.maxPerOrder} unidades',
        theme.colorScheme.tertiary,
      ),
      _ => (
        Icons.check_circle_outline,
        'Disponible',
        theme.colorScheme.primary,
      ),
    };

    return Row(
      children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            inCart > 0 ? '$text · $inCart en tu carrito' : text,
            style: theme.textTheme.bodyMedium?.copyWith(color: color),
          ),
        ),
      ],
    );
  }
}

class _FavoriteButton extends ConsumerWidget {
  const _FavoriteButton({required this.product, required this.favorites});

  final Product product;
  final List<Product>? favorites;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isFavorite =
        favorites?.any((value) => value.id == product.id) ?? false;
    return OutlinedButton.icon(
      onPressed: () async {
        final repository = ref.read(favoritesRepositoryProvider);
        if (isFavorite) {
          await repository.remove(product.id);
        } else {
          await repository.add(product.id);
        }
        await ref
            .read(hapticsServiceProvider)
            .play(isFavorite ? HapticCue.tap : HapticCue.selection);
        ref.invalidate(favoritesProvider);
      },
      icon: Icon(isFavorite ? Icons.favorite : Icons.favorite_outline),
      label: Text(isFavorite ? 'Quitar de favoritos' : 'Guardar en favoritos'),
    );
  }
}

class _ProductCard extends ConsumerWidget {
  const _ProductCard({required this.product, required this.onOpen});

  final Product product;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final room = math.max(
      0,
      product.maxPerOrder - _quantityInCart(ref, product.id),
    );

    return Card(
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
              Text(
                product.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 2),
              Text(
                formatMoney(product.price, product.currency),
                style: theme.textTheme.titleMedium,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Flexible(
                    child: Text(
                      product.maxPerOrder <= 0
                          ? 'Sin stock'
                          : product.showsRemainingUnits
                          ? 'Quedan ${product.maxPerOrder}'
                          : 'Disponible',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: product.maxPerOrder <= 0
                            ? theme.colorScheme.error
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                  AddToCartButton(
                    compact: true,
                    disabledLabel: product.maxPerOrder <= 0
                        ? 'Sin stock disponible'
                        : 'Ya tenés todo el stock en el carrito',
                    onPressed: room <= 0
                        ? null
                        : () => addProductToCart(context, ref, product),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
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
