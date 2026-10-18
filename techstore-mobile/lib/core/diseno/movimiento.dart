import 'package:flutter/material.dart';

/// Duraciones y curvas de la marca, y la regla de accesibilidad que manda
/// sobre todas: si el sistema pide quitar animaciones, no hay desplazamientos.
///
/// La marca se mueve como un cursor: aparece, se ubica y se queda quieta. Nada
/// queda en bucle salvo un indicador de carga, que muere con la carga.
abstract final class Movimiento {
  /// Respuesta al toque.
  static const toque = Duration(milliseconds: 120);

  /// Cambio de estado de un control.
  static const corto = Duration(milliseconds: 200);

  /// Cambio de contenido dentro de una pantalla.
  static const medio = Duration(milliseconds: 320);

  /// Paso entre pantallas.
  static const pagina = Duration(milliseconds: 380);

  /// Presentación de un producto o de la vidriera.
  static const escena = Duration(milliseconds: 620);

  /// Separación entre elementos de una cascada de entrada.
  static const cascada = Duration(milliseconds: 55);

  /// Salida rápida y llegada suave: lo que entra se ubica sin rebotar.
  static const llegada = Cubic(0.05, 0.7, 0.1, 1);

  /// Para lo que se va: arranca lento y se acelera.
  static const salida = Cubic(0.3, 0, 0.8, 0.15);

  static const estandar = Cubic(0.2, 0, 0, 1);

  /// Si esta persona pidió que no haya animaciones. Lo respeta toda la app:
  /// las transiciones pasan a un corte con fundido y nada se desplaza.
  static bool reducido(BuildContext context) =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  /// La duración pedida, o cero si las animaciones están quitadas.
  static Duration o(BuildContext context, Duration duracion) =>
      reducido(context) ? Duration.zero : duracion;
}

/// Transición entre pantallas de la marca: la nueva sube un poco y se enfoca,
/// la anterior se hunde en el negro.
///
/// No desliza la pantalla entera de costado: sobre fondo negro, un deslizamiento
/// largo deja una franja vacía que se ve como un error de dibujo.
final class TransicionDeMarca extends PageTransitionsBuilder {
  const TransicionDeMarca();

  @override
  Duration get transitionDuration => Movimiento.pagina;

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 300);

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (Movimiento.reducido(context)) {
      return FadeTransition(opacity: animation, child: child);
    }
    final entrada = CurvedAnimation(
      parent: animation,
      curve: Movimiento.llegada,
      reverseCurve: Movimiento.salida,
    );
    final aparicion = CurvedAnimation(
      parent: animation,
      curve: const Interval(0, 0.55, curve: Curves.easeOut),
      reverseCurve: const Interval(0.3, 1, curve: Curves.easeIn),
    );
    final hundida = CurvedAnimation(
      parent: secondaryAnimation,
      curve: Movimiento.estandar,
    );
    return AnimatedBuilder(
      animation: hundida,
      builder: (context, hijo) {
        final t = hundida.value;
        // La pantalla de atrás se aleja y se oscurece, sin desaparecer: al
        // volver, sigue ahí donde estaba. La página va siempre en el mismo
        // lugar del árbol, con o sin efecto: si cambiara de padre al empezar
        // la transición, perdería su estado —el scroll, un formulario a medio
        // llenar— justo cuando la persona abre otra cosa encima.
        return Stack(
          fit: StackFit.passthrough,
          children: [
            Transform.scale(scale: 1 - 0.04 * t, child: hijo),
            // Una capa negra encima en vez de un filtro de color sobre la
            // página: el filtro obliga a pintar la página entera en una capa
            // aparte en cada cuadro de la transición.
            if (t > 0)
              Positioned.fill(
                child: IgnorePointer(
                  child: ColoredBox(
                    color: Colors.black.withValues(alpha: 0.55 * t),
                  ),
                ),
              ),
          ],
        );
      },
      child: FadeTransition(
        opacity: aparicion,
        child: SlideTransition(
          position: Tween(
            begin: const Offset(0, 0.035),
            end: Offset.zero,
          ).animate(entrada),
          child: ScaleTransition(
            scale: Tween(begin: 0.985, end: 1.0).animate(entrada),
            child: child,
          ),
        ),
      ),
    );
  }
}
