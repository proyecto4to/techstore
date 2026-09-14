import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../security/application/security_providers.dart';

/// La configuración de permisos de un rol, tal como la devuelve el servidor.
final matrizPermisosProvider = FutureProvider.autoDispose
    .family<Map<String, Object?>, String>((ref, rol) async {
      final respuesta = await ref
          .watch(apiClientProvider)
          .get<Map<String, Object?>>('/api/v1/seguridad/roles/$rol/permisos');
      return respuesta.data ?? const {};
    });

final rolesProvider = FutureProvider.autoDispose<List<String>>((ref) async {
  final respuesta = await ref
      .watch(apiClientProvider)
      .get<List<dynamic>>('/api/v1/seguridad/roles');
  return (respuesta.data ?? const []).map((e) => e.toString()).toList();
});

/// Las personas que se pueden configurar, filtradas por lo que se escribió.
final usuariosDeSeguridadProvider = FutureProvider.autoDispose
    .family<List<Map<String, Object?>>, String>(
      (ref, busqueda) =>
          ref.watch(securityRepositoryProvider).usuarios(busqueda: busqueda),
    );

/// Lo que una persona puede hacer de verdad, con el origen de cada permiso.
final permisosDeUsuarioProvider = FutureProvider.autoDispose
    .family<Map<String, Object?>, int>(
      (ref, usuarioId) =>
          ref.watch(securityRepositoryProvider).permisosDelUsuario(usuarioId),
    );

/// Seguridad: qué puede hacer cada rol, qué pantallas ve y qué excepciones
/// tiene cada persona.
///
/// Lo que se marca acá es lo mismo que aplica el servidor, no una copia local:
/// se guarda y se comprueba en cada pedido a la API. Ocultar algo en la app
/// nunca fue una restricción por sí solo.
class SecurityPage extends ConsumerStatefulWidget {
  const SecurityPage({super.key});

  @override
  ConsumerState<SecurityPage> createState() => _SecurityPageState();
}

class _SecurityPageState extends ConsumerState<SecurityPage>
    with SingleTickerProviderStateMixin {
  late final TabController _pestanas = TabController(length: 2, vsync: this);
  String? _rol;
  bool _guardando = false;

  @override
  void dispose() {
    _pestanas.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Seguridad'),
        bottom: TabBar(
          controller: _pestanas,
          tabs: const [
            Tab(text: 'Por rol'),
            Tab(text: 'Por persona'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _pestanas,
        children: [_porRol(), const _PorPersona()],
      ),
    );
  }

  Widget _porRol() {
    final roles = ref.watch(rolesProvider);
    return roles.when(
      loading: () => const AppLoading(label: 'Cargando roles'),
      error: (error, _) => AppErrorState(
        message: error is AppFailure
            ? error.message
            : 'No pudimos cargar los roles.',
        onRetry: () => ref.invalidate(rolesProvider),
      ),
      data: (lista) {
        if (lista.isEmpty) {
          return const Center(child: Text('No hay roles configurados'));
        }
        final seleccionado = _rol ?? lista.first;
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Wrap(
                  spacing: 8,
                  children: [
                    for (final rol in lista)
                      ChoiceChip(
                        label: Text(rol),
                        selected: rol == seleccionado,
                        onSelected: (_) => setState(() => _rol = rol),
                      ),
                  ],
                ),
              ),
            ),
            if (_guardando) const LinearProgressIndicator(),
            Expanded(
              child: _Matriz(rol: seleccionado, onGuardar: _guardar),
            ),
          ],
        );
      },
    );
  }

  Future<void> _guardar(
    String rol,
    Map<String, Object?> cuerpo,
    String ruta,
  ) async {
    setState(() => _guardando = true);
    try {
      await ref
          .read(apiClientProvider)
          .put<void>('/api/v1/seguridad/roles/$rol/$ruta', data: cuerpo);
      ref.invalidate(matrizPermisosProvider(rol));
      // Si el rol que se tocó es el propio, el menú de esta misma sesión
      // cambia: conviene volver a preguntar antes que mostrar lo de antes.
      ref.invalidate(permisosProvider);
    } on AppFailure catch (falla) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(falla.message)));
      }
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }
}

typedef _Guardar = Future<void> Function(
  String rol,
  Map<String, Object?> cuerpo,
  String ruta,
);

class _Matriz extends ConsumerWidget {
  const _Matriz({required this.rol, required this.onGuardar});

  final String rol;
  final _Guardar onGuardar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final matriz = ref.watch(matrizPermisosProvider(rol));
    return matriz.when(
      loading: () => const AppLoading(label: 'Cargando permisos'),
      error: (error, _) => AppErrorState(
        message: error is AppFailure
            ? error.message
            : 'No pudimos cargar los permisos de este rol.',
        onRetry: () => ref.invalidate(matrizPermisosProvider(rol)),
      ),
      data: (datos) {
        final modulos = (datos['modulos'] as List? ?? const [])
            .map((e) => Map<String, Object?>.from(e as Map))
            .toList();
        final pantallas = (datos['pantallas'] as List? ?? const [])
            .map((e) => Map<String, Object?>.from(e as Map))
            .toList();
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          children: [
            Text(
              'Qué puede hacer',
              style: theme.textTheme.titleSmall?.copyWith(
                color: theme.colorScheme.primary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Destildar cierra el acceso también en la API. Tildar no habilita '
              'lo que el sistema ya niega para ese rol.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            for (final modulo in modulos)
              _FilaModulo(
                modulo: modulo,
                onCambiar: (accion, permitido) => onGuardar(rol, {
                  'modulo': modulo['modulo'],
                  'accion': accion,
                  'permitido': permitido,
                }, 'permisos'),
              ),
            if (pantallas.isNotEmpty) ...[
              const Divider(height: 32),
              Text(
                'Qué pantallas ve',
                style: theme.textTheme.titleSmall?.copyWith(
                  color: theme.colorScheme.primary,
                ),
              ),
              const SizedBox(height: 8),
              for (final pantalla in pantallas)
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  title: Text(
                    _nombreDePantalla(pantalla['pantalla']?.toString() ?? ''),
                  ),
                  value: pantalla['permitido'] == true,
                  onChanged: (valor) => onGuardar(rol, {
                    'pantalla': pantalla['pantalla'],
                    'permitido': valor,
                  }, 'pantallas'),
                ),
            ],
          ],
        );
      },
    );
  }
}

class _FilaModulo extends StatelessWidget {
  const _FilaModulo({required this.modulo, required this.onCambiar});

  final Map<String, Object?> modulo;
  final void Function(String accion, bool permitido) onCambiar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final acciones = (modulo['acciones'] as List? ?? const [])
        .map((e) => Map<String, Object?>.from(e as Map))
        .toList();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            modulo['etiqueta']?.toString() ?? '',
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 2),
          Wrap(
            spacing: 6,
            children: [
              for (final accion in acciones)
                FilterChip(
                  label: Text(accion['etiqueta']?.toString() ?? ''),
                  selected: accion['permitido'] == true,
                  onSelected: (valor) =>
                      onCambiar(accion['accion']?.toString() ?? '', valor),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Por persona
// ---------------------------------------------------------------------------

/// Las excepciones de una persona sobre la matriz de su rol.
///
/// Sirve para el caso que un rol nuevo no resuelve: a este cajero, y solo a
/// este, hay que darle exportar; a aquel otro hay que sacarle eliminar. El
/// servidor resuelve la mezcla y devuelve el resultado con su origen, así que
/// la app no calcula nada: muestra lo que le dijeron.
class _PorPersona extends ConsumerStatefulWidget {
  const _PorPersona();

  @override
  ConsumerState<_PorPersona> createState() => _PorPersonaState();
}

class _PorPersonaState extends ConsumerState<_PorPersona> {
  final _buscador = TextEditingController();
  String _busqueda = '';
  int? _elegido;
  String? _nombreElegido;

  @override
  void dispose() {
    _buscador.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_elegido case final usuarioId?) {
      return _PermisosDeLaPersona(
        usuarioId: usuarioId,
        nombre: _nombreElegido ?? '',
        onVolver: () => setState(() {
          _elegido = null;
          _nombreElegido = null;
        }),
      );
    }

    final usuarios = ref.watch(usuariosDeSeguridadProvider(_busqueda));
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: TextField(
            controller: _buscador,
            textInputAction: TextInputAction.search,
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              labelText: 'Buscar por nombre o correo',
            ),
            onSubmitted: (valor) => setState(() => _busqueda = valor),
          ),
        ),
        Expanded(
          child: usuarios.when(
            loading: () => const AppLoading(label: 'Cargando personas'),
            error: (error, _) => AppErrorState(
              message: error is AppFailure
                  ? error.message
                  : 'No pudimos cargar las personas.',
              onRetry: () =>
                  ref.invalidate(usuariosDeSeguridadProvider(_busqueda)),
            ),
            data: (lista) {
              if (lista.isEmpty) {
                return const Center(child: Text('Nadie coincide con eso'));
              }
              return ListView.separated(
                itemCount: lista.length,
                separatorBuilder: (context, index) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final usuario = lista[index];
                  final nombre =
                      '${usuario['nombre'] ?? ''} ${usuario['apellido'] ?? ''}'
                          .trim();
                  final activo = usuario['activo'] == true;
                  return ListTile(
                    title: Text(nombre.isEmpty ? '—' : nombre),
                    subtitle: Text(
                      '${usuario['email'] ?? ''} · ${usuario['rol'] ?? ''}'
                      '${activo ? '' : ' · inactivo'}',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => setState(() {
                      _elegido = (usuario['id'] as num?)?.toInt();
                      _nombreElegido = nombre;
                    }),
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }
}

class _PermisosDeLaPersona extends ConsumerStatefulWidget {
  const _PermisosDeLaPersona({
    required this.usuarioId,
    required this.nombre,
    required this.onVolver,
  });

  final int usuarioId;
  final String nombre;
  final VoidCallback onVolver;

  @override
  ConsumerState<_PermisosDeLaPersona> createState() =>
      _PermisosDeLaPersonaState();
}

class _PermisosDeLaPersonaState extends ConsumerState<_PermisosDeLaPersona> {
  final _motivo = TextEditingController();
  bool _guardando = false;

  @override
  void dispose() {
    _motivo.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final permisos = ref.watch(permisosDeUsuarioProvider(widget.usuarioId));
    return Column(
      children: [
        ListTile(
          leading: BackButton(onPressed: widget.onVolver),
          title: Text(widget.nombre),
          subtitle: Text(
            permisos.value?['rol']?.toString() ?? 'Permisos efectivos',
          ),
        ),
        if (_guardando) const LinearProgressIndicator(),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: TextField(
            controller: _motivo,
            maxLength: 200,
            decoration: const InputDecoration(
              labelText: 'Motivo de la excepción (opcional)',
              helperText: 'Queda guardado y se ve al lado del permiso',
            ),
          ),
        ),
        Expanded(
          child: permisos.when(
            loading: () => const AppLoading(label: 'Cargando permisos'),
            error: (error, _) => AppErrorState(
              message: error is AppFailure
                  ? error.message
                  : 'No pudimos cargar los permisos de esta persona.',
              onRetry: () =>
                  ref.invalidate(permisosDeUsuarioProvider(widget.usuarioId)),
            ),
            data: (datos) {
              final modulos = (datos['modulos'] as List? ?? const [])
                  .map((e) => Map<String, Object?>.from(e as Map))
                  .toList();
              return ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                children: [
                  Text(
                    'Verde: se le concedió. Rojo: se le restringió aunque el '
                    'rol lo permita. El resto lo hereda del rol.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 12),
                  for (final modulo in modulos)
                    _ModuloDeLaPersona(
                      modulo: modulo,
                      onConceder: (accion) => _guardar(modulo, accion, true),
                      onRestringir: (accion) => _guardar(modulo, accion, false),
                      onHeredar: (accion) => _heredar(modulo, accion),
                    ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Future<void> _guardar(
    Map<String, Object?> modulo,
    String accion,
    bool permitido,
  ) => _operar(
    () => ref
        .read(securityRepositoryProvider)
        .guardarPermisoDelUsuario(
          widget.usuarioId,
          modulo: modulo['modulo']?.toString() ?? '',
          accion: accion,
          permitido: permitido,
          motivo: _motivo.text.trim().isEmpty ? null : _motivo.text.trim(),
        ),
  );

  Future<void> _heredar(Map<String, Object?> modulo, String accion) => _operar(
    () => ref
        .read(securityRepositoryProvider)
        .quitarPermisoDelUsuario(
          widget.usuarioId,
          modulo: modulo['modulo']?.toString() ?? '',
          accion: accion,
        ),
  );

  Future<void> _operar(Future<void> Function() accion) async {
    setState(() => _guardando = true);
    try {
      await accion();
      ref.invalidate(permisosDeUsuarioProvider(widget.usuarioId));
      // Puede ser la sesión propia la que cambió: que el menú se entere.
      ref.invalidate(permisosProvider);
    } on AppFailure catch (falla) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(falla.message)));
      }
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }
}

class _ModuloDeLaPersona extends StatelessWidget {
  const _ModuloDeLaPersona({
    required this.modulo,
    required this.onConceder,
    required this.onRestringir,
    required this.onHeredar,
  });

  final Map<String, Object?> modulo;
  final void Function(String accion) onConceder;
  final void Function(String accion) onRestringir;
  final void Function(String accion) onHeredar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final acciones = (modulo['acciones'] as List? ?? const [])
        .map((e) => Map<String, Object?>.from(e as Map))
        .toList();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            modulo['etiqueta']?.toString() ?? '',
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          for (final accion in acciones)
            _AccionDeLaPersona(
              accion: accion,
              onConceder: onConceder,
              onRestringir: onRestringir,
              onHeredar: onHeredar,
            ),
        ],
      ),
    );
  }
}

class _AccionDeLaPersona extends StatelessWidget {
  const _AccionDeLaPersona({
    required this.accion,
    required this.onConceder,
    required this.onRestringir,
    required this.onHeredar,
  });

  final Map<String, Object?> accion;
  final void Function(String accion) onConceder;
  final void Function(String accion) onRestringir;
  final void Function(String accion) onHeredar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final clave = accion['accion']?.toString() ?? '';
    final permitido = accion['permitido'] == true;
    final origen = accion['origen']?.toString() ?? '';
    final esExcepcion = origen == 'CONCEDIDO' || origen == 'RESTRINGIDO';
    final color = switch (origen) {
      'CONCEDIDO' => Colors.green,
      'RESTRINGIDO' => theme.colorScheme.error,
      _ => theme.colorScheme.onSurfaceVariant,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Icon(
            permitido ? Icons.check_circle_outline : Icons.block,
            size: 18,
            color: permitido ? Colors.green : theme.colorScheme.error,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(accion['etiqueta']?.toString() ?? clave),
                Text(
                  accion['motivo'] == null
                      ? (accion['etiquetaOrigen']?.toString() ?? '')
                      : '${accion['etiquetaOrigen']} · ${accion['motivo']}',
                  style: theme.textTheme.bodySmall?.copyWith(color: color),
                ),
              ],
            ),
          ),
          if (!permitido)
            TextButton(
              onPressed: () => onConceder(clave),
              child: const Text('Conceder'),
            ),
          if (permitido)
            TextButton(
              onPressed: () => onRestringir(clave),
              child: const Text('Restringir'),
            ),
          if (esExcepcion)
            TextButton(
              onPressed: () => onHeredar(clave),
              child: const Text('Heredar'),
            ),
        ],
      ),
    );
  }
}

/// Cómo se llama cada pantalla para quien configura, no cómo se llama la ruta.
String _nombreDePantalla(String clave) => switch (clave) {
  'catalogo' => 'Catálogo',
  'carrito' => 'Carrito',
  'pedidos' => 'Pedidos',
  'facturas' => 'Facturas',
  'direcciones' => 'Direcciones',
  'favoritos' => 'Favoritos',
  'notificaciones' => 'Notificaciones',
  'chat' => 'Conversaciones',
  'cuenta' => 'Mi cuenta',
  'caja' => 'Caja',
  'ventas' => 'Venta directa',
  'compras' => 'Compras',
  'operaciones' => 'Operaciones',
  'repartidor' => 'Reparto',
  'administracion' => 'Administración',
  'seguridad' => 'Seguridad',
  _ => clave,
};
