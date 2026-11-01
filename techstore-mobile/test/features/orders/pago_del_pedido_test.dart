import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/features/checkout/application/checkout_providers.dart';
import 'package:techstore_mobile/features/orders/presentation/orders_page.dart';
import 'package:techstore_mobile/features/payments/application/payments_providers.dart';
import 'package:techstore_mobile/features/payments/domain/payment_models.dart';
import 'package:techstore_mobile/features/payments/presentation/verify_payments_page.dart';

/// Las pantallas del pago por transferencia, con los datos que manda el
/// servidor. No las alcanza el recorrido general, y son las que el cliente y
/// la tienda miran mientras la plata está en juego.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_PY');
  });

  Map<String, Object?> pedido({
    required String estadoPago,
    String estado = 'P',
    String? motivo,
  }) => {
    'id': 80,
    'numero': 80,
    'estado': estado,
    'fecha': '2026-10-31T10:00:00',
    'moneda': 'PYG',
    'total': 135000,
    'subtotalProductos': 100000,
    'costoEnvio': 35000,
    'metodoEntrega': 'ENVIO_DOMICILIO',
    'metodoPago': 'TRANSFERENCIA_BANCARIA',
    'estadoPago': estadoPago,
    'pagoRechazoMotivo': motivo,
    'usuarioEmail': 'cliente@test.py',
    'facturaDisponible': false,
    'direccionEntrega': {
      'nombreDestinatario': 'Cliente QA',
      'telefono': '0981123456',
      'direccionLinea1': 'Calle QA 100',
      'ciudad': 'Asunción',
    },
    'detalles': [
      {
        'productoNombre': 'QA Producto Movil',
        'cantidad': 1,
        'subtotal': 100000,
      },
    ],
  };

  const comprobante = PaymentReceipt(
    id: 5,
    mimeType: 'application/pdf',
    sizeBytes: 70000,
    review: ReceiptReview.inReview,
  );

  Future<void> abrir(
    WidgetTester tester,
    Widget pantalla,
    Map<String, Object?> datos, {
    List<PaymentReceipt> comprobantes = const [comprobante],
  }) async {
    // Un teléfono común de ancho y alto de sobra: la lista construye todas
    // las secciones sin tener que desplazarla.
    tester.view.physicalSize = const Size(411, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final router = GoRouter(
      routes: [GoRoute(path: '/', builder: (_, _) => pantalla)],
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          orderProvider.overrideWith((ref, id) async => datos),
          shipmentProvider.overrideWith(
            (ref, id) async => {'estado': 'PENDIENTE'},
          ),
          orderReceiptsProvider.overrideWith((ref, id) async => comprobantes),
          orderHistoryProvider.overrideWith((ref, id) async => const []),
          orderReceiptFileProvider.overrideWith(
            (ref, ids) async => Uint8List(0),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('en verificación lo dice y no ofrece cancelar', (tester) async {
    await abrir(
      tester,
      const OrderDetailPage(orderId: 80, justPlaced: true),
      pedido(estadoPago: 'EN_VERIFICACION'),
    );

    expect(find.text('¡Recibimos tu pedido!'), findsOneWidget);
    expect(find.text('Verificando el pago'), findsOneWidget);
    expect(find.text('Verificando tu transferencia'), findsOneWidget);
    expect(find.text('En revisión'), findsOneWidget);
    // Ya transfirió: cancelarlo solo dejaría la plata sin pedido.
    expect(find.text('Cancelar pedido'), findsNothing);
    expect(find.text('Adjuntar comprobante'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('rechazado muestra el motivo y deja cargar otro', (tester) async {
    await abrir(
      tester,
      const OrderDetailPage(orderId: 80),
      pedido(estadoPago: 'RECHAZADO', motivo: 'El monto no coincide'),
      comprobantes: const [
        PaymentReceipt(
          id: 5,
          mimeType: 'application/pdf',
          sizeBytes: 70000,
          review: ReceiptReview.rejected,
          rejectionReason: 'El monto no coincide',
        ),
      ],
    );

    expect(find.text('Pago rechazado'), findsOneWidget);
    expect(find.text('Motivo: El monto no coincide'), findsOneWidget);
    expect(find.text('Cargar un nuevo comprobante'), findsOneWidget);
    expect(find.text('Ver los datos para transferir'), findsOneWidget);
    expect(find.text('Rechazado'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('el recorrido marca la verificación como paso actual', (
    tester,
  ) async {
    final semantica = tester.ensureSemantics();
    await abrir(
      tester,
      const OrderDetailPage(orderId: 80),
      pedido(estadoPago: 'EN_VERIFICACION'),
    );

    expect(find.text('Pago pendiente'), findsOneWidget);
    expect(find.text('Verificación'), findsOneWidget);
    expect(find.text('En camino'), findsOneWidget);
    // Con lector de pantalla se dice en qué paso está, no solo con el color.
    expect(
      find.bySemanticsLabel(RegExp('Verificación: en curso')),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel(RegExp('Pago pendiente: hecho')),
      findsOneWidget,
    );
    semantica.dispose();
  });

  testWidgets('la tienda ve el monto y puede aprobar o rechazar', (
    tester,
  ) async {
    await abrir(
      tester,
      const VerifyPaymentDetailPage(orderId: 80),
      pedido(estadoPago: 'EN_VERIFICACION'),
    );

    expect(find.text('Tiene que haber llegado'), findsOneWidget);
    expect(find.text('Aprobar pago'), findsOneWidget);
    expect(find.text('Rechazar'), findsOneWidget);

    // Rechazar pide el motivo antes de hacer nada.
    await tester.tap(find.text('Rechazar'));
    await tester.pumpAndSettle();
    expect(find.text('¿Por qué no se puede validar?'), findsOneWidget);
    final boton = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Rechazar y avisar al cliente'),
    );
    expect(boton.onPressed, isNull, reason: 'sin motivo no se rechaza');
    expect(tester.takeException(), isNull);
  });

  testWidgets('aprobar y rechazar entran en un renglón en un teléfono chico', (
    tester,
  ) async {
    await abrir(
      tester,
      const VerifyPaymentDetailPage(orderId: 80),
      pedido(estadoPago: 'EN_VERIFICACION'),
    );
    // Lo que se vio en el emulador: «Rechazar» partido en dos.
    tester.view.physicalSize = const Size(360, 800);
    tester.platformDispatcher.textScaleFactorTestValue = 1.2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await tester.pumpAndSettle();

    final renglon = tester.getSize(find.text('Aprobar pago')).height;
    expect(tester.getSize(find.text('Rechazar')).height, renglon);
    expect(tester.takeException(), isNull);
  });

  testWidgets('un pago ya aprobado no ofrece decidir de nuevo', (tester) async {
    await abrir(
      tester,
      const VerifyPaymentDetailPage(orderId: 80),
      pedido(estadoPago: 'APROBADO', estado: 'G'),
    );

    expect(find.text('Este pago ya está aprobado.'), findsOneWidget);
    expect(find.text('Aprobar pago'), findsNothing);
  });
}
