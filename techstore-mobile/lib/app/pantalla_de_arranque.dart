import 'package:flutter/material.dart';

import '../core/diseno/movimiento.dart';
import '../core/diseno/paleta.dart';
import '../core/widgets/marca/isotipo.dart';
import '../core/widgets/marca/logotipo.dart';

/// Lo que se ve mientras se restaura la sesión.
///
/// Continúa el splash nativo sin salto: el mismo isotipo, del mismo tamaño y
/// en el mismo punto. Android lo dibuja centrado en la pantalla entera, así
/// que acá se centra igual y no dentro del área segura —que descuenta la barra
/// de navegación y lo correría unos puntos hacia arriba—. Mientras espera, el
/// cursor late; el nombre aparece abajo.
class PantallaDeArranque extends StatefulWidget {
  const PantallaDeArranque({super.key});

  /// El ancho del isotipo en el splash nativo: 72 unidades de grilla a
  /// 7,627 px por unidad, en una imagen de 4×. Lo calcula y lo imprime el
  /// generador de la marca; si cambia allá, cambia acá.
  static const anchoDelIsotipo = 137.3;

  @override
  State<PantallaDeArranque> createState() => _PantallaDeArranqueState();
}

class _PantallaDeArranqueState extends State<PantallaDeArranque>
    with SingleTickerProviderStateMixin {
  late final AnimationController _latido = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!Movimiento.reducido(context) && !_latido.isAnimating) {
      _latido.repeat();
    }
  }

  @override
  void dispose() {
    _latido.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pantalla = MediaQuery.sizeOf(context);
    const ancho = PantallaDeArranque.anchoDelIsotipo;
    const alto = ancho * Isotipo.proporcion;
    return Scaffold(
      backgroundColor: Paleta.fondo,
      body: Semantics(
        label: 'Cargando TechStore',
        liveRegion: true,
        excludeSemantics: true,
        child: Stack(
          children: [
            Positioned(
              left: pantalla.width / 2 - ancho / 2,
              top: pantalla.height / 2 - alto / 2,
              child: AnimatedBuilder(
                animation: _latido,
                builder: (context, _) {
                  final t = _latido.value;
                  // El cursor se apaga un instante por vuelta, como el de un
                  // editor esperando que se escriba.
                  return Isotipo(
                    ancho: ancho,
                    encendido: t > 0.55 && t < 0.78 ? 0.18 : 1,
                  );
                },
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 56 + MediaQuery.viewPaddingOf(context).bottom / 2,
              child: TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: 1),
                duration: Movimiento.o(context, const Duration(milliseconds: 700)),
                curve: const Interval(0.4, 1, curve: Curves.easeOut),
                builder: (context, valor, hijo) =>
                    Opacity(opacity: valor, child: hijo),
                child: const Center(child: Logotipo(tamano: 20)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
