import 'dart:math' as math;
import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/diseno/forma_con_corte.dart';
import '../../../../core/diseno/movimiento.dart';
import '../../../../core/diseno/paleta.dart';
import '../../../../core/diseno/tipografia.dart';
import '../../domain/product.dart';
import 'estilo_de_coleccion.dart';
import 'foto_de_producto.dart';
import 'rasgos_de_imagen.dart';

/// Para qué lugar se arma el escenario. Cuanto más grande, más capas: en una
/// fila del carrito no entra una marca de agua; en la ficha, sí.
enum Encuadre { miniatura, tarjeta, destacado, ficha }

/// El producto presentado dentro de la composición, no pegado encima de ella.
///
/// Arma capas —luz, patrón de la colección, marca de agua, producto, sombra— y
/// elige el tratamiento según lo que la foto *es* ([RasgosDeImagen]): un
/// recorte flota sobre su propia silueta; una foto de estudio se monta en una
/// vitrina iluminada del color de su fondo, así el borde de la foto no se ve;
/// una escena se enmarca sobre su propia luz desenfocada; y sin foto queda una
/// composición tipográfica, nunca un ícono roto.
class EscenarioDeProducto extends ConsumerWidget {
  const EscenarioDeProducto({
    required this.producto,
    this.encuadre = Encuadre.tarjeta,
    this.heroTag,
    this.paralaje = 0,
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
              paralaje: paralaje,
            ),
            if (encuadre != Encuadre.miniatura && rasgos != null)
              _MarcaDeAgua(
                producto: producto,
                estilo: estilo,
                rasgos: rasgos,
                foto: foto,
                encuadre: encuadre,
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
                        _Producto(
                          producto: producto,
                          foto: foto,
                          rasgos: rasgos,
                          estilo: estilo,
                          encuadre: encuadre,
                          tamano: tamano,
                          paralaje: paralaje,
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

  Widget _envolverHero(Widget hijo) => heroTag == null
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

/// Luz y textura de la colección, detrás de todo.
class _Fondo extends StatelessWidget {
  const _Fondo({
    required this.estilo,
    required this.rasgos,
    required this.encuadre,
    required this.paralaje,
  });

  final EstiloDeColeccion estilo;
  final RasgosDeImagen? rasgos;
  final Encuadre encuadre;
  final double paralaje;

  @override
  Widget build(BuildContext context) {
    // La luz es azul siempre; el tono del producto la entibia apenas, para
    // que una placa con luces violetas no quede bajo una luz que no es suya.
    final luz = Color.lerp(
      estilo.acento,
      rasgos?.tono ?? estilo.acento,
      0.18,
    )!;
    if (encuadre == Encuadre.miniatura) {
      return const ColoredBox(color: Paleta.negroAzulado);
    }
    final intensidad = switch (encuadre) {
      Encuadre.ficha || Encuadre.destacado => 0.42,
      _ => 0.3,
    };
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Paleta.negroPrincipal,
        gradient: RadialGradient(
          center: Alignment(0, encuadre == Encuadre.ficha ? -0.1 : 0.05),
          radius: 0.85,
          colors: [
            luz.withValues(alpha: intensidad),
            luz.withValues(alpha: intensidad * 0.22),
            Paleta.negroPrincipal.withValues(alpha: 0),
          ],
          stops: const [0, 0.55, 1],
        ),
      ),
      child: CustomPaint(
        painter: PintorDePatron(
          estilo: estilo,
          opacidad: encuadre == Encuadre.tarjeta ? 0.06 : 0.075,
          desplazamiento: Offset(0, -18 * paralaje),
        ),
        child: const SizedBox.expand(),
      ),
    );
  }
}

/// La marca de agua: el nombre de la marca en contorno, enorme y cortado por
/// el borde, y —si la foto es un recorte— la silueta del mismo producto.
class _MarcaDeAgua extends StatelessWidget {
  const _MarcaDeAgua({
    required this.producto,
    required this.estilo,
    required this.rasgos,
    required this.foto,
    required this.encuadre,
    required this.tamano,
    required this.paralaje,
  });

  final Product producto;
  final EstiloDeColeccion estilo;
  final RasgosDeImagen rasgos;
  final ImageProvider? foto;
  final Encuadre encuadre;
  final Size tamano;
  final double paralaje;

  @override
  Widget build(BuildContext context) {
    final texto = (producto.brand ?? producto.name).toUpperCase();
    final cuerpo = tamano.height *
        switch (encuadre) {
          Encuadre.ficha => 0.36,
          Encuadre.destacado => 0.4,
          _ => 0.34,
        };
    return Stack(
      fit: StackFit.expand,
      children: [
        Positioned(
          left: -cuerpo * 0.08 + 22 * paralaje,
          bottom: -cuerpo * 0.22,
          child: Text(
            texto,
            maxLines: 1,
            softWrap: false,
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
        ),
        // La silueta translúcida del mismo producto, más grande y corrida:
        // solo tiene sentido con un recorte, donde la forma es el producto y
        // no un rectángulo de foto.
        if (rasgos.tipo == TipoDeFoto.recortada &&
            foto != null &&
            encuadre != Encuadre.tarjeta)
          Positioned.fill(
            child: Transform.translate(
              offset: Offset(tamano.width * 0.16 - 26 * paralaje, -8),
              child: Transform.scale(
                scale: 1.45,
                child: Opacity(
                  opacity: 0.14,
                  child: ColorFiltered(
                    colorFilter: ColorFilter.mode(
                      estilo.acento,
                      BlendMode.srcIn,
                    ),
                    child: Image(
                      image: ResizeImage.resizeIfNeeded(
                        (tamano.width * 0.8).round(),
                        null,
                        foto!,
                      ),
                      fit: BoxFit.contain,
                      gaplessPlayback: true,
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
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
  });

  final Product producto;
  final ImageProvider? foto;
  final RasgosDeImagen rasgos;
  final EstiloDeColeccion estilo;
  final Encuadre encuadre;
  final Size tamano;
  final double paralaje;

  double get _margen => switch (encuadre) {
    Encuadre.miniatura => 0,
    Encuadre.tarjeta => 12,
    Encuadre.destacado => 22,
    Encuadre.ficha => 28,
  };

  @override
  Widget build(BuildContext context) {
    if (foto == null || rasgos.tipo == TipoDeFoto.ausente) {
      return _SinFoto(producto: producto, estilo: estilo, encuadre: encuadre);
    }
    final desplazado = Transform.translate(
      offset: Offset(-8 * paralaje, 0),
      child: switch (rasgos.tipo) {
        TipoDeFoto.recortada => _recorte(context),
        TipoDeFoto.estudio when rasgos.fondoOscuro => _fundido(context),
        TipoDeFoto.estudio => _vitrina(context),
        TipoDeFoto.escena => _cuadro(context),
        TipoDeFoto.ausente => const SizedBox.shrink(),
      },
    );
    return desplazado;
  }

  Widget _imagen(BuildContext context, BoxFit ajuste, {double escala = 1}) {
    final ancho = (tamano.width * escala * MediaQuery.devicePixelRatioOf(context))
        .round()
        .clamp(64, 1600);
    return Image(
      image: ResizeImage.resizeIfNeeded(ancho, null, foto!),
      fit: ajuste,
      filterQuality: FilterQuality.medium,
      gaplessPlayback: true,
      errorBuilder: (_, _, _) =>
          _SinFoto(producto: producto, estilo: estilo, encuadre: encuadre),
    );
  }

  /// Recorte: el producto flota, con su sombra en el piso.
  Widget _recorte(BuildContext context) {
    final piso = Positioned(
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
    );
    return Stack(
      fit: StackFit.expand,
      children: [
        if (encuadre != Encuadre.miniatura) piso,
        Padding(
          padding: EdgeInsets.all(_margen + tamano.shortestSide * 0.06),
          child: _imagen(context, BoxFit.contain),
        ),
      ],
    );
  }

  /// Estudio con fondo oscuro: la foto se funde con el negro por los bordes.
  Widget _fundido(BuildContext context) => Padding(
    padding: EdgeInsets.all(_margen * 0.5),
    child: ShaderMask(
      blendMode: BlendMode.dstIn,
      shaderCallback: (rect) => const RadialGradient(
        radius: 0.75,
        colors: [Colors.white, Colors.white, Color(0x00FFFFFF)],
        stops: [0, 0.62, 1],
      ).createShader(rect),
      child: _imagen(context, BoxFit.contain),
    ),
  );

  /// Estudio con fondo claro o gris: una caja de luz del color del fondo de la
  /// foto, encendida desde el centro.
  ///
  /// El blanco puro sobre negro encandila y se lee como una foto pegada. Por
  /// eso la foto pasa por un tinte frío y la caja entera —foto incluida— por
  /// una viñeta: como las dos cosas afectan igual a la foto y a la caja, el
  /// borde de la foto no aparece y queda una vitrina iluminada, no un recorte.
  Widget _vitrina(BuildContext context) {
    final fondo = _tenir(rasgos.fondo!);
    final foto = ColorFiltered(
      colorFilter: const ColorFilter.mode(_tinte, BlendMode.multiply),
      child: _imagen(context, BoxFit.contain),
    );
    if (encuadre == Encuadre.miniatura) {
      return ColoredBox(
        color: fondo,
        child: Padding(padding: const EdgeInsets.all(5), child: foto),
      );
    }
    final grande = encuadre != Encuadre.tarjeta;
    final forma = FormaConCorte(
      corte: grande ? 16 : 10,
      radio: grande ? 20 : 14,
    );
    final caja = DecoratedBox(
      decoration: ShapeDecoration(
        color: fondo,
        shape: forma,
        shadows: [
          // La caja encendida irradia su propia luz sobre el negro, y la luz
          // azul de la colección la levanta desde abajo.
          BoxShadow(
            color: fondo.withValues(alpha: 0.12),
            blurRadius: grande ? 46 : 20,
            spreadRadius: -6,
          ),
          BoxShadow(
            color: estilo.acento.withValues(alpha: 0.32),
            blurRadius: grande ? 40 : 24,
            spreadRadius: -12,
            offset: Offset(0, grande ? 20 : 10),
          ),
        ],
      ),
      child: ClipPath(
        clipper: ShapeBorderClipper(shape: forma),
        child: ShaderMask(
          blendMode: BlendMode.modulate,
          shaderCallback: (rect) => const RadialGradient(
            radius: 0.95,
            colors: [Colors.white, Color(0xFFDDE2E9), Color(0xFF6B7380)],
            stops: [0, 0.42, 1],
          ).createShader(rect),
          child: ColoredBox(
            color: fondo,
            child: Padding(
              padding: EdgeInsets.all(grande ? tamano.shortestSide * 0.04 : 6),
              child: foto,
            ),
          ),
        ),
      ),
    );
    // La caja toma la proporción de la foto: no sobra panel alrededor.
    return Padding(
      padding: EdgeInsets.all(_margen + (grande ? 12 : 2)),
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
      return ShaderMask(
        blendMode: BlendMode.dstIn,
        shaderCallback: (rect) => const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.white, Colors.white, Color(0x00FFFFFF)],
          stops: [0, 0.56, 1],
        ).createShader(rect),
        child: _imagen(context, BoxFit.cover),
      );
    }
    const forma = FormaConCorte(corte: 16, radio: 20);
    return Stack(
      fit: StackFit.expand,
      children: [
        // La misma foto, enorme y desenfocada: la luz de la escena se derrama
        // alrededor del cuadro en lugar de cortarse en su borde. Se decodifica
        // chica: desenfocada, la resolución no se nota y cuesta mucho menos.
        Opacity(
          opacity: 0.62,
          child: ImageFiltered(
            imageFilter: ImageFilter.blur(sigmaX: 28, sigmaY: 28),
            child: _imagen(context, BoxFit.cover, escala: 0.3),
          ),
        ),
        const ColoredBox(color: Color(0x59000000)),
        Padding(
          padding: EdgeInsets.symmetric(
            horizontal: _margen + tamano.width * 0.08,
            vertical: _margen + 6,
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
