import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/diseno/forma_con_corte.dart';
import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/format/money.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/marca/rotulo.dart';
import '../../../core/widgets/quantity_stepper.dart';
import '../../auth/application/session_controller.dart';
import '../../catalog/presentation/vitrina/escenario_de_producto.dart';
import '../../catalog/presentation/vitrina/tarjetas_de_producto.dart';
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
                preferredSize: Size.fromHeight(3),
                child: LinearProgressIndicator(minHeight: 3),
              )
            : null,
      ),
      body: items.isEmpty
          ? AppEmptyState(
              icon: Icons.shopping_bag_outlined,
              title: 'Tu carrito está vacío',
              message: 'Cuando agregues productos van a aparecer acá.',
              accion: 'Ver productos',
              onAccion: () => context.go('/catalog'),
            )
          : RefreshIndicator(
              onRefresh: _refreshAvailability,
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(
                  Medidas.margen,
                  8,
                  Medidas.margen,
                  24,
                ),
                itemCount: items.length + 1,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return Rotulo(
                      unitCount == 1
                          ? '1 unidad · deslizá para quitar'
                          : '$unitCount unidades · deslizá para quitar',
                    );
                  }
                  final line = items[index - 1];
                  return Aparicion(
                    orden: index.clamp(0, 6),
                    child: _CartLineTile(
                      key: ValueKey(line.product.id),
                      line: line,
                      onQuantityChanged: (value) => ref
                          .read(cartProvider.notifier)
                          .setQuantity(line.product.id, value),
                      onRemove: () => _confirmRemove(line),
                      onDismissed: () => _remove(line),
                    ),
                  );
                },
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
                  // Encima del carrito y no en su lugar: si la persona se
                  // arrepiente y vuelve atrás, encuentra su carrito y no se
                  // cierra la app.
                  context.push('/login?returnTo=/checkout');
                }
              },
            ),
    );
  }

  Future<bool> _askRemove(CartLine line) async {
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
    return confirmed ?? false;
  }

  void _remove(CartLine line) {
    ref.read(cartProvider.notifier).remove(line.product.id);
    if (mounted) _notify('${line.product.name} salió del carrito');
  }

  Future<void> _confirmRemove(CartLine line) async {
    if (await _askRemove(line)) _remove(line);
  }
}

class _CartLineTile extends ConsumerWidget {
  const _CartLineTile({
    required this.line,
    required this.onQuantityChanged,
    required this.onRemove,
    required this.onDismissed,
    super.key,
  });

  final CartLine line;
  final ValueChanged<int> onQuantityChanged;
  final VoidCallback onRemove;
  final VoidCallback onDismissed;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final marca = MarcaTema.of(context);
    final product = line.product;
    final tarjeta = DecoratedBox(
      decoration: BoxDecoration(
        color: Paleta.negroAzulado,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(
          color: line.exceedsStock ? marca.error : Paleta.borde,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                GestureDetector(
                  onTap: () => abrirProducto(context, product),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: SizedBox.square(
                      dimension: 76,
                      child: EscenarioDeProducto(
                        producto: product,
                        encuadre: Encuadre.miniatura,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (product.brand != null) Rotulo(product.brand!),
                      const SizedBox(height: 2),
                      Text(
                        product.name,
                        style: theme.textTheme.titleSmall,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      // En el carrito el precio unitario va chico y al lado el
                      // de lista tachado: es donde se confirma que la rebaja
                      // que se vio en el catálogo sigue estando.
                      Wrap(
                        spacing: 6,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Text(
                            '${formatMoney(product.price, product.currency)} c/u',
                            style: theme.textTheme.bodySmall,
                          ),
                          if (product.hasPromotion)
                            Text(
                              formatMoney(product.listPrice!, product.currency),
                              style: theme.textTheme.bodySmall?.copyWith(
                                decoration: TextDecoration.lineThrough,
                              ),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Quitar ${product.name} del carrito',
                  onPressed: onRemove,
                  icon: const Icon(Icons.delete_outline_rounded),
                ),
              ],
            ),
            if (line.exceedsStock) ...[
              const SizedBox(height: 10),
              _StockWarning(available: product.maxPerOrder),
            ],
            const SizedBox(height: 10),
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
                  style: marca.precio,
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

    // Deslizar para quitar, con la misma confirmación que el tacho: un gesto
    // involuntario no puede vaciar una línea sin preguntar.
    return Dismissible(
      key: ValueKey('linea-${product.id}'),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) async {
        final estado = context.findAncestorStateOfType<_CartPageState>();
        return estado?._askRemove(line) ?? Future.value(false);
      },
      onDismissed: (_) => onDismissed(),
      background: DecoratedBox(
        decoration: BoxDecoration(
          color: Paleta.errorHundido,
          borderRadius: Medidas.bordeTarjeta,
          border: Border.all(color: marca.error.withValues(alpha: 0.5)),
        ),
        child: Align(
          alignment: Alignment.centerRight,
          child: Padding(
            padding: const EdgeInsets.only(right: 22),
            child: Icon(Icons.delete_outline_rounded, color: marca.error),
          ),
        ),
      ),
      child: tarjeta,
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
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(Medidas.radioChico),
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
    final marca = MarcaTema.of(context);
    return DecoratedBox(
      decoration: const ShapeDecoration(
        color: Paleta.negroPrincipal,
        shape: FormaConCorte(
          corte: 16,
          radio: 0,
          side: BorderSide(color: Paleta.borde),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 14),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Rotulo('total'),
                        const SizedBox(height: 2),
                        Text(
                          '$unitCount ${unitCount == 1 ? 'unidad' : 'unidades'}',
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  Flexible(
                    child: AnimatedSwitcher(
                      duration: Movimiento.o(context, Movimiento.corto),
                      transitionBuilder: (hijo, animacion) => FadeTransition(
                        opacity: animacion,
                        child: SlideTransition(
                          position: Tween(
                            begin: const Offset(0, 0.3),
                            end: Offset.zero,
                          ).animate(animacion),
                          child: hijo,
                        ),
                      ),
                      // En una línea: con la letra del sistema agrandada, el
                      // «₲» saltaba abajo del número. Se achica antes de
                      // partirse.
                      child: FittedBox(
                        key: ValueKey(formatTotals(totals)),
                        fit: BoxFit.scaleDown,
                        alignment: Alignment.centerRight,
                        child: Text(
                          formatTotals(totals),
                          maxLines: 1,
                          style: marca.precioGrande.copyWith(fontSize: 28),
                        ),
                      ),
                    ),
                  ),
                ],
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
                  icon: const Icon(Icons.arrow_forward_rounded),
                  label: const Text('Continuar con la compra'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
