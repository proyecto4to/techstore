import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/format/money_input.dart';

TextEditingValue _tecleado(String texto) => TextEditingValue(
  text: texto,
  selection: TextSelection.collapsed(offset: texto.length),
);

void main() {
  group('agrupado de miles', () {
    test('agrupa de a tres desde la derecha', () {
      expect(agruparMiles('1000'), '1.000');
      expect(agruparMiles('10000'), '10.000');
      expect(agruparMiles('150000'), '150.000');
      expect(agruparMiles('1500000'), '1.500.000');
    });

    test('los números cortos quedan como están', () {
      expect(agruparMiles(''), '');
      expect(agruparMiles('7'), '7');
      expect(agruparMiles('999'), '999');
    });
  });

  group('formato de lo que se escribe', () {
    test('agrupa a medida que se teclea', () {
      expect(formatearEntradaMonetaria('4200000'), '4.200.000');
    });

    test('reformatear un monto ya agrupado no lo cambia', () {
      // El punto es separador de grupo: si se lo leyera como coma decimal,
      // volver a pasar el texto por el formateador rompería el monto.
      expect(formatearEntradaMonetaria('1.500.000'), '1.500.000');
    });

    test('sin decimales se corta en la coma y no se pegan los centavos', () {
      // Concatenarlos multiplicaba el monto por cien.
      expect(formatearEntradaMonetaria('1000,50'), '1.000');
      expect(parsearMonto('1000,50'), 1000);
    });

    test('con decimales respeta la coma y el tope de dígitos', () {
      expect(
        formatearEntradaMonetaria('1000,509', decimales: 2),
        '1.000,50',
      );
    });

    test('las letras no entran', () {
      expect(formatearEntradaMonetaria('12a3b'), '123');
      expect(formatearEntradaMonetaria('abc'), '');
    });
  });

  group('valor que se manda al servidor', () {
    test('el texto formateado se lee como el número original', () {
      expect(parsearMonto('1.500.000'), 1500000);
      expect(parsearMonto('45.000'), 45000);
    });

    test('los decimales se leen con coma', () {
      expect(parsearMonto('1.000,50', decimales: 2), 1000.5);
    });

    test('vacío o sin dígitos no inventa un cero', () {
      expect(parsearMonto(''), isNull);
      expect(parsearMonto(null), isNull);
      expect(parsearMonto('abc'), isNull);
    });
  });

  group('formateador de campo', () {
    test('agrupa el texto entrante', () {
      const formateador = MoneyInputFormatter();
      final resultado = formateador.formatEditUpdate(
        const TextEditingValue(),
        _tecleado('4200000'),
      );
      expect(resultado.text, '4.200.000');
    });

    test('el cursor queda donde estaba respecto del final', () {
      const formateador = MoneyInputFormatter();
      // «1234|567»: tres dígitos a la derecha del cursor.
      final resultado = formateador.formatEditUpdate(
        const TextEditingValue(),
        const TextEditingValue(
          text: '1234567',
          selection: TextSelection.collapsed(offset: 4),
        ),
      );
      expect(resultado.text, '1.234.567');
      // Lo que importa es que sigan quedando los mismos tres dígitos a la
      // derecha del cursor; dónde caiga el separador es indistinto.
      final cola = resultado.text.substring(resultado.selection.end);
      expect(cola.replaceAll(separadorMiles, ''), '567');
    });

    test('borrar todo deja el campo vacío', () {
      const formateador = MoneyInputFormatter();
      final resultado = formateador.formatEditUpdate(
        _tecleado('1.000'),
        const TextEditingValue(),
      );
      expect(resultado.text, isEmpty);
    });
  });
}
