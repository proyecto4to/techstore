import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/features/administration/presentation/detail_fields.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_PY');
  });

  group('etiquetas de campo', () {
    test('usa el nombre en castellano cuando lo conoce', () {
      expect(etiquetaDeCampo('razonSocial'), 'Razón social');
      expect(etiquetaDeCampo('codigoSeguimiento'), 'Código de seguimiento');
    });

    test('una clave desconocida igual se muestra legible', () {
      // Lo importante: que una clave nueva del backend nunca aparezca como
      // código pegado al usuario.
      expect(etiquetaDeCampo('costoActualizadoEn'), 'Costo actualizado en');
      expect(etiquetaDeCampo('nombreDelProveedor'), 'Nombre del proveedor');
    });
  });

  group('formato de valores', () {
    test('los importes llevan separador de miles y símbolo', () {
      // intl separa el importe del simbolo con un espacio duro; lo que importa
      // es el agrupado y que el simbolo este, no que espacio use.
      expect(formatearValor('total', 4200000), startsWith('4.200.000'));
      expect(formatearValor('total', 4200000), endsWith('₲'));
      expect(formatearValor('precio', 45000), startsWith('45.000'));
    });

    test('un número que no es plata no lleva símbolo de moneda', () {
      expect(formatearValor('stockReal', 12), '12');
      expect(formatearValor('iva', 10), '10');
    });

    test('los booleanos se dicen en palabras', () {
      expect(formatearValor('activo', true), 'Sí');
      expect(formatearValor('activo', false), 'No');
    });

    test('los códigos del backend se dicen en palabras', () {
      expect(formatearValor('estado', 'LISTO_PARA_ENVIO'), 'Listo para envio');
      expect(formatearValor('estado', 'PENDIENTE'), 'Pendiente');
    });

    test('una razón social en mayúsculas no se toca', () {
      // Tratar toda palabra en mayusculas como enum convertia «ACME» en
      // «Acme» y le cambiaba el nombre al proveedor.
      expect(formatearValor('razonSocial', 'ACME'), 'ACME');
      expect(formatearValor('razonSocial', 'ACME SRL'), 'ACME SRL');
      expect(formatearValor('ruc', '80012345-6'), '80012345-6');
    });

    test('un vacío se muestra como raya y no como null', () {
      expect(formatearValor('telefono', null), '—');
      expect(formatearValor('telefono', '  '), '—');
    });

    test('las fechas se muestran en formato local', () {
      final texto = formatearValor('fecha', '2026-09-03T15:43:13');
      expect(texto, isNot(contains('T')));
      expect(texto, contains('2026'));
    });
  });

  group('separación de campos', () {
    test('los identificadores van a información técnica', () {
      final campos = separarCampos({
        'id': 73,
        'numero': 69,
        'proveedorId': 4,
        'razonSocial': 'ACME',
      });
      expect(
        campos.visibles.map((e) => e.key),
        containsAll(<String>['numero', 'razonSocial']),
      );
      expect(
        campos.tecnicas.map((e) => e.key),
        containsAll(<String>['id', 'proveedorId']),
      );
    });
  });

  group('la tarjeta de detalle', () {
    testWidgets('muestra etiquetas y valores en vez de JSON', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: DetailFields(
                registro: {
                  'id': 7,
                  'razonSocial': 'Distribuidora Sur',
                  'total': 1500000,
                  'activo': true,
                },
              ),
            ),
          ),
        ),
      );

      expect(find.text('Razón social'), findsOneWidget);
      expect(_seleccionable('Distribuidora Sur'), findsOneWidget);
      expect(find.text('Total'), findsOneWidget);
      expect(_seleccionableQueContenga('1.500.000'), findsOneWidget);
      expect(_seleccionable('Sí'), findsOneWidget);
      // Nada de llaves ni comillas de JSON en pantalla.
      expect(find.textContaining('{'), findsNothing);
      expect(_seleccionableQueContenga('{'), findsNothing);
    });

    testWidgets('un objeto anidado se abre como sección, no como texto', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: DetailFields(
                registro: {
                  'numero': 1,
                  'proveedor': {'razonSocial': 'ACME', 'ruc': '80012345-6'},
                },
              ),
            ),
          ),
        ),
      );

      expect(find.text('Proveedor'), findsOneWidget);
      expect(_seleccionable('ACME'), findsOneWidget);
      expect(find.text('RUC'), findsOneWidget);
      expect(_seleccionable('80012345-6'), findsOneWidget);
    });
  });

  group('humanizarCodigo', () {
    test('separa las mayúsculas pegadas de los nombres de entidad', () {
      // La auditoría mostraba «Cierrecaja» y «Eventovehiculo»: los nombres de
      // entidad vienen en CamelCase y solo se les bajaba la caja.
      expect(humanizarCodigo('CierreCaja'), 'Cierre caja');
      expect(humanizarCodigo('AperturaCaja'), 'Apertura caja');
      expect(humanizarCodigo('EventoVehiculo'), 'Evento vehiculo');
      expect(humanizarCodigo('MovimientoCaja'), 'Movimiento caja');
    });

    test('un código en mayúsculas sigue saliendo igual que antes', () {
      // No hay minúscula antes de la mayúscula, así que la separación de
      // CamelCase no lo toca.
      expect(humanizarCodigo('LISTO_PARA_ENVIO'), 'Listo para envio');
      expect(humanizarCodigo('PENDIENTE'), 'Pendiente');
    });

    test('una sola palabra queda con la inicial en mayúscula', () {
      expect(humanizarCodigo('Usuario'), 'Usuario');
      expect(humanizarCodigo('producto'), 'Producto');
    });
  });
}

/// Los valores se muestran con SelectableText para poder copiar un RUC o un
/// codigo de seguimiento, y find.text no llega a ese widget.
Finder _seleccionable(String texto) => find.byWidgetPredicate(
  (w) => w is SelectableText && w.data == texto,
);

Finder _seleccionableQueContenga(String parte) => find.byWidgetPredicate(
  (w) => w is SelectableText && (w.data ?? '').contains(parte),
);
