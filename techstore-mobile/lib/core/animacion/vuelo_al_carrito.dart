import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../diseno/movimiento.dart';
import '../diseno/paleta.dart';

/// Un ícono de carrito que puede recibir el vuelo de un producto.
abstract interface class DestinoDelCarrito {
  /// Dónde está en pantalla, o nulo si ahora no se ve (otra pantalla encima).
  Rect? get rectEnPantalla;

  /// El golpe de llegada: el ícono se agranda y vuelve.
  void rebotar();
}

/// Los íconos de carrito montados en este momento.
///
/// No se usa una `GlobalKey`: el catálogo y la ficha tienen cada uno su
/// carrito en la barra y los dos están montados a la vez en la pila de
/// pantallas; con una sola llave, Flutter falla por llave duplicada.
abstract final class DestinosDelCarrito {
  static final _montados = <DestinoDelCarrito>[];

  static void registrar(DestinoDelCarrito destino) => _montados.add(destino);

  static void quitar(DestinoDelCarrito destino) => _montados.remove(destino);

  /// El último registrado que se ve en la pantalla actual.
  static DestinoDelCarrito? get visible {
    for (final destino in _montados.reversed) {
      if (destino.rectEnPantalla != null) return destino;
    }
    return null;
  }
}

/// Hace volar el producto desde [desde] hasta el carrito que esté a la vista.
///
/// Es la mitad visual de «agregado»: el carrito vive en otra pantalla, y sin
/// esto el producto entraba sin que se viera a dónde. Si no hay carrito a la
/// vista o el sistema pidió quitar animaciones, no vuela: solo rebota el
/// contador, que alcanza para enterarse.
void lanzarVueloAlCarrito(
  BuildContext context, {
  required Rect desde,
  ImageProvider? imagen,
}) {
  final destino = DestinosDelCarrito.visible;
  if (destino == null) return;
  final hasta = destino.rectEnPantalla!;
  final overlay = Overlay.maybeOf(context, rootOverlay: true);
  if (overlay == null || Movimiento.reducido(context)) {
    destino.rebotar();
    return;
  }
  late final OverlayEntry entrada;
  entrada = OverlayEntry(
    builder: (_) => _Vuelo(
      desde: desde.center,
      hasta: hasta.center,
      imagen: imagen,
      alLlegar: () {
        entrada.remove();
        destino.rebotar();
      },
    ),
  );
  overlay.insert(entrada);
}

class _Vuelo extends StatefulWidget {
  const _Vuelo({
    required this.desde,
    required this.hasta,
    required this.alLlegar,
    this.imagen,
  });

  final Offset desde;
  final Offset hasta;
  final ImageProvider? imagen;
  final VoidCallback alLlegar;

  @override
  State<_Vuelo> createState() => _VueloState();
}

class _VueloState extends State<_Vuelo> with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 640),
  )..forward().whenComplete(widget.alLlegar);

  @override
  void dispose() {
    _control.dispose();
    super.dispose();
  }

  /// Punto de una curva cuadrática que sube antes de bajar al carrito: un
  /// arco, como algo que se lanza, no una línea recta.
  Offset _punto(double t) {
    final a = widget.desde;
    final c = widget.hasta;
    final b = Offset(
      a.dx + (c.dx - a.dx) * 0.25,
      math.min(a.dy, c.dy) - 140,
    );
    final u = 1 - t;
    return a * (u * u) + b * (2 * u * t) + c * (t * t);
  }

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: AnimatedBuilder(
      animation: _control,
      builder: (context, _) {
        final t = Curves.easeInOutCubic.transform(_control.value);
        final punto = _punto(t);
        final escala = 1 - 0.62 * t;
        const lado = 46.0;
        return Stack(
          children: [
            Positioned(
              left: punto.dx - lado / 2,
              top: punto.dy - lado / 2,
              child: Opacity(
                opacity: t > 0.86 ? (1 - t) / 0.14 : 1,
                child: Transform.scale(
                  scale: escala,
                  child: Container(
                    width: lado,
                    height: lado,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Paleta.superficieElevada,
                      border: Border.all(color: Paleta.cian, width: 2),
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x8038BDF8),
                          blurRadius: 18,
                          spreadRadius: 1,
                        ),
                      ],
                      image: widget.imagen == null
                          ? null
                          : DecorationImage(
                              image: ResizeImage.resizeIfNeeded(
                                140,
                                null,
                                widget.imagen!,
                              ),
                              fit: BoxFit.cover,
                            ),
                    ),
                    child: widget.imagen == null
                        ? const Icon(
                            Icons.shopping_bag_outlined,
                            color: Paleta.cian,
                            size: 22,
                          )
                        : null,
                  ),
                ),
              ),
            ),
          ],
        );
      },
    ),
  );
}
