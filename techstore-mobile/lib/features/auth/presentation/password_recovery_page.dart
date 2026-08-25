import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/errors/app_failure.dart';
import '../application/session_controller.dart';

class PasswordRecoveryPage extends ConsumerStatefulWidget {
  const PasswordRecoveryPage({super.key});

  @override
  ConsumerState<PasswordRecoveryPage> createState() =>
      _PasswordRecoveryPageState();
}

class _PasswordRecoveryPageState extends ConsumerState<PasswordRecoveryPage> {
  final _email = TextEditingController();
  bool _sending = false;
  String? _message;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Recuperar contraseña')),
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Si el correo corresponde a una cuenta, enviaremos un enlace de recuperación.',
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  decoration: const InputDecoration(
                    labelText: 'Correo electrónico',
                  ),
                ),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _sending ? null : _send,
                  child: Text(_sending ? 'Enviando…' : 'Enviar enlace'),
                ),
                if (_message != null) ...[
                  const SizedBox(height: 16),
                  Text(_message!, textAlign: TextAlign.center),
                ],
              ],
            ),
          ),
        ),
      ),
    ),
  );

  Future<void> _send() async {
    if (!_email.text.contains('@')) {
      setState(() => _message = 'Ingresá un correo válido');
      return;
    }
    setState(() {
      _sending = true;
      _message = null;
    });
    try {
      await ref
          .read(authRepositoryProvider)
          .requestPasswordRecovery(_email.text);
      if (mounted) {
        setState(
          () => _message = 'Si la cuenta existe, el enlace ya fue generado.',
        );
      }
    } on AppFailure {
      if (mounted) {
        setState(
          () => _message =
              'No pudimos procesar la solicitud. Intentá nuevamente.',
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }
}

class PasswordResetPage extends ConsumerStatefulWidget {
  const PasswordResetPage({required this.token, super.key});

  final String token;

  @override
  ConsumerState<PasswordResetPage> createState() => _PasswordResetPageState();
}

class _PasswordResetPageState extends ConsumerState<PasswordResetPage> {
  final _password = TextEditingController();
  bool _sending = false;
  String? _message;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Nueva contraseña')),
    body: SafeArea(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: _password,
                  obscureText: true,
                  autofillHints: const [AutofillHints.newPassword],
                  decoration: const InputDecoration(
                    labelText: 'Nueva contraseña',
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Mínimo 8 caracteres, con mayúscula, minúscula y número.',
                ),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _sending || widget.token.isEmpty ? null : _reset,
                  child: Text(_sending ? 'Guardando…' : 'Cambiar contraseña'),
                ),
                if (_message != null) ...[
                  const SizedBox(height: 16),
                  Text(_message!, textAlign: TextAlign.center),
                ],
              ],
            ),
          ),
        ),
      ),
    ),
  );

  Future<void> _reset() async {
    final password = _password.text;
    final valid =
        password.length >= 8 &&
        password.contains(RegExp('[a-z]')) &&
        password.contains(RegExp('[A-Z]')) &&
        password.contains(RegExp('[0-9]'));
    if (!valid) {
      setState(() => _message = 'La contraseña no cumple los requisitos.');
      return;
    }
    setState(() => _sending = true);
    try {
      await ref
          .read(authRepositoryProvider)
          .resetPassword(token: widget.token, password: password);
      if (mounted) {
        setState(
          () => _message = 'Contraseña actualizada. Ya podés iniciar sesión.',
        );
      }
    } on AppFailure catch (failure) {
      if (mounted) setState(() => _message = failure.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }
}
