import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/diseno/forma_con_corte.dart';
import '../../../../core/diseno/marca_tema.dart';
import '../../../../core/diseno/movimiento.dart';
import '../../../../core/diseno/paleta.dart';
import '../../../../core/format/money.dart';
import '../../application/favoritos.dart';
import '../../domain/product.dart';

/// El porcentaje de una promoción, en una etiqueta con la esquina cortada de
/// la marca. Amarillo Monokai: el único color cálido de la vidriera.
class EtiquetaDePromocion extends StatelessWidget {
  const EtiquetaDePromocion({required this.porcentaje, super.key});

  final double porcentaje;

  @override
  Widget build(BuildContext context) {
    final texto = porcentaje == porcentaje.roundToDouble()
        ? '−${porcentaje.round()}%'
        : '−${porcentaje.toStringAsFixed(1)}%';
    return Semantics(
      label: 'En oferta, $texto',
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: const ShapeDecoration(
          color: Paleta.promocion,
          shape: FormaConCorte(corte: 7, radio: 7),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(13, 4, 9, 4),
          child: Text(
            texto,
            style: MarcaTema.of(context).precio.copyWith(
              fontSize: 13,
              color: const Color(0xFF1F1B00),
            ),
          ),
        ),
      ),
    );
  }
}

/// Precio a cobrar y, si hay promoción, el de lista tachado debajo.
///
/// El número grande es siempre el que se cobra: mostrar grande un precio que
/// no es el que se cobra es la forma más rápida de que alguien se sienta
/// engañado en el carrito.
class PrecioDeProducto extends StatelessWidget {
  const PrecioDeProducto({
    required this.producto,
    this.grande = false,
    super.key,
  });

  final Product producto;
  final bool grande;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    final estilo = grande ? marca.precioGrande : marca.precio;
    final texto = formatMoney(producto.price, producto.currency);
    // Si el precio cambia mientras se mira (llega por tiempo real), el número
    // nuevo entra desde abajo: un cambio de precio no puede pasar desapercibido.
    final precio = AnimatedSwitcher(
      duration: Movimiento.o(context, Movimiento.medio),
      transitionBuilder: (hijo, animacion) => FadeTransition(
        opacity: animacion,
        child: SlideTransition(
          position: Tween(
            begin: const Offset(0, 0.45),
            end: Offset.zero,
          ).animate(CurvedAnimation(parent: animacion, curve: Movimiento.llegada)),
          child: hijo,
        ),
      ),
      layoutBuilder: (actual, previos) => Stack(
        alignment: Alignment.centerLeft,
        children: [...previos, ?actual],
      ),
      // Un precio no se corta nunca: si no entra (una tarjeta angosta, un
      // teléfono con la pantalla agrandada), se achica hasta entrar entero.
      child: FittedBox(
        key: ValueKey(texto),
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: Text(texto, style: estilo, maxLines: 1, softWrap: false),
      ),
    );
    if (!producto.hasPromotion) return precio;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        precio,
        Text(
          formatMoney(producto.listPrice!, producto.currency),
          semanticsLabel:
              'Antes ${formatMoney(producto.listPrice!, producto.currency)}',
          maxLines: 1,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            decoration: TextDecoration.lineThrough,
            decorationColor: Paleta.textoSecundario,
            fontSize: grande ? 14 : 11.5,
          ),
        ),
      ],
    );
  }
}

/// Cómo está el stock, en palabras y con su color. Solo dice cuánto queda
/// cuando ese número significa escasez real.
class IndicadorDeStock extends StatelessWidget {
  const IndicadorDeStock({
    required this.producto,
    this.enCarrito = 0,
    super.key,
  });

  final Product producto;
  final int enCarrito;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    final (String texto, Color color) = switch (producto) {
      final p when p.maxPerOrder <= 0 => ('Sin stock por ahora', marca.error),
      final p when p.quedaPoco => (
        p.maxPerOrder == 1 ? 'Queda 1 unidad' : 'Quedan ${p.maxPerOrder}',
        marca.aviso,
      ),
      // El número es real pero no escaso: se dice, sin alarma.
      final p when p.showsRemainingUnits => (
        '${p.maxPerOrder} en stock',
        marca.exito,
      ),
      _ => ('Disponible', marca.exito),
    };
    final completo = enCarrito > 0 ? '$texto · $enCarrito en tu carrito' : texto;
    // Mismo criterio que el precio: si el stock cambia en pantalla, se nota.
    return AnimatedSwitcher(
      duration: Movimiento.o(context, Movimiento.medio),
      child: _fila(completo, color, key: ValueKey(completo)),
    );
  }

  Widget _fila(String completo, Color color, {Key? key}) => Builder(
    key: key,
    builder: (context) => Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // El halo es un segundo círculo translúcido y no una sombra
        // desenfocada: hay uno por tarjeta, y un desenfoque por tarjeta en
        // cada cuadro de un desplazamiento se nota.
        Container(
          width: 13,
          height: 13,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.18),
            shape: BoxShape.circle,
          ),
          child: Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
        ),
        const SizedBox(width: 7),
        Flexible(
          child: Text(
            completo,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: color,
            ),
          ),
        ),
      ],
    ),
  );
}

/// El corazón de favoritos. Cambia en el acto y, si el servidor no confirma,
/// vuelve atrás y avisa (ver [alternarFavorito]).
class BotonFavorito extends ConsumerWidget {
  const BotonFavorito({
    required this.producto,
    this.sobreFoto = false,
    super.key,
  });

  final Product producto;

  /// Sobre una foto lleva su propio fondo, para leerse sobre cualquier color.
  final bool sobreFoto;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final favorito = esFavorito(ref, producto.id);
    final icono = AnimatedSwitcher(
      duration: Movimiento.o(context, Movimiento.corto),
      transitionBuilder: (hijo, animacion) => ScaleTransition(
        scale: CurvedAnimation(parent: animacion, curve: Curves.elasticOut),
        child: hijo,
      ),
      child: Icon(
        favorito ? Icons.favorite_rounded : Icons.favorite_border_rounded,
        key: ValueKey(favorito),
        color: favorito ? Paleta.error : Paleta.texto,
        size: 21,
      ),
    );
    return IconButton(
      tooltip: favorito ? 'Quitar de favoritos' : 'Guardar en favoritos',
      onPressed: () => alternarFavorito(context, ref, producto),
      style: sobreFoto
          ? IconButton.styleFrom(
              backgroundColor: Paleta.fondo.withValues(alpha: 0.55),
              side: const BorderSide(color: Color(0x33F8FAFC)),
            )
          : null,
      icon: icono,
    );
  }
}
