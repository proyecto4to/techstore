import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/format/money.dart';
import '../../../core/widgets/quantity_stepper.dart';
import '../../auth/application/session_controller.dart';
import '../../catalog/domain/product.dart';
import '../../checkout/application/checkout_providers.dart';
import '../application/cart_controller.dart';

class CartPage extends ConsumerStatefulWidget {
  const CartPage({super.key});

  @override
  ConsumerState<CartPage> createState() => _CartPageState();
}

class _CartPageState extends ConsumerState<CartPage> {
  bool _checking = false;

  @override
  void initState() {
    super.initState();
    // El stock pudo cambiar desde que se agregaron las cosas. Se revisa al
    // abrir para que el cliente vea el carrito ya corregido, en vez de
    // enterarse recién al confirmar la compra.
    WidgetsBinding.instance.addPostFrameCallback((_) => _refreshAvailability());
  }

  Future<void> _refreshAvailability() async {
    final lines = ref.read(cartProvider);
    if (lines.isEmpty || _checking) return;
    setState(() => _checking = true);
    try {
      final availability = await ref
          .read(checkoutRepositoryProvider)
          .availability(lines);
      if (!mounted) return;
      final report = ref
          .read(cartProvider.notifier)
          .applyAvailability(availability.maxByProduct);
      if (report.hasChanges && mounted) {
        _notify(report.message);
      }
    } on Object {
      // Sin conexión el carrito guardado sigue sirviendo: no se molesta al
      // cliente con un error acá, porque el checkout vuelve a validar y ahí sí
      // corresponde avisar si algo no se puede comprar.
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  void _notify(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), duration: const Duration(seconds: 5)),
      );
  }

  @override
  Widget build(BuildContext context) {
    final items = ref.watch(cartProvider);
    final totals = totalsByCurrency([
      for (final line in items)
        (amount: line.subtotal, currency: line.product.currency),
    ]);
    final unitCount = items.fold<int>(0, (sum, line) => sum + line.quantity);
    final blocked = items.any((line) => line.exceedsStock);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Tu carrito'),
        bottom: _checking
            ? const PreferredSize(
                preferredSize: Size.fromHeight(4),
                child: LinearProgressIndicator(minHeight: 4),
              )
            : null,
      ),
      body: items.isEmpty
          ? _EmptyCart(onBrowse: () => context.go('/catalog'))
          : RefreshIndicator(
              onRefresh: _refreshAvailability,
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
                itemCount: items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) => _CartLineTile(
                  line: items[index],
                  onQuantityChanged: (value) => ref
                      .read(cartProvider.notifier)
                      .setQuantity(items[index].product.id, value),
                  onRemove: () => _confirmRemove(items[index]),
                ),
              ),
            ),
      bottomNavigationBar: items.isEmpty
          ? null
          : _CartSummary(
              totals: totals,
              unitCount: unitCount,
              blocked: blocked,
              onContinue: () {
                if (ref.read(authSessionProvider).isAuthenticated) {
                  context.push('/checkout');
                } else {
                  context.go('/login?returnTo=/checkout');
                }
              },
            ),
    );
  }

  Future<void> _confirmRemove(CartLine line) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿Quitar este producto?'),
        content: Text(
          '${line.product.name} sale de tu carrito. '
          'Podés volver a agregarlo desde el catálogo.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Quitar del carrito'),
          ),
        ],
      ),
    );
    if (confirmed ?? false) {
      ref.read(cartProvider.notifier).remove(line.product.id);
      if (mounted) _notify('${line.product.name} salió del carrito');
    }
  }
}

class _CartLineTile extends ConsumerWidget {
  const _CartLineTile({
    required this.line,
    required this.onQuantityChanged,
    required this.onRemove,
  });

  final CartLine line;
  final ValueChanged<int> onQuantityChanged;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final product = line.product;
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Thumbnail(product: product),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        product.name,
                        style: theme.textTheme.titleSmall,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${formatMoney(product.price, product.currency)} c/u',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Quitar ${product.name} del carrito',
                  onPressed: onRemove,
                  icon: const Icon(Icons.delete_outline),
                ),
              ],
            ),
            if (line.exceedsStock) ...[
              const SizedBox(height: 8),
              _StockWarning(available: product.maxPerOrder),
            ],
            const SizedBox(height: 8),
            // En pantallas angostas el selector y el subtotal no entran en una
            // fila sin recortarse, así que ahí se apilan.
            LayoutBuilder(
              builder: (context, constraints) {
                final stepper = QuantityStepper(
                  value: line.quantity,
                  max: product.maxPerOrder,
                  dense: true,
                  limitMeansScarcity: product.showsRemainingUnits,
                  onChanged: onQuantityChanged,
                );
                final subtotal = Text(
                  formatMoney(line.subtotal, product.currency),
                  style: theme.textTheme.titleMedium,
                );
                if (constraints.maxWidth < 300) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [stepper, const SizedBox(height: 8), subtotal],
                  );
                }
                return Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [stepper, subtotal],
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _StockWarning extends StatelessWidget {
  const _StockWarning({required this.available});

  final int available;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final message = available <= 0
        ? 'Se quedó sin stock. Quitalo para poder continuar.'
        : 'Quedan $available ${available == 1 ? 'unidad' : 'unidades'}. '
              'Ajustá la cantidad para poder continuar.';
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          // El color no alcanza para comunicar: va acompañado de icono y texto.
          Icon(
            Icons.warning_amber_rounded,
            size: 18,
            color: theme.colorScheme.onErrorContainer,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onErrorContainer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Thumbnail extends ConsumerWidget {
  const _Thumbnail({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final raw = product.imageUrl;
    final base = ref.watch(environmentProvider).apiBaseUrl;
    final url = raw == null || raw.isEmpty
        ? null
        : Uri.parse(base).resolve(raw).toString();
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox(
        width: 64,
        height: 64,
        child: url == null
            ? const ColoredBox(
                color: Color(0xFFECEFF1),
                child: Icon(Icons.inventory_2_outlined),
              )
            : Image.network(
                url,
                fit: BoxFit.cover,
                cacheWidth: 192,
                errorBuilder: (_, _, _) => const ColoredBox(
                  color: Color(0xFFECEFF1),
                  child: Icon(Icons.inventory_2_outlined),
                ),
              ),
      ),
    );
  }
}

class _CartSummary extends StatelessWidget {
  const _CartSummary({
    required this.totals,
    required this.unitCount,
    required this.blocked,
    required this.onContinue,
  });

  final Map<String, double> totals;
  final int unitCount;
  final bool blocked;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '$unitCount ${unitCount == 1 ? 'unidad' : 'unidades'}',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                Text('Total', style: theme.textTheme.bodyMedium),
              ],
            ),
            const SizedBox(height: 2),
            Align(
              alignment: Alignment.centerRight,
              child: Text(
                formatTotals(totals),
                style: theme.textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 12),
            if (blocked)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  'Ajustá las cantidades marcadas para continuar.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: blocked ? null : onContinue,
                icon: const Icon(Icons.arrow_forward),
                label: const Text('Continuar con la compra'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyCart extends StatelessWidget {
  const _EmptyCart({required this.onBrowse});

  final VoidCallback onBrowse;

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
                Icons.shopping_cart_outlined,
                size: 64,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            Text('Tu carrito está vacío', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(
              'Cuando agregues productos van a aparecer acá.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: onBrowse,
              icon: const Icon(Icons.storefront_outlined),
              label: const Text('Ver productos'),
            ),
          ],
        ),
      ),
    );
  }
}
