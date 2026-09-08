import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';
import '../application/checkout_providers.dart';
import '../domain/checkout_models.dart';
import 'address_map_page.dart';

/// Las direcciones guardadas de quien compra.
///
/// La ficha se encabeza con el nombre que le puso la persona —«Casa»,
/// «Trabajo»— y no con la calle: entre dos calles parecidas cuesta elegir, y
/// entre «Casa» y «Trabajo» no. La calle va debajo, deducida del punto marcado.
class AddressPage extends ConsumerWidget {
  const AddressPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final direcciones = ref.watch(addressesProvider);
    return Scaffold(
      appBar: AppBar(title: const TituloDePantalla('Mis direcciones')),
      body: direcciones.when(
        loading: () => const AppLoading(label: 'Cargando tus direcciones'),
        error: (error, _) => AppErrorState(
          message: error is AppFailure
              ? error.message
              : 'No pudimos cargar tus direcciones.',
          onRetry: () => ref.invalidate(addressesProvider),
        ),
        data: (items) => items.isEmpty
            ? _SinDirecciones(onAgregar: () => _abrirMapa(context, ref))
            : ListView.builder(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 96),
                itemCount: items.length,
                itemBuilder: (context, indice) => _Ficha(
                  direccion: items[indice],
                  onEditar: () =>
                      _abrirMapa(context, ref, direccion: items[indice]),
                  onEliminar: () => _eliminar(context, ref, items[indice]),
                ),
              ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _abrirMapa(context, ref),
        icon: const Icon(Icons.add_location_alt_outlined),
        label: const Text('Agregar'),
      ),
    );
  }

  Future<void> _abrirMapa(
    BuildContext context,
    WidgetRef ref, {
    DeliveryAddress? direccion,
  }) async {
    final guardada = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => AddressMapPage(direccion: direccion)),
    );
    if (guardada ?? false) ref.invalidate(addressesProvider);
  }

  /// Borra una dirección, preguntando antes.
  ///
  /// Antes se borraba con un toque y sin preguntar, con el botón pegado al de
  /// editar: dos iconos vecinos, uno reversible y el otro no.
  Future<void> _eliminar(
    BuildContext context,
    WidgetRef ref,
    DeliveryAddress direccion,
  ) async {
    final confirmado = await showDialog<bool>(
      context: context,
      builder: (dialogo) => AlertDialog(
        title: const Text('Borrar la dirección'),
        content: Text(
          'Vas a borrar «${direccion.label}». '
          'Los pedidos que ya la usaron no cambian.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogo, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogo, true),
            child: const Text('Borrar'),
          ),
        ],
      ),
    );
    if (confirmado != true || !context.mounted) return;
    final mensajero = ScaffoldMessenger.of(context);
    try {
      await ref.read(checkoutRepositoryProvider).deleteAddress(direccion.id);
      ref.invalidate(addressesProvider);
      mensajero
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text('Se borró «${direccion.label}».')),
        );
    } on AppFailure catch (falla) {
      mensajero
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(falla.message)));
    }
  }
}

class _Ficha extends StatelessWidget {
  const _Ficha({
    required this.direccion,
    required this.onEditar,
    required this.onEliminar,
  });

  final DeliveryAddress direccion;
  final VoidCallback onEditar;
  final VoidCallback onEliminar;

  /// El icono cuenta de qué lugar se trata, cuando el nombre lo dice.
  IconData get _icono {
    final nombre = (direccion.description ?? '').toLowerCase();
    if (nombre.contains('casa') || nombre.contains('hogar')) {
      return Icons.home_outlined;
    }
    if (nombre.contains('trabajo') || nombre.contains('oficina')) {
      return Icons.work_outline;
    }
    return Icons.place_outlined;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onEditar,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ExcludeSemantics(child: Icon(_icono)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            direccion.label,
                            style: theme.textTheme.titleMedium,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (direccion.primary) ...[
                          const SizedBox(width: 8),
                          const _EtiquetaPrincipal(),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      direccion.calleYCiudad,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    if (direccion.reference != null &&
                        direccion.reference!.trim().isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          direccion.reference!.trim(),
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(
                        '${direccion.recipient} · ${direccion.phone}',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              MenuAnchor(
                menuChildren: [
                  MenuItemButton(
                    leadingIcon: const Icon(Icons.edit_location_alt_outlined),
                    onPressed: onEditar,
                    child: const Text('Editar en el mapa'),
                  ),
                  MenuItemButton(
                    leadingIcon: const Icon(Icons.delete_outline),
                    onPressed: onEliminar,
                    child: const Text('Borrar'),
                  ),
                ],
                builder: (context, controlador, _) => IconButton(
                  tooltip: 'Opciones de ${direccion.label}',
                  icon: const Icon(Icons.more_vert),
                  onPressed: () => controlador.isOpen
                      ? controlador.close()
                      : controlador.open(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EtiquetaPrincipal extends StatelessWidget {
  const _EtiquetaPrincipal();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: theme.colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        'Principal',
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onSecondaryContainer,
        ),
      ),
    );
  }
}

class _SinDirecciones extends StatelessWidget {
  const _SinDirecciones({required this.onAgregar});

  final VoidCallback onAgregar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Icon(
                Icons.map_outlined,
                size: 56,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Todavía no guardaste ninguna dirección',
              style: theme.textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'Marcá en el mapa dónde te llevamos el pedido. '
              'La calle la buscamos nosotros.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: onAgregar,
              icon: const Icon(Icons.add_location_alt_outlined),
              label: const Text('Marcar mi dirección'),
            ),
          ],
        ),
      ),
    );
  }
}
