import 'package:flutter/material.dart';

import '../core/update/app_update_service.dart';

/// Lo que el usuario ve mientras se actualiza.
///
/// Deliberadamente no dice "descargar APK" ni "reinstalar": para quien mira,
/// esto es la aplicación actualizándose. El detalle técnico —el archivo, el
/// hash, el instalador de Android— queda abajo.
class UpdateProgressDialog extends StatelessWidget {
  const UpdateProgressDialog({
    required this.policy,
    required this.recibidos,
    required this.total,
    this.error,
    this.onReintentar,
    super.key,
  });

  final UpdatePolicy policy;
  final int recibidos;
  final int total;
  final String? error;
  final VoidCallback? onReintentar;

  static String tamano(int bytes) {
    if (bytes <= 0) return '';
    final mb = bytes / (1024 * 1024);
    return '${mb.toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final fallo = error;
    final progreso = total > 0 ? (recibidos / total).clamp(0.0, 1.0) : null;
    final porcentaje = progreso == null ? null : (progreso * 100).round();

    return AlertDialog(
      title: Text(fallo == null ? 'Actualizando aplicación…' : 'No se pudo actualizar'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (fallo != null) ...[
            Text(fallo),
            const SizedBox(height: 8),
            const Text(
              'Tu aplicación sigue funcionando con la versión actual.',
              style: TextStyle(fontSize: 12),
            ),
          ] else ...[
            Text(
              policy.latestVersionName == null
                  ? 'Versión ${policy.latestVersionCode}'
                  : 'Versión ${policy.latestVersionName}',
            ),
            const SizedBox(height: 12),
            LinearProgressIndicator(value: progreso),
            const SizedBox(height: 8),
            Text(
              porcentaje == null
                  ? 'Preparando…'
                  : '$porcentaje %${total > 0 ? ' de ${tamano(total)}' : ''}',
              style: const TextStyle(fontSize: 12),
            ),
          ],
        ],
      ),
      actions: [
        if (fallo != null) ...[
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cerrar'),
          ),
          if (onReintentar != null)
            FilledButton(onPressed: onReintentar, child: const Text('Reintentar')),
        ],
      ],
    );
  }
}

/// Explica por qué Android necesita autorizar a la app antes de instalar.
class InstallPermissionDialog extends StatelessWidget {
  const InstallPermissionDialog({super.key});

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Permiso necesario'),
    content: const Text(
      'Para instalar actualizaciones directamente desde la aplicación, Android '
      'necesita autorizar a TechStore como origen de instalación.',
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context, false),
        child: const Text('Ahora no'),
      ),
      FilledButton(
        onPressed: () => Navigator.pop(context, true),
        child: const Text('Conceder permiso'),
      ),
    ],
  );
}
