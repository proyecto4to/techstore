import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
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
  bool _busy = false;
  bool _biometricAvailable = false;
  bool _biometricEnabled = false;

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
            subtitle: Text('${user.email}\n${user.role.name}'),
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
          OutlinedButton(
            onPressed: _busy ? null : _changePassword,
            child: const Text('Cambiar contraseña'),
          ),
          if (_biometricAvailable) ...[
            const Divider(height: 40),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              secondary: const Icon(Icons.fingerprint),
              title: const Text('Bloqueo biométrico'),
              subtitle: const Text(
                'Solicita huella o biometría del dispositivo al abrir la app',
              ),
              value: _biometricEnabled,
              onChanged: _busy ? null : _setBiometric,
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _saveTaxData() async => _run(
    () => ref
        .read(authSessionProvider.notifier)
        .updateTaxData(ruc: _ruc.text, businessName: _businessName.text),
  );

  Future<void> _changePassword() async {
    if (_newPassword.text.length < 8) {
      _message('La nueva contraseña debe tener al menos 8 caracteres');
      return;
    }
    await _run(
      () => ref
          .read(authRepositoryProvider)
          .changePassword(
            currentPassword: _currentPassword.text,
            newPassword: _newPassword.text,
          ),
    );
    _currentPassword.clear();
    _newPassword.clear();
  }

  Future<void> _loadBiometrics() async {
    final service = ref.read(biometricServiceProvider);
    final available = await service.isAvailable();
    final enabled = available && await service.isEnabled();
    if (mounted) {
      setState(() {
        _biometricAvailable = available;
        _biometricEnabled = enabled;
      });
    }
  }

  Future<void> _setBiometric(bool value) async {
    setState(() => _busy = true);
    try {
      await ref.read(biometricServiceProvider).setEnabled(value);
      if (mounted) setState(() => _biometricEnabled = value);
      _message(value ? 'Bloqueo biométrico activado' : 'Bloqueo desactivado');
    } catch (error) {
      _message(error.toString().replaceFirst('Bad state: ', ''));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      _message('Cambios guardados');
    } on AppFailure catch (failure) {
      _message(failure.message);
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
