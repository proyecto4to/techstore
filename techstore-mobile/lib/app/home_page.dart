import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/application/session_controller.dart';
import '../features/courier/presentation/courier_dashboard_page.dart';

class HomePage extends ConsumerWidget {
  const HomePage({required this.environmentLabel, super.key});

  final String environmentLabel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authSessionProvider).user!;
    if (user.role.isCourier) return const CourierDashboardPage();
    final modules = <_ModuleCard>[
      _ModuleCard(
        icon: Icons.storefront_outlined,
        label: 'Catálogo',
        onTap: () => context.push('/catalog'),
      ),
      _ModuleCard(
        icon: Icons.shopping_cart_outlined,
        label: 'Carrito',
        onTap: () => context.push('/cart'),
      ),
      _ModuleCard(
        icon: Icons.receipt_long_outlined,
        label: 'Pedidos',
        onTap: () => context.push('/orders'),
      ),
      _ModuleCard(
        icon: Icons.description_outlined,
        label: 'Facturas',
        onTap: () => context.push('/invoices'),
      ),
      _ModuleCard(
        icon: Icons.location_on_outlined,
        label: 'Direcciones',
        onTap: () => context.push('/addresses'),
      ),
      _ModuleCard(
        icon: Icons.favorite_outline,
        label: 'Favoritos',
        onTap: () => context.push('/favorites'),
      ),
      _ModuleCard(
        icon: Icons.notifications_outlined,
        label: 'Notificaciones',
        onTap: () => context.push('/notifications'),
      ),
      _ModuleCard(
        icon: Icons.chat_bubble_outline,
        label: 'Chat',
        onTap: () => context.push('/chat'),
      ),
      _ModuleCard(
        icon: Icons.person_outline,
        label: 'Mi cuenta',
        onTap: () => context.push('/account'),
      ),
      if (user.role.canOperateStore)
        _ModuleCard(
          icon: Icons.point_of_sale_outlined,
          label: 'Operaciones',
          onTap: () => context.push('/operations'),
        ),
      if (user.role.canAdminister)
        _ModuleCard(
          icon: Icons.admin_panel_settings_outlined,
          label: 'Administración',
          onTap: () => context.push('/admin'),
        ),
    ];
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
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            Text('${user.role.name} · $environmentLabel'),
            const SizedBox(height: 20),
            GridView.builder(
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 240,
                mainAxisExtent: 144,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
              ),
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: modules.length,
              itemBuilder: (context, index) => modules[index],
            ),
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
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: Semantics(
      button: true,
      label: label,
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ExcludeSemantics(child: Icon(icon, size: 42)),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Text(label, textAlign: TextAlign.center, maxLines: 2),
            ),
          ],
        ),
      ),
    ),
  );
}
