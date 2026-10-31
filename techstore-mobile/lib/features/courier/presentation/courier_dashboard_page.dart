import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../auth/application/session_controller.dart';
import '../../notifications/presentation/campana_de_avisos.dart';
import '../application/courier_providers.dart';

class CourierDashboardPage extends ConsumerWidget {
  const CourierDashboardPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authSessionProvider).user!;
    final dashboard = ref.watch(courierDashboardProvider);
    final vehicle = ref.watch(courierCurrentVehicleProvider);
    return Scaffold(
      appBar: AppBar(
        // La campana arriba a la izquierda, como en el resto de los inicios, y
        // ahora con el número de avisos sin leer.
        leading: const CampanaDeAvisos(),
        title: const Text('Mi jornada'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: ref.read(authSessionProvider.notifier).logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(courierDashboardProvider);
          ref.invalidate(courierCurrentVehicleProvider);
          try {
            await ref.read(courierDashboardProvider.future);
          } on Object {
            // El fallo ya se ve en la tarjeta de estadísticas; tirar de la
            // lista sin conexión no tiene por qué romper el gesto.
          }
        },
        // Las estadísticas se dibujan aparte del menú: si la consulta falla, el
        // repartidor pierde los números, no la forma de llegar a sus entregas.
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Hola, ${user.firstName}',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const Text('Todo lo necesario para completar tu ruta de hoy.'),
            const SizedBox(height: 16),
            ...dashboard.when(
              loading: () => const [
                Center(
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 32),
                    child: CircularProgressIndicator(),
                  ),
                ),
              ],
              error: (error, _) => [
                _JornadaNoDisponible(
                  onPressed: () => ref.invalidate(courierDashboardProvider),
                ),
              ],
              data: (data) => [
                GridView.count(
                  crossAxisCount: 2,
                  childAspectRatio: 1.45,
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  children: [
                    _Metric(
                      label: 'Pendientes',
                      value: '${data['entregasPendientes'] ?? 0}',
                      icon: Icons.pending_actions_outlined,
                    ),
                    _Metric(
                      label: 'En curso',
                      value: '${data['entregasEnCurso'] ?? 0}',
                      icon: Icons.local_shipping_outlined,
                    ),
                    _Metric(
                      label: 'Realizadas',
                      value: '${data['entregasRealizadas'] ?? 0}',
                      icon: Icons.task_alt_outlined,
                    ),
                    _Metric(
                      label: 'Cobros',
                      value: '${data['cobrosRealizados'] ?? 0}',
                      icon: Icons.payments_outlined,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.account_balance_wallet_outlined),
                    title: const Text('Saldo en mi caja'),
                    subtitle: Text(
                      '${data['solicitudesPendientes'] ?? 0} solicitud(es) pendiente(s)',
                    ),
                    trailing: Text(
                      _money(data['saldoActual'], data['moneda']?.toString()),
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    onTap: () => context.push('/courier/cash'),
                  ),
                ),
              ],
            ),
            Card(
              child: vehicle.when(
                loading: () => const ListTile(
                  leading: CircularProgressIndicator(),
                  title: Text('Consultando vehículo'),
                ),
                error: (error, _) => ListTile(
                  leading: const Icon(Icons.no_crash_outlined),
                  title: const Text('Vehículos'),
                  subtitle: const Text('No se pudo consultar el uso actual'),
                  onTap: () => context.push('/courier/vehicles'),
                ),
                data: (use) => ListTile(
                  leading: const Icon(Icons.two_wheeler_outlined),
                  title: Text(
                    use?['vehiculo']?.toString() ?? 'Sin vehículo activo',
                  ),
                  subtitle: Text(
                    use == null
                        ? 'Seleccioná uno antes de salir'
                        : 'Desde ${_date(use['fechaInicio'])}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/courier/vehicles'),
                ),
              ),
            ),
            const SizedBox(height: 12),
            _Action(
              icon: Icons.list_alt_outlined,
              title: 'Mis entregas',
              subtitle: 'Por estado o agrupadas por cliente',
              onTap: () => context.push('/courier/deliveries'),
            ),
            _Action(
              icon: Icons.route_outlined,
              title: 'Ruta optimizada',
              subtitle: 'Orden, distancias y navegación',
              onTap: () => context.push('/courier/route'),
            ),
            _Action(
              icon: Icons.account_balance_wallet_outlined,
              title: 'Caja y transferencias',
              subtitle: 'Movimientos, solicitudes y motivos de rechazo',
              onTap: () => context.push('/courier/cash'),
            ),
            _Action(
              icon: Icons.directions_car_outlined,
              title: 'Vehículos',
              subtitle: 'Inicio, eventos y fin de uso',
              onTap: () => context.push('/courier/vehicles'),
            ),
            _Action(
              icon: Icons.forum_outlined,
              title: 'Mensajes',
              subtitle: 'Escribile a administración',
              onTap: () => context.push('/chat'),
            ),
            _Action(
              icon: Icons.person_outline,
              title: 'Mi cuenta',
              subtitle: 'Seguridad y contraseña',
              onTap: () => context.push('/account'),
            ),
          ],
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value, required this.icon});
  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon),
          const SizedBox(height: 5),
          Text(value, style: Theme.of(context).textTheme.titleLarge),
          Text(label, maxLines: 1),
        ],
      ),
    ),
  );
}

class _Action extends StatelessWidget {
  const _Action({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    ),
  );
}

/// Aviso de que fallaron los números del día, del tamaño de una tarjeta.
///
/// Antes ocupaba la pantalla entera y se llevaba puesto el menú: sin
/// estadísticas, el repartidor tampoco podía entrar a sus entregas ni a su
/// ruta. Lo que falla es una consulta, así que lo que se pierde es una tarjeta.
class _JornadaNoDisponible extends StatelessWidget {
  const _JornadaNoDisponible({required this.onPressed});
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.query_stats_outlined),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'No pudimos cargar los números de tu jornada',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          const Text('Tus entregas y tu ruta siguen disponibles acá abajo.'),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.tonal(
              onPressed: onPressed,
              child: const Text('Reintentar'),
            ),
          ),
        ],
      ),
    ),
  );
}

String _money(Object? raw, String? currency) {
  final value = raw is num ? raw.toDouble() : double.tryParse('$raw') ?? 0;
  return NumberFormat.currency(
    locale: 'es_PY',
    symbol: currency == 'PYG' ? '₲' : currency ?? '',
    decimalDigits: currency == 'PYG' ? 0 : 2,
  ).format(value);
}

String _date(Object? raw) {
  final date = DateTime.tryParse(raw?.toString() ?? '');
  return date == null
      ? 'ahora'
      : DateFormat.yMd('es_PY').add_Hm().format(date.toLocal());
}
