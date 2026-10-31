import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'notifications_page.dart';

/// La campana de avisos, arriba a la izquierda de cada inicio.
///
/// Va en el lugar de la flecha de volver, que en una pantalla de inicio no
/// existe: así se encuentra siempre en el mismo sitio, con el pulgar o con la
/// vista, y el área segura del sistema la respeta la barra. El número se mueve
/// solo: el coordinador de tiempo real invalida el contador cuando llega un
/// aviso.
class CampanaDeAvisos extends ConsumerWidget {
  const CampanaDeAvisos({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pendientes = ref.watch(unreadNotificationsProvider).value ?? 0;
    final etiqueta = pendientes == 0
        ? 'Notificaciones'
        : 'Notificaciones, $pendientes sin leer';
    return Semantics(
      button: true,
      label: etiqueta,
      excludeSemantics: true,
      child: IconButton(
        tooltip: etiqueta,
        onPressed: () => context.push('/notifications'),
        icon: Badge(
          isLabelVisible: pendientes > 0,
          label: Text(pendientes > 99 ? '99+' : '$pendientes'),
          child: Icon(
            pendientes > 0
                ? Icons.notifications_active_rounded
                : Icons.notifications_none_rounded,
          ),
        ),
      ),
    );
  }
}
