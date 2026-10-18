import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/diseno/paleta.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/estilo_de_coleccion.dart';

void main() {
  test('las marcas parecidas comparten dirección de arte', () {
    expect(
      EstiloDeColeccion.paraMarca('Redragon'),
      EstiloDeColeccion.paraMarca('HyperX'),
    );
    expect(
      EstiloDeColeccion.paraMarca('Kingston').patron,
      PatronDeFondo.matriz,
    );
    expect(EstiloDeColeccion.paraMarca('TP-Link').patron, PatronDeFondo.ondas);
  });

  test('no importa cómo venga escrito el nombre', () {
    expect(
      EstiloDeColeccion.paraMarca('  western digital '),
      EstiloDeColeccion.paraMarca('Western Digital'),
    );
  });

  test('una marca nueva recibe un estilo, y siempre el mismo', () {
    final primera = EstiloDeColeccion.paraMarca('Marca Que No Existe');
    final segunda = EstiloDeColeccion.paraMarca('Marca Que No Existe');
    expect(primera, segunda);
    // Siempre dentro de los azules de la marca.
    expect(
      [Paleta.azulPrincipal, Paleta.azulLuminoso, Paleta.cian],
      contains(primera.acento),
    );
  });

  test('sin marca, el estilo neutro', () {
    expect(EstiloDeColeccion.paraMarca(null), EstiloDeColeccion.sinMarca);
    expect(EstiloDeColeccion.paraMarca(''), EstiloDeColeccion.sinMarca);
  });
}
