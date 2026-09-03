import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../application/session_controller.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  static const _backgroundAsset = 'assets/images/login_background.png';

  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authSessionProvider);
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          ExcludeSemantics(
            child: Image.asset(
              _backgroundAsset,
              key: const Key('login-background'),
              fit: BoxFit.cover,
              alignment: Alignment.center,
            ),
          ),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0x33030E20),
                  Color(0x66030E20),
                  Color(0xB3030E20),
                ],
                stops: [0, 0.48, 1],
              ),
            ),
          ),
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) => SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 24,
                ),
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    minHeight: constraints.maxHeight - 48,
                  ),
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 460),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const _LoginBrand(),
                          const SizedBox(height: 28),
                          DecoratedBox(
                            decoration: BoxDecoration(
                              color: colors.surface.withValues(
                                alpha: isDark ? 0.94 : 0.96,
                              ),
                              borderRadius: BorderRadius.circular(28),
                              border: Border.all(
                                color: Colors.white.withValues(alpha: 0.18),
                              ),
                              boxShadow: const [
                                BoxShadow(
                                  color: Color(0x59000000),
                                  blurRadius: 32,
                                  offset: Offset(0, 16),
                                ),
                              ],
                            ),
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(
                                24,
                                26,
                                24,
                                20,
                              ),
                              child: AutofillGroup(
                                child: Form(
                                  key: _formKey,
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      Text(
                                        'Iniciar sesión',
                                        style: theme.textTheme.headlineSmall
                                            ?.copyWith(
                                              fontWeight: FontWeight.w700,
                                            ),
                                      ),
                                      const SizedBox(height: 6),
                                      Text(
                                        'Ingresá para administrar tus compras y facturas.',
                                        style: theme.textTheme.bodyMedium
                                            ?.copyWith(
                                              color: colors.onSurfaceVariant,
                                            ),
                                      ),
                                      const SizedBox(height: 24),
                                      TextFormField(
                                        controller: _email,
                                        autofillHints: const [
                                          AutofillHints.username,
                                        ],
                                        keyboardType:
                                            TextInputType.emailAddress,
                                        textInputAction: TextInputAction.next,
                                        decoration: _fieldDecoration(
                                          context,
                                          label: 'Correo electrónico',
                                          icon: Icons.mail_outline_rounded,
                                        ),
                                        validator: (value) =>
                                            value == null ||
                                                !value.contains('@')
                                            ? 'Ingresá un correo válido'
                                            : null,
                                      ),
                                      const SizedBox(height: 16),
                                      TextFormField(
                                        controller: _password,
                                        obscureText: _obscure,
                                        autofillHints: const [
                                          AutofillHints.password,
                                        ],
                                        onFieldSubmitted: (_) => _submit(),
                                        decoration: _fieldDecoration(
                                          context,
                                          label: 'Contraseña',
                                          icon: Icons.lock_outline_rounded,
                                          suffixIcon: IconButton(
                                            tooltip: _obscure
                                                ? 'Mostrar contraseña'
                                                : 'Ocultar contraseña',
                                            onPressed: () => setState(
                                              () => _obscure = !_obscure,
                                            ),
                                            icon: Icon(
                                              _obscure
                                                  ? Icons.visibility_outlined
                                                  : Icons
                                                        .visibility_off_outlined,
                                            ),
                                          ),
                                        ),
                                        validator: (value) =>
                                            value == null || value.isEmpty
                                            ? 'Ingresá tu contraseña'
                                            : null,
                                      ),
                                      if (auth.message != null) ...[
                                        const SizedBox(height: 14),
                                        Container(
                                          padding: const EdgeInsets.all(12),
                                          decoration: BoxDecoration(
                                            color: colors.errorContainer,
                                            borderRadius: BorderRadius.circular(
                                              12,
                                            ),
                                          ),
                                          child: Text(
                                            auth.message!,
                                            style: TextStyle(
                                              color: colors.onErrorContainer,
                                            ),
                                          ),
                                        ),
                                      ],
                                      const SizedBox(height: 22),
                                      FilledButton.icon(
                                        style: FilledButton.styleFrom(
                                          minimumSize: const Size.fromHeight(
                                            52,
                                          ),
                                        ),
                                        onPressed: auth.isBusy ? null : _submit,
                                        icon: auth.isBusy
                                            ? const SizedBox.square(
                                                dimension: 20,
                                                child:
                                                    CircularProgressIndicator(
                                                      strokeWidth: 2,
                                                    ),
                                              )
                                            : const Icon(Icons.login_rounded),
                                        label: Text(
                                          auth.isBusy
                                              ? 'Ingresando…'
                                              : 'Ingresar',
                                        ),
                                      ),
                                      TextButton(
                                        onPressed: () =>
                                            context.go('/password/forgot'),
                                        child: const Text(
                                          'Olvidé mi contraseña',
                                        ),
                                      ),
                                      const Divider(height: 28),
                                      OutlinedButton.icon(
                                        style: OutlinedButton.styleFrom(
                                          minimumSize: const Size.fromHeight(
                                            50,
                                          ),
                                        ),
                                        onPressed: () => context.go('/catalog'),
                                        icon: const Icon(
                                          Icons.storefront_outlined,
                                        ),
                                        label: const Text(
                                          'Ver catálogo sin iniciar sesión',
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      TextButton(
                                        onPressed: () =>
                                            context.go('/register'),
                                        child: const Text('Crear una cuenta'),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 20),
                          Text(
                            'Acceso seguro · TechStore',
                            textAlign: TextAlign.center,
                            style: theme.textTheme.labelMedium?.copyWith(
                              color: Colors.white.withValues(alpha: 0.78),
                              letterSpacing: 0.3,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  InputDecoration _fieldDecoration(
    BuildContext context, {
    required String label,
    required IconData icon,
    Widget? suffixIcon,
  }) {
    final colors = Theme.of(context).colorScheme;
    return InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon),
      suffixIcon: suffixIcon,
      filled: true,
      fillColor: colors.surfaceContainerHighest.withValues(alpha: 0.48),
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    TextInput.finishAutofillContext();
    await ref
        .read(authSessionProvider.notifier)
        .login(email: _email.text, password: _password.text);
  }
}

class _LoginBrand extends StatelessWidget {
  const _LoginBrand();

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Container(
        width: 76,
        height: 76,
        decoration: BoxDecoration(
          color: const Color(0xE60A2E5D),
          shape: BoxShape.circle,
          border: Border.all(
            color: Colors.white.withValues(alpha: 0.28),
            width: 1.5,
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x802078FF),
              blurRadius: 28,
              spreadRadius: 2,
            ),
          ],
        ),
        child: const Icon(
          Icons.storefront_rounded,
          size: 38,
          color: Colors.white,
        ),
      ),
      const SizedBox(height: 14),
      Text(
        'TechStore',
        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
          color: Colors.white,
          fontWeight: FontWeight.w800,
          letterSpacing: -0.6,
          shadows: const [Shadow(color: Color(0xB3000000), blurRadius: 12)],
        ),
      ),
      const SizedBox(height: 4),
      Text(
        'Tu tecnología, en un solo lugar',
        style: Theme.of(context).textTheme.bodyMedium
            ?.copyWith(color: Colors.white.withValues(alpha: 0.84)),
      ),
    ],
  );
}
