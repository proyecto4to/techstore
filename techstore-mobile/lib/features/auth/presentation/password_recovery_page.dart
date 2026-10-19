import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/format/password_rules.dart';
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
  bool _sent = false;

  /// Solo los fallos. Antes había un único `_message` para el éxito y para el
  /// error, mostrado con el mismo estilo: «no pudimos procesar la solicitud» se
  /// veía igual que una confirmación.
  String? _error;

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
            child: _sent ? _sentBody(context) : _formBody(context),
          ),
        ),
      ),
    ),
  );

  Widget _sentBody(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(
          Icons.mark_email_read_outlined,
          size: 56,
          color: theme.colorScheme.primary,
        ),
        const SizedBox(height: 16),
        Text('Revisá tu correo', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 10),
        Text(
          'Si ${_email.text.trim()} tiene una cuenta, le enviamos un enlace '
          'para crear una contraseña nueva. Puede tardar unos minutos y a '
          'veces cae en el correo no deseado.',
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 8),
        Text(
          'El enlace vence en 15 minutos y funciona una sola vez.',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 24),
        FilledButton(
          // Si se llegó desde el ingreso, se vuelve a ese mismo: lo que había
          // debajo —el carrito, la ficha— sigue ahí.
          onPressed: () =>
              context.canPop() ? context.pop() : context.go('/login'),
          child: const Text('Volver al ingreso'),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => setState(() => _sent = false),
          child: const Text('Usar otro correo'),
        ),
      ],
    );
  }

  Widget _formBody(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Poné tu correo y te mandamos un enlace para crear una contraseña '
          'nueva.',
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 20),
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.email],
          autofocus: true,
          onChanged: _error == null
              ? null
              : (_) => setState(() => _error = null),
          onSubmitted: _sending ? null : (_) => _send(),
          decoration: InputDecoration(
            labelText: 'Correo electrónico',
            errorText: _error,
          ),
        ),
        const SizedBox(height: 20),
        FilledButton(
          onPressed: _sending ? null : _send,
          child: Text(_sending ? 'Enviando...' : 'Enviar enlace'),
        ),
      ],
    );
  }

  Future<void> _send() async {
    final email = _email.text.trim();
    if (!email.contains('@') || email.length < 5) {
      setState(() => _error = 'Escribí un correo válido, como nombre@correo.com');
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).requestPasswordRecovery(email);
      if (mounted) setState(() => _sent = true);
    } on AppFailure {
      if (mounted) {
        setState(
          () => _error =
              'No pudimos procesar la solicitud. Revisá tu conexión e '
              'intentá de nuevo.',
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
  final _confirmation = TextEditingController();
  bool _sending = false;
  bool _completed = false;
  bool _reveal = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  /// Se valida mientras escribe: el motivo aparece pegado al campo que falla,
  /// no todo junto arriba recién al apretar el botón.
  String? get _passwordError => errorDePassword(_password.text);

  String? get _confirmationError =>
      _confirmation.text.isNotEmpty && _confirmation.text != _password.text
      ? 'Las dos contraseñas tienen que ser iguales.'
      : null;

  bool get _canSave =>
      widget.token.isNotEmpty &&
      passwordValida(_password.text) &&
      _password.text == _confirmation.text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Nueva contraseña')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: _completed
                  ? _completedBody(theme)
                  : widget.token.isEmpty
                  ? _noTokenBody(theme)
                  : _formBody(theme),
            ),
          ),
        ),
      ),
    );
  }

  Widget _completedBody(ThemeData theme) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Icon(Icons.check_circle_outline, size: 56, color: theme.colorScheme.primary),
      const SizedBox(height: 16),
      Text('Contraseña actualizada', style: theme.textTheme.headlineSmall),
      const SizedBox(height: 10),
      Text(
        'Ya podés entrar con tu contraseña nueva. Por seguridad cerramos las '
        'sesiones que estaban abiertas en otros dispositivos.',
        style: theme.textTheme.bodyMedium,
      ),
      const SizedBox(height: 24),
      FilledButton(
        onPressed: () => context.go('/login'),
        child: const Text('Iniciar sesión'),
      ),
    ],
  );

  /// Sin token la pantalla no sirve para nada: se dice qué pasó y cómo salir,
  /// en vez de mostrar un formulario con el botón siempre apagado.
  Widget _noTokenBody(ThemeData theme) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Icon(Icons.link_off, size: 56, color: theme.colorScheme.error),
      const SizedBox(height: 16),
      Text('Este enlace no es válido', style: theme.textTheme.headlineSmall),
      const SizedBox(height: 10),
      Text(
        'Puede que se haya cortado al abrirlo. Pedí uno nuevo y abrilo '
        'directamente desde el correo.',
        style: theme.textTheme.bodyMedium,
      ),
      const SizedBox(height: 24),
      FilledButton(
        onPressed: () => context.go('/password/forgot'),
        child: const Text('Pedir un enlace nuevo'),
      ),
    ],
  );

  Widget _formBody(ThemeData theme) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(requisitosPassword, style: theme.textTheme.bodyMedium),
      const SizedBox(height: 20),
      if (_error != null) ...[
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: theme.colorScheme.errorContainer,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Row(
            children: [
              Icon(
                Icons.error_outline,
                size: 20,
                color: theme.colorScheme.onErrorContainer,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _error!,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onErrorContainer,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
      ],
      TextField(
        controller: _password,
        obscureText: !_reveal,
        autofocus: true,
        autofillHints: const [AutofillHints.newPassword],
        onChanged: (_) => setState(() => _error = null),
        decoration: InputDecoration(
          labelText: 'Nueva contraseña',
          errorText: _passwordError,
        ),
      ),
      const SizedBox(height: 16),
      TextField(
        controller: _confirmation,
        obscureText: !_reveal,
        autofillHints: const [AutofillHints.newPassword],
        onChanged: (_) => setState(() => _error = null),
        onSubmitted: _canSave && !_sending ? (_) => _reset() : null,
        decoration: InputDecoration(
          labelText: 'Repetila para confirmar',
          errorText: _confirmationError,
        ),
      ),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(
          onPressed: () => setState(() => _reveal = !_reveal),
          icon: Icon(
            _reveal ? Icons.visibility_off_outlined : Icons.visibility_outlined,
            size: 18,
          ),
          label: Text(_reveal ? 'Ocultar' : 'Ver lo que escribo'),
        ),
      ),
      const SizedBox(height: 16),
      FilledButton(
        onPressed: _sending || !_canSave ? null : _reset,
        child: Text(_sending ? 'Guardando...' : 'Guardar contraseña nueva'),
      ),
    ],
  );

  Future<void> _reset() async {
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await ref
          .read(authRepositoryProvider)
          .resetPassword(token: widget.token, password: _password.text);
      if (mounted) {
        setState(() {
          _completed = true;
          _error = null;
        });
        _password.clear();
        _confirmation.clear();
      }
    } on AppFailure catch (failure) {
      if (mounted) setState(() => _error = failure.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }
}
