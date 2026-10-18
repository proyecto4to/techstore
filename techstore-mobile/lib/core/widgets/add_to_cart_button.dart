import 'dart:async';

import 'package:flutter/material.dart';

import '../animacion/vuelo_al_carrito.dart';
import '../diseno/medidas.dart';
import '../diseno/movimiento.dart';
import '../diseno/paleta.dart';

/// Cuánto dura la confirmación en el botón antes de volver a su estado normal.
///
/// Suficiente para verla, corto para no estorbar a quien compra varias cosas
/// seguidas.
const Duration _confirmationHold = Duration(milliseconds: 1400);

/// Botón de agregar al carrito que confirma en el lugar.
///
/// Al tocarlo se convierte por un momento en una tilde y el producto vuela
/// hasta el carrito de la barra: es la respuesta que faltaba, porque el
/// carrito vive en otra pantalla y sin ella agregar algo parecía no hacer nada.
class AddToCartButton extends StatefulWidget {
  const AddToCartButton({
    required this.onPressed,
    super.key,
    this.label = 'Agregar al carrito',
    this.disabledLabel = 'Sin stock',
    this.compact = false,
    this.imagenParaVuelo,
  });

  /// Devuelve `true` si el producto entró al carrito. Solo entonces se
  /// confirma: si no entró nada, mostrar una tilde sería mentir.
  final Future<bool> Function()? onPressed;

  final String label;
  final String disabledLabel;

  /// Solo icono, para las tarjetas del catálogo.
  final bool compact;

  /// La foto que viaja al carrito. Sin ella vuela un ícono.
  final ImageProvider? imagenParaVuelo;

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
        _volar();
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

  void _volar() {
    final caja = context.findRenderObject();
    if (caja is! RenderBox || !caja.hasSize) return;
    lanzarVueloAlCarrito(
      context,
      desde: caja.localToGlobal(Offset.zero) & caja.size,
      imagen: widget.imagenParaVuelo,
    );
  }

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onPressed != null;
    final onTap = enabled && !_busy ? _handle : null;
    final transicion = Movimiento.o(context, Movimiento.corto);

    if (widget.compact) {
      return IconButton.filled(
        tooltip: enabled ? widget.label : widget.disabledLabel,
        onPressed: onTap,
        style: IconButton.styleFrom(
          backgroundColor: _confirmed ? Paleta.cian : Paleta.azulElectrico,
          foregroundColor: _confirmed ? Paleta.tinta : Colors.white,
          disabledBackgroundColor: Paleta.superficie,
          disabledForegroundColor: Paleta.textoSecundario,
          minimumSize: const Size(Medidas.toque - 2, Medidas.toque - 2),
          shape: const RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(13)),
          ),
          shadowColor: Paleta.azulElectrico,
          elevation: enabled ? 6 : 0,
        ),
        icon: AnimatedSwitcher(
          duration: transicion,
          transitionBuilder: (hijo, animacion) =>
              ScaleTransition(scale: animacion, child: hijo),
          child: _confirmed
              ? const Icon(Icons.check_rounded, key: ValueKey('ok'))
              : const Icon(Icons.add_rounded, key: ValueKey('add')),
        ),
      );
    }

    return FilledButton.icon(
      onPressed: onTap,
      style: _confirmed
          ? FilledButton.styleFrom(
              backgroundColor: Paleta.cian,
              foregroundColor: Paleta.tinta,
              iconColor: Paleta.tinta,
            )
          : null,
      icon: AnimatedSwitcher(
        duration: transicion,
        child: _busy
            ? const SizedBox.square(
                key: ValueKey('busy'),
                dimension: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              )
            : Icon(
                _confirmed
                    ? Icons.check_circle_outline_rounded
                    : Icons.shopping_bag_outlined,
                key: ValueKey(_confirmed),
              ),
      ),
      label: AnimatedSwitcher(
        duration: transicion,
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
