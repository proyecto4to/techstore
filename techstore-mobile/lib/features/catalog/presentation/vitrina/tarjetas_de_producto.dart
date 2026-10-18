import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/animacion/aparicion.dart';
import '../../../../core/diseno/forma_con_corte.dart';
import '../../../../core/diseno/medidas.dart';
import '../../../../core/diseno/paleta.dart';
import '../../../../core/format/money.dart';
import '../../../../core/widgets/add_to_cart_button.dart';
import '../../../../core/widgets/marca/rotulo.dart';
import '../../../cart/application/cart_controller.dart';
import '../../../cart/presentation/agregar_al_carrito.dart';
import '../../domain/product.dart';
import 'escenario_de_producto.dart';
import 'foto_de_producto.dart';
import 'piezas_de_producto.dart';

/// Cuántas unidades de este producto hay en el carrito. Observa solo ese
/// número: la tarjeta se reconstruye cuando cambia su línea y no ante
/// cualquier movimiento del carrito.
int unidadesEnCarrito(WidgetRef ref, int idProducto) => ref.watch(
  cartProvider.select((lineas) {
    for (final linea in lineas) {
      if (linea.product.id == idProducto) return linea.quantity;
    }
    return 0;
  }),
);

/// Abre la ficha. El nombre del Hero viaja aparte porque el mismo producto
/// puede estar dos veces en una pantalla —en el carrusel y en la grilla— y la
/// foto tiene que salir de la tarjeta que se tocó.
void abrirProducto(BuildContext context, Product producto, {Object? heroTag}) =>
    context.push('/products/${producto.id}', extra: heroTag);

/// La escala de letra dentro de las tarjetas, topada en 1,3. Más allá, el
/// nombre de un producto ya no entra en media pantalla; la ficha, que tiene
/// lugar, respeta la escala completa.
TextScaler escalaDeTarjeta(BuildContext context) =>
    MediaQuery.textScalerOf(context).clamp(maxScaleFactor: 1.3);

/// Alto de la parte de texto de una [TarjetaDeProducto], según la escala de
/// letra: así la grilla reserva lo justo y nada se desborda con letra grande.
double altoDeInfoDeTarjeta(BuildContext context) {
  final e = escalaDeTarjeta(context).scale(1);
  final textos = (15 + 4 + 38 + 8) * e;
  final fila = math.max(Medidas.toque, 36 * e);
  return 12 + textos + fila + 8 + 17 * e + 14;
}

/// Tarjeta vertical: la de la grilla del catálogo.
class TarjetaDeProducto extends ConsumerWidget {
  const TarjetaDeProducto({
    required this.producto,
    required this.contexto,
    this.proporcionFoto = 1,
    super.key,
  });

  final Product producto;

  /// Dónde está la tarjeta («catalogo», «inicio-nuevos»…): arma el nombre
  /// del Hero, que tiene que ser único en la pantalla.
  final String contexto;

  /// Ancho sobre alto del escenario.
  final double proporcionFoto;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final heroTag = 'producto-${producto.id}-$contexto';
    final enCarrito = unidadesEnCarrito(ref, producto.id);
    final lugar = math.max(0, producto.maxPerOrder - enCarrito);
    final url = ref.watch(urlDeFotoProvider(producto.imageUrl));
    final foto = url == null ? null : ref.watch(fabricaDeFotosProvider)(url);
    final textos = Theme.of(context).textTheme;

    return MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: escalaDeTarjeta(context),
      ),
      child: Presionable(
        onTap: () => abrirProducto(context, producto, heroTag: heroTag),
        child: DecoratedBox(
          decoration: const ShapeDecoration(
            color: Paleta.negroAzulado,
            shape: RoundedRectangleBorder(
              borderRadius: Medidas.bordeTarjeta,
              side: BorderSide(color: Paleta.borde),
            ),
          ),
          child: ClipRRect(
            borderRadius: Medidas.bordeTarjeta,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                AspectRatio(
                  aspectRatio: proporcionFoto,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      EscenarioDeProducto(producto: producto, heroTag: heroTag),
                      if (producto.hasPromotion)
                        Positioned(
                          top: 10,
                          left: 10,
                          child: EtiquetaDePromocion(
                            porcentaje: producto.promotionPercentage!,
                          ),
                        ),
                      Positioned(
                        top: 4,
                        right: 4,
                        child: BotonFavorito(producto: producto, sobreFoto: true),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 12, 10, 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (producto.brand != null) Rotulo(producto.brand!),
                        const SizedBox(height: 4),
                        Text(
                          producto.name,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: textos.titleSmall,
                        ),
                        const Spacer(),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Expanded(
                              child: PrecioDeProducto(producto: producto),
                            ),
                            AddToCartButton(
                              compact: true,
                              imagenParaVuelo: foto,
                              disabledLabel: producto.maxPerOrder <= 0
                                  ? 'Sin stock disponible'
                                  : 'Ya tenés todo el stock en el carrito',
                              onPressed: lugar <= 0
                                  ? null
                                  : () => agregarAlCarrito(context, ref, producto),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        IndicadorDeStock(producto: producto, enCarrito: enCarrito),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Tarjeta horizontal a todo el ancho: corta el ritmo de la grilla y deja
/// lugar para la descripción.
class TarjetaAncha extends ConsumerWidget {
  const TarjetaAncha({
    required this.producto,
    required this.contexto,
    this.fotoALaDerecha = false,
    super.key,
  });

  final Product producto;
  final String contexto;

  /// Alterna el lado de la foto entre una ancha y la siguiente.
  final bool fotoALaDerecha;

  /// La suma de lo que lleva la columna de texto, con la escala de letra:
  /// márgenes (28), fila de marca y corazón (34), nombre en dos líneas (43),
  /// descripción en dos (35), fila del precio con su botón (48), stock (17) y
  /// un poco de aire. Calculado así y no a ojo: a ojo quedaba un píxel corto.
  static double alto(BuildContext context) {
    final e = escalaDeTarjeta(context).scale(1);
    return math.max(220, 28 + 34 + 4 + 48 + 6 + 8 + (43 + 35 + 17) * e);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final heroTag = 'producto-${producto.id}-$contexto';
    final enCarrito = unidadesEnCarrito(ref, producto.id);
    final lugar = math.max(0, producto.maxPerOrder - enCarrito);
    final url = ref.watch(urlDeFotoProvider(producto.imageUrl));
    final foto = url == null ? null : ref.watch(fabricaDeFotosProvider)(url);
    final textos = Theme.of(context).textTheme;

    final escenario = Expanded(
      flex: 9,
      child: Stack(
        fit: StackFit.expand,
        children: [
          EscenarioDeProducto(producto: producto, heroTag: heroTag),
          if (producto.hasPromotion)
            Positioned(
              top: 10,
              left: 10,
              child: EtiquetaDePromocion(
                porcentaje: producto.promotionPercentage!,
              ),
            ),
        ],
      ),
    );
    final info = Expanded(
      flex: 11,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (producto.brand != null)
                  Expanded(child: Rotulo(producto.brand!))
                else
                  const Spacer(),
                SizedBox(
                  height: 34,
                  child: BotonFavorito(producto: producto),
                ),
              ],
            ),
            Text(
              producto.name,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: textos.titleMedium,
            ),
            if (producto.description?.trim().isNotEmpty ?? false) ...[
              const SizedBox(height: 4),
              Text(
                producto.description!.trim(),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: textos.bodySmall,
              ),
            ],
            const Spacer(),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(child: PrecioDeProducto(producto: producto)),
                AddToCartButton(
                  compact: true,
                  imagenParaVuelo: foto,
                  disabledLabel: producto.maxPerOrder <= 0
                      ? 'Sin stock disponible'
                      : 'Ya tenés todo el stock en el carrito',
                  onPressed: lugar <= 0
                      ? null
                      : () => agregarAlCarrito(context, ref, producto),
                ),
              ],
            ),
            const SizedBox(height: 6),
            IndicadorDeStock(producto: producto, enCarrito: enCarrito),
          ],
        ),
      ),
    );

    return MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: escalaDeTarjeta(context),
      ),
      child: Presionable(
        onTap: () => abrirProducto(context, producto, heroTag: heroTag),
        hundimiento: 0.985,
        child: DecoratedBox(
          decoration: const ShapeDecoration(
            color: Paleta.negroAzulado,
            shape: RoundedRectangleBorder(
              borderRadius: Medidas.bordeTarjeta,
              side: BorderSide(color: Paleta.borde),
            ),
          ),
          child: ClipRRect(
            borderRadius: Medidas.bordeTarjeta,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: fotoALaDerecha ? [info, escenario] : [escenario, info],
            ),
          ),
        ),
      ),
    );
  }
}

/// El escaparate: el producto a todo el ancho, con su nombre y su precio
/// sobre la escena. Lleva la esquina cortada de la marca.
class TarjetaDestacada extends ConsumerWidget {
  const TarjetaDestacada({
    required this.producto,
    required this.contexto,
    required this.rotulo,
    this.paralaje = 0,
    super.key,
  });

  final Product producto;
  final String contexto;

  /// Por qué está destacado, dicho con datos: «oferta», «recién llegado».
  final String rotulo;
  final double paralaje;

  static const forma = FormaConCorte(corte: 18, radio: 26);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final heroTag = 'producto-${producto.id}-$contexto';
    final enCarrito = unidadesEnCarrito(ref, producto.id);
    final lugar = math.max(0, producto.maxPerOrder - enCarrito);
    final url = ref.watch(urlDeFotoProvider(producto.imageUrl));
    final foto = url == null ? null : ref.watch(fabricaDeFotosProvider)(url);
    final textos = Theme.of(context).textTheme;

    return MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: escalaDeTarjeta(context),
      ),
      child: Presionable(
        onTap: () => abrirProducto(context, producto, heroTag: heroTag),
        hundimiento: 0.985,
        child: DecoratedBox(
          decoration: const ShapeDecoration(
            shape: forma,
            shadows: [
              BoxShadow(
                color: Color(0x332563EB),
                blurRadius: 40,
                spreadRadius: -8,
                offset: Offset(0, 18),
              ),
            ],
          ),
          child: ClipPath(
            clipper: const ShapeBorderClipper(shape: forma),
            child: Stack(
              fit: StackFit.expand,
              children: [
                EscenarioDeProducto(
                  producto: producto,
                  encuadre: Encuadre.destacado,
                  heroTag: heroTag,
                  paralaje: paralaje,
                ),
                // El texto va sobre la escena: un degradé lo separa de la
                // foto sin taparla entera.
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Color(0x00000000),
                        Color(0x00000000),
                        Color(0xD9000000),
                      ],
                      stops: [0, 0.42, 1],
                    ),
                  ),
                ),
                const DecoratedBox(
                  decoration: ShapeDecoration(
                    shape: FormaConCorte(
                      corte: 18,
                      radio: 26,
                      side: BorderSide(color: Paleta.borde),
                    ),
                  ),
                ),
                if (producto.hasPromotion)
                  Positioned(
                    top: 16,
                    left: 18,
                    child: EtiquetaDePromocion(
                      porcentaje: producto.promotionPercentage!,
                    ),
                  ),
                Positioned(
                  top: 8,
                  right: 8,
                  child: BotonFavorito(producto: producto, sobreFoto: true),
                ),
                Positioned(
                  left: 20,
                  right: 16,
                  bottom: 18,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Rotulo(rotulo),
                      const SizedBox(height: 6),
                      Text(
                        producto.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: textos.headlineSmall,
                      ),
                      const SizedBox(height: 10),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Expanded(
                            child: PrecioDeProducto(
                              producto: producto,
                              grande: true,
                            ),
                          ),
                          AddToCartButton(
                            compact: true,
                            imagenParaVuelo: foto,
                            disabledLabel: producto.maxPerOrder <= 0
                                ? 'Sin stock disponible'
                                : 'Ya tenés todo el stock en el carrito',
                            onPressed: lugar <= 0
                                ? null
                                : () => agregarAlCarrito(context, ref, producto),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Precio corto para lugares chicos, sin el tachado.
String precioCorto(Product producto) =>
    formatMoney(producto.price, producto.currency);
