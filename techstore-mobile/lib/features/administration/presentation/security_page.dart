import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';

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

/// Seguridad: qué puede hacer cada rol y qué pantallas ve.
///
/// La matriz es la misma que aplica el servidor, no una copia local: lo que se
/// marca acá se guarda y se comprueba en cada pedido a la API. Ocultar algo en
/// la app nunca fue una restricción por sí solo.
class SecurityPage extends ConsumerStatefulWidget {
  const SecurityPage({super.key});

  @override
  ConsumerState<SecurityPage> createState() => _SecurityPageState();
}

class _SecurityPageState extends ConsumerState<SecurityPage> {
  String? _rol;
  bool _guardando = false;

  @override
  Widget build(BuildContext context) {
    final roles = ref.watch(rolesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Seguridad')),
      body: roles.when(
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
      ),
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
