import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../di/providers.dart';
import '../errors/app_failure.dart';

/// Confirma la identidad antes de una operación sensible.
///
/// Devuelve `true` si se puede seguir adelante.
///
/// El orden importa. Si la guarda está apagada no molesta a nadie. Si está
/// encendida y el equipo tiene biometría, la usa. Y si está encendida pero el
/// equipo **no** tiene biometría —o el sensor falla— pide la contraseña de la
/// cuenta en lugar de dejar pasar la operación.
///
/// Ese último caso es el que importa: antes se devolvía `true` cuando no había
/// sensor, así que en un teléfono sin huella registrada la confirmación no
/// existía y bastaba un toque para cerrar la caja. Quien enciende la guarda
/// espera que lo frene, no que se apague sola.
Future<bool> confirmarOperacion(
  BuildContext context,
  WidgetRef ref, {
  required String reason,
  required String titulo,
}) async {
  final biometria = ref.read(biometricServiceProvider);
  if (!await biometria.isOperationGuardEnabled()) return true;
  if (!context.mounted) return false;
  return confirmarIdentidad(context, ref, reason: reason, titulo: titulo);
}

/// Comprueba que quien está del otro lado es el dueño de la sesión.
///
/// Biometría si el equipo la tiene, contraseña de la cuenta si no. A
/// diferencia de [confirmarOperacion], no mira si la guarda está encendida:
/// lo usa la pantalla de Mi cuenta justamente para encenderla.
Future<bool> confirmarIdentidad(
  BuildContext context,
  WidgetRef ref, {
  required String reason,
  required String titulo,
}) async {
  final biometria = ref.read(biometricServiceProvider);
  if (await biometria.isAvailable() &&
      await biometria.confirmIdentity(reason: reason)) {
    return true;
  }
  if (!context.mounted) return false;
  return await _pedirPassword(context, ref, titulo: titulo, motivo: reason) ??
      false;
}

/// Pide la contraseña de la cuenta y la valida contra el servidor.
///
/// La comprobación es del servidor y no local: la app no guarda la contraseña
/// —solo el token—, así que no tiene con qué compararla. El servidor además
/// aplica el mismo límite de intentos que el login.
Future<bool?> _pedirPassword(
  BuildContext context,
  WidgetRef ref, {
  required String titulo,
  required String motivo,
}) {
  return showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (context) => _PasswordDialog(titulo: titulo, motivo: motivo),
  );
}

class _PasswordDialog extends ConsumerStatefulWidget {
  const _PasswordDialog({required this.titulo, required this.motivo});

  final String titulo;
  final String motivo;

  @override
  ConsumerState<_PasswordDialog> createState() => _PasswordDialogState();
}

class _PasswordDialogState extends ConsumerState<_PasswordDialog> {
  final _controlador = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _verificando = false;
  bool _oculta = true;
  String? _error;

  @override
  void dispose() {
    _controlador.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return AlertDialog(
      icon: const Icon(Icons.lock_outline),
      title: Text(widget.titulo),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${widget.motivo}. Este equipo no tiene biometría configurada, '
              'así que confirmamos con tu contraseña.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _controlador,
              obscureText: _oculta,
              autofocus: true,
              enabled: !_verificando,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                labelText: 'Tu contraseña',
                errorText: _error,
                suffixIcon: IconButton(
                  tooltip: _oculta ? 'Mostrar' : 'Ocultar',
                  icon: Icon(
                    _oculta
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                  onPressed: () => setState(() => _oculta = !_oculta),
                ),
              ),
              validator: (valor) => (valor == null || valor.isEmpty)
                  ? 'Escribí tu contraseña'
                  : null,
              onFieldSubmitted: (_) => _confirmar(),
            ),
            if (_verificando)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: LinearProgressIndicator(),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _verificando ? null : () => Navigator.pop(context, false),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: _verificando ? null : _confirmar,
          child: const Text('Confirmar'),
        ),
      ],
    );
  }

  Future<void> _confirmar() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() {
      _verificando = true;
      _error = null;
    });
    try {
      await ref
          .read(apiClientProvider)
          .post<void>(
            '/api/v1/auth/reautenticar',
            data: {'password': _controlador.text},
          );
      if (mounted) Navigator.pop(context, true);
    } on AppFailure catch (falla) {
      if (!mounted) return;
      setState(() {
        _verificando = false;
        _error = falla.message;
      });
    }
  }
}
