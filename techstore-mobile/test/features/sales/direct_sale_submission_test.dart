import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:local_auth_platform_interface/local_auth_platform_interface.dart';
import 'package:techstore_mobile/app/realtime_coordinator.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/native/haptics_service.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/realtime/realtime_event.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/core/widgets/money_field.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/data/catalog_repository.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/sales/presentation/direct_sale_page.dart';

import '../../support/sesion_de_prueba.dart';

const _producto = Product(
  id: 7,
  name: 'Teclado de prueba',
  price: 100000,
  currency: 'PYG',
  available: true,
  maxPerOrder: 10,
);

final _botonVenta = find.widgetWithText(
  FilledButton,
  'Confirmar venta y facturar',
);

void main() {
  late LocalAuthPlatform previousAuth;
  late bool previousHitTestWarningShouldBeFatal;
  setUp(() {
    previousAuth = LocalAuthPlatform.instance;
    LocalAuthPlatform.instance = _SinBiometria();
    previousHitTestWarningShouldBeFatal =
        WidgetController.hitTestWarningShouldBeFatal;
    WidgetController.hitTestWarningShouldBeFatal = true;
  });
  tearDown(() {
    LocalAuthPlatform.instance = previousAuth;
    WidgetController.hitTestWarningShouldBeFatal =
        previousHitTestWarningShouldBeFatal;
  });

  testWidgets('dos pulsaciones antes del frame envían una sola venta', (
    tester,
  ) async {
    final app = await _prepararVenta(tester);
    final enviar = tester.widget<FilledButton>(_botonVenta).onPressed!;

    // Se conserva el callback anterior al siguiente frame, como dos toques
    // rápidos. La comprobación de identidad queda pendiente a propósito.
    enviar();
    enviar();
    expect(app.store.guardReads, 1);
    expect(app.red.ventas, isEmpty);
    await tester.pump();
    expect(tester.widget<FilledButton>(_botonVenta).onPressed, isNull);

    app.store.guard.complete(false);
    await tester.pumpAndSettle();
    expect(app.red.ventas, hasLength(1));
    expect(app.red.ventas.single.data, {
      'items': [
        {'productoId': 7, 'cantidad': 1, 'descuento': 0},
      ],
      'lineas': [
        {'tipoPago': 'Efectivo', 'monto': 100000},
      ],
    });
    app.red.respuestas.single.complete(_json(_pago(77)));
    await tester.pumpAndSettle();
    expect(
      find.text('Venta N.º 77 registrada · Factura 001-001-0000077'),
      findsOneWidget,
    );
    expect(app.red.ventas, hasLength(1));
    // El pago era de la venta facturada: la siguiente arranca sin monto.
    final monto = tester.widget<TextFormField>(
      find.descendant(
        of: find.byType(MoneyField),
        matching: find.byType(TextFormField),
      ),
    );
    expect(monto.controller!.text, isEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets('cancelar la identidad recupera el botón sin enviar una venta', (
    tester,
  ) async {
    final app = await _prepararVenta(tester, guardaActiva: true);
    await tester.tap(_botonVenta);
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.text('Confirmar venta'), findsOneWidget);
    expect(app.red.ventas, isEmpty);

    await tester.tap(find.widgetWithText(TextButton, 'Cancelar'));
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsNothing);
    expect(tester.widget<FilledButton>(_botonVenta).onPressed, isNotNull);
    expect(app.red.ventas, isEmpty);
    expect(
      find.text('Venta cancelada: no se validó la identidad'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('un error de red permite reintentar la venta conservada', (
    tester,
  ) async {
    final app = await _prepararVenta(tester, guardaActiva: false);
    await tester.tap(_botonVenta);
    await tester.pumpAndSettle();
    expect(app.red.ventas, hasLength(1));
    app.red.respuestas.single.completeError(
      DioException(
        requestOptions: app.red.ventas.single,
        type: DioExceptionType.connectionError,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('No se pudo conectar con TechStore'), findsOneWidget);
    expect(tester.widget<FilledButton>(_botonVenta).onPressed, isNotNull);

    // El aviso tapa el pie de la lista, donde está el botón: quien reintenta
    // lo descarta primero, y sin esto el toque cae sobre el aviso.
    await tester.drag(find.byType(SnackBar), const Offset(0, 150));
    await tester.pumpAndSettle();
    expect(find.byType(SnackBar), findsNothing);
    await tester.ensureVisible(_botonVenta);
    await tester.pumpAndSettle();
    expect(_botonVenta.hitTestable(), findsOneWidget);
    await tester.tap(_botonVenta);
    await tester.pumpAndSettle();
    expect(app.red.ventas, hasLength(2));
    expect(app.red.ventas.last.data, app.red.ventas.first.data);
    app.red.respuestas.last.complete(_json(_pago(78)));
    await tester.pumpAndSettle();
    expect(
      find.text('Venta N.º 78 registrada · Factura 001-001-0000078'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('una respuesta sin número no muestra «null» en el aviso', (
    tester,
  ) async {
    final app = await _prepararVenta(tester, guardaActiva: false);
    await tester.tap(_botonVenta);
    await tester.pumpAndSettle();
    app.red.respuestas.single.complete(_json(<String, Object?>{}));
    await tester.pumpAndSettle();
    expect(find.text('Venta registrada'), findsOneWidget);
    expect(find.textContaining('null'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  for (final falla in [false, true]) {
    testWidgets(
      'salir con la venta pendiente tolera ${falla ? 'un error de red' : 'una respuesta 200'}',
      (tester) async {
        final app = await _prepararVenta(tester, guardaActiva: false);
        await tester.tap(_botonVenta);
        await tester.pumpAndSettle();
        expect(app.red.ventas, hasLength(1));
        expect(app.red.respuestas.single.isCompleted, isFalse);
        expect(tester.widget<FilledButton>(_botonVenta).onPressed, isNull);
        final solicitudesAntes = app.red.solicitudes.length;

        // El container sigue vivo: se desmonta la pantalla, no se cancela
        // artificialmente la petición que está esperando su respuesta.
        await tester.pumpWidget(
          const MaterialApp(
            home: Scaffold(body: Text('Fuera de venta directa')),
          ),
        );
        expect(find.byType(DirectSalePage), findsNothing);
        expect(tester.takeException(), isNull);

        if (falla) {
          app.red.respuestas.single.completeError(
            DioException(
              requestOptions: app.red.ventas.single,
              type: DioExceptionType.connectionError,
            ),
          );
        } else {
          app.red.respuestas.single.complete(_json(_pago(79)));
        }
        await tester.pumpAndSettle();
        expect(find.text('Fuera de venta directa'), findsOneWidget);
        expect(find.byType(SnackBar), findsNothing);
        expect(app.red.ventas, hasLength(1));
        expect(app.red.solicitudes, hasLength(solicitudesAntes));
        expect(tester.takeException(), isNull);
      },
    );
  }
}

Future<({_RedDeVentas red, _StoreDeConfirmacion store})> _prepararVenta(
  WidgetTester tester, {
  bool? guardaActiva,
}) async {
  final red = _RedDeVentas();
  final store = _StoreDeConfirmacion();
  if (guardaActiva != null) store.guard.complete(guardaActiva);
  final api = ApiClient(
    baseUrl: 'https://techstore.local',
    logger: const LoggerMudo(),
    dio: Dio()..httpClientAdapter = red,
  );
  final container = ProviderContainer(
    overrides: [
      apiClientProvider.overrideWithValue(api),
      secureStoreProvider.overrideWithValue(store),
      catalogRepositoryProvider.overrideWithValue(CatalogRepository(api)),
      eventosRealtimeProvider.overrideWithValue(
        const Stream<RealtimeEvent>.empty(),
      ),
    ],
  );
  addTearDown(() {
    container.dispose();
    api.dio.close(force: true);
  });
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: const MaterialApp(home: DirectSalePage()),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byTooltip('Agregar Teclado de prueba'));
  await tester.pumpAndSettle();
  final monto = find.descendant(
    of: find.byType(MoneyField),
    matching: find.byType(TextFormField),
  );
  await tester.ensureVisible(monto);
  await tester.enterText(monto, '100000');
  tester.testTextInput.hide();
  await tester.ensureVisible(_botonVenta);
  await tester.pumpAndSettle();
  expect(tester.widget<FilledButton>(_botonVenta).onPressed, isNotNull);
  return (red: red, store: store);
}

final class _RedDeVentas implements HttpClientAdapter {
  final solicitudes = <RequestOptions>[];
  final ventas = <RequestOptions>[];
  final respuestas = <Completer<ResponseBody>>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    solicitudes.add(options);
    if (options.method == 'GET' && options.path == '/api/v1/productos') {
      return _json({
        'content': [_producto.toJson()],
      });
    }
    if (options.method == 'POST' && options.path == '/api/v1/ventas') {
      ventas.add(options);
      final respuesta = Completer<ResponseBody>();
      respuestas.add(respuesta);
      return respuesta.future;
    }
    throw StateError('Solicitud inesperada: ${options.method} ${options.path}');
  }

  @override
  void close({bool force = false}) {}
}

/// Lo que devuelve `POST /api/v1/ventas` (`PagoResponse` del backend). Con un
/// `{'numero': …}` inventado, la prueba pasaba y la app decía «Venta #null».
Map<String, Object?> _pago(int numero) => {
  'pedidoNumero': numero,
  'moneda': 'PYG',
  'total': 100000,
  'lineas': [
    {'numero': 1, 'tipoPago': 'Efectivo', 'monto': 100000},
  ],
  'factura': {'numero': '001-001-00000$numero', 'pedidoNumero': numero},
};

ResponseBody _json(Object value) => ResponseBody.fromString(
  jsonEncode(value),
  200,
  headers: {
    Headers.contentTypeHeader: ['application/json'],
  },
);

final class _StoreDeConfirmacion implements SecureStore {
  final guard = Completer<bool>();
  int guardReads = 0;

  @override
  Future<String?> read(String key) {
    if (key == 'techstore.biometric_guard_operations') {
      guardReads++;
      return guard.future.then((enabled) => enabled ? 'true' : null);
    }
    return Future.value(key == HapticsService.enabledKey ? 'false' : null);
  }

  @override
  Future<void> clear() async {}

  @override
  Future<void> delete(String key) async {}

  @override
  Future<void> write(String key, String value) async {}
}

final class _SinBiometria extends LocalAuthPlatform {
  @override
  Future<bool> isDeviceSupported() async => false;
}
