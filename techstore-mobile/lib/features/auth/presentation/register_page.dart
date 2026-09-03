import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/password_rules.dart';
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
  bool _obscure = true;

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
                  autofocus: entry.$1 == _firstName,
                  decoration: InputDecoration(labelText: entry.$2),
                  keyboardType: entry.$3,
                  textInputAction: TextInputAction.next,
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Completá ${entry.$2.toLowerCase()}';
                    }
                    // El correo solo se comprobaba como «no vacío»: escribir
                    // «hola» pasaba y el rechazo llegaba desde el servidor.
                    if (entry.$1 == _email && !value.contains('@')) {
                      return 'Escribí un correo válido, como nombre@correo.com';
                    }
                    return null;
                  },
                ),
              ),
            TextFormField(
              controller: _password,
              obscureText: _obscure,
              // Los requisitos se decían recién al rechazar el alta. Dichos
              // antes, la persona escribe una contraseña que va a ser aceptada
              // en el primer intento.
              decoration: InputDecoration(
                labelText: 'Contraseña',
                helperText: requisitosPassword,
                suffixIcon: IconButton(
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                  tooltip: _obscure
                      ? 'Mostrar contraseña'
                      : 'Ocultar contraseña',
                ),
              ),
              validator: (value) => value == null || value.isEmpty
                  ? 'Elegí una contraseña'
                  // La misma regla que usa el reseteo y que exige el servidor,
                  // para que el texto de ayuda y el rechazo nunca discrepen.
                  : errorDePassword(value),
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
