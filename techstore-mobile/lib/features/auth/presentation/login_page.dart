import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/widgets/marca/isotipo.dart';
import '../../../core/widgets/marca/logotipo.dart';
import '../application/session_controller.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
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

    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          const ExcludeSemantics(
            child: _FondoDeIngreso(key: Key('login-background')),
          ),
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) => SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: Medidas.margen,
                  vertical: 24,
                ),
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    minHeight: constraints.maxHeight - 48,
                  ),
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 440),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const _Marca(),
                          const SizedBox(height: 30),
                          Aparicion(
                            orden: 3,
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                color: Paleta.negroAzulado.withValues(
                                  alpha: 0.94,
                                ),
                                borderRadius: BorderRadius.circular(26),
                                border: Border.all(color: Paleta.borde),
                                boxShadow: const [
                                  BoxShadow(
                                    color: Color(0x1F2563EB),
                                    blurRadius: 70,
                                    spreadRadius: -28,
                                    offset: Offset(0, 18),
                                  ),
                                ],
                              ),
                              child: Padding(
                                padding: const EdgeInsets.fromLTRB(
                                  22,
                                  24,
                                  22,
                                  18,
                                ),
                                child: AutofillGroup(
                                  child: Form(
                                    key: _formKey,
                                    // Revalida mientras se escribe, pero recién
                                    // después del primer intento fallido: así
                                    // el cartel rojo desaparece al corregir el
                                    // campo en vez de quedarse hasta volver a
                                    // tocar «Ingresar», y no se le grita a
                                    // nadie por tener el formulario vacío.
                                    autovalidateMode:
                                        AutovalidateMode.onUserInteraction,
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.stretch,
                                      children: [
                                        Text(
                                          'Iniciar sesión',
                                          style: theme.textTheme.headlineSmall,
                                        ),
                                        const SizedBox(height: 6),
                                        Text(
                                          'Ingresá para seguir tus pedidos, '
                                          'tus facturas y tus favoritos.',
                                          style: theme.textTheme.bodyMedium
                                              ?.copyWith(
                                                color: colors.onSurfaceVariant,
                                              ),
                                        ),
                                        const SizedBox(height: 22),
                                        TextFormField(
                                          controller: _email,
                                          autofillHints: const [
                                            AutofillHints.username,
                                          ],
                                          keyboardType:
                                              TextInputType.emailAddress,
                                          textInputAction: TextInputAction.next,
                                          decoration: const InputDecoration(
                                            labelText: 'Correo electrónico',
                                            prefixIcon: Icon(
                                              Icons.mail_outline_rounded,
                                            ),
                                          ),
                                          validator: (value) =>
                                              value == null ||
                                                  !value.contains('@')
                                              ? 'Ingresá un correo válido'
                                              : null,
                                        ),
                                        const SizedBox(height: 14),
                                        TextFormField(
                                          controller: _password,
                                          obscureText: _obscure,
                                          autofillHints: const [
                                            AutofillHints.password,
                                          ],
                                          onFieldSubmitted: (_) => _submit(),
                                          decoration: InputDecoration(
                                            labelText: 'Contraseña',
                                            prefixIcon: const Icon(
                                              Icons.lock_outline_rounded,
                                            ),
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
                                          _Aviso(mensaje: auth.message!),
                                        ],
                                        const SizedBox(height: 22),
                                        FilledButton.icon(
                                          style: FilledButton.styleFrom(
                                            minimumSize: const Size.fromHeight(
                                              54,
                                            ),
                                          ),
                                          onPressed: auth.isBusy
                                              ? null
                                              : _submit,
                                          icon: auth.isBusy
                                              ? const SizedBox.square(
                                                  dimension: 20,
                                                  child:
                                                      CircularProgressIndicator(
                                                        strokeWidth: 2,
                                                      ),
                                                )
                                              : const Icon(
                                                  Icons.login_rounded,
                                                ),
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
                                      ],
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 18),
                          Aparicion(
                            orden: 5,
                            child: OutlinedButton.icon(
                              style: OutlinedButton.styleFrom(
                                minimumSize: const Size.fromHeight(52),
                              ),
                              onPressed: () => context.go('/catalog'),
                              icon: const Icon(Icons.storefront_outlined),
                              label: const Text(
                                'Ver la tienda sin iniciar sesión',
                              ),
                            ),
                          ),
                          const SizedBox(height: 6),
                          Aparicion(
                            orden: 6,
                            child: TextButton(
                              onPressed: () => context.go('/register'),
                              child: const Text('Crear una cuenta'),
                            ),
                          ),
                          const SizedBox(height: 14),
                          Text(
                            'Acceso seguro · TechStore',
                            textAlign: TextAlign.center,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: Paleta.textoSecundario,
                              letterSpacing: 0.6,
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

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    TextInput.finishAutofillContext();
    await ref
        .read(authSessionProvider.notifier)
        .login(email: _email.text, password: _password.text);
  }
}

/// El isotipo llega con el cursor, el nombre aparece y queda el lema.
class _Marca extends StatelessWidget {
  const _Marca();

  @override
  Widget build(BuildContext context) => Column(
    children: [
      const IsotipoAnimado(ancho: 62, demora: Duration(milliseconds: 120)),
      const SizedBox(height: 18),
      const Aparicion(orden: 1, child: Logotipo(tamano: 34)),
      const SizedBox(height: 8),
      Aparicion(
        orden: 2,
        child: Text(
          'Precio claro. Stock real.',
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: Paleta.textoSecundario,
          ),
        ),
      ),
    ],
  );
}

class _Aviso extends StatelessWidget {
  const _Aviso({required this.mensaje});

  final String mensaje;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: colors.errorContainer,
          borderRadius: BorderRadius.circular(Medidas.radioControl),
          border: Border.all(color: colors.error.withValues(alpha: 0.5)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.error_outline_rounded, color: colors.error, size: 20),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                mensaje,
                style: TextStyle(color: colors.onErrorContainer),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// El fondo del ingreso: la luz azul arriba, las diagonales de la marca y la
/// T enorme como marca de agua. Dibujado, no una foto: pesa nada y se ve
/// nítido en cualquier pantalla.
class _FondoDeIngreso extends StatelessWidget {
  const _FondoDeIngreso({super.key});

  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: DecoratedBox(
      decoration: const BoxDecoration(
        color: Paleta.fondo,
        gradient: RadialGradient(
          center: Alignment(0.6, -1.05),
          radius: 1.25,
          colors: [Color(0x662563EB), Color(0x14111827), Color(0x00000000)],
          stops: [0, 0.55, 1],
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          CustomPaint(painter: _Diagonales()),
          const Positioned(
            right: -90,
            top: -40,
            child: Opacity(opacity: 0.07, child: Isotipo(ancho: 360)),
          ),
        ],
      ),
    ),
  );
}

class _Diagonales extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final linea = Paint()
      ..color = Paleta.azulPrincipal.withValues(alpha: 0.06)
      ..strokeWidth = 1;
    // El ángulo del corte del isotipo (32°): sube 0,625 por cada punto.
    for (var x = -size.height * 1.6; x < size.width; x += 22) {
      canvas.drawLine(
        Offset(x, size.height),
        Offset(x + size.height * 1.6, 0),
        linea,
      );
    }
  }

  @override
  bool shouldRepaint(_Diagonales viejo) => false;
}
