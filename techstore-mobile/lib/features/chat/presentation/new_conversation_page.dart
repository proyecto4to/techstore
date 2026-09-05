import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../auth/application/session_controller.dart';
import '../../auth/domain/auth_user.dart';
import 'chat_page.dart';

/// Qué se le pidió al servidor: el texto buscado y el rol elegido.
@immutable
class BusquedaDestinatarios {
  const BusquedaDestinatarios({this.texto = '', this.rol});

  final String texto;

  /// Nombre del rol tal como lo entiende el servidor, o nulo para todos.
  final String? rol;

  BusquedaDestinatarios copyWith({String? texto, String? rol, bool sinRol = false}) =>
      BusquedaDestinatarios(
        texto: texto ?? this.texto,
        rol: sinRol ? null : (rol ?? this.rol),
      );

  @override
  bool operator ==(Object other) =>
      other is BusquedaDestinatarios && other.texto == texto && other.rol == rol;

  @override
  int get hashCode => Object.hash(texto, rol);
}

final destinatariosProvider = FutureProvider.autoDispose
    .family<List<Map<String, Object?>>, BusquedaDestinatarios>(
      (ref, busqueda) => ref
          .watch(chatRepositoryProvider)
          .recipients(query: busqueda.texto, role: busqueda.rol),
    );

/// Elegir con quién empezar una conversación.
///
/// La lista la arma el servidor según el rol de quien pregunta: administración
/// ve a todos, y cajero o repartidor solamente a administración. Acá no se
/// filtra nada por cuenta propia, así que la pantalla no puede quedar
/// desalineada con lo que la API realmente permite.
class NewConversationPage extends ConsumerStatefulWidget {
  const NewConversationPage({super.key});

  @override
  ConsumerState<NewConversationPage> createState() =>
      _NewConversationPageState();
}

class _NewConversationPageState extends ConsumerState<NewConversationPage> {
  final _buscador = TextEditingController();
  Timer? _rebote;
  BusquedaDestinatarios _busqueda = const BusquedaDestinatarios();

  @override
  void dispose() {
    _rebote?.cancel();
    _buscador.dispose();
    super.dispose();
  }

  /// Espera a que pare de escribir antes de consultar.
  ///
  /// Sin esto cada tecla dispara una consulta, y las respuestas pueden llegar
  /// desordenadas y dejar en pantalla el resultado de una búsqueda vieja.
  void _buscar(String texto) {
    _rebote?.cancel();
    _rebote = Timer(const Duration(milliseconds: 300), () {
      if (mounted) setState(() => _busqueda = _busqueda.copyWith(texto: texto));
    });
  }

  void _filtrarPorRol(String? rol) {
    setState(() {
      _busqueda = rol == null
          ? _busqueda.copyWith(sinRol: true)
          : _busqueda.copyWith(rol: rol);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final rolPropio = ref.watch(authSessionProvider).user?.role;
    // Solo administración elige entre varios roles. Para cajero y repartidor la
    // única opción es administración, así que mostrar filtros sería ofrecer una
    // elección que no existe.
    final puedeFiltrar = rolPropio == AppRole.admin;
    final destinatarios = ref.watch(destinatariosProvider(_busqueda));

    return Scaffold(
      appBar: AppBar(title: const Text('Nueva conversación')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _buscador,
              onChanged: _buscar,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                labelText: 'Buscar por nombre',
                helperText: 'Podés escribir el nombre, el apellido o los dos',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _buscador.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Limpiar',
                        icon: const Icon(Icons.close),
                        onPressed: () {
                          _buscador.clear();
                          _buscar('');
                        },
                      ),
              ),
            ),
          ),
          if (puedeFiltrar)
            SizedBox(
              height: 48,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                children: [
                  _ChipRol(
                    etiqueta: 'Todos',
                    activo: _busqueda.rol == null,
                    onTap: () => _filtrarPorRol(null),
                  ),
                  for (final rol in const [
                    ('Admin', 'Administradores'),
                    ('Cajero', 'Cajeros'),
                    ('Repartidor', 'Repartidores'),
                    ('Cliente', 'Clientes'),
                  ])
                    _ChipRol(
                      etiqueta: rol.$2,
                      activo: _busqueda.rol == rol.$1,
                      onTap: () => _filtrarPorRol(rol.$1),
                    ),
                ],
              ),
            ),
          const Divider(height: 1),
          Expanded(
            child: destinatarios.when(
              loading: () => const AppLoading(label: 'Buscando personas'),
              error: (error, _) => AppErrorState(
                message: error is AppFailure
                    ? error.message
                    : 'No pudimos cargar la lista.',
                onRetry: () =>
                    ref.invalidate(destinatariosProvider(_busqueda)),
              ),
              data: (lista) => lista.isEmpty
                  ? _SinResultados(
                      buscando: _busqueda.texto.isNotEmpty ||
                          _busqueda.rol != null,
                    )
                  : ListView.separated(
                      itemCount: lista.length,
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) =>
                          _Persona(datos: lista[index], theme: theme),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ChipRol extends StatelessWidget {
  const _ChipRol({
    required this.etiqueta,
    required this.activo,
    required this.onTap,
  });

  final String etiqueta;
  final bool activo;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        label: Text(etiqueta),
        selected: activo,
        onSelected: (_) => onTap(),
      ),
    );
  }
}

class _Persona extends StatelessWidget {
  const _Persona({required this.datos, required this.theme});

  final Map<String, Object?> datos;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    final nombre = datos['nombreCompleto']?.toString() ?? 'Sin nombre';
    final rol = datos['rol']?.toString() ?? '';
    final email = datos['email']?.toString() ?? '';
    final conversacionId = (datos['conversacionId'] as num?)?.toInt();
    final destinatarioId = (datos['id'] as num?)?.toInt();

    return ListTile(
      leading: CircleAvatar(
        backgroundColor: _colorDeRol(rol, theme),
        child: Text(
          _iniciales(nombre),
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.onPrimaryContainer,
          ),
        ),
      ),
      title: Text(nombre),
      subtitle: Text(email, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _BadgeRol(rol: rol, theme: theme),
          if (conversacionId != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                'Ya hablaron',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
        ],
      ),
      onTap: destinatarioId == null
          ? null
          : () {
              // Si ya existe la conversación se entra a esa: abrir otra
              // dejaría el historial partido en dos hilos.
              if (conversacionId != null) {
                context.pushReplacement('/chat/$conversacionId');
              } else {
                context.pushReplacement('/chat/nuevo?destinatario=$destinatarioId');
              }
            },
    );
  }

  static String _iniciales(String nombre) {
    final partes = nombre.trim().split(RegExp(r'\s+'));
    if (partes.isEmpty || partes.first.isEmpty) return '?';
    if (partes.length == 1) return partes.first.characters.first.toUpperCase();
    return (partes.first.characters.first + partes[1].characters.first)
        .toUpperCase();
  }

  static Color _colorDeRol(String rol, ThemeData theme) => switch (rol) {
    'Admin' => theme.colorScheme.primaryContainer,
    'Cajero' => theme.colorScheme.secondaryContainer,
    'Repartidor' => theme.colorScheme.tertiaryContainer,
    _ => theme.colorScheme.surfaceContainerHighest,
  };
}

/// El rol, con su propio color, para distinguirlo de un vistazo en la lista.
class _BadgeRol extends StatelessWidget {
  const _BadgeRol({required this.rol, required this.theme});

  final String rol;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: _Persona._colorDeRol(rol, theme),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        etiquetaDeRol(rol),
        style: theme.textTheme.labelSmall,
      ),
    );
  }
}

/// Cómo se nombra el rol en pantalla.
String etiquetaDeRol(String rol) => switch (rol) {
  'Admin' => 'Administrador',
  'Cajero' => 'Cajero',
  'Repartidor' => 'Repartidor',
  'Cliente' => 'Cliente',
  _ => rol,
};

class _SinResultados extends StatelessWidget {
  const _SinResultados({required this.buscando});

  final bool buscando;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const SizedBox(height: 100),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                ExcludeSemantics(
                  child: Icon(
                    buscando ? Icons.search_off : Icons.people_outline,
                    size: 52,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  buscando
                      ? 'Nadie coincide con esa búsqueda'
                      : 'No hay personas disponibles',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 6),
                Text(
                  buscando
                      ? 'Probá con otro nombre o quitá el filtro de rol.'
                      : 'Cuando haya alguien a quien escribirle, aparece acá.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
