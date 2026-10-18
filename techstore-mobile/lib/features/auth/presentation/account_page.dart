import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/errors/user_message.dart';
import '../application/session_controller.dart';

class AccountPage extends ConsumerStatefulWidget {
  const AccountPage({super.key});

  @override
  ConsumerState<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends ConsumerState<AccountPage> {
  late final TextEditingController _ruc;
  late final TextEditingController _businessName;
  final _currentPassword = TextEditingController();
  final _newPassword = TextEditingController();
  final _passwordConfirmation = TextEditingController();
  bool _busy = false;
  bool _biometricAvailable = false;
  bool _biometricEnabled = false;
  bool _operationGuard = false;
  bool _hapticsEnabled = true;
  String _sensorLabel = 'biometría del dispositivo';

  @override
  void initState() {
    super.initState();
    final user = ref.read(authSessionProvider).user!;
    _ruc = TextEditingController(text: user.ruc);
    _businessName = TextEditingController(text: user.businessName);
    _loadBiometrics();
  }

  @override
  void dispose() {
    _ruc.dispose();
    _businessName.dispose();
    _currentPassword.dispose();
    _newPassword.dispose();
    _passwordConfirmation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authSessionProvider).user!;
    return Scaffold(
      appBar: AppBar(title: const Text('Mi cuenta')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          ListTile(
            leading: const CircleAvatar(child: Icon(Icons.person_outline)),
            title: Text(user.fullName),
            // `label` y no `name`: el segundo es el nombre de la constante y se
            // mostraba como «courier». La pantalla de inicio ya lo hacía bien;
            // a esta se le había pasado.
            subtitle: Text('${user.email}\n${user.role.label}'),
            isThreeLine: true,
          ),
          const Divider(height: 32),
          Text('Datos fiscales', style: Theme.of(context).textTheme.titleLarge),
          TextField(
            controller: _ruc,
            decoration: const InputDecoration(labelText: 'RUC'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _businessName,
            decoration: const InputDecoration(labelText: 'Razón social'),
          ),
          const SizedBox(height: 12),
          FilledButton.tonal(
            onPressed: _busy ? null : _saveTaxData,
            child: const Text('Guardar datos fiscales'),
          ),
          const Divider(height: 40),
          Text(
            'Cambiar contraseña',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          TextField(
            controller: _currentPassword,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Contraseña actual'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _newPassword,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Nueva contraseña'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _passwordConfirmation,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'Confirmar nueva contraseña',
            ),
          ),
          const SizedBox(height: 8),
          const Text('Mínimo 8 caracteres, con mayúscula, minúscula y número.'),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _busy ? null : _changePassword,
            child: const Text('Cambiar contraseña'),
          ),
          const Divider(height: 40),
          Text('Dispositivo', style: Theme.of(context).textTheme.titleLarge),
          if (_biometricAvailable)
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              secondary: const Icon(Icons.fingerprint),
              title: const Text('Bloqueo biométrico'),
              subtitle: Text('Solicita $_sensorLabel al abrir la app'),
              value: _biometricEnabled,
              onChanged: _busy ? null : _setBiometric,
            )
          else
            const ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.fingerprint),
              title: Text('Bloqueo biométrico no disponible'),
              subtitle: Text(
                'Este equipo no tiene un sensor biométrico configurado',
              ),
            ),
          // La confirmación de operaciones se ofrece siempre, haya biometría o
          // no. Antes vivía dentro del bloque de arriba, así que en un equipo
          // sin huella registrada la opción ni aparecía: quien más la
          // necesitaba era justamente quien no podía activarla.
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            secondary: const Icon(Icons.verified_user_outlined),
            title: const Text('Confirmar operaciones sensibles'),
            subtitle: Text(
              _biometricAvailable
                  ? 'Pide identidad antes de facturar una venta, cerrar caja o '
                        'cancelar un pedido'
                  : 'Pide tu contraseña antes de facturar una venta, cerrar '
                        'caja o cancelar un pedido',
            ),
            value: _operationGuard,
            onChanged: _busy ? null : _setOperationGuard,
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            secondary: const Icon(Icons.vibration),
            title: const Text('Vibración al confirmar'),
            subtitle: const Text(
              'Avisa con una vibración corta al agregar productos, cobrar o '
              'fallar una operación',
            ),
            value: _hapticsEnabled,
            onChanged: _busy ? null : _setHaptics,
          ),
          const SizedBox(height: 28),
          // Antes solo estaba en la barra del inicio. Mi cuenta es donde se lo
          // busca, y la vidriera del cliente lo tiene ahora en un menú.
          OutlinedButton.icon(
            onPressed: _busy
                ? null
                : ref.read(authSessionProvider.notifier).logout,
            icon: const Icon(Icons.logout_rounded),
            label: const Text('Cerrar sesión'),
          ),
        ],
      ),
    );
  }

  Future<void> _saveTaxData() async {
    await _run(
      () => ref
          .read(authSessionProvider.notifier)
          .updateTaxData(ruc: _ruc.text, businessName: _businessName.text),
    );
  }

  Future<void> _changePassword() async {
    final password = _newPassword.text;
    final valid =
        password.length >= 8 &&
        password.contains(RegExp('[a-z]')) &&
        password.contains(RegExp('[A-Z]')) &&
        password.contains(RegExp('[0-9]'));
    if (!valid) {
      _message('La nueva contraseña no cumple los requisitos');
      return;
    }
    if (password != _passwordConfirmation.text) {
      _message('Las contraseñas no coinciden');
      return;
    }
    final changed = await _run(
      () => ref
          .read(authRepositoryProvider)
          .changePassword(
            currentPassword: _currentPassword.text,
            newPassword: password,
          ),
    );
    if (changed) {
      _currentPassword.clear();
      _newPassword.clear();
      _passwordConfirmation.clear();
    }
  }

  Future<void> _loadBiometrics() async {
    final service = ref.read(biometricServiceProvider);
    final available = await service.isAvailable();
    final enabled = available && await service.isEnabled();
    // La guarda ya no depende de que haya biometría: se confirma con la
    // contraseña cuando no la hay, así que su estado se lee siempre.
    final guard = await service.isOperationGuardEnabled();
    final label = available ? await service.sensorLabel() : '';
    final haptics = await ref.read(hapticsServiceProvider).isEnabled();
    if (mounted) {
      setState(() {
        _biometricAvailable = available;
        _biometricEnabled = enabled;
        _operationGuard = guard;
        _hapticsEnabled = haptics;
        if (label.isNotEmpty) _sensorLabel = label;
      });
    }
  }

  Future<void> _setBiometric(bool value) async {
    setState(() => _busy = true);
    try {
      await ref.read(biometricServiceProvider).setEnabled(value);
      if (mounted) {
        setState(() {
          _biometricEnabled = value;
        });
      }
      _message(value ? 'Bloqueo biométrico activado' : 'Bloqueo desactivado');
    } catch (error) {
      _message(
        userMessageFor(
          error,
          fallback: 'No pudimos cambiar esta preferencia. Intentá de nuevo.',
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _setOperationGuard(bool value) async {
    // Encenderla y apagarla piden identidad. Apagarla sobre todo: si no,
    // alcanzaría con tener el teléfono un momento para desactivar la
    // protección y recién después hacer la operación.
    final confirmado = await confirmarIdentidad(
      context,
      ref,
      reason: value
          ? 'Confirmá tu identidad para activar la protección'
          : 'Confirmá tu identidad para desactivar la protección',
      titulo: value ? 'Activar confirmación' : 'Desactivar confirmación',
    );
    if (!confirmado || !mounted) return;

    setState(() => _busy = true);
    try {
      await ref.read(biometricServiceProvider).setOperationGuard(value);
      if (mounted) setState(() => _operationGuard = value);
      _message(
        value
            ? 'Las operaciones sensibles pedirán confirmación'
            : 'Confirmación de operaciones desactivada',
      );
    } catch (error) {
      _message(
        userMessageFor(
          error,
          fallback: 'No pudimos cambiar esta preferencia. Intentá de nuevo.',
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _setHaptics(bool value) async {
    await ref.read(hapticsServiceProvider).setEnabled(value);
    if (mounted) setState(() => _hapticsEnabled = value);
  }

  Future<bool> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      _message('Cambios guardados');
      return true;
    } on AppFailure catch (failure) {
      _message(failure.message);
      return false;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}
