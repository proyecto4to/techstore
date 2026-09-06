import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';

/// Dónde centrar el mapa cuando todavía nadie marcó la ubicación.
///
/// Asunción: es donde está la tienda, y evita abrir el mapa en el punto cero
/// del Atlántico, que no le sirve a nadie para orientarse.
const _centroPorDefecto = LatLng(-25.2637, -57.5759);

/// Los tiles vienen de OpenStreetMap, que exige identificar la aplicación.
///
/// Sin este encabezado el servidor puede devolver 403 y el mapa queda en gris
/// sin decir por qué.
const _agente = 'py.com.techstore.app';

final ubicacionEmpresaProvider = FutureProvider.autoDispose((ref) async {
  final respuesta = await ref
      .read(apiClientProvider)
      .get<Map<String, Object?>>('/api/v1/empresa/ubicacion');
  return respuesta.data ?? const <String, Object?>{};
});

/// La ubicación de la empresa: primero se ve, después se edita.
class CompanyLocationPage extends ConsumerWidget {
  const CompanyLocationPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ubicacion = ref.watch(ubicacionEmpresaProvider);
    return Scaffold(
      appBar: AppBar(title: const TituloDePantalla('Ubicación de la empresa')),
      body: ubicacion.when(
        loading: () => const AppLoading(label: 'Cargando la ubicación'),
        error: (error, _) => AppErrorState(
          message: error is AppFailure
              ? error.message
              : 'No pudimos cargar la ubicación.',
          onRetry: () => ref.invalidate(ubicacionEmpresaProvider),
        ),
        data: (datos) => _Vista(datos: datos),
      ),
    );
  }
}

class _Vista extends ConsumerWidget {
  const _Vista({required this.datos});

  final Map<String, Object?> datos;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final definida = datos['definida'] == true;
    final punto = definida
        ? LatLng(
            (datos['latitud'] as num).toDouble(),
            (datos['longitud'] as num).toDouble(),
          )
        : null;
    final referencia = datos['referencia']?.toString();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                height: 220,
                child: punto == null
                    ? _SinUbicacion(theme: theme)
                    : _MapaSoloLectura(punto: punto),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      definida ? 'Ubicación marcada' : 'Sin ubicación',
                      style: theme.textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      definida
                          ? (referencia == null || referencia.isEmpty
                                ? 'Sin referencia escrita'
                                : referencia)
                          : 'Marcala en el mapa para que aparezca en la app.',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    if (punto != null) ...[
                      const SizedBox(height: 12),
                      _Coordenada(
                        etiqueta: 'Latitud',
                        valor: punto.latitude.toStringAsFixed(6),
                        theme: theme,
                      ),
                      _Coordenada(
                        etiqueta: 'Longitud',
                        valor: punto.longitude.toStringAsFixed(6),
                        theme: theme,
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: () => _editar(context, ref, punto, referencia),
          icon: const Icon(Icons.edit_location_alt_outlined),
          label: Text(definida ? 'Editar ubicación' : 'Marcar ubicación'),
        ),
        const SizedBox(height: 12),
        Text(
          'El mapa usa OpenStreetMap, que es de uso libre.',
          textAlign: TextAlign.center,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  Future<void> _editar(
    BuildContext context,
    WidgetRef ref,
    LatLng? actual,
    String? referencia,
  ) async {
    final guardada = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => _EditorDeUbicacion(
          inicial: actual ?? _centroPorDefecto,
          yaMarcada: actual != null,
          referencia: referencia ?? '',
        ),
      ),
    );
    if (guardada == true) ref.invalidate(ubicacionEmpresaProvider);
  }
}

class _Coordenada extends StatelessWidget {
  const _Coordenada({
    required this.etiqueta,
    required this.valor,
    required this.theme,
  });

  final String etiqueta;
  final String valor;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            etiqueta,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          Text(
            valor,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}

class _SinUbicacion extends StatelessWidget {
  const _SinUbicacion({required this.theme});

  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: theme.colorScheme.surfaceContainerHighest,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Icon(
                Icons.location_off_outlined,
                size: 44,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Todavía no marcaste dónde queda',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// El mapa de la vista, sin permitir mover nada.
class _MapaSoloLectura extends StatelessWidget {
  const _MapaSoloLectura({required this.punto});

  final LatLng punto;

  @override
  Widget build(BuildContext context) {
    return FlutterMap(
      options: MapOptions(
        initialCenter: punto,
        initialZoom: 16,
        // Es una vista previa, no un mapa para explorar: si se pudiera mover,
        // parecería que se está editando cuando en realidad no se guarda nada.
        interactionOptions: const InteractionOptions(flags: InteractiveFlag.none),
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: _agente,
        ),
        MarkerLayer(
          markers: [
            Marker(
              point: punto,
              width: 40,
              height: 40,
              child: Icon(
                Icons.location_on,
                size: 40,
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// El mapa interactivo donde se elige el punto.
class _EditorDeUbicacion extends ConsumerStatefulWidget {
  const _EditorDeUbicacion({
    required this.inicial,
    required this.yaMarcada,
    required this.referencia,
  });

  final LatLng inicial;
  final bool yaMarcada;
  final String referencia;

  @override
  ConsumerState<_EditorDeUbicacion> createState() => _EditorDeUbicacionState();
}

class _EditorDeUbicacionState extends ConsumerState<_EditorDeUbicacion> {
  late LatLng _punto = widget.inicial;
  late final TextEditingController _referencia = TextEditingController(
    text: widget.referencia,
  );
  final _mapa = MapController();
  bool _guardando = false;

  @override
  void dispose() {
    _referencia.dispose();
    _mapa.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.yaMarcada ? 'Editar ubicación' : 'Marcar ubicación'),
      ),
      body: Column(
        children: [
          Material(
            color: theme.colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Icon(
                    Icons.touch_app_outlined,
                    color: theme.colorScheme.onSecondaryContainer,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Movete por el mapa y tocá dónde queda la empresa. '
                      'El marcador se mueve a donde toques.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSecondaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapa,
                  options: MapOptions(
                    initialCenter: _punto,
                    initialZoom: 16,
                    minZoom: 3,
                    maxZoom: 19,
                    onTap: (_, punto) => setState(() => _punto = punto),
                  ),
                  children: [
                    TileLayer(
                      urlTemplate:
                          'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: _agente,
                    ),
                    MarkerLayer(
                      markers: [
                        Marker(
                          point: _punto,
                          width: 44,
                          height: 44,
                          // El icono apunta hacia abajo: sin esta alineación,
                          // el marcador queda centrado en el punto y la punta
                          // señala unos metros más al norte de lo elegido.
                          alignment: Alignment.topCenter,
                          child: Icon(
                            Icons.location_on,
                            size: 44,
                            color: theme.colorScheme.error,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                Positioned(
                  right: 12,
                  bottom: 12,
                  child: Column(
                    children: [
                      FloatingActionButton.small(
                        heroTag: 'acercar',
                        tooltip: 'Acercar',
                        onPressed: () =>
                            _mapa.move(_mapa.camera.center, _mapa.camera.zoom + 1),
                        child: const Icon(Icons.add),
                      ),
                      const SizedBox(height: 8),
                      FloatingActionButton.small(
                        heroTag: 'alejar',
                        tooltip: 'Alejar',
                        onPressed: () =>
                            _mapa.move(_mapa.camera.center, _mapa.camera.zoom - 1),
                        child: const Icon(Icons.remove),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'Lat ${_punto.latitude.toStringAsFixed(6)}   '
                    'Lon ${_punto.longitude.toStringAsFixed(6)}',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _referencia,
                    maxLength: 255,
                    enabled: !_guardando,
                    decoration: const InputDecoration(
                      labelText: 'Referencia (opcional)',
                      helperText: 'Por ejemplo: sobre la avenida, frente a la plaza',
                      counterText: '',
                    ),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _guardando ? null : _guardar,
                    icon: _guardando
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.check),
                    label: const Text('Confirmar ubicación'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _guardar() async {
    setState(() => _guardando = true);
    try {
      await ref
          .read(apiClientProvider)
          .put<Map<String, Object?>>(
            '/api/v1/empresa/ubicacion',
            data: {
              'latitud': _punto.latitude,
              'longitud': _punto.longitude,
              'referencia': _referencia.text.trim(),
            },
          );
      if (!mounted) return;
      Navigator.pop(context, true);
    } on AppFailure catch (falla) {
      if (!mounted) return;
      setState(() => _guardando = false);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(falla.message)));
    }
  }
}
