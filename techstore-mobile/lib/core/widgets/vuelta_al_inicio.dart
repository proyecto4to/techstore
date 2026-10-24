import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Para una pantalla a la que se puede llegar sin nada detrás: el pedido recién
/// hecho, que reemplaza al checkout, o el enlace de una notificación.
///
/// Sin esto no había flecha para volver, y el «atrás» del teléfono cerraba la
/// app justo después de comprar. Si no hay pantalla anterior, la flecha y el
/// «atrás» llevan al inicio; si la hay, se comportan como siempre.
class VueltaAlInicio extends StatelessWidget {
  const VueltaAlInicio({required this.child, super.key});

  final Widget child;

  /// La flecha de la barra cuando no hay pantalla anterior; nula si la hay,
  /// para que la barra ponga la suya.
  static Widget? flecha(BuildContext context) =>
      context.canPop() ? null : BackButton(onPressed: () => context.go('/'));

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: context.canPop(),
    onPopInvokedWithResult: (salio, _) {
      if (!salio) context.go('/');
    },
    child: child,
  );
}
