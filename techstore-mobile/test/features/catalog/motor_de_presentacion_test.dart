import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/motor_de_presentacion.dart';

Product producto(int id, {double? lista, double? porcentaje, int tope = 10}) =>
    Product(
      id: id,
      name: 'Producto $id',
      price: 100000,
      currency: 'PYG',
      available: tope > 0,
      maxPerOrder: tope,
      listPrice: lista,
      promotionPercentage: porcentaje,
      promotionName: porcentaje == null ? null : 'Semana del gamer',
    );

List<int> idsDe(List<BloqueDeCatalogo> bloques) => [
  for (final bloque in bloques)
    ...switch (bloque) {
      BloqueDestacado(:final producto) => [producto.id],
      BloqueAncho(:final producto) => [producto.id],
      BloquePar(:final primero, :final segundo) => [
        primero.id,
        if (segundo != null) segundo.id,
      ],
    },
];

void main() {
  test('sin productos no hay bloques', () {
    expect(
      componerVidriera(const [], orden: OrdenDeCatalogo.novedades),
      isEmpty,
    );
  });

  test('cada producto aparece una sola vez, en el orden pedido', () {
    final lista = [for (var i = 1; i <= 23; i++) producto(i)];
    final bloques = componerVidriera(lista, orden: OrdenDeCatalogo.novedades);
    expect(idsDe(bloques), [for (var i = 1; i <= 23; i++) i]);
  });

  test('una oferta del primer bloque ocupa el escaparate con su nombre', () {
    final lista = [
      producto(1),
      producto(2),
      producto(3, lista: 125000, porcentaje: 20),
      producto(4),
    ];
    final bloques = componerVidriera(lista, orden: OrdenDeCatalogo.novedades);
    final primero = bloques.first as BloqueDestacado;
    expect(primero.producto.id, 3);
    expect(primero.rotulo, 'Semana del gamer');
    // No se repite más abajo.
    expect(idsDe(bloques).where((id) => id == 3), hasLength(1));
  });

  test('sin ofertas, el escaparate dice por qué está ahí según el orden', () {
    final lista = [producto(7), producto(8), producto(9)];
    expect(
      (componerVidriera(lista, orden: OrdenDeCatalogo.novedades).first
              as BloqueDestacado)
          .rotulo,
      'recién llegado',
    );
    expect(
      (componerVidriera(lista, orden: OrdenDeCatalogo.precioMenor).first
              as BloqueDestacado)
          .rotulo,
      'el precio más bajo',
    );
  });

  test('en orden alfabético no se destaca a nadie: ser primero no dice nada', () {
    final lista = [producto(1), producto(2), producto(3)];
    final bloques = componerVidriera(lista, orden: OrdenDeCatalogo.alfabetico);
    expect(bloques.whereType<BloqueDestacado>(), isEmpty);
  });

  test('una oferta sin stock no se destaca', () {
    final lista = [
      producto(1),
      producto(2, lista: 150000, porcentaje: 30, tope: 0),
    ];
    final bloques = componerVidriera(lista, orden: OrdenDeCatalogo.novedades);
    expect((bloques.first as BloqueDestacado).producto.id, 1);
  });

  test('el ritmo alterna pares y tarjetas anchas, y las anchas cambian de lado', () {
    final lista = [for (var i = 1; i <= 16; i++) producto(i)];
    final bloques = componerVidriera(lista, orden: OrdenDeCatalogo.novedades);
    expect(bloques.map((b) => b.runtimeType).toList(), [
      BloqueDestacado,
      BloquePar,
      BloqueAncho,
      BloquePar,
      BloquePar,
      BloqueAncho,
      BloquePar,
      BloquePar,
      BloqueAncho,
      BloquePar,
    ]);
    final anchas = bloques.whereType<BloqueAncho>().toList();
    expect(anchas[0].fotoALaDerecha, isFalse);
    expect(anchas[1].fotoALaDerecha, isTrue);
  });

  test('cargar el bloque siguiente no reacomoda lo que ya se ve', () {
    final primerBloque = [for (var i = 1; i <= productosPorBloque; i++) producto(i)];
    // El segundo bloque trae una oferta: no puede subir al escaparate, porque
    // movería toda la grilla debajo de lo que la persona está mirando.
    final segundoBloque = [
      producto(31, lista: 200000, porcentaje: 50),
      for (var i = 32; i <= 45; i++) producto(i),
    ];
    final antes = componerVidriera(
      primerBloque,
      orden: OrdenDeCatalogo.novedades,
    );
    final despues = componerVidriera(
      [...primerBloque, ...segundoBloque],
      orden: OrdenDeCatalogo.novedades,
    );
    // Todos los bloques completos de antes siguen iguales; solo el último,
    // que podía ser un par a medias, puede completarse.
    final completos = antes.length - 1;
    expect(
      despues.take(completos).map((b) => b.clave).toList(),
      antes.take(completos).map((b) => b.clave).toList(),
    );
  });
}
