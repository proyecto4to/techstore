import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/animacion/aparicion.dart';
import '../core/diseno/marca_tema.dart';
import '../core/diseno/medidas.dart';
import '../core/diseno/paleta.dart';
import '../core/widgets/baldosa_de_acceso.dart';
import '../core/widgets/marca/logotipo.dart';
import '../core/widgets/marca/rotulo.dart';
import '../features/auth/application/session_controller.dart';
import '../features/auth/domain/auth_user.dart';
import '../features/catalog/presentation/vidriera_page.dart';
import '../features/courier/presentation/courier_dashboard_page.dart';
import '../features/notifications/presentation/notifications_page.dart';
import '../features/security/application/security_providers.dart';
import '../features/security/domain/app_permissions.dart';

/// Un módulo del inicio: a dónde va y con qué nombre.
final class _Module {
  const _Module(
    this.icon,
    this.label,
    this.route, {
    this.cuentaPendientes = false,
    this.modulo,
  });

  final IconData icon;
  final String label;
  final String route;

  /// Qué permiso hace falta para que esta baldosa se muestre.
  ///
  /// Solo lo llevan las de trabajo. Lo de comprar —catálogo, carrito, mi
  /// cuenta— no se esconde nunca: es el uso normal de la tienda, y un permiso
  /// mal puesto no puede dejar a un cliente sin poder comprar.
  final String? modulo;

  /// Si la baldosa muestra cuántos avisos quedan sin leer.
  ///
  /// Solo la campana. El número se pide aparte —es una consulta liviana— y el
  /// coordinador de tiempo real lo invalida cuando llega una notificación, así
  /// que se mueve solo, sin entrar a la pantalla.
  final bool cuentaPendientes;
}

/// Un bloque de módulos con su título.
final class _Section {
  const _Section(this.title, this.modules);

  final String title;
  final List<_Module> modules;
}

/// El inicio, según quién entra: quien compra ve la vidriera; el repartidor,
/// su jornada; caja y administración, sus herramientas primero.
class HomePage extends ConsumerWidget {
  const HomePage({required this.environmentLabel, super.key});

  /// Nulo en producción: ahí no hay nada que aclararle al usuario.
  final String? environmentLabel;

  /// Qué ve cada rol que trabaja en el local, y en qué orden.
  ///
  /// Antes todos veían la misma grilla de cliente y quien trabaja en el local
  /// encontraba «Favoritos» y «Direcciones» antes que su propia herramienta,
  /// que quedaba décima. Ahora el trabajo va primero y lo personal después.
  /// No se le quita nada a nadie: un cajero también puede comprar, así que sus
  /// módulos de cliente siguen ahí, más abajo.
  List<_Section> _sectionsFor(AppRole role) {
    const account = [
      _Module(
        Icons.notifications_outlined,
        'Notificaciones',
        '/notifications',
        cuentaPendientes: true,
      ),
      _Module(Icons.chat_bubble_outline_rounded, 'Conversaciones', '/chat'),
      _Module(Icons.person_outline_rounded, 'Mi cuenta', '/account'),
    ];

    if (role.isCashier) {
      return [
        _Section('En el mostrador', [
          _Module(
            Icons.point_of_sale_outlined,
            'Vender',
            '/sales/new',
            modulo: Modulos.facturas,
          ),
          _Module(
            Icons.savings_outlined,
            'Operar caja',
            '/cash/actions',
            modulo: Modulos.caja,
          ),
          _Module(
            Icons.receipt_long_outlined,
            'Pedidos',
            '/orders',
            modulo: Modulos.pedidos,
          ),
          _Module(
            Icons.description_outlined,
            'Facturas',
            '/invoices',
            modulo: Modulos.facturas,
          ),
          const _Module(
            Icons.storefront_outlined,
            'Buscar producto',
            '/catalog',
          ),
          const _Module(
            Icons.dashboard_outlined,
            'Más operaciones',
            '/operations',
          ),
        ]),
        const _Section('Tu cuenta', account),
        const _Section('Comprar para vos', [
          _Module(Icons.shopping_bag_outlined, 'Carrito', '/cart'),
          _Module(Icons.location_on_outlined, 'Direcciones', '/addresses'),
          _Module(Icons.favorite_border_rounded, 'Favoritos', '/favorites'),
        ]),
      ];
    }

    return [
      _Section('Gestión', [
        const _Module(
          Icons.admin_panel_settings_outlined,
          'Administración',
          '/admin',
        ),
        const _Module(Icons.dashboard_outlined, 'Operaciones', '/operations'),
        _Module(
          Icons.point_of_sale_outlined,
          'Vender',
          '/sales/new',
          modulo: Modulos.facturas,
        ),
        _Module(
          Icons.savings_outlined,
          'Operar caja',
          '/cash/actions',
          modulo: Modulos.caja,
        ),
        const _Module(Icons.folder_open_outlined, 'Documentos', '/documents'),
        _Module(
          Icons.receipt_long_outlined,
          'Pedidos',
          '/orders',
          modulo: Modulos.pedidos,
        ),
      ]),
      const _Section('Tu cuenta', account),
      _Section('Tienda', [
        const _Module(Icons.storefront_outlined, 'Catálogo', '/catalog'),
        _Module(
          Icons.description_outlined,
          'Facturas',
          '/invoices',
          modulo: Modulos.facturas,
        ),
        const _Module(Icons.shopping_bag_outlined, 'Carrito', '/cart'),
      ]),
    ];
  }

  /// Saca las baldosas de trabajo que esta persona no puede usar, y las
  /// secciones que quedaron vacías con ellas.
  List<_Section> _visibles(List<_Section> secciones, AppPermissions? permisos) {
    if (permisos == null) return secciones;
    return [
      for (final seccion in secciones)
        if (seccion.modules
            .where((m) => m.modulo == null || permisos.puede(m.modulo!))
            .toList()
            case final visibles when visibles.isNotEmpty)
          _Section(seccion.title, visibles),
    ];
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authSessionProvider).user!;
    if (user.role.isCourier) return const CourierDashboardPage();
    if (!user.role.isCashier && !user.role.canAdminister) {
      return VidrieraPage(etiquetaDeEntorno: environmentLabel);
    }

    final theme = Theme.of(context);
    // Mientras la respuesta no llegó se muestra todo: esta pantalla es el
    // inicio, y dejarla en blanco medio segundo se ve como si la app no
    // hubiera cargado. Lo que restringe de verdad está en el servidor.
    final permisos = ref.watch(permisosProvider).value;
    final sections = _visibles(_sectionsFor(user.role), permisos);
    var orden = 0;

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: const Imagotipo(tamano: 19),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: ref.read(authSessionProvider.notifier).logout,
            icon: const Icon(Icons.logout_rounded),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          Medidas.margen,
          8,
          Medidas.margen,
          32,
        ),
        children: [
          Rotulo(
            environmentLabel == null
                ? user.role.label
                : '${user.role.label} · $environmentLabel',
          ),
          const SizedBox(height: 6),
          Aparicion(
            child: Text(
              'Hola, ${user.firstName}',
              style: theme.textTheme.headlineMedium,
            ),
          ),
          for (final section in sections) ...[
            const SizedBox(height: 28),
            Text(
              section.title,
              style: MarcaTema.of(context).rotulo.copyWith(
                color: Paleta.textoSecundario,
                fontSize: 12.5,
              ),
            ),
            const SizedBox(height: 10),
            GridView.builder(
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 220,
                mainAxisExtent: 116,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
              ),
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: section.modules.length,
              itemBuilder: (context, index) {
                final module = section.modules[index];
                void abrir() => context.push(module.route);
                final baldosa = !module.cuentaPendientes
                    ? BaldosaDeAcceso(
                        icono: module.icon,
                        etiqueta: module.label,
                        onTap: abrir,
                      )
                    // Un Consumer propio para que el número no reconstruya
                    // la grilla entera cada vez que llega un aviso.
                    : Consumer(
                        builder: (context, ref, _) => BaldosaDeAcceso(
                          icono: module.icon,
                          etiqueta: module.label,
                          onTap: abrir,
                          pendientes:
                              ref.watch(unreadNotificationsProvider).value ??
                              0,
                        ),
                      );
                return Aparicion(orden: orden++, child: baldosa);
              },
            ),
          ],
        ],
      ),
    );
  }
}
