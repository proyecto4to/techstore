import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../application/session_controller.dart';

class RegisterPage extends ConsumerStatefulWidget {
  const RegisterPage({super.key});

  @override
  ConsumerState<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends ConsumerState<RegisterPage> {
  final _form = GlobalKey<FormState>();
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();

  @override
  void dispose() {
    _firstName.dispose();
    _lastName.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authSessionProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Crear cuenta')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            for (final entry in [
              (_firstName, 'Nombre', TextInputType.name),
              (_lastName, 'Apellido', TextInputType.name),
              (_email, 'Correo electrónico', TextInputType.emailAddress),
            ])
              Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: TextFormField(
                  controller: entry.$1,
                  decoration: InputDecoration(labelText: entry.$2),
                  keyboardType: entry.$3,
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'Campo requerido'
                      : null,
                ),
              ),
            TextFormField(
              controller: _password,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Contraseña'),
              validator: (value) {
                if (value == null || value.length < 8) {
                  return 'Mínimo 8 caracteres';
                }
                if (!RegExp(r'(?=.*[a-z])(?=.*[A-Z])(?=.*\d)')
                    .hasMatch(value)) {
                  return 'Incluí mayúscula, minúscula y número';
                }
                return null;
              },
            ),
            if (auth.message != null) ...[
              const SizedBox(height: 12),
              Text(
                auth.message!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: auth.isBusy
                  ? null
                  : () {
                      if (!_form.currentState!.validate()) return;
                      ref
                          .read(authSessionProvider.notifier)
                          .register(
                            firstName: _firstName.text,
                            lastName: _lastName.text,
                            email: _email.text,
                            password: _password.text,
                          );
                    },
              child: const Text('Crear cuenta'),
            ),
          ],
        ),
      ),
    );
  }
}
