import 'dart:async';

import 'package:flutter/material.dart';

import '../../diseno/movimiento.dart';
import '../../diseno/paleta.dart';

/// El isotipo de TechStore dibujado en código, con las mismas curvas que el
/// generador de la marca (`docs/marca/generador/generar-marca.mjs`).
///
/// Dibujado y no como imagen para que sea nítido a cualquier tamaño y para
/// poder mover el cursor: el fragmento cian es la única parte de la marca que
/// se anima.
class Isotipo extends StatelessWidget {
  const Isotipo({
    this.ancho = 40,
    this.cursor = 1,
    this.encendido = 1,
    this.monocromo,
    super.key,
  });

  /// Ancho de la T; el alto sale de la proporción de la marca (72 × 66).
  final double ancho;

  /// Dónde está el cursor: en 0 todavía no llegó, en 1 está en su lugar.
  final double cursor;

  /// Cuánto se ve el cursor ya ubicado: baja durante el parpadeo sin moverlo.
  final double encendido;

  /// Para pintar todo en un solo color (sellos, avisos de una tinta).
  final Color? monocromo;

  static const proporcion = _GrillaIsotipo.alto / _GrillaIsotipo.ancho;

  @override
  Widget build(BuildContext context) => Semantics(
    image: true,
    label: 'TechStore',
    child: CustomPaint(
      size: Size(ancho, ancho * proporcion),
      painter: _PintorIsotipo(
        cursor: cursor,
        encendido: encendido,
        monocromo: monocromo,
      ),
    ),
  );
}

/// El isotipo con el cursor entrando: se desliza por el corte hasta su lugar y
/// parpadea una vez, como al terminar de escribir.
///
/// Con [repetir], el cursor late mientras la pieza está en pantalla: es el
/// indicador de carga de la marca. Sin repetir, entra una vez y se queda.
class IsotipoAnimado extends StatefulWidget {
  const IsotipoAnimado({
    this.ancho = 40,
    this.repetir = false,
    this.demora = Duration.zero,
    super.key,
  });

  final double ancho;
  final bool repetir;
  final Duration demora;

  @override
  State<IsotipoAnimado> createState() => _IsotipoAnimadoState();
}

class _IsotipoAnimadoState extends State<IsotipoAnimado>
    with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  );
  Timer? _espera;
  bool _arrancado = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_arrancado) return;
    _arrancado = true;
    if (Movimiento.reducido(context)) {
      _control.value = 1;
      return;
    }
    // Sin demora se arranca en el acto: un temporizador de cero igual queda
    // pendiente, y una prueba que termina antes de que corra falla con «A
    // Timer is still pending».
    if (widget.demora == Duration.zero) {
      _arrancar();
    } else {
      _espera = Timer(widget.demora, _arrancar);
    }
  }

  void _arrancar() {
    if (!mounted) return;
    if (widget.repetir) {
      _control.repeat();
    } else {
      _control.forward();
    }
  }

  @override
  void dispose() {
    _espera?.cancel();
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _control,
    builder: (context, _) {
      final t = _control.value;
      // Primero llega (0 → 0,55), después parpadea una vez (0,7 → 0,8).
      return Isotipo(
        ancho: widget.ancho,
        cursor: Movimiento.llegada.transform((t / 0.55).clamp(0, 1)),
        encendido: t > 0.7 && t < 0.8 ? 0.25 : 1,
      );
    },
  );
}

abstract final class _GrillaIsotipo {
  static const x = 12.0;
  static const y = 16.0;
  static const ancho = 72.0;
  static const alto = 66.0;
}

class _PintorIsotipo extends CustomPainter {
  _PintorIsotipo({
    required this.cursor,
    required this.encendido,
    this.monocromo,
  });

  final double cursor;
  final double encendido;
  final Color? monocromo;

  static final Path _t = Path()
    ..moveTo(12, 26)
    ..lineTo(28, 16)
    ..lineTo(81, 16)
    ..quadraticBezierTo(84, 16, 84, 19)
    ..lineTo(84, 33)
    ..quadraticBezierTo(84, 36, 81, 36)
    ..lineTo(64, 36)
    ..lineTo(64, 52)
    ..lineTo(32, 72)
    ..lineTo(32, 36)
    ..lineTo(15, 36)
    ..quadraticBezierTo(12, 36, 12, 33)
    ..close();

  static final Path _cursor = Path()
    ..moveTo(32, 78)
    ..lineTo(64, 58)
    ..lineTo(64, 79)
    ..quadraticBezierTo(64, 82, 61, 82)
    ..lineTo(35, 82)
    ..quadraticBezierTo(32, 82, 32, 79)
    ..close();

  @override
  void paint(Canvas canvas, Size size) {
    final escala = size.width / _GrillaIsotipo.ancho;
    canvas
      ..save()
      ..scale(escala)
      ..translate(-_GrillaIsotipo.x, -_GrillaIsotipo.y);

    final pinturaT = Paint()..isAntiAlias = true;
    if (monocromo != null) {
      pinturaT.color = monocromo!;
    } else {
      pinturaT.shader = const LinearGradient(
        colors: [Paleta.azulElectrico, Paleta.azulPrincipal],
      ).createShader(const Rect.fromLTRB(12, 16, 84, 82));
    }
    canvas.drawPath(_t, pinturaT);

    if (cursor > 0) {
      // El cursor viene deslizándose por el corte, paralelo a él: el vector
      // (32, −20) es la dirección del corte en la grilla.
      final faltante = 1 - cursor.clamp(0.0, 1.0);
      canvas
        ..save()
        ..translate(-12.8 * faltante, 8 * faltante);
      canvas.drawPath(
        _cursor,
        Paint()
          ..isAntiAlias = true
          ..color = (monocromo ?? Paleta.cian).withValues(
            alpha: cursor.clamp(0.0, 1.0) * encendido.clamp(0.0, 1.0),
          ),
      );
      canvas.restore();
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_PintorIsotipo viejo) =>
      viejo.cursor != cursor ||
      viejo.encendido != encendido ||
      viejo.monocromo != monocromo;
}
