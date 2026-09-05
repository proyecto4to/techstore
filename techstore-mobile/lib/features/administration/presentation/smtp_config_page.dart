import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';

final smtpConfigProvider = FutureProvider.autoDispose((ref) async {
  final respuesta = await ref
      .read(apiClientProvider)
      .get<Map<String, Object?>>('/api/v1/correos/config');
  return respuesta.data ?? const <String, Object?>{};
});

/// Cómo se cifra la conexión, con el nombre que entiende el servidor.
const _seguridades = <(String, String, String)>[
  ('STARTTLS', 'STARTTLS', 'Lo habitual en el puerto 587'),
  ('SSL', 'SSL/TLS', 'Lo habitual en el puerto 465'),
  ('NINGUNA', 'Sin cifrar', 'La contraseña viaja legible: solo en red propia'),
];

/// El correo saliente de la empresa.
///
/// Es de las pantallas más delicadas del sistema: quien cambia el servidor y el
/// usuario se queda con la recuperación de contraseñas de todas las cuentas.
/// Por eso guardar pide de nuevo la contraseña de quien está editando, y la
/// contraseña del servidor no se muestra nunca —el servidor tampoco la
/// devuelve—.
class SmtpConfigPage extends ConsumerWidget {
  const SmtpConfigPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(smtpConfigProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Correo saliente')),
      body: config.when(
        loading: () => const AppLoading(label: 'Cargando la configuración'),
        error: (error, _) => AppErrorState(
          message: error is AppFailure
              ? error.message
              : 'No pudimos cargar la configuración.',
          onRetry: () => ref.invalidate(smtpConfigProvider),
        ),
        data: (datos) => _Formulario(datos: datos),
      ),
    );
  }
}

class _Formulario extends ConsumerStatefulWidget {
  const _Formulario({required this.datos});

  final Map<String, Object?> datos;

  @override
  ConsumerState<_Formulario> createState() => _FormularioState();
}

class _FormularioState extends ConsumerState<_Formulario> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _host;
  late final TextEditingController _puerto;
  late final TextEditingController _usuario;
  late final TextEditingController _remitente;
  late final TextEditingController _nombreRemitente;
  final _password = TextEditingController();

  late String _seguridad;
  late bool _teniaPassword;
  bool _cambiarPassword = false;
  bool _ocultaPassword = true;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    final datos = widget.datos;
    _host = TextEditingController(text: datos['host']?.toString() ?? '');
    _puerto = TextEditingController(
      text: (datos['port'] as num?)?.toString() ?? '587',
    );
    _usuario = TextEditingController(text: datos['username']?.toString() ?? '');
    _remitente = TextEditingController(text: datos['from']?.toString() ?? '');
    _nombreRemitente = TextEditingController(
      text: datos['fromNombre']?.toString() ?? '',
    );
    _seguridad = datos['seguridad']?.toString() ?? 'STARTTLS';
    _teniaPassword = datos['tienePassword'] == true;
    // Sin contraseña guardada no hay nada que conservar: el campo arranca
    // abierto para escribirla, en lugar de esconderlo detrás de un interruptor.
    _cambiarPassword = !_teniaPassword;
  }

  @override
  void dispose() {
    for (final c in [
      _host,
      _puerto,
      _usuario,
      _remitente,
      _nombreRemitente,
      _password,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final configurado = widget.datos['configurado'] == true;
    return Form(
      key: _formKey,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Estado(configurado: configurado, theme: theme),
          const SizedBox(height: 20),
          _Seccion(titulo: 'Servidor', theme: theme),
          TextFormField(
            controller: _host,
            enabled: !_guardando,
            decoration: const InputDecoration(
              labelText: 'Servidor SMTP',
              helperText: 'Por ejemplo: smtp.gmail.com',
            ),
            validator: (v) =>
                (v == null || v.trim().isEmpty) ? 'Indicá el servidor' : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _puerto,
            enabled: !_guardando,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Puerto'),
            validator: (v) {
              final numero = int.tryParse((v ?? '').trim());
              if (numero == null || numero < 1 || numero > 65535) {
                return 'Un puerto entre 1 y 65535';
              }
              return null;
            },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _seguridad,
            decoration: const InputDecoration(labelText: 'Seguridad'),
            items: [
              for (final (valor, etiqueta, ayuda) in _seguridades)
                DropdownMenuItem(
                  value: valor,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(etiqueta),
                      Text(
                        ayuda,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
            onChanged: _guardando
                ? null
                : (valor) => setState(() => _seguridad = valor ?? 'STARTTLS'),
          ),
          if (_seguridad == 'NINGUNA')
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: _Aviso(
                texto: 'Sin cifrado, la contraseña del correo viaja legible por '
                    'la red. Usalo solo con un servidor de tu propia red.',
                theme: theme,
              ),
            ),

          const SizedBox(height: 24),
          _Seccion(titulo: 'Credenciales', theme: theme),
          TextFormField(
            controller: _usuario,
            enabled: !_guardando,
            autofillHints: const [AutofillHints.username],
            decoration: const InputDecoration(labelText: 'Usuario'),
          ),
          const SizedBox(height: 12),
          if (_teniaPassword && !_cambiarPassword)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.password_outlined),
              title: const Text('Contraseña guardada'),
              subtitle: const Text('••••••••'),
              trailing: TextButton(
                onPressed: _guardando
                    ? null
                    : () => setState(() => _cambiarPassword = true),
                child: const Text('Cambiar'),
              ),
            )
          else
            TextFormField(
              controller: _password,
              enabled: !_guardando,
              obscureText: _ocultaPassword,
              decoration: InputDecoration(
                labelText: 'Contraseña del servidor',
                helperText: _teniaPassword
                    ? 'Vacío conserva la que ya está guardada'
                    : 'La guardamos cifrada y no se muestra más',
                suffixIcon: IconButton(
                  tooltip: _ocultaPassword ? 'Mostrar' : 'Ocultar',
                  icon: Icon(
                    _ocultaPassword
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                  onPressed: () =>
                      setState(() => _ocultaPassword = !_ocultaPassword),
                ),
              ),
            ),

          const SizedBox(height: 24),
          _Seccion(titulo: 'Remitente', theme: theme),
          TextFormField(
            controller: _remitente,
            enabled: !_guardando,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(
              labelText: 'Correo remitente',
              helperText: 'Vacío usa el usuario del servidor',
            ),
            validator: (v) {
              final texto = (v ?? '').trim();
              if (texto.isEmpty) return null;
              return texto.contains('@') ? null : 'Escribí un correo válido';
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _nombreRemitente,
            enabled: !_guardando,
            decoration: const InputDecoration(
              labelText: 'Nombre del remitente',
              helperText: 'Cómo aparece en la bandeja de quien recibe',
            ),
          ),

          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: _guardando ? null : _guardar,
            icon: _guardando
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: const Text('Guardar configuración'),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _guardando || !configurado ? null : _probar,
            icon: const Icon(Icons.outgoing_mail),
            label: const Text('Probar configuración'),
          ),
          if (!configurado)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                'Completá servidor, usuario y contraseña para poder probar.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Future<void> _guardar() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final password = await _pedirMiPassword();
    if (password == null) return;

    setState(() => _guardando = true);
    try {
      await ref
          .read(apiClientProvider)
          .put<Map<String, Object?>>(
            '/api/v1/correos/config',
            data: {
              'host': _host.text.trim(),
              'port': int.tryParse(_puerto.text.trim()) ?? 587,
              'username': _usuario.text.trim(),
              // Vacío conserva la guardada: el servidor solo la pisa si viene
              // con valor.
              'password': _cambiarPassword ? _password.text : '',
              'from': _remitente.text.trim(),
              'fromNombre': _nombreRemitente.text.trim(),
              'seguridad': _seguridad,
              'passwordActual': password,
            },
          );
      if (!mounted) return;
      _password.clear();
      ref.invalidate(smtpConfigProvider);
      _avisar('Configuración guardada.');
    } on AppFailure catch (falla) {
      if (!mounted) return;
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  /// La contraseña de quien está editando, no la del servidor de correo.
  Future<String?> _pedirMiPassword() {
    final controlador = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.lock_outline),
        title: const Text('Confirmá que sos vos'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Cambiar el correo saliente afecta la recuperación de '
              'contraseñas de todas las cuentas. Escribí tu contraseña para '
              'seguir.',
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controlador,
              obscureText: true,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Tu contraseña'),
              onSubmitted: (valor) => Navigator.pop(context, valor),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controlador.text),
            child: const Text('Continuar'),
          ),
        ],
      ),
    ).then((valor) {
      controlador.dispose();
      return valor == null || valor.isEmpty ? null : valor;
    });
  }

  Future<void> _probar() async {
    final destinatario = await _pedirDestinatario();
    if (destinatario == null || !mounted) return;
    setState(() => _guardando = true);
    try {
      final respuesta = await ref
          .read(apiClientProvider)
          .post<Map<String, Object?>>(
            '/api/v1/correos/prueba',
            data: {'destinatario': destinatario},
          );
      if (!mounted) return;
      final datos = respuesta.data ?? const <String, Object?>{};
      // El servidor contesta 200 aunque el envío falle: el detalle viene en el
      // cuerpo, así que hay que mirarlo y no solo el código de estado.
      final ok = datos['exito'] == true;
      _mostrarResultado(
        ok: ok,
        mensaje: ok
            ? 'Enviamos un correo de prueba a $destinatario. Revisá la bandeja, '
                  'y también el correo no deseado.'
            : (datos['mensaje']?.toString() ??
                  'El servidor rechazó el envío y no explicó por qué.'),
      );
    } on AppFailure catch (falla) {
      if (!mounted) return;
      _mostrarResultado(ok: false, mensaje: falla.message);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  Future<String?> _pedirDestinatario() {
    final controlador = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Probar el correo saliente'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('¿A qué dirección mandamos el correo de prueba?'),
            const SizedBox(height: 16),
            TextField(
              controller: controlador,
              autofocus: true,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: 'Destinatario'),
              onSubmitted: (valor) => Navigator.pop(context, valor),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controlador.text.trim()),
            child: const Text('Enviar prueba'),
          ),
        ],
      ),
    ).then((valor) {
      controlador.dispose();
      return valor == null || !valor.contains('@') ? null : valor;
    });
  }

  void _mostrarResultado({required bool ok, required String mensaje}) {
    showDialog<void>(
      context: context,
      builder: (context) {
        final theme = Theme.of(context);
        return AlertDialog(
          icon: Icon(
            ok ? Icons.mark_email_read_outlined : Icons.error_outline,
            color: ok ? theme.colorScheme.primary : theme.colorScheme.error,
          ),
          title: Text(ok ? 'Envío exitoso' : 'No se pudo enviar'),
          content: Text(mensaje),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Entendido'),
            ),
          ],
        );
      },
    );
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

class _Estado extends StatelessWidget {
  const _Estado({required this.configurado, required this.theme});

  final bool configurado;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    final color = configurado
        ? theme.colorScheme.primaryContainer
        : theme.colorScheme.errorContainer;
    final alFrente = configurado
        ? theme.colorScheme.onPrimaryContainer
        : theme.colorScheme.onErrorContainer;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(
            configurado ? Icons.check_circle_outline : Icons.warning_amber,
            color: alFrente,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  configurado
                      ? 'El correo saliente está activo'
                      : 'El correo saliente no está configurado',
                  style: theme.textTheme.titleSmall?.copyWith(color: alFrente),
                ),
                const SizedBox(height: 2),
                Text(
                  configurado
                      ? 'Se están enviando los avisos a los clientes.'
                      : 'Los avisos y la recuperación de contraseña no salen.',
                  style: theme.textTheme.bodySmall?.copyWith(color: alFrente),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Seccion extends StatelessWidget {
  const _Seccion({required this.titulo, required this.theme});

  final String titulo;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(
        titulo.toUpperCase(),
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.primary,
          letterSpacing: 1.1,
        ),
      ),
    );
  }
}

class _Aviso extends StatelessWidget {
  const _Aviso({required this.texto, required this.theme});

  final String texto;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.warning_amber,
            size: 20,
            color: theme.colorScheme.onErrorContainer,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              texto,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onErrorContainer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
