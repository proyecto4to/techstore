import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show ProviderListenable;
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/home_page.dart';
import 'package:techstore_mobile/app/realtime_coordinator.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';
import 'package:techstore_mobile/features/auth/presentation/account_page.dart';
import 'package:techstore_mobile/features/auth/presentation/login_page.dart';
import 'package:techstore_mobile/features/auth/presentation/password_recovery_page.dart';
import 'package:techstore_mobile/features/auth/presentation/register_page.dart';
import 'package:techstore_mobile/features/cart/application/cart_controller.dart';
import 'package:techstore_mobile/features/cart/presentation/cart_page.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/presentation/catalog_page.dart';
import 'package:techstore_mobile/features/catalog/presentation/ficha_de_producto_page.dart';
import 'package:techstore_mobile/features/notifications/presentation/notifications_page.dart';
import 'package:techstore_mobile/features/orders/presentation/orders_page.dart';

import '../../test/support/sesion_de_prueba.dart';
import 'api_de_banco.dart';
import 'banco.dart';

ProviderContainer _contenedor({bool conSesion = true, String rol = 'Cliente'}) =>
    contenedorDeBanco(
  extra: [
    apiClientProvider.overrideWithValue(apiDeBanco(rol: rol)),
    secureStoreProvider.overrideWithValue(
      conSesion ? StoreConSesion() : (StoreConSesion()..value = null),
    ),
    eventosRealtimeProvider.overrideWithValue(const Stream.empty()),
  ],
);

/// Monta la pantalla, deja que cargue todo —datos, fotos, animaciones de
/// entrada— y la guarda.
///
/// Todo lo que espera disco o red se precarga **antes** de montar y en tiempo
/// real: lo que se empieza adentro del tiempo falso de la prueba solo avanza
/// cuando ese reloj se mueve, y esperarlo desde afuera traba la captura.
/// [precarga] recibe los providers que la pantalla va a mirar.
Future<void> _capturar(
  WidgetTester tester,
  ProviderContainer contenedor,
  Widget pantalla,
  String nombre, {
  double alto = 915,
  double ancho = 412,
  double escalaDeTexto = 1,
  List<ProviderListenable<AsyncValue<Object?>>> precarga = const [],
  void Function()? antes,
}) async {
  await tester.runAsync(() async {
    // Se escuchan para que no se descarten mientras se espera.
    final sesion = contenedor.listen(authSessionProvider, (_, _) {});
    addTearDown(sesion.close);
    for (var i = 0; i < 150 && contenedor.read(authSessionProvider).isBusy; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 20));
    }
    for (final provider in precarga) {
      final escucha = contenedor.listen(provider, (_, _) {});
      addTearDown(escucha.close);
      for (var i = 0; i < 150 && contenedor.read(provider).isLoading; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 20));
      }
    }
  });
  await prepararFotos(tester, contenedor, productosReales());
  antes?.call();
  await montar(
    tester,
    contenedor,
    pantalla,
    alto: alto,
    ancho: ancho,
    escalaDeTexto: escalaDeTexto,
  );
  await esperarImagenes(tester, vueltas: 40);
  await tester.pump(const Duration(seconds: 2));
  await expectLater(
    find.byType(MaterialApp),
    matchesGoldenFile('salida/pantalla-$nombre.png'),
  );
}

void main() {
  setUpAll(() async {
    if (hayBanco) await cargarFuentes();
  });

  const limite = Timeout(Duration(minutes: 3));
  const novedades = OrdenDeCatalogo.novedades;

  testWidgets('pantalla: vidriera', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const HomePage(environmentLabel: null),
      'vidriera',
      alto: 2700,
      precarga: [vidrieraProvider, unreadNotificationsProvider],
    );
  }, timeout: limite);

  testWidgets('pantalla: catálogo', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const CatalogPage(),
      'catalogo',
      alto: 3400,
      precarga: [
        brandsProvider,
        catalogoOrdenadoProvider((search: '', brandId: null, orden: novedades)),
      ],
    );
  }, timeout: limite);

  testWidgets('pantalla: colección', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const CatalogPage(marcaInicial: 9),
      'coleccion',
      alto: 1700,
      precarga: [
        brandsProvider,
        catalogoOrdenadoProvider((search: '', brandId: 9, orden: novedades)),
      ],
    );
  }, timeout: limite);

  for (final (id, nombre, escala) in const [
    (33, 'ups', 1.0),
    (2, 'rtx', 1.0),
    (36, 'silla', 1.0),
    (33, 'ups-letra-grande', 1.3),
  ]) {
    testWidgets('pantalla: ficha $nombre', (tester) async {
      if (!hayBanco) return;
      final contenedor = _contenedor();
      addTearDown(contenedor.dispose);
      final producto = productosReales().firstWhere((p) => p.id == id);
      await _capturar(
        tester,
        contenedor,
        ProductDetailPage(productId: id),
        'ficha-$nombre',
        alto: 1900,
        ancho: escala == 1 ? 412 : 393,
        escalaDeTexto: escala,
        precarga: [
          productProvider(id),
          relacionadosProvider((
            id: id,
            marcaId: producto.brandId,
            marca: producto.brand,
          )),
        ],
      );
    }, timeout: limite);
  }

  for (final (nombre, escala) in const [
    ('carrito', 1.0),
    ('carrito-letra-grande', 1.3),
  ]) {
    testWidgets('pantalla: $nombre', (tester) async {
      if (!hayBanco) return;
      final contenedor = _contenedor();
      addTearDown(contenedor.dispose);
      final productos = productosReales();
      // El carrito se arma en tiempo real, así su restauración termina antes
      // de agregarle las líneas.
      await tester.runAsync(() async {
        final escucha = contenedor.listen(cartProvider, (_, _) {});
        addTearDown(escucha.close);
        await Future<void>.delayed(const Duration(milliseconds: 200));
      });
      await _capturar(
        tester,
        contenedor,
        const CartPage(),
        nombre,
        ancho: escala == 1 ? 412 : 393,
        escalaDeTexto: escala,
        antes: () {
          final carrito = contenedor.read(cartProvider.notifier);
          for (final (id, cantidad) in const [(10, 2), (8, 1), (32, 3)]) {
            carrito.add(
              productos.firstWhere((p) => p.id == id),
              quantity: cantidad,
            );
          }
        },
      );
    }, timeout: limite);
  }

  testWidgets('pantalla: ingreso', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor(conSesion: false);
    addTearDown(contenedor.dispose);
    await _capturar(tester, contenedor, const LoginPage(), 'ingreso');
  }, timeout: limite);

  // Cada una también con la letra del sistema al 130 %, en un Redmi Note.
  for (final (nombre, pantalla, conSesion) in <(String, Widget, bool)>[
    ('registro', const RegisterPage(), false),
    ('recuperacion', const PasswordRecoveryPage(), false),
    ('cuenta', const AccountPage(), true),
    ('pedidos', const OrdersPage(), true),
    ('notificaciones', const NotificationsPage(), true),
  ]) {
    for (final escala in const [1.0, 1.3]) {
      final captura = escala == 1 ? nombre : '$nombre-letra-grande';
      testWidgets('pantalla: $captura', (tester) async {
        if (!hayBanco) return;
        final contenedor = _contenedor(conSesion: conSesion);
        addTearDown(contenedor.dispose);
        await _capturar(
          tester,
          contenedor,
          pantalla,
          captura,
          ancho: escala == 1 ? 412 : 393,
          escalaDeTexto: escala,
        );
      }, timeout: limite);
    }
  }

  for (final (nombre, escala) in const [
    ('inicio-caja', 1.0),
    ('inicio-caja-letra-grande', 1.3),
  ]) {
    testWidgets('pantalla: $nombre', (tester) async {
      if (!hayBanco) return;
      final contenedor = _contenedor(rol: 'Cajero');
      addTearDown(contenedor.dispose);
      await _capturar(
        tester,
        contenedor,
        const HomePage(environmentLabel: null),
        nombre,
        alto: 1400,
        ancho: escala == 1 ? 412 : 393,
        escalaDeTexto: escala,
      );
    }, timeout: limite);
  }

  // El Moto G42 del local tiene la pantalla agrandada: 346 puntos de ancho.
  testWidgets('pantalla: vidriera angosta', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const HomePage(environmentLabel: null),
      'vidriera-346',
      alto: 2600,
      ancho: 346,
      precarga: [vidrieraProvider, unreadNotificationsProvider],
    );
  }, timeout: limite);

  testWidgets('pantalla: catálogo angosto', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const CatalogPage(),
      'catalogo-346',
      alto: 2400,
      ancho: 346,
      precarga: [
        brandsProvider,
        catalogoOrdenadoProvider((search: '', brandId: null, orden: novedades)),
      ],
    );
  }, timeout: limite);

  // Un Redmi Note (393 dp) con la letra del sistema al 130 %.
  testWidgets('pantalla: vidriera con letra grande', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const HomePage(environmentLabel: null),
      'vidriera-letra-grande',
      alto: 2900,
      ancho: 393,
      escalaDeTexto: 1.3,
      precarga: [vidrieraProvider, unreadNotificationsProvider],
    );
  }, timeout: limite);

  testWidgets('pantalla: catálogo con letra grande', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor();
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const CatalogPage(),
      'catalogo-letra-grande',
      alto: 2400,
      ancho: 393,
      escalaDeTexto: 1.3,
      precarga: [
        brandsProvider,
        catalogoOrdenadoProvider((search: '', brandId: null, orden: novedades)),
      ],
    );
  }, timeout: limite);

  testWidgets('pantalla: ingreso con letra grande', (tester) async {
    if (!hayBanco) return;
    final contenedor = _contenedor(conSesion: false);
    addTearDown(contenedor.dispose);
    await _capturar(
      tester,
      contenedor,
      const LoginPage(),
      'ingreso-letra-grande',
      ancho: 393,
      escalaDeTexto: 1.3,
    );
  }, timeout: limite);
}
