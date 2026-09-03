import 'dart:async';

import 'package:flutter/material.dart';

/// Cuánto dura la confirmación en el botón antes de volver a su estado normal.
///
/// Suficiente para verla, corto para no estorbar a quien compra varias cosas
/// seguidas.
const Duration _confirmationHold = Duration(milliseconds: 1400);
const Duration _morph = Duration(milliseconds: 180);

/// Botón de agregar al carrito que confirma en el lugar.
///
/// Al tocarlo se convierte por un momento en una tilde: es la respuesta que
/// faltaba: sin ella, agregar algo parecía no hacer nada, porque el carrito
/// vive en otra pantalla.
class AddToCartButton extends StatefulWidget {
  const AddToCartButton({
    required this.onPressed,
    super.key,
    this.label = 'Agregar al carrito',
    this.disabledLabel = 'Sin stock',
    this.compact = false,
  });

  /// Devuelve `true` si el producto entró al carrito. Solo entonces se
  /// confirma: si no entró nada, mostrar una tilde sería mentir.
  final Future<bool> Function()? onPressed;

  final String label;
  final String disabledLabel;

  /// Solo icono, para las tarjetas del catálogo.
  final bool compact;

  @override
  State<AddToCartButton> createState() => _AddToCartButtonState();
}

class _AddToCartButtonState extends State<AddToCartButton> {
  bool _busy = false;
  bool _confirmed = false;
  Timer? _reset;

  @override
  void dispose() {
    _reset?.cancel();
    super.dispose();
  }

  Future<void> _handle() async {
    // Blinda contra el doble toque: sin esto, dos toques rápidos agregan dos
    // veces y el cliente no se entera.
    if (_busy || widget.onPressed == null) return;
    setState(() => _busy = true);
    try {
      final added = await widget.onPressed!();
      if (!mounted) return;
      if (added) {
        setState(() => _confirmed = true);
        _reset?.cancel();
        _reset = Timer(_confirmationHold, () {
          if (mounted) setState(() => _confirmed = false);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onPressed != null;
    final onTap = enabled && !_busy ? _handle : null;

    if (widget.compact) {
      return IconButton.filledTonal(
        tooltip: enabled ? widget.label : widget.disabledLabel,
        onPressed: onTap,
        icon: AnimatedSwitcher(
          duration: _morph,
          child: _confirmed
              ? const Icon(Icons.check, key: ValueKey('ok'))
              : const Icon(Icons.add_shopping_cart, key: ValueKey('add')),
        ),
      );
    }

    return FilledButton.icon(
      onPressed: onTap,
      icon: AnimatedSwitcher(
        duration: _morph,
        child: _busy
            ? const SizedBox.square(
                key: ValueKey('busy'),
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Icon(
                _confirmed ? Icons.check_circle_outline : Icons.add_shopping_cart,
                key: ValueKey(_confirmed),
              ),
      ),
      label: AnimatedSwitcher(
        duration: _morph,
        child: Text(
          !enabled
              ? widget.disabledLabel
              : _confirmed
              ? 'Agregado'
              : widget.label,
          key: ValueKey('$enabled-$_confirmed'),
        ),
      ),
    );
  }
}
