import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Selector de cantidad que no deja elegir más de lo que hay.
///
/// El tope se aplica en el propio control: al llegar al máximo el botón de
/// sumar queda deshabilitado y aparece el motivo. Así el cliente se entera
/// mientras elige y no después, al intentar confirmar la compra.
class QuantityStepper extends StatelessWidget {
  const QuantityStepper({
    required this.value,
    required this.max,
    required this.onChanged,
    super.key,
    this.min = 1,
    this.enabled = true,
    this.limitMeansScarcity = true,
    this.dense = false,
  });

  final int value;
  final int max;
  final int min;
  final bool enabled;

  /// Si el tope refleja lo que queda en góndola y no un límite fijo por
  /// pedido. Solo entonces se le dice al cliente cuántas unidades quedan:
  /// anunciar el límite administrativo como si fuera escasez sería engañoso.
  final bool limitMeansScarcity;

  /// Versión compacta, para filas de lista.
  final bool dense;

  final ValueChanged<int> onChanged;

  bool get _canDecrease => enabled && value > min;
  bool get _canIncrease => enabled && value < max;

  void _change(int next) {
    if (next == value) return;
    unawaited(HapticFeedback.selectionClick());
    onChanged(next);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final atMax = value >= max;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          container: true,
          label: 'Cantidad',
          value: '$value',
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _StepButton(
                icon: Icons.remove,
                tooltip: 'Quitar una unidad',
                onPressed: _canDecrease ? () => _change(value - 1) : null,
                dense: dense,
              ),
              Container(
                constraints: BoxConstraints(minWidth: dense ? 36 : 48),
                alignment: Alignment.center,
                child: ExcludeSemantics(
                  child: Text(
                    '$value',
                    style: dense
                        ? theme.textTheme.titleMedium
                        : theme.textTheme.titleLarge,
                  ),
                ),
              ),
              _StepButton(
                icon: Icons.add,
                tooltip: atMax
                    ? 'No hay más unidades disponibles'
                    : 'Agregar una unidad',
                onPressed: _canIncrease ? () => _change(value + 1) : null,
                dense: dense,
              ),
            ],
          ),
        ),
        if (atMax && limitMeansScarcity && max > 0) ...[
          SizedBox(height: dense ? 2 : 6),
          Text(
            max == 1
                ? 'Queda 1 unidad disponible'
                : 'Quedan $max unidades disponibles',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}

class _StepButton extends StatelessWidget {
  const _StepButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    required this.dense,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool dense;

  @override
  Widget build(BuildContext context) => IconButton.outlined(
    tooltip: tooltip,
    onPressed: onPressed,
    iconSize: dense ? 18 : 22,
    // Debajo de 44 el dedo falla; es el mínimo táctil recomendado.
    constraints: BoxConstraints.tightFor(
      width: dense ? 44 : 48,
      height: dense ? 44 : 48,
    ),
    icon: Icon(icon),
  );
}
