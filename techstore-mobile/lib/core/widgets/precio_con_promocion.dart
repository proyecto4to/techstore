import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../format/money.dart';

/// El precio de un producto, con la rebaja a la vista cuando hay promoción.
///
/// El número grande es siempre lo que se va a cobrar. El precio de lista, si
/// existe, va tachado al lado: nunca al revés. Mostrar grande un precio que no
/// es el que se cobra es la forma más rápida de que alguien se sienta
/// engañado en el carrito.
class PrecioConPromocion extends StatelessWidget {
  const PrecioConPromocion({
    required this.precio,
    required this.moneda,
    this.precioLista,
    this.porcentaje,
    this.hasta,
    this.estilo,
    this.mostrarVencimiento = false,
    super.key,
  });

  final double precio;
  final String moneda;

  /// El «antes». Nulo, o menor o igual al precio, cuando no hay promoción.
  final double? precioLista;
  final double? porcentaje;
  final DateTime? hasta;
  final TextStyle? estilo;

  /// Si se aclara hasta cuándo dura. En una grilla de productos no entra; en
  /// el detalle sí, y ahí es lo que empuja a decidir.
  final bool mostrarVencimiento;

  bool get _hayPromocion =>
      precioLista != null && porcentaje != null && precioLista! > precio;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final precioTexto = Text(
      formatMoney(precio, moneda),
      style: estilo ?? theme.textTheme.titleMedium,
    );
    if (!_hayPromocion) return precioTexto;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(child: precioTexto),
            const SizedBox(width: 6),
            _Etiqueta(porcentaje: porcentaje!),
          ],
        ),
        Text(
          formatMoney(precioLista!, moneda),
          semanticsLabel: 'Antes ${formatMoney(precioLista!, moneda)}',
          style: theme.textTheme.bodySmall?.copyWith(
            decoration: TextDecoration.lineThrough,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        if (mostrarVencimiento && hasta != null)
          Text(
            'Hasta el ${DateFormat('d/M', 'es_PY').format(hasta!.toLocal())}',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
      ],
    );
  }
}

class _Etiqueta extends StatelessWidget {
  const _Etiqueta({required this.porcentaje});

  final double porcentaje;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Sin decimales cuando son redondos: «20%», no «20.0%».
    final texto = porcentaje == porcentaje.roundToDouble()
        ? '-${porcentaje.round()}%'
        : '-${porcentaje.toStringAsFixed(1)}%';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: theme.colorScheme.tertiaryContainer,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        texto,
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onTertiaryContainer,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
