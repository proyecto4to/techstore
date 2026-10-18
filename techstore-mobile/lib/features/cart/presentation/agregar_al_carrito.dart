import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/native/haptics_service.dart';
import '../../catalog/domain/product.dart';
import '../application/cart_controller.dart';

/// Agrega al carrito y le cuenta al cliente qué pasó.
///
/// Está suelto para que el catálogo, la ficha, la vidriera y los favoritos den
/// exactamente la misma respuesta ante el mismo caso. El vuelo hasta el
/// carrito lo hace el botón, que es quien sabe desde dónde sale.
Future<bool> agregarAlCarrito(
  BuildContext context,
  WidgetRef ref,
  Product producto, {
  int cantidad = 1,
}) async {
  final resultado = ref
      .read(cartProvider.notifier)
      .add(producto, quantity: cantidad);

  unawaited(
    ref
        .read(hapticsServiceProvider)
        .play(resultado.isSuccess ? HapticCue.success : HapticCue.tap),
  );

  if (context.mounted) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(resultado.message),
          duration: Duration(seconds: resultado.isSuccess ? 2 : 4),
          action: resultado.isSuccess
              ? SnackBarAction(
                  label: 'Ver carrito',
                  onPressed: () => context.push('/cart'),
                )
              : null,
        ),
      );
  }
  return resultado.added > 0;
}
