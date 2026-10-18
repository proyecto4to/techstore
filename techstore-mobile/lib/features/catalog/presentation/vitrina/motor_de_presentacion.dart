import '../../application/catalog_providers.dart';
import '../../domain/product.dart';

/// Un bloque de la vidriera del catálogo.
sealed class BloqueDeCatalogo {
  const BloqueDeCatalogo();

  /// Identifica al bloque entre recargas: la entrada animada se hace una sola
  /// vez por bloque y no cada vez que vuelve a la pantalla.
  String get clave;
}

/// Un producto a todo el ancho, presentado como escaparate.
final class BloqueDestacado extends BloqueDeCatalogo {
  const BloqueDestacado(this.producto, this.rotulo);

  final Product producto;

  /// Por qué está ahí, dicho con datos.
  final String rotulo;

  @override
  String get clave => 'destacado-${producto.id}';
}

/// Dos tarjetas lado a lado; la segunda puede faltar al final de la lista.
final class BloquePar extends BloqueDeCatalogo {
  const BloquePar(this.primero, [this.segundo]);

  final Product primero;
  final Product? segundo;

  @override
  String get clave => 'par-${primero.id}-${segundo?.id}';
}

/// Una tarjeta horizontal a todo el ancho.
final class BloqueAncho extends BloqueDeCatalogo {
  const BloqueAncho(this.producto, {this.fotoALaDerecha = false});

  final Product producto;
  final bool fotoALaDerecha;

  @override
  String get clave => 'ancho-${producto.id}';
}

/// Compone la vidriera editorial: un escaparate arriba y después un ritmo que
/// alterna pares y tarjetas anchas.
///
/// Las decisiones salen solo de datos que llegan con la lista —el orden
/// elegido y si hay promoción—, nunca de la foto: la foto se analiza después
/// de bajarla, y armar la grilla con eso haría saltar las tarjetas de lugar
/// mientras la persona mira. Lo que sí depende de la foto (cómo se presenta
/// cada producto dentro de su tarjeta) lo resuelve el escenario.
///
/// Es determinista y estable: agregar el bloque siguiente del catálogo solo
/// suma bloques al final, no reacomoda los que ya se ven.
List<BloqueDeCatalogo> componerVidriera(
  List<Product> productos, {
  required OrdenDeCatalogo orden,
}) {
  if (productos.isEmpty) return const [];
  final restantes = [...productos];
  final bloques = <BloqueDeCatalogo>[];

  // El escaparate: una oferta si la hay en el primer bloque; si no, el
  // primero de la lista cuando el orden le da sentido. En orden alfabético,
  // ser el primero no dice nada del producto, así que no se destaca a nadie.
  //
  // Se busca solo en el primer bloque a propósito: si una oferta que llega
  // con el segundo bloque pudiera ocupar el escaparate, toda la grilla se
  // reacomodaría justo debajo de lo que la persona está mirando.
  final indiceOferta = restantes
      .take(productosPorBloque)
      .toList()
      .indexWhere((p) => p.hasPromotion && p.available);
  if (indiceOferta >= 0) {
    final oferta = restantes.removeAt(indiceOferta);
    bloques.add(BloqueDestacado(oferta, oferta.promotionName ?? 'en oferta'));
  } else if (orden != OrdenDeCatalogo.alfabetico) {
    final rotulo = switch (orden) {
      OrdenDeCatalogo.novedades => 'recién llegado',
      OrdenDeCatalogo.precioMenor => 'el precio más bajo',
      OrdenDeCatalogo.precioMayor => 'lo más alto de la gama',
      OrdenDeCatalogo.alfabetico => '',
    };
    bloques.add(BloqueDestacado(restantes.removeAt(0), rotulo));
  }

  // Ritmo: par, ancha, par, par, ancha del otro lado, par.
  const ritmo = [2, 1, 2, 2, 1, 2];
  var paso = 0;
  var anchas = 0;
  while (restantes.isNotEmpty) {
    final cuantos = ritmo[paso % ritmo.length];
    paso++;
    if (cuantos == 1) {
      bloques.add(
        BloqueAncho(restantes.removeAt(0), fotoALaDerecha: anchas.isOdd),
      );
      anchas++;
    } else {
      final primero = restantes.removeAt(0);
      final segundo = restantes.isEmpty ? null : restantes.removeAt(0);
      bloques.add(BloquePar(primero, segundo));
    }
  }
  return bloques;
}
