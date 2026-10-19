import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/diseno/forma_con_corte.dart';
import '../../../../core/diseno/movimiento.dart';
import '../../../../core/diseno/paleta.dart';
import '../../../../core/diseno/tipografia.dart';
import '../../domain/product.dart';
import 'estilo_de_coleccion.dart';
import 'fondo_rasterizado.dart';
import 'foto_de_producto.dart';
import 'rasgos_de_imagen.dart';

/// Para qué lugar se arma el escenario. Cuanto más grande, más capas: en una
/// fila del carrito no entra una marca de agua; en la ficha, sí.
enum Encuadre { miniatura, tarjeta, destacado, ficha }

/// El producto presentado dentro de la composición, no pegado encima de ella.
///
/// Arma capas —luz, patrón de la colección, marca de agua, producto— y elige
/// el tratamiento según lo que la foto *es* ([RasgosDeImagen]): un recorte
/// flota sobre su propia silueta; una foto de estudio se monta en una vitrina
/// iluminada del color de su fondo, así el borde de la foto no se ve; una
/// escena se enmarca sobre su propia luz desenfocada; y sin foto queda una
/// composición tipográfica, nunca un ícono roto.
///
/// Pensado para que desplazar una grilla llena de escenarios no cueste: el
/// fondo se dibuja una vez como imagen ([FondosRasterizados]) y los fundidos
/// son degradados superpuestos, no máscaras que obliguen a pintar cada
/// tarjeta en una capa aparte en cada cuadro.
class EscenarioDeProducto extends ConsumerWidget {
  const EscenarioDeProducto({
    required this.producto,
    this.encuadre = Encuadre.tarjeta,
    this.heroTag,
    this.paralaje = 0,
    this.aireSuperior = 0,
    this.aireInferior = 0,
    super.key,
  });

  final Product producto;
  final Encuadre encuadre;

  /// Si se pasa, la foto viaja entre pantallas con este nombre.
  final Object? heroTag;

  /// Entre −1 y 1: cuánto está corrido el escenario de su lugar (por el
  /// desplazamiento de la lista o del carrusel). Las capas de atrás se mueven
  /// menos que el producto y eso da la profundidad.
  final double paralaje;

  /// Lugar que el producto deja libre arriba para la barra que flota encima
  /// (la ficha): sin él, la foto quedaba debajo del reloj y de los botones.
  /// El fondo y la luz siguen llegando hasta el borde.
  final double aireSuperior;

  /// Lugar que el producto deja libre abajo para el texto que va encima del
  /// escenario (el escaparate): sin él, la foto quedaba debajo del título.
  /// El fondo y la luz siguen ocupando todo.
  final double aireInferior;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final url = ref.watch(urlDeFotoProvider(producto.imageUrl));
    final rasgos = url == null
        ? RasgosDeImagen.ausente
        : ref.watch(rasgosDeFotoProvider(url)).value;
    final foto = url == null ? null : ref.watch(fabricaDeFotosProvider)(url);
    final estilo = EstiloDeColeccion.paraMarca(producto.brand);
    final apagado = producto.maxPerOrder <= 0;

    final escena = LayoutBuilder(
      builder: (context, limites) {
        final tamano = Size(
          limites.maxWidth.isFinite ? limites.maxWidth : 320,
          limites.maxHeight.isFinite ? limites.maxHeight : 320,
        );
        return Stack(
          fit: StackFit.expand,
          children: [
            _Fondo(
              estilo: estilo,
              rasgos: rasgos,
              encuadre: encuadre,
              marcaDeAgua: (producto.brand ?? producto.name).toUpperCase(),
              paralaje: paralaje,
            ),
            // La silueta translúcida del mismo producto: solo con un recorte,
            // donde la forma es el producto y no un rectángulo de foto.
            if (rasgos?.tipo == TipoDeFoto.recortada &&
                foto != null &&
                (encuadre == Encuadre.ficha || encuadre == Encuadre.destacado))
              _Silueta(
                foto: foto,
                acento: estilo.acento,
                tamano: tamano,
                paralaje: paralaje,
              ),
            // Mientras se mira la foto, la vitrina queda vacía y con su luz;
            // cuando se sabe qué foto es, el producto aparece una sola vez,
            // ya con su tratamiento, en lugar de saltar de uno a otro.
            AnimatedSwitcher(
              duration: Movimiento.o(context, Movimiento.medio),
              switchInCurve: Movimiento.llegada,
              child: rasgos == null
                  ? const SizedBox.expand(key: ValueKey('esperando'))
                  : KeyedSubtree(
                      key: ValueKey(rasgos.tipo),
                      child: _envolverHero(
                        context,
                        _Producto(
                          producto: producto,
                          foto: foto,
                          rasgos: rasgos,
                          estilo: estilo,
                          encuadre: encuadre,
                          tamano: tamano,
                          paralaje: paralaje,
                          aireSuperior: aireSuperior,
                          aireInferior: aireInferior,
                        ),
                      ),
                    ),
            ),
          ],
        );
      },
    );

    return Semantics(
      image: true,
      label: 'Foto de ${producto.name}',
      child: ExcludeSemantics(
        child: RepaintBoundary(
          child: ClipRect(
            // Sin stock, el producto se apaga: sigue a la vista, pero se lee
            // de un vistazo que hoy no se puede comprar.
            child: apagado
                ? ColorFiltered(
                    colorFilter: const ColorFilter.matrix(_desaturado),
                    child: Opacity(opacity: 0.55, child: escena),
                  )
                : escena,
          ),
        ),
      ),
    );
  }

  /// Sin animaciones pedidas por el sistema, la foto no viaja: la ficha
  /// aparece con un fundido, como cualquier otra pantalla.
  Widget _envolverHero(BuildContext context, Widget hijo) =>
      heroTag == null || Movimiento.reducido(context)
      ? hijo
      : Hero(
          tag: heroTag!,
          // La foto que viaja es la del destino: así llega ya con su
          // tratamiento definitivo en lugar de transformarse al aterrizar.
          transitionOnUserGestures: true,
          child: hijo,
        );

  static const _desaturado = <double>[
    0.33, 0.59, 0.11, 0, 0, //
    0.33, 0.59, 0.11, 0, 0, //
    0.33, 0.59, 0.11, 0, 0, //
    0, 0, 0, 1, 0,
  ];
}

/// Luz, textura de la colección y marca de agua: todo en una imagen que se
/// dibuja una vez por colección y tamaño.
class _Fondo extends StatelessWidget {
  const _Fondo({
    required this.estilo,
    required this.rasgos,
    required this.encuadre,
    required this.marcaDeAgua,
    required this.paralaje,
  });

  final EstiloDeColeccion estilo;
  final RasgosDeImagen? rasgos;
  final Encuadre encuadre;
  final String marcaDeAgua;
  final double paralaje;

  @override
  Widget build(BuildContext context) {
    if (encuadre == Encuadre.miniatura) {
      return const ColoredBox(color: Paleta.negroAzulado);
    }
    final grande = encuadre != Encuadre.tarjeta;
    // La luz es azul siempre; en los encuadres grandes el tono del producto
    // la entibia apenas, para que una placa con luces violetas no quede bajo
    // una luz que no es suya. En las tarjetas no: cambiaría el fondo cuando
    // llega la foto y obligaría a dibujarlo dos veces.
    final luz = grande
        ? Color.lerp(estilo.acento, rasgos?.tono ?? estilo.acento, 0.18)!
        : estilo.acento;
    final intensidad = grande ? 0.42 : 0.3;
    final margen = grande ? 28.0 : 0.0;
    final clave = [
      estilo.patron.name,
      estilo.acento.toARGB32(),
      luz.toARGB32(),
      encuadre.name,
      marcaDeAgua,
    ].join('|');

    return CustomPaint(
      painter: PintorDeFondoRasterizado(
        clave: clave,
        escala: MediaQuery.devicePixelRatioOf(context),
        margen: margen,
        desplazamiento: Offset(14 * paralaje, -12 * paralaje),
        dibujar: (canvas, tamano) => _dibujarFondo(
          canvas,
          tamano,
          estilo: estilo,
          luz: luz,
          intensidad: intensidad,
          encuadre: encuadre,
          marcaDeAgua: marcaDeAgua,
          margen: margen,
        ),
      ),
      child: const SizedBox.expand(),
    );
  }
}

void _dibujarFondo(
  Canvas canvas,
  Size tamano, {
  required EstiloDeColeccion estilo,
  required Color luz,
  required double intensidad,
  required Encuadre encuadre,
  required String marcaDeAgua,
  required double margen,
}) {
  final todo = Offset.zero & tamano;
  canvas.drawRect(todo, Paint()..color = Paleta.negroPrincipal);
  // La zona que se ve (sin el margen del paralaje) define dónde va la luz.
  final visible = todo.deflate(margen);
  canvas.drawRect(
    todo,
    Paint()
      ..shader = RadialGradient(
        center: Alignment(0, encuadre == Encuadre.ficha ? -0.1 : 0.05),
        radius: 0.85,
        colors: [
          luz.withValues(alpha: intensidad),
          luz.withValues(alpha: intensidad * 0.22),
          Paleta.negroPrincipal.withValues(alpha: 0),
        ],
        stops: const [0, 0.55, 1],
      ).createShader(visible),
  );
  PintorDePatron(
    estilo: estilo,
    opacidad: encuadre == Encuadre.tarjeta ? 0.06 : 0.075,
  ).paint(canvas, tamano);

  // El nombre de la marca en contorno, enorme y cortado por el borde.
  final cuerpo =
      visible.height *
      switch (encuadre) {
        Encuadre.ficha => 0.36,
        Encuadre.destacado => 0.4,
        _ => 0.34,
      };
  final texto = TextPainter(
    text: TextSpan(
      text: marcaDeAgua,
      style: TextStyle(
        fontFamily: Tipografia.titulares,
        fontWeight: FontWeight.w700,
        fontSize: cuerpo,
        height: 1,
        letterSpacing: -cuerpo * 0.04,
        foreground: Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = math.max(1, cuerpo / 90)
          ..color = estilo.acento.withValues(alpha: 0.16),
      ),
    ),
    textDirection: TextDirection.ltr,
    maxLines: 1,
  )..layout();
  texto.paint(
    canvas,
    Offset(visible.left - cuerpo * 0.08, visible.bottom - cuerpo * 0.78),
  );
  texto.dispose();
}

/// La silueta translúcida del producto recortado, más grande y corrida. Se
/// tiñe en el mismo dibujo de la imagen: sin capa de opacidad aparte.
class _Silueta extends StatelessWidget {
  const _Silueta({
    required this.foto,
    required this.acento,
    required this.tamano,
    required this.paralaje,
  });

  final ImageProvider foto;
  final Color acento;
  final Size tamano;
  final double paralaje;

  @override
  Widget build(BuildContext context) => Positioned.fill(
    child: Transform.translate(
      offset: Offset(tamano.width * 0.16 - 26 * paralaje, -8),
      child: Transform.scale(
        scale: 1.45,
        child: Image(
          image: ResizeImage.resizeIfNeeded(
            (tamano.width * 0.8).round(),
            null,
            foto,
          ),
          fit: BoxFit.contain,
          gaplessPlayback: true,
          color: acento.withValues(alpha: 0.14),
          colorBlendMode: BlendMode.srcIn,
          errorBuilder: (_, _, _) => const SizedBox.shrink(),
        ),
      ),
    ),
  );
}

/// El producto, con el tratamiento que le corresponde a su foto.
class _Producto extends StatelessWidget {
  const _Producto({
    required this.producto,
    required this.foto,
    required this.rasgos,
    required this.estilo,
    required this.encuadre,
    required this.tamano,
    required this.paralaje,
    this.aireSuperior = 0,
    this.aireInferior = 0,
  });

  final Product producto;
  final ImageProvider? foto;
  final RasgosDeImagen rasgos;
  final EstiloDeColeccion estilo;
  final Encuadre encuadre;
  final Size tamano;
  final double paralaje;
  final double aireSuperior;
  final double aireInferior;

  /// El mismo margen de cada lado, más el aire de arriba y de abajo.
  EdgeInsets _relleno(double margen) => EdgeInsets.fromLTRB(
    margen,
    margen + aireSuperior,
    margen,
    margen + aireInferior,
  );

  double get _margen => switch (encuadre) {
    Encuadre.miniatura => 0,
    Encuadre.tarjeta => 12,
    Encuadre.destacado => 22,
    Encuadre.ficha => 28,
  };

  bool get _grande =>
      encuadre == Encuadre.ficha || encuadre == Encuadre.destacado;

  @override
  Widget build(BuildContext context) {
    if (foto == null || rasgos.tipo == TipoDeFoto.ausente) {
      return _SinFoto(producto: producto, estilo: estilo, encuadre: encuadre);
    }
    return Transform.translate(
      offset: Offset(-8 * paralaje, 0),
      child: switch (rasgos.tipo) {
        TipoDeFoto.recortada => _recorte(context),
        TipoDeFoto.estudio when rasgos.fondoOscuro => _fundido(context),
        TipoDeFoto.estudio => _vitrina(context),
        TipoDeFoto.escena => _cuadro(context),
        TipoDeFoto.ausente => const SizedBox.shrink(),
      },
    );
  }

  Widget _imagen(
    BuildContext context,
    BoxFit ajuste, {
    double escala = 1,
    Color? tinte,
  }) {
    final ancho = (tamano.width * escala * MediaQuery.devicePixelRatioOf(context))
        .round()
        .clamp(64, 1600);
    return Image(
      image: ResizeImage.resizeIfNeeded(ancho, null, foto!),
      fit: ajuste,
      filterQuality: FilterQuality.medium,
      gaplessPlayback: true,
      // El tinte se aplica al dibujar la imagen, no con un filtro sobre una
      // capa aparte: cuesta lo mismo que dibujarla sin tinte.
      color: tinte,
      colorBlendMode: tinte == null ? null : BlendMode.multiply,
      errorBuilder: (_, _, _) =>
          _SinFoto(producto: producto, estilo: estilo, encuadre: encuadre),
    );
  }

  /// Recorte: el producto flota, con su sombra en el piso.
  Widget _recorte(BuildContext context) => Stack(
    fit: StackFit.expand,
    children: [
      if (encuadre != Encuadre.miniatura)
        Positioned(
          left: tamano.width * 0.2,
          right: tamano.width * 0.2,
          bottom: _margen * 0.6,
          height: math.max(8, tamano.height * 0.07),
          child: const DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [Color(0xB3000000), Color(0x00000000)],
              ),
            ),
          ),
        ),
      Padding(
        padding: _relleno(_margen + tamano.shortestSide * 0.06),
        child: _imagen(context, BoxFit.contain),
      ),
    ],
  );

  /// Estudio con fondo oscuro: la foto se funde con el negro por los bordes.
  /// El fundido es un degradado encima, del color del escenario.
  Widget _fundido(BuildContext context) => Stack(
    fit: StackFit.expand,
    children: [
      Padding(
        padding: _relleno(_margen * 0.5),
        child: _imagen(context, BoxFit.contain),
      ),
      const DecoratedBox(
        decoration: BoxDecoration(
          gradient: RadialGradient(
            radius: 0.75,
            colors: [
              Color(0x00080A10),
              Color(0x00080A10),
              Paleta.negroPrincipal,
            ],
            stops: [0, 0.62, 1],
          ),
        ),
      ),
    ],
  );

  /// Estudio con fondo claro o gris: una caja de luz del color del fondo de la
  /// foto, encendida desde el centro.
  ///
  /// El blanco puro sobre negro encandila y se lee como una foto pegada. Por
  /// eso la foto lleva un tinte frío y la caja entera —foto incluida— una
  /// viñeta: como las dos cosas afectan igual a la foto y a la caja, el borde
  /// de la foto no aparece y queda una vitrina iluminada, no un recorte.
  Widget _vitrina(BuildContext context) {
    final fondo = _tenir(rasgos.fondo!);
    final foto = _imagen(context, BoxFit.contain, tinte: _tinte);
    if (encuadre == Encuadre.miniatura) {
      return ColoredBox(
        color: fondo,
        child: Padding(padding: const EdgeInsets.all(5), child: foto),
      );
    }
    final forma = FormaConCorte(
      corte: _grande ? 16 : 10,
      radio: _grande ? 20 : 14,
    );
    final caja = DecoratedBox(
      decoration: ShapeDecoration(
        color: fondo,
        shape: forma,
        // La luz alrededor de la caja, solo en los encuadres grandes: en una
        // grilla serían decenas de desenfoques por cuadro.
        shadows: _grande
            ? [
                BoxShadow(
                  color: fondo.withValues(alpha: 0.12),
                  blurRadius: 46,
                  spreadRadius: -6,
                ),
                BoxShadow(
                  color: estilo.acento.withValues(alpha: 0.32),
                  blurRadius: 40,
                  spreadRadius: -12,
                  offset: const Offset(0, 20),
                ),
              ]
            : null,
      ),
      child: ClipPath(
        clipper: ShapeBorderClipper(shape: forma),
        child: Stack(
          fit: StackFit.expand,
          children: [
            Padding(
              padding: EdgeInsets.all(_grande ? tamano.shortestSide * 0.04 : 6),
              child: foto,
            ),
            // La viñeta: oscurece los bordes de la caja y de la foto por
            // igual, así la caja parece encendida desde adentro.
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  radius: 0.95,
                  colors: [Color(0x00000000), Color(0x14000000), Color(0x8C000000)],
                  stops: [0, 0.42, 1],
                ),
              ),
            ),
          ],
        ),
      ),
    );
    // La caja toma la proporción de la foto: no sobra panel alrededor.
    return Padding(
      padding: _relleno(_margen + (_grande ? 12 : 2)),
      child: Center(
        child: AspectRatio(
          aspectRatio: rasgos.proporcion.clamp(0.55, 2.4),
          child: caja,
        ),
      ),
    );
  }

  /// Tinte frío de la vitrina: el blanco pasa a un gris azulado claro.
  static const _tinte = Color(0xFFCCD5E2);

  /// El fondo de la foto con el mismo tinte, para que la caja y la foto
  /// queden exactamente del mismo color.
  static Color _tenir(Color color) => Color.from(
    alpha: 1,
    red: color.r * _tinte.r,
    green: color.g * _tinte.g,
    blue: color.b * _tinte.b,
  );

  /// Escena: en los encuadres grandes, la foto se enmarca sobre su propia luz
  /// desenfocada, que funciona de marca de agua; en la tarjeta, se derrite en
  /// el escenario por abajo en lugar de cortarse en un borde recto.
  Widget _cuadro(BuildContext context) {
    if (encuadre == Encuadre.miniatura) return _imagen(context, BoxFit.cover);
    if (encuadre == Encuadre.tarjeta) {
      return Stack(
        fit: StackFit.expand,
        children: [
          _imagen(context, BoxFit.cover),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0x00080A10),
                  Color(0x00080A10),
                  Paleta.negroPrincipal,
                ],
                stops: [0, 0.56, 1],
              ),
            ),
          ),
        ],
      );
    }
    const forma = FormaConCorte(corte: 16, radio: 20);
    return Stack(
      fit: StackFit.expand,
      children: [
        // La misma foto, enorme y desenfocada: la luz de la escena se derrama
        // alrededor del cuadro en lugar de cortarse en su borde.
        //
        // No es un desenfoque de verdad, que era lo más caro de dibujar de
        // toda la vidriera: la foto se decodifica a 24 píxeles de ancho y se
        // estira. El filtrado al agrandarla ya la deja borrosa, y el oscurecido
        // de encima se aplica en el mismo dibujo.
        Image(
          image: ResizeImage.resizeIfNeeded(24, null, foto!),
          fit: BoxFit.cover,
          filterQuality: FilterQuality.medium,
          gaplessPlayback: true,
          color: const Color(0x9E000000),
          colorBlendMode: BlendMode.srcATop,
          errorBuilder: (_, _, _) => const SizedBox.shrink(),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(
            _margen + tamano.width * 0.08,
            _margen + 6 + aireSuperior,
            _margen + tamano.width * 0.08,
            _margen + 6 + aireInferior,
          ),
          child: Center(
            child: AspectRatio(
              aspectRatio: rasgos.proporcion.clamp(0.6, 2.2),
              child: Container(
                decoration: const ShapeDecoration(
                  shape: forma,
                  shadows: [
                    BoxShadow(
                      color: Color(0xCC000000),
                      blurRadius: 34,
                      offset: Offset(0, 18),
                    ),
                  ],
                ),
                foregroundDecoration: const ShapeDecoration(
                  shape: FormaConCorte(
                    corte: 16,
                    radio: 20,
                    side: BorderSide(color: Color(0x3DF8FAFC)),
                  ),
                ),
                child: ClipPath(
                  clipper: const ShapeBorderClipper(shape: forma),
                  child: _imagen(context, BoxFit.cover),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Sin foto: una composición tipográfica con la marca del producto. Nunca un
/// ícono de imagen rota.
class _SinFoto extends StatelessWidget {
  const _SinFoto({
    required this.producto,
    required this.estilo,
    required this.encuadre,
  });

  final Product producto;
  final EstiloDeColeccion estilo;
  final Encuadre encuadre;

  @override
  Widget build(BuildContext context) {
    final iniciales = (producto.brand ?? producto.name)
        .split(RegExp(r'\s+'))
        .where((p) => p.isNotEmpty)
        .take(2)
        .map((p) => p[0].toUpperCase())
        .join();
    return LayoutBuilder(
      builder: (context, limites) {
        final lado = limites.biggest.shortestSide.isFinite
            ? limites.biggest.shortestSide
            : 120.0;
        return Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                iniciales,
                style: TextStyle(
                  fontFamily: Tipografia.titulares,
                  fontWeight: FontWeight.w700,
                  fontSize: lado * 0.34,
                  height: 1,
                  letterSpacing: -lado * 0.015,
                  foreground: Paint()
                    ..style = PaintingStyle.stroke
                    ..strokeWidth = math.max(1.2, lado / 80)
                    ..color = estilo.acento.withValues(alpha: 0.7),
                ),
              ),
              if (encuadre != Encuadre.miniatura && lado > 110) ...[
                SizedBox(height: lado * 0.06),
                Text(
                  'sin foto',
                  style: TextStyle(
                    fontFamily: Tipografia.mono,
                    fontSize: 11,
                    color: Paleta.textoSecundario.withValues(alpha: 0.8),
                    letterSpacing: 0.6,
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}
