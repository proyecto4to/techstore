import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/application/session_controller.dart';
import '../features/auth/domain/auth_user.dart';
import '../features/courier/presentation/courier_dashboard_page.dart';
import '../features/notifications/presentation/notifications_page.dart';

/// Un módulo del inicio: a dónde va y con qué nombre.
final class _Module {
  const _Module(
    this.icon,
    this.label,
    this.route, {
    this.cuentaPendientes = false,
  });

  final IconData icon;
  final String label;
  final String route;

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

class HomePage extends ConsumerWidget {
  const HomePage({required this.environmentLabel, super.key});

  /// Nulo en producción: ahí no hay nada que aclararle al usuario.
  final String? environmentLabel;

  /// Qué ve cada rol, y en qué orden.
  ///
  /// Antes todos veían la misma grilla de cliente y quien trabaja en el local
  /// encontraba «Favoritos» y «Direcciones» antes que su propia herramienta,
  /// que quedaba décima. Ahora el trabajo va primero y lo personal después.
  /// No se le quita nada a nadie: un cajero también puede comprar, así que sus
  /// módulos de cliente siguen ahí, más abajo.
  List<_Section> _sectionsFor(AppRole role) {
    // Para quien compra, ese canal se llama «Atención al Cliente» en todas las
    // pantallas; decirle «Chat» en la baldosa era el único lugar donde no
    // coincidía. Para el resto sí son conversaciones: hablan entre ellos.
    final account = [
      const _Module(
        Icons.notifications_outlined,
        'Notificaciones',
        '/notifications',
        cuentaPendientes: true,
      ),
      _Module(
        role == AppRole.customer
            ? Icons.support_agent_outlined
            : Icons.chat_bubble_outline,
        role == AppRole.customer ? 'Atención al Cliente' : 'Conversaciones',
        '/chat',
      ),
      const _Module(Icons.person_outline, 'Mi cuenta', '/account'),
    ];
    const shopping = [
      _Module(Icons.storefront_outlined, 'Catálogo', '/catalog'),
      _Module(Icons.shopping_cart_outlined, 'Carrito', '/cart'),
      _Module(Icons.receipt_long_outlined, 'Mis pedidos', '/orders'),
      _Module(Icons.description_outlined, 'Mis facturas', '/invoices'),
      _Module(Icons.location_on_outlined, 'Direcciones', '/addresses'),
      _Module(Icons.favorite_outline, 'Favoritos', '/favorites'),
    ];

    if (role.isCashier) {
      return [
        _Section('En el mostrador', [
          _Module(Icons.point_of_sale_outlined, 'Vender', '/sales/new'),
          _Module(Icons.savings_outlined, 'Operar caja', '/cash/actions'),
          _Module(Icons.receipt_long_outlined, 'Pedidos', '/orders'),
          _Module(Icons.description_outlined, 'Facturas', '/invoices'),
          _Module(Icons.storefront_outlined, 'Buscar producto', '/catalog'),
          _Module(Icons.dashboard_outlined, 'Más operaciones', '/operations'),
        ]),
        _Section('Tu cuenta', account),
        _Section('Comprar para vos', [
          _Module(Icons.shopping_cart_outlined, 'Carrito', '/cart'),
          _Module(Icons.location_on_outlined, 'Direcciones', '/addresses'),
          _Module(Icons.favorite_outline, 'Favoritos', '/favorites'),
        ]),
      ];
    }

    if (role.canAdminister) {
      return [
        _Section('Gestión', [
          _Module(
            Icons.admin_panel_settings_outlined,
            'Administración',
            '/admin',
          ),
          _Module(Icons.dashboard_outlined, 'Operaciones', '/operations'),
          _Module(Icons.point_of_sale_outlined, 'Vender', '/sales/new'),
          _Module(Icons.savings_outlined, 'Operar caja', '/cash/actions'),
          _Module(Icons.folder_open_outlined, 'Documentos', '/documents'),
          _Module(Icons.receipt_long_outlined, 'Pedidos', '/orders'),
        ]),
        _Section('Tu cuenta', account),
        _Section('Tienda', [
          _Module(Icons.storefront_outlined, 'Catálogo', '/catalog'),
          _Module(Icons.description_outlined, 'Facturas', '/invoices'),
          _Module(Icons.shopping_cart_outlined, 'Carrito', '/cart'),
        ]),
      ];
    }

    // Cliente: su tienda primero, su cuenta después.
    return [_Section('Comprar', shopping), _Section('Tu cuenta', account)];
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authSessionProvider).user!;
    if (user.role.isCourier) return const CourierDashboardPage();

    final theme = Theme.of(context);
    final sections = _sectionsFor(user.role);

    return Scaffold(
      appBar: AppBar(
        title: const Text('TechStore'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: ref.read(authSessionProvider.notifier).logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Hola, ${user.firstName}',
              style: theme.textTheme.headlineSmall,
            ),
            Text(
              environmentLabel == null
                  ? user.role.label
                  : '${user.role.label} · $environmentLabel',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            for (final section in sections) ...[
              const SizedBox(height: 22),
              Text(
                section.title,
                style: theme.textTheme.titleSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  letterSpacing: .4,
                ),
              ),
              const SizedBox(height: 10),
              GridView.builder(
                gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 240,
                  mainAxisExtent: 132,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                ),
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: section.modules.length,
                itemBuilder: (context, index) {
                  final module = section.modules[index];
                  void abrir() => context.push(module.route);
                  if (!module.cuentaPendientes) {
                    return _ModuleCard(
                      icon: module.icon,
                      label: module.label,
                      onTap: abrir,
                    );
                  }
                  // Un Consumer propio para que el número no reconstruya la
                  // grilla entera cada vez que llega un aviso.
                  return Consumer(
                    builder: (context, ref, _) => _ModuleCard(
                      icon: module.icon,
                      label: module.label,
                      onTap: abrir,
                      pendientes:
                          ref.watch(unreadNotificationsProvider).value ?? 0,
                    ),
                  );
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ModuleCard extends StatelessWidget {
  const _ModuleCard({
    required this.icon,
    required this.label,
    required this.onTap,
    this.pendientes = 0,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  /// Cuántos avisos sin leer anunciar sobre el icono. Cero no muestra nada.
  final int pendientes;

  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: Semantics(
      button: true,
      // El número va también en la etiqueta hablada: el globo del icono queda
      // fuera de la semántica y, sin esto, quien usa lector de pantalla no se
      // entera de que hay algo pendiente.
      label: pendientes == 0 ? label : '$label, $pendientes sin leer',
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ExcludeSemantics(
              child: Badge(
                isLabelVisible: pendientes > 0,
                label: Text(pendientes > 99 ? '99+' : '$pendientes'),
                child: Icon(icon, size: 40),
              ),
            ),
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              // Fuera de la semántica porque el nombre ya está en la etiqueta
              // de la baldosa: sin esto el lector de pantalla lo decía dos
              // veces, «Notificaciones, Notificaciones».
              child: ExcludeSemantics(
                child: Text(label, textAlign: TextAlign.center, maxLines: 2),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
