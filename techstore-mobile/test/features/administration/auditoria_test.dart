import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/features/administration/presentation/audit_page.dart';

/// La auditoría: qué se ve de cada registro y cómo se lee un cambio.
///
/// El punto de la pantalla es que nadie tenga que leer JSON para entender qué
/// pasó, así que los casos miran el detalle del cambio —«antes → después»— y no
/// solo que la lista aparezca.
void main() {
  setUpAll(() async {
    // La pantalla formatea fechas en es_PY; sin esto el widget lanza
    // LocaleDataException al construirse.
    await initializeDateFormatting('es_PY');
  });

  final registros = [
    {
      'id': 1,
      'entidad': 'Producto',
      'entidadId': 42,
      'accion': 'Editar',
      'detalle': 'Cambió el precio de venta',
      'usuarioEmail': 'admin@techstore.py',
      'usuarioRol': 'Admin',
      'cambios': {
        'precio': {'antes': 100000, 'despues': 125000},
      },
      'fecha': '2026-09-04T10:15:00',
    },
    {
      'id': 2,
      'entidad': 'CierreCaja',
      'entidadId': 16,
      'accion': 'Crear',
      'detalle': 'Cerró la caja del turno',
      'usuarioEmail': 'cajero@techstore.py',
      'usuarioRol': 'Cajero',
      'cambios': null,
      'fecha': '2026-09-04T16:24:50',
    },
  ];

  Widget pantalla({
    List<Map<String, Object?>>? datos,
    List<String> entidades = const ['Producto', 'CierreCaja'],
  }) => ProviderScope(
    overrides: [
      auditoriaProvider.overrideWith((ref, filtro) async => datos ?? registros),
      entidadesAuditadasProvider.overrideWith((ref) async => entidades),
    ],
    child: const MaterialApp(home: AuditPage()),
  );

  testWidgets('cada registro dice quién, qué, sobre qué y cuándo', (
    tester,
  ) async {
    await tester.pumpWidget(pantalla());
    await tester.pumpAndSettle();

    expect(find.textContaining('Editar · Producto N.º 42'), findsOneWidget);
    expect(find.text('Cambió el precio de venta'), findsOneWidget);
    // El rol viaja en el propio registro: es el que tenía al hacerlo.
    expect(find.textContaining('admin@techstore.py · Admin'), findsOneWidget);
  });

  testWidgets('el detalle muestra el antes y el después, no JSON', (
    tester,
  ) async {
    await tester.pumpWidget(pantalla());
    await tester.pumpAndSettle();

    await tester.tap(find.textContaining('Editar · Producto').first);
    await tester.pumpAndSettle();

    expect(find.text('Qué cambió'), findsOneWidget);
    // Los montos pasan por el formateador: 125000 se lee como cantidad, no
    // como el número crudo que guardó la base.
    expect(find.textContaining('125.000'), findsOneWidget);
    expect(find.textContaining('100.000'), findsOneWidget);
    // Y nada de llaves ni comillas.
    expect(find.textContaining('{'), findsNothing);
  });

  testWidgets('sin cambios registrados no inventa una sección vacía', (
    tester,
  ) async {
    await tester.pumpWidget(pantalla());
    await tester.pumpAndSettle();

    await tester.tap(find.textContaining('Crear · Cierre').first);
    await tester.pumpAndSettle();

    expect(find.text('Qué cambió'), findsNothing);
    expect(find.text('Cajero'), findsWidgets);
  });

  testWidgets('el estado vacío distingue si hay filtros puestos', (
    tester,
  ) async {
    await tester.pumpWidget(pantalla(datos: const []));
    await tester.pumpAndSettle();

    expect(find.text('Todavía no hay nada auditado'), findsOneWidget);
  });

  testWidgets('en un teléfono chico no se desborda', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(pantalla());
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });
}
