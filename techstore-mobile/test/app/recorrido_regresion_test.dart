import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/app/app_theme.dart';
import 'package:techstore_mobile/app/home_page.dart';
import 'package:techstore_mobile/core/cache/cache_store.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/diseno/paleta.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/administration/domain/resource_definition.dart';
import 'package:techstore_mobile/features/administration/presentation/business_hub_page.dart';
import 'package:techstore_mobile/features/administration/presentation/resource_page.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';
import 'package:techstore_mobile/features/auth/presentation/account_page.dart';
import 'package:techstore_mobile/features/cart/application/cart_controller.dart';
import 'package:techstore_mobile/features/cart/presentation/cart_page.dart';
import 'package:techstore_mobile/features/cash/presentation/cash_actions_page.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/catalog/presentation/favorites_page.dart';
import 'package:techstore_mobile/features/checkout/application/checkout_providers.dart';
import 'package:techstore_mobile/features/checkout/domain/checkout_models.dart';
import 'package:techstore_mobile/features/checkout/presentation/address_page.dart';
import 'package:techstore_mobile/features/checkout/presentation/checkout_page.dart';
import 'package:techstore_mobile/features/orders/presentation/orders_page.dart';
import 'package:techstore_mobile/features/sales/presentation/direct_sale_page.dart';
import 'package:techstore_mobile/features/security/application/security_providers.dart';
import 'package:techstore_mobile/features/security/domain/app_permissions.dart';

import '../support/sesion_de_prueba.dart';

const _producto = Product(
  id: 7,
  name: 'Teclado',
  price: 100000,
  currency: 'PYG',
  available: true,
  maxPerOrder: 10,
);

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_PY');
    for (final family in ['Inter', 'SpaceGrotesk', 'JetBrainsMono']) {
      final loader = FontLoader(family);
      final weights = family == 'Inter'
          ? [400, 500, 600, 700]
          : family == 'SpaceGrotesk'
          ? [500, 600, 700]
          : [500, 700];
      for (final weight in weights) {
        loader.addFont(rootBundle.load('assets/fonts/$family-$weight.ttf'));
      }
      await loader.load();
    }
  });

  Future<({ProviderContainer container, GoRouter router, _Red red})> abrir(
    WidgetTester tester,
    String ruta, {
    bool conProducto = false,
    String rol = 'Cliente',
    bool cajaAbierta = false,
  }) async {
    final db = AppDatabase(NativeDatabase.memory());
    final red = _Red(rol);
    final container = ProviderContainer(
      overrides: [
        cacheStoreProvider.overrideWithValue(CacheStore(db)),
        secureStoreProvider.overrideWithValue(StoreConSesion()),
        apiClientProvider.overrideWithValue(
          ApiClient(
            baseUrl: 'https://techstore.local',
            logger: const LoggerMudo(),
            dio: Dio()..httpClientAdapter = red,
          ),
        ),
        ordersProvider.overrideWith((ref) async => []),
        favoritesProvider.overrideWith((ref) async => []),
        orderProvider(78).overrideWith(
          (ref) async => {
            'id': 78,
            'numero': 78,
            'estado': 'G',
            'total': 100000,
            'moneda': 'PYG',
            'metodoPago': 'PAGO_EN_LOCAL',
            'detalles': [],
          },
        ),
        shipmentProvider(78).overrideWith((ref) async => <String, Object?>{}),
        addressesProvider.overrideWith(
          (ref) async => const [
            DeliveryAddress(
              id: 1,
              recipient: 'Persona',
              phone: '0981000000',
              description: 'Casa',
              line1: 'Calle de prueba',
              city: 'Asunción',
            ),
          ],
        ),
        cajaAbiertaProvider.overrideWith(
          (ref) async => {
            'abierta': cajaAbierta,
            'numero': 1,
            'saldoInicial': 100000,
            'fechaApertura': '2026-10-20T08:00:00',
            'moneda': 'PYG',
          },
        ),
        permisosProvider.overrideWith(
          (ref) async => AppPermissions(
            rol: rol,
            permitidos: {
              'facturas.crear',
              'caja.ver',
              'pedidos.ver',
              if (rol == 'Admin') ...[
                'seguridad.ver',
                'productos.ver',
                'usuarios.ver',
              ],
            },
            pantallas: {},
          ),
        ),
      ],
    );
    await tester.runAsync(() async {
      container.read(authSessionProvider);
      for (
        var i = 0;
        i < 30 && !container.read(authSessionProvider).isAuthenticated;
        i++
      ) {
        await Future<void>.delayed(const Duration(milliseconds: 10));
      }
      container.read(cartProvider);
      await Future<void>.delayed(const Duration(milliseconds: 30));
    });
    expect(container.read(authSessionProvider).isAuthenticated, isTrue);
    if (conProducto) container.read(cartProvider.notifier).add(_producto);
    final router = GoRouter(
      initialLocation: ruta,
      routes: [
        GoRoute(
          path: '/',
          builder: (_, _) => const Scaffold(body: Text('Inicio de prueba')),
        ),
        GoRoute(
          path: '/catalog',
          builder: (_, _) =>
              Scaffold(appBar: AppBar(title: const Text('Catálogo de prueba'))),
        ),
        GoRoute(path: '/orders', builder: (_, _) => const OrdersPage()),
        GoRoute(
          path: '/orders/:id',
          builder: (_, _) => const OrderDetailPage(orderId: 78),
        ),
        GoRoute(path: '/favorites', builder: (_, _) => const FavoritesPage()),
        GoRoute(path: '/cart', builder: (_, _) => const CartPage()),
        GoRoute(path: '/checkout', builder: (_, _) => const CheckoutPage()),
        GoRoute(path: '/addresses', builder: (_, _) => const AddressPage()),
        GoRoute(path: '/account', builder: (_, _) => const AccountPage()),
        GoRoute(
          path: '/home',
          builder: (_, _) => const HomePage(environmentLabel: null),
        ),
        GoRoute(
          path: '/resources/all-orders',
          builder: (_, _) => ResourcePage(definition: resources['all-orders']!),
        ),
        GoRoute(
          path: '/operations',
          builder: (_, _) => const BusinessHubPage(admin: false),
        ),
        GoRoute(
          path: '/admin',
          builder: (_, _) => const BusinessHubPage(admin: true),
        ),
        GoRoute(
          path: '/cash/actions',
          builder: (_, _) => const CashActionsPage(),
        ),
        GoRoute(path: '/sales/new', builder: (_, _) => const DirectSalePage()),
      ],
    );
    addTearDown(() async {
      router.dispose();
      container.dispose();
      await db.close();
    });
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(
          theme: AppTheme.marca(),
          routerConfig: router,
          locale: const Locale('es', 'PY'),
          supportedLocales: const [Locale('es', 'PY')],
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return (container: container, router: router, red: red);
  }

  for (final ruta in ['/orders', '/favorites', '/cart', '/checkout']) {
    testWidgets('estado vacío $ruta conserva el regreso desde el catálogo', (
      tester,
    ) async {
      final app = await abrir(tester, ruta);
      await tester.ensureVisible(find.text('Ver productos'));
      await tester.tap(find.text('Ver productos'));
      await tester.pumpAndSettle();
      expect(app.router.canPop(), isTrue);
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(app.router.routeInformationProvider.value.uri.path, ruta);
    });
  }

  testWidgets('cajero abre venta directa desde sus operaciones', (
    tester,
  ) async {
    await abrir(tester, '/operations', rol: 'Cajero');
    await tester.tap(find.text('Venta directa'));
    await tester.pumpAndSettle();
    expect(find.text('Venta directa'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Confirmar venta y facturar'),
      200,
      scrollable: find
          .descendant(
            of: find.descendant(
              of: find.byType(DirectSalePage),
              matching: find.byType(ListView),
            ),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Confirmar venta y facturar'),
          )
          .onPressed,
      isNull,
    );
    expect(tester.takeException(), isNull);
  });

  for (final rol in ['Cajero', 'Admin']) {
    testWidgets('$rol abre la gestión de pedidos desde el inicio', (
      tester,
    ) async {
      await abrir(tester, '/home', rol: rol);
      await tester.scrollUntilVisible(
        find.text('Pedidos'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Pedidos'));
      await tester.pumpAndSettle();
      expect(find.byType(ResourcePage), findsOneWidget);
      expect(find.byType(OrdersPage), findsNothing);
      await tester.tap(find.byType(PopupMenuButton<String>).first);
      await tester.pumpAndSettle();
      expect(find.text('Confirmar'), findsOneWidget);
      expect(find.text('Registrar pago'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  for (final abierta in [false, true]) {
    testWidgets(
      'cajero con caja ${abierta ? 'abierta' : 'cerrada'} ofrece las acciones correspondientes',
      (tester) async {
        final app = await abrir(
          tester,
          '/cash/actions',
          rol: 'Cajero',
          cajaAbierta: abierta,
        );
        if (abierta) {
          expect(find.text('Registrar movimiento'), findsOneWidget);
          expect(find.text('Cerrar caja'), findsOneWidget);
          expect(find.text('Abrir caja'), findsNothing);
        } else {
          expect(find.text('La caja está cerrada'), findsOneWidget);
          await tester.tap(find.text('Abrir caja'));
          await tester.pumpAndSettle();
          expect(find.byType(AlertDialog), findsOneWidget);
          await tester.tap(find.text('Cancelar'));
          await tester.pumpAndSettle();
          expect(app.red.escrituras, isEmpty);
        }
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('administrador ve seguridad, promociones y usuarios', (
    tester,
  ) async {
    await abrir(tester, '/admin', rol: 'Admin');
    for (final titulo in ['Seguridad', 'Promociones', 'Usuarios']) {
      await tester.scrollUntilVisible(find.text(titulo), 150);
      expect(find.text(titulo), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });

  for (final conFlecha in [true, false]) {
    testWidgets(
      'pedido sin historial vuelve al inicio con ${conFlecha ? 'flecha' : 'atrás del sistema'}',
      (tester) async {
        final app = await abrir(tester, '/orders/78');
        expect(find.text('Pedido N.º 78'), findsOneWidget);
        if (conFlecha) {
          await tester.tap(find.byType(BackButton));
        } else {
          await tester.binding.handlePopRoute();
        }
        await tester.pumpAndSettle();
        expect(app.router.routeInformationProvider.value.uri.path, '/');
      },
    );
  }

  testWidgets('pedido abierto encima de la lista vuelve a la lista', (
    tester,
  ) async {
    final app = await abrir(tester, '/orders');
    unawaited(app.router.push<void>('/orders/78'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(app.router.routeInformationProvider.value.uri.path, '/orders');
  });

  testWidgets('Pagado usa el verde de éxito', (tester) async {
    await abrir(tester, '/orders/78');
    expect(tester.widget<Text>(find.text('Pagado')).style?.color, Paleta.exito);
  });

  testWidgets(
    'Mi cuenta carga el perfil y valida contraseñas antes de enviar',
    (tester) async {
      final app = await abrir(tester, '/account');
      expect(find.text('Persona De Prueba'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.widgetWithText(OutlinedButton, 'Cambiar contraseña'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(
        find.widgetWithText(OutlinedButton, 'Cambiar contraseña'),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('La nueva contraseña no cumple los requisitos'),
        findsOneWidget,
      );
      expect(app.red.escrituras, isEmpty);
    },
  );

  testWidgets('Direcciones muestra la dirección guardada', (tester) async {
    await abrir(tester, '/addresses');
    expect(find.text('Casa'), findsOneWidget);
    expect(find.textContaining('Calle de prueba'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final (ancho, escala) in [(411.0, 1.0), (320.0, 1.3)]) {
    testWidgets(
      'confirmación ocupa el ancho y no corta texto: $ancho dp, escala $escala',
      (tester) async {
        tester.view.physicalSize = Size(ancho, 900);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = escala;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        await abrir(tester, '/checkout', conProducto: true);
        for (var i = 0; i < 2; i++) {
          await tester.tap(find.text('Continuar'));
          await tester.pumpAndSettle();
        }
        final boton = find.widgetWithText(FilledButton, 'Confirmar compra');
        final texto = tester.getSize(find.text('Confirmar compra'));
        expect(texto.height, lessThan(40));
        expect(tester.getRect(boton).right, closeTo(ancho - 16, 1));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'con transferencia no se puede seguir sin adjuntar el comprobante',
    (tester) async {
      await abrir(tester, '/checkout', conProducto: true);
      await tester.tap(find.text('Continuar'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Transferencia bancaria'));
      await tester.pumpAndSettle();

      // Los datos de la tienda, listos para copiar, y el pedido del comprobante.
      expect(find.text('Transferí exactamente'), findsOneWidget);
      expect(find.text('Banco de Prueba'), findsOneWidget);
      expect(find.text('Adjuntar comprobante'), findsOneWidget);
      final continuar = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Continuar'),
      );
      expect(continuar.onPressed, isNull, reason: 'falta el comprobante');

      // Pagando al retirar no hace falta.
      await tester.tap(find.text('Pago al retirar'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Continuar'),
            )
            .onPressed,
        isNotNull,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('envío a domicilio excluye el retiro de la cotización', (
    tester,
  ) async {
    await abrir(tester, '/checkout', conProducto: true);
    await tester.tap(find.text('Envío a domicilio'));
    await tester.pumpAndSettle();
    // La dirección se elige con el selector del paso de entrega.
    await tester.tap(find.byType(DropdownButtonFormField<int>));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Casa').last);
    await tester.pumpAndSettle();
    // Sin el retiro queda una sola tarifa a domicilio: se elige sola, sin
    // abrir una hoja con una opción.
    expect(find.text('Elegí cómo querés recibirlo'), findsNothing);
    expect(find.text('Transportadora de prueba'), findsOneWidget);
    expect(find.text('Retiro en tienda'), findsNothing);
    // Y se dice antes de pagar que el envío va por transferencia.
    expect(find.textContaining('por transferencia'), findsWidgets);
  });

  for (final sinStock in [false, true]) {
    testWidgets(
      'confirmar compra ${sinStock ? 'vuelve al carrito si se agotó el stock' : 'crea un solo pedido y permite volver al inicio'}',
      (tester) async {
        final app = await abrir(tester, '/', conProducto: true);
        unawaited(app.router.push<void>('/cart'));
        await tester.pumpAndSettle();
        unawaited(app.router.push<void>('/checkout'));
        await tester.pumpAndSettle();
        for (var i = 0; i < 2; i++) {
          await tester.tap(find.text('Continuar'));
          await tester.pumpAndSettle();
        }
        app.red.sinStock = sinStock;
        app.red.esperarDisponibilidad = true;
        await tester.tap(find.text('Confirmar compra'));
        await tester.tap(find.text('Confirmar compra'));
        await tester.pumpAndSettle();
        expect(app.container.read(cartProvider), isEmpty);
        expect(
          app.red.escrituras.where((path) => path == '/api/v1/pedidos').length,
          sinStock ? 0 : 1,
        );
        expect(
          find.text(sinStock ? 'Tu carrito' : 'Detalle del pedido'),
          findsOneWidget,
        );
        await tester.tap(find.byType(BackButton));
        await tester.pumpAndSettle();
        expect(app.router.routeInformationProvider.value.uri.path, '/');
        expect(tester.takeException(), isNull);
      },
    );
  }
}

final class _Red implements HttpClientAdapter {
  _Red(String rol) : _sesion = AdaptadorDeRol(rol);
  final escrituras = <String>[];
  bool sinStock = false;
  bool esperarDisponibilidad = false;
  final AdaptadorDeRol _sesion;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.endsWith('/carrito/validar')) {
      if (esperarDisponibilidad) {
        await Future<void>.delayed(const Duration(milliseconds: 100));
      }
      return ResponseBody.fromString(
        jsonEncode({
          'valido': !sinStock,
          'items': [
            {
              'productoId': 7,
              'producto': {'maxPorPedido': sinStock ? 0 : 10},
            },
          ],
        }),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (options.path == '/api/v1/pedidos' && options.method == 'GET') {
      return ResponseBody.fromString(
        jsonEncode({
          'content': [
            {
              'id': 78,
              'numero': 78,
              'estado': 'P',
              'total': 100000,
              'moneda': 'PYG',
            },
          ],
          'totalElements': 1,
          'totalPages': 1,
        }),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (options.path == '/api/v1/pedidos' && options.method == 'POST') {
      escrituras.add(options.path);
      return ResponseBody.fromString(
        '{"id":78}',
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (options.path.endsWith('/pagos/transferencia')) {
      return ResponseBody.fromString(
        jsonEncode({
          'disponible': true,
          'banco': 'Banco de Prueba',
          'titular': 'TechStore S.A.',
          'numeroCuenta': '0011223344',
          'tieneQr': false,
        }),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (options.path.endsWith('/envios/cotizar')) {
      return ResponseBody.fromString(
        jsonEncode([
          {
            'tarifaId': 1,
            'nombre': 'Retiro en tienda',
            'tipo': 'RETIRO_TIENDA',
            'costo': 0,
            'moneda': 'PYG',
          },
          {
            'tarifaId': 2,
            'nombre': 'Transportadora de prueba',
            'tipo': 'TRANSPORTADORA',
            'costo': 20000,
            'moneda': 'PYG',
          },
        ]),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (options.method != 'GET' && !options.path.endsWith('/auth/refresh')) {
      escrituras.add(options.path);
    }
    return _sesion.fetch(options, requestStream, cancelFuture);
  }

  @override
  void close({bool force = false}) {}
}
