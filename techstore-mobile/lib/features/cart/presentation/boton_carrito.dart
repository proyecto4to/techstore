import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/animacion/vuelo_al_carrito.dart';
import '../../../core/diseno/movimiento.dart';
import '../application/cart_controller.dart';

/// El carrito de las barras, con su contador.
///
/// Es destino del vuelo de los productos: cuando algo entra, el ícono rebota y
/// el número cambia con un salto, que es la segunda mitad de la respuesta al
/// agregar.
class BotonCarrito extends ConsumerStatefulWidget {
  const BotonCarrito({super.key});

  @override
  ConsumerState<BotonCarrito> createState() => _BotonCarritoState();
}

class _BotonCarritoState extends ConsumerState<BotonCarrito>
    with SingleTickerProviderStateMixin
    implements DestinoDelCarrito {
  late final AnimationController _rebote = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 420),
  );

  late final Animation<double> _escala = TweenSequence([
    TweenSequenceItem(tween: Tween(begin: 1.0, end: 1.28), weight: 35),
    TweenSequenceItem(
      tween: Tween(
        begin: 1.28,
        end: 1.0,
      ).chain(CurveTween(curve: Curves.elasticOut)),
      weight: 65,
    ),
  ]).animate(_rebote);

  @override
  void initState() {
    super.initState();
    DestinosDelCarrito.registrar(this);
  }

  @override
  void dispose() {
    DestinosDelCarrito.quitar(this);
    _rebote.dispose();
    super.dispose();
  }

  @override
  Rect? get rectEnPantalla {
    if (!mounted) return null;
    // Una pantalla tapada por otra sigue montada: su carrito no cuenta.
    final ruta = ModalRoute.of(context);
    if (ruta != null && !ruta.isCurrent) return null;
    final caja = context.findRenderObject();
    if (caja is! RenderBox || !caja.attached || !caja.hasSize) return null;
    return caja.localToGlobal(Offset.zero) & caja.size;
  }

  @override
  void rebotar() {
    if (!mounted || Movimiento.reducido(context)) return;
    _rebote.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final unidades = ref.watch(
      cartProvider.select(
        (items) => items.fold(0, (suma, item) => suma + item.quantity),
      ),
    );
    return ScaleTransition(
      scale: _escala,
      child: IconButton(
        tooltip: unidades == 0
            ? 'Carrito vacío'
            : 'Carrito, $unidades ${unidades == 1 ? 'unidad' : 'unidades'}',
        onPressed: () => context.push('/cart'),
        icon: Badge(
          isLabelVisible: unidades > 0,
          label: AnimatedSwitcher(
            duration: Movimiento.o(context, Movimiento.corto),
            transitionBuilder: (hijo, animacion) =>
                ScaleTransition(scale: animacion, child: hijo),
            // El número solo se ve: el lector de pantalla dice el tooltip
            // («Carrito, 3 unidades»). Si no, anunciaba «3, botón».
            child: ExcludeSemantics(
              key: ValueKey(unidades),
              child: Text(unidades > 99 ? '99+' : '$unidades'),
            ),
          ),
          child: const Icon(Icons.shopping_bag_outlined),
        ),
      ),
    );
  }
}
