import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';

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

class NotificationsPage extends ConsumerWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(notificationsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notificaciones'),
        actions: [
          TextButton(
            onPressed: () async {
              await ref
                  .read(apiClientProvider)
                  .put<void>('/api/v1/notificaciones/leidas');
              ref.invalidate(notificationsProvider);
            },
            child: const Text('Leer todas'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(notificationsProvider.future),
        child: notifications.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: const [
              SizedBox(height: 160),
              Center(child: Text('No se pudieron cargar las notificaciones')),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    SizedBox(height: 160),
                    Center(child: Text('Sin notificaciones')),
                  ],
                )
              : ListView.builder(
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    final read = item['leida'] == true;
                    return ListTile(
                      leading: Icon(
                        read
                            ? Icons.notifications_none
                            : Icons.notifications_active,
                      ),
                      title: Text(item['titulo']?.toString() ?? 'Notificación'),
                      subtitle: Text(item['mensaje']?.toString() ?? ''),
                      tileColor: read
                          ? null
                          : Theme.of(context).colorScheme.primaryContainer
                                .withValues(alpha: .35),
                      onTap: read
                          ? null
                          : () async {
                              await ref
                                  .read(apiClientProvider)
                                  .put<void>(
                                    '/api/v1/notificaciones/${item['id']}/leida',
                                  );
                              ref.invalidate(notificationsProvider);
                            },
                    );
                  },
                ),
        ),
      ),
    );
  }
}
