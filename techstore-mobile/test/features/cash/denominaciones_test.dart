import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/cash/domain/denominaciones.dart';

void main() {
  group('planilla de conteo', () {
    test('trae los diez cortes de guaraní, de mayor a menor', () {
      final planilla = planillaEnBlanco();
      expect(planilla.length, 10);
      expect(planilla.first.valor, 100000);
      expect(planilla.last.valor, 50);
      final valores = planilla.map((f) => f.valor).toList();
      final ordenados = [...valores]..sort((a, b) => b.compareTo(a));
      expect(valores, ordenados, reason: 'se cuenta del billete grande al chico');
    });

    test('arranca en cero, sin cantidades inventadas', () {
      final planilla = planillaEnBlanco();
      expect(unidadesContadas(planilla), 0);
      expect(totalContado(planilla), 0);
    });
  });

  group('subtotales y total', () {
    test('el subtotal de una fila es la multiplicación del corte', () {
      // El caso del enunciado: 100.000 x 3 = 300.000
      final fila = ConteoDenominacion(100000, 3);
      expect(fila.subtotal, 300000);
    });

    test('el total suma todas las filas', () {
      final planilla = planillaEnBlanco();
      planilla.firstWhere((f) => f.valor == 100000).cantidad = 3;
      planilla.firstWhere((f) => f.valor == 10000).cantidad = 5;
      planilla.firstWhere((f) => f.valor == 500).cantidad = 4;

      expect(totalContado(planilla), 300000 + 50000 + 2000);
      expect(unidadesContadas(planilla), 12);
    });

    test('las denominaciones sin unidades no aportan', () {
      final planilla = planillaEnBlanco();
      planilla.firstWhere((f) => f.valor == 50).cantidad = 0;
      expect(totalContado(planilla), 0);
    });
  });
}
