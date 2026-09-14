import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/administration/presentation/business_hub_page.dart';
import 'package:techstore_mobile/features/security/application/security_providers.dart';
import 'package:techstore_mobile/features/security/domain/app_permissions.dart';

/// Los permisos en la app: qué se lee del servidor y qué se muestra con eso.
///
/// Lo que importa comprobar acá es que la app no inventa permisos. Todo sale de
/// la respuesta del backend, y lo que no vino no se habilita. Esconder una
/// baldosa no es la protección —el servidor rechaza igual la operación— pero sí
/// es lo que evita ofrecer algo que va a terminar en un error.
void main() {
  group('permisos que responde el servidor', () {
    final permisos = AppPermissions.fromJson(const {
      'rol': 'Cajero',
      'permitidos': ['productos.ver', 'facturas.crear', 'caja.ver'],
      'pantallas': ['catalogo', 'caja'],
    });

    test('responde por módulo y acción, sin distinguir mayúsculas', () {
      expect(permisos.puede(Modulos.productos), isTrue);
      expect(permisos.puede(Modulos.facturas, Acciones.crear), isTrue);
      expect(permisos.puede(Modulos.facturas, Acciones.eliminar), isFalse);
      expect(permisos.puede(Modulos.seguridad), isFalse);
    });

    test('la acción por defecto es ver', () {
      expect(permisos.puede(Modulos.caja), isTrue);
      expect(permisos.puede(Modulos.caja, Acciones.editar), isFalse);
    });

    test('sabe qué pantallas le habilitaron', () {
      expect(permisos.vePantalla('caja'), isTrue);
      expect(permisos.vePantalla('seguridad'), isFalse);
    });

    test('sin respuesta todavía, no habilita nada', () {
      expect(AppPermissions.vacio.puede(Modulos.productos), isFalse);
      expect(AppPermissions.vacio.administraSeguridad, isFalse);
    });

    test('una respuesta incompleta no rompe la app', () {
      final parcial = AppPermissions.fromJson(const {});
      expect(parcial.rol, isEmpty);
      expect(parcial.puede(Modulos.pedidos), isFalse);
    });
  });

  group('el menú de trabajo', () {
    Future<void> montar(WidgetTester tester, AppPermissions permisos) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            permisosProvider.overrideWith((ref) async => permisos),
          ],
          child: const MaterialApp(home: BusinessHubPage(admin: true)),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('muestra Seguridad a quien la administra', (tester) async {
      await montar(
        tester,
        AppPermissions.fromJson(const {
          'rol': 'Admin',
          'permitidos': ['seguridad.ver', 'usuarios.ver'],
          'pantallas': <String>[],
        }),
      );

      expect(find.text('Seguridad'), findsOneWidget);
      expect(find.text('Usuarios'), findsOneWidget);
      // Empresa no vino en la respuesta: no se ofrece.
      expect(find.text('Empresa'), findsNothing);
    });

    testWidgets('a quien no la tiene, no se la ofrece', (tester) async {
      await montar(
        tester,
        AppPermissions.fromJson(const {
          'rol': 'Admin',
          'permitidos': ['usuarios.ver'],
          'pantallas': <String>[],
        }),
      );

      expect(find.text('Seguridad'), findsNothing);
      expect(find.text('Usuarios'), findsOneWidget);
    });
  });
}
