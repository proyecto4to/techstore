import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/deep_links.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';

final notificationsProvider = FutureProvider.autoDispose((ref) async {
  final response = await ref
      .watch(apiClientProvider)
      .get<Object?>(
        '/api/v1/notificaciones',
        queryParameters: {'page': 0, 'size': 50},
      );
  final value = response.data;
  final raw = value is List
      ? value
      : value is Map && value['content'] is List
      ? value['content']! as List
      : const [];
  return raw
      .map((item) => Map<String, Object?>.from(item as Map))
      .toList(growable: false);
});

/// Cuántas quedan sin leer, para el contador de la campana.
///
/// Es una consulta aparte y liviana: la lista trae cincuenta notificaciones con
/// su contenido, y para pintar un número al lado de un icono alcanza con esto.
/// El coordinador de tiempo real la invalida cuando llega un aviso por la cola
/// personal, así que el número se mueve solo, también cuando la persona lee
/// desde otro dispositivo.
final unreadNotificationsProvider = FutureProvider.autoDispose<int>((
  ref,
) async {
  final response = await ref
      .watch(apiClientProvider)
      .get<Map<String, Object?>>('/api/v1/notificaciones/no-leidas');
  return (response.data?['noLeidas'] as num?)?.toInt() ?? 0;
});

class NotificationsPage extends ConsumerWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(notificationsProvider);
    final pendientes = ref.watch(unreadNotificationsProvider).value ?? 0;
    return Scaffold(
      appBar: AppBar(
        title: const TituloDePantalla('Notificaciones'),
        actions: [
          TextButton(
            // Apagado cuando no hay nada pendiente: un botón que se puede
            // apretar y no cambia nada deja pensando que algo falló.
            onPressed: pendientes == 0 ? null : () => _leerTodas(context, ref),
            child: const Text('Leer todas'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(notificationsProvider.future),
        child: notifications.when(
          loading: () => const AppLoading(label: 'Cargando tus avisos'),
          error: (error, _) => AppErrorState(
            message: error is AppFailure
                ? error.message
                : 'No pudimos cargar tus notificaciones.',
            onRetry: () => ref.invalidate(notificationsProvider),
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    SizedBox(height: 120),
                    AppEmptyState(
                      icon: Icons.notifications_none,
                      message:
                          'Todavía no hay avisos.\n'
                          'Acá te vamos a contar cómo avanza tu pedido.',
                    ),
                  ],
                )
              : ListView.separated(
                  itemCount: items.length,
                  separatorBuilder: (context, index) =>
                      const Divider(height: 1),
                  itemBuilder: (context, index) =>
                      _Aviso(aviso: items[index], ref: ref),
                ),
        ),
      ),
    );
  }

  Future<void> _leerTodas(BuildContext context, WidgetRef ref) async {
    final mensajero = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(apiClientProvider)
          .put<void>('/api/v1/notificaciones/leidas');
    } on AppFailure catch (falla) {
      mensajero
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(falla.message)));
      return;
    }
    // El contador también: si no, la campana del inicio sigue mostrando un
    // número que ya no existe hasta que llegue otro aviso.
    ref
      ..invalidate(notificationsProvider)
      ..invalidate(unreadNotificationsProvider);
  }
}

class _Aviso extends StatelessWidget {
  const _Aviso({required this.aviso, required this.ref});

  final Map<String, Object?> aviso;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final leida = aviso['leida'] == true;
    final titulo = aviso['titulo']?.toString() ?? 'Notificación';
    return ListTile(
      leading: Icon(
        leida ? Icons.notifications_none : Icons.notifications_active,
        color: leida ? theme.colorScheme.onSurfaceVariant : null,
      ),
      title: Text(
        titulo,
        style: leida ? null : const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(aviso['mensaje']?.toString() ?? ''),
      // Sin leer se marca de dos maneras a la vez —el fondo y la negrita—
      // porque con el color solo no se distingue con la pantalla al sol.
      tileColor: leida
          ? null
          : theme.colorScheme.primaryContainer.withValues(alpha: .35),
      trailing: leida ? null : const Icon(Icons.circle, size: 10),
      // Cada aviso trae el enlace de la pantalla que lo originó; antes se
      // descartaba y la fila quedaba muerta una vez leída.
      onTap: () => _abrir(context, leida: leida),
    );
  }

  Future<void> _abrir(BuildContext context, {required bool leida}) async {
    if (!leida) {
      try {
        await ref
            .read(apiClientProvider)
            .put<void>('/api/v1/notificaciones/${aviso['id']}/leida');
        ref
          ..invalidate(notificationsProvider)
          ..invalidate(unreadNotificationsProvider);
      } on AppFailure catch (_) {
        // Que no se haya podido marcar no tiene por qué impedir abrir lo que
        // el aviso anuncia; el estado se vuelve a intentar al refrescar.
      }
    }
    final enlace = aviso['deepLink']?.toString();
    final destino = enlace == null || enlace.isEmpty
        ? null
        : rutaDeEnlaceTechstore(Uri.parse(enlace));
    // El aviso genérico apunta al propio centro: no tiene sentido apilarlo
    // sobre sí mismo.
    if (destino != null && destino != '/notifications' && context.mounted) {
      await context.push<void>(destino);
    }
  }
}
