import 'dart:async';

import 'package:flutter/material.dart';

import '../diseno/movimiento.dart';
import '../diseno/paleta.dart';

/// Entrada de un elemento: sube un poco y aparece. En una lista, [orden]
/// escalona la cascada.
///
/// Con [clave], anima una sola vez por sesión: una tarjeta que vuelve a la
/// pantalla al desplazar no repite su entrada, que a la segunda ya molesta.
class Aparicion extends StatefulWidget {
  const Aparicion({
    required this.child,
    this.orden = 0,
    this.clave,
    this.desplazamiento = 18,
    this.escala = 1,
    this.animar = true,
    super.key,
  });

  final Widget child;

  /// En una lista larga, solo lo que se ve al abrir entra animado. Lo que
  /// aparece después, al desplazar, llega directo: animar cada tarjeta
  /// mientras se desliza suma capas de opacidad justo cuando más se necesita
  /// fluidez.
  final bool animar;

  /// Posición en la cascada; se topa para que el décimo no espere de más.
  final int orden;
  final String? clave;

  /// Cuánto sube al entrar, en puntos.
  final double desplazamiento;

  /// Desde qué escala crece; 1 no crece.
  final double escala;

  static final _vistas = <String>{};

  /// Para las pruebas: que todo vuelva a animarse.
  @visibleForTesting
  static void olvidarVistas() => _vistas.clear();

  @override
  State<Aparicion> createState() => _AparicionState();
}

class _AparicionState extends State<Aparicion>
    with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(
    vsync: this,
    duration: Movimiento.medio,
  );
  Timer? _espera;
  bool _decidido = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_decidido) return;
    _decidido = true;
    final clave = widget.clave;
    if (!widget.animar ||
        Movimiento.reducido(context) ||
        (clave != null && Aparicion._vistas.contains(clave))) {
      _control.value = 1;
      return;
    }
    if (clave != null) Aparicion._vistas.add(clave);
    final demora = Movimiento.cascada * widget.orden.clamp(0, 8);
    if (demora == Duration.zero) {
      _control.forward();
    } else {
      _espera = Timer(demora, () {
        if (mounted) _control.forward();
      });
    }
  }

  @override
  void dispose() {
    _espera?.cancel();
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.animar) return widget.child;
    final curva = CurvedAnimation(parent: _control, curve: Movimiento.llegada);
    return FadeTransition(
      opacity: CurvedAnimation(parent: _control, curve: Curves.easeOut),
      child: AnimatedBuilder(
        animation: curva,
        builder: (context, hijo) => Transform.translate(
          offset: Offset(0, widget.desplazamiento * (1 - curva.value)),
          child: widget.escala == 1
              ? hijo
              : Transform.scale(
                  scale: widget.escala + (1 - widget.escala) * curva.value,
                  child: hijo,
                ),
        ),
        child: widget.child,
      ),
    );
  }
}

/// Algo que se hunde apenas al apretarlo: la respuesta física al toque.
class Presionable extends StatefulWidget {
  const Presionable({
    required this.child,
    required this.onTap,
    this.onLongPress,
    this.hundimiento = 0.97,
    this.etiqueta,
    super.key,
  });

  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final double hundimiento;

  /// Lo que dice el lector de pantalla. Sin ella, se lee el contenido.
  final String? etiqueta;

  @override
  State<Presionable> createState() => _PresionableState();
}

class _PresionableState extends State<Presionable> {
  bool _apretado = false;

  void _apretar(bool valor) {
    if (_apretado != valor) setState(() => _apretado = valor);
  }

  @override
  Widget build(BuildContext context) {
    final reducido = Movimiento.reducido(context);
    final gesto = GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: widget.onTap,
      onLongPress: widget.onLongPress,
      onTapDown: widget.onTap == null ? null : (_) => _apretar(true),
      onTapUp: (_) => _apretar(false),
      onTapCancel: () => _apretar(false),
      child: AnimatedScale(
        scale: _apretado && !reducido ? widget.hundimiento : 1,
        duration: Movimiento.toque,
        curve: Movimiento.estandar,
        child: widget.child,
      ),
    );
    // Sin etiqueta propia no se agrega un nodo: el toque se suma al nodo que
    // envuelve a esta pieza. Un nodo vacío en el medio dejaba al lector de
    // pantalla leyendo «botón» sin nombre en lugar de la etiqueta de afuera.
    if (widget.etiqueta == null) return gesto;
    return Semantics(button: true, label: widget.etiqueta, child: gesto);
  }
}

/// Un texto que se escribe solo, con el cursor de la marca al final. El
/// cursor parpadea dos veces y se apaga: no queda latiendo en pantalla.
class TextoTecleado extends StatefulWidget {
  const TextoTecleado(
    this.texto, {
    required this.estilo,
    this.demora = Duration.zero,
    super.key,
  });

  final String texto;
  final TextStyle estilo;
  final Duration demora;

  @override
  State<TextoTecleado> createState() => _TextoTecleadoState();
}

class _TextoTecleadoState extends State<TextoTecleado>
    with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(
    vsync: this,
    duration: Duration(milliseconds: 260 + 28 * widget.texto.length + 700),
  );
  Timer? _espera;
  bool _decidido = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_decidido) return;
    _decidido = true;
    if (Movimiento.reducido(context)) {
      _control.value = 1;
    } else if (widget.demora == Duration.zero) {
      _control.forward();
    } else {
      _espera = Timer(widget.demora, () {
        if (mounted) _control.forward();
      });
    }
  }

  @override
  void dispose() {
    _espera?.cancel();
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final total = _control.duration!.inMilliseconds;
    final escribiendo = (28 * widget.texto.length + 260) / total;
    return Semantics(
      label: widget.texto,
      excludeSemantics: true,
      child: AnimatedBuilder(
        animation: _control,
        builder: (context, _) {
          final t = _control.value;
          final letras = (widget.texto.length * (t / escribiendo).clamp(0, 1))
              .round();
          // Después de escribir: dos parpadeos y se apaga.
          final resto = ((t - escribiendo) / (1 - escribiendo)).clamp(0.0, 1.0);
          final cursorVisible =
              t < 1 && (t < escribiendo || (resto * 4).floor().isEven);
          return Text.rich(
            TextSpan(
              children: [
                TextSpan(text: widget.texto.substring(0, letras)),
                if (cursorVisible)
                  WidgetSpan(
                    alignment: PlaceholderAlignment.middle,
                    child: _CursorDeTexto(
                      alto: (widget.estilo.fontSize ?? 12) * 1.05,
                    ),
                  ),
                // El resto ocupa su lugar invisible: así la línea no cambia de
                // ancho mientras se escribe y nada salta alrededor.
                TextSpan(
                  text: widget.texto.substring(letras),
                  style: const TextStyle(color: Color(0x00000000)),
                ),
              ],
            ),
            style: widget.estilo,
            maxLines: 1,
            overflow: TextOverflow.clip,
          );
        },
      ),
    );
  }
}

class _CursorDeTexto extends StatelessWidget {
  const _CursorDeTexto({required this.alto});

  final double alto;

  @override
  Widget build(BuildContext context) => Container(
    width: alto * 0.48,
    height: alto,
    margin: const EdgeInsets.only(left: 2),
    color: Paleta.cian,
  );
}
