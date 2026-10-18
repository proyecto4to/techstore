import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';

final promocionesProvider = FutureProvider.autoDispose((ref) async {
  final respuesta = await ref
      .read(apiClientProvider)
      .get<Map<String, Object?>>(
        '/api/v1/promociones?size=100&sort=desde,desc',
      );
  final contenido = respuesta.data?['content'] as List? ?? const [];
  return contenido.cast<Map<String, Object?>>();
});

final marcasProvider = FutureProvider.autoDispose((ref) async {
  final respuesta = await ref
      .read(apiClientProvider)
      .get<List<Object?>>('/api/v1/marcas');
  return (respuesta.data ?? const []).cast<Map<String, Object?>>();
});

/// Promociones: descuentos con fecha que administración enciende y apaga.
///
/// La lista distingue tres estados y no dos, porque es lo que más confunde al
/// mirarla: apagada, encendida pero todavía no empezó, y rigiendo ahora.
class PromotionsPage extends ConsumerWidget {
  const PromotionsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final promociones = ref.watch(promocionesProvider);
    return Scaffold(
      appBar: AppBar(title: const TituloDePantalla('Promociones')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _abrirFormulario(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Nueva'),
      ),
      body: promociones.when(
        loading: () => const AppLoading(label: 'Cargando promociones'),
        error: (error, _) => AppErrorState(
          message: error is AppFailure
              ? error.message
              : 'No pudimos cargar las promociones.',
          onRetry: () => ref.invalidate(promocionesProvider),
        ),
        data: (lista) => lista.isEmpty
            ? const AppEmptyState(
                message:
                    'Todavía no hay promociones.\n'
                    'Creá una para descontar un porcentaje por unos días.',
              )
            : RefreshIndicator(
                onRefresh: () async => ref.invalidate(promocionesProvider),
                child: ListView.builder(
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
                  itemCount: lista.length,
                  itemBuilder: (context, indice) =>
                      _Tarjeta(promocion: lista[indice]),
                ),
              ),
      ),
    );
  }
}

class _Tarjeta extends ConsumerWidget {
  const _Tarjeta({required this.promocion});

  final Map<String, Object?> promocion;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final activa = promocion['activa'] == true;
    final vigente = promocion['vigente'] == true;
    final porcentaje = (promocion['porcentaje'] as num?)?.toDouble() ?? 0;
    final desde = DateTime.tryParse(promocion['desde']?.toString() ?? '');
    final hasta = DateTime.tryParse(promocion['hasta']?.toString() ?? '');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    promocion['nombre']?.toString() ?? 'Promoción',
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                Text(
                  '−${porcentaje == porcentaje.roundToDouble() ? porcentaje.round() : porcentaje}%',
                  style: theme.textTheme.titleLarge?.copyWith(
                    color: theme.colorScheme.primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(_alcance(promocion)),
            Text(
              '${_fecha(desde)} → ${_fecha(hasta)}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                _Estado(activa: activa, vigente: vigente),
                const Spacer(),
                Switch(
                  value: activa,
                  onChanged: (valor) => _cambiarEstado(context, ref, valor),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _alcance(Map<String, Object?> promocion) {
    return switch (promocion['alcance']?.toString()) {
      'PRODUCTO' => 'Solo ${promocion['productoNombre'] ?? 'un producto'}',
      'MARCA' => 'Marca ${promocion['marcaNombre'] ?? ''}'.trim(),
      _ => 'Todo el catálogo',
    };
  }

  Future<void> _cambiarEstado(
    BuildContext context,
    WidgetRef ref,
    bool activa,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(apiClientProvider)
          .put<Map<String, Object?>>(
            '/api/v1/promociones/${promocion['id']}/activa?activa=$activa',
          );
      ref.invalidate(promocionesProvider);
      messenger.showSnackBar(
        SnackBar(
          content: Text(activa ? 'Promoción encendida' : 'Promoción apagada'),
        ),
      );
    } on AppFailure catch (failure) {
      messenger.showSnackBar(SnackBar(content: Text(failure.message)));
    }
  }
}

class _Estado extends StatelessWidget {
  const _Estado({required this.activa, required this.vigente});

  final bool activa;
  final bool vigente;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Apagada, encendida a futuro y rigiendo son tres cosas distintas: una
    // promoción puede estar activa y no descontar nada todavía.
    final (texto, color) = switch ((activa, vigente)) {
      (false, _) => ('Apagada', theme.colorScheme.outline),
      (true, true) => ('Rigiendo ahora', theme.colorScheme.primary),
      (true, false) => (
        'Encendida, fuera de fecha',
        theme.colorScheme.tertiary,
      ),
    };
    return Chip(
      label: Text(texto),
      labelStyle: theme.textTheme.labelMedium?.copyWith(color: color),
      visualDensity: VisualDensity.compact,
    );
  }
}

String _fecha(DateTime? fecha) => fecha == null
    ? '—'
    : DateFormat('d/M/y HH:mm', 'es_PY').format(fecha.toLocal());

Future<void> _abrirFormulario(BuildContext context, WidgetRef ref) async {
  final creada = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (context) => const _Formulario(),
  );
  if (creada == true) ref.invalidate(promocionesProvider);
}

class _Formulario extends ConsumerStatefulWidget {
  const _Formulario();

  @override
  ConsumerState<_Formulario> createState() => _FormularioState();
}

class _FormularioState extends ConsumerState<_Formulario> {
  final _formKey = GlobalKey<FormState>();
  final _nombre = TextEditingController();
  final _porcentaje = TextEditingController(text: '20');
  String _alcance = 'TODOS';
  int? _marcaId;
  int? _productoId;
  String? _productoNombre;
  DateTime _desde = DateTime.now();
  DateTime _hasta = DateTime.now().add(const Duration(days: 7));
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _nombre.dispose();
    _porcentaje.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final marcas = ref.watch(marcasProvider).value ?? const [];
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Nueva promoción',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _nombre,
                decoration: const InputDecoration(
                  labelText: 'Nombre',
                  helperText: 'Cómo la vas a reconocer: «Liquidación de enero»',
                ),
                textCapitalization: TextCapitalization.sentences,
                validator: (valor) => (valor == null || valor.trim().isEmpty)
                    ? 'Poné un nombre'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _porcentaje,
                decoration: const InputDecoration(
                  labelText: 'Descuento (%)',
                  helperText: 'Entre 1 y 90',
                  suffixText: '%',
                ),
                keyboardType: TextInputType.number,
                validator: (valor) {
                  final numero = double.tryParse(
                    (valor ?? '').replaceAll(',', '.'),
                  );
                  if (numero == null) return 'Escribí un número';
                  if (numero <= 0 || numero > 90) {
                    return 'Tiene que ir de 1 a 90';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _alcance,
                decoration: const InputDecoration(labelText: 'Se aplica a'),
                items: const [
                  DropdownMenuItem(
                    value: 'TODOS',
                    child: Text('Todo el catálogo'),
                  ),
                  DropdownMenuItem(value: 'MARCA', child: Text('Una marca')),
                  DropdownMenuItem(
                    value: 'PRODUCTO',
                    child: Text('Un producto'),
                  ),
                ],
                onChanged: (valor) => setState(() {
                  _alcance = valor ?? 'TODOS';
                  if (_alcance != 'MARCA') _marcaId = null;
                  if (_alcance != 'PRODUCTO') {
                    _productoId = null;
                    _productoNombre = null;
                  }
                }),
              ),
              if (_alcance == 'PRODUCTO') ...[
                const SizedBox(height: 12),
                // Los productos se buscan por nombre en vez de listarse: son
                // decenas y una lista desplegable con todos no se maneja.
                _BuscadorDeProducto(
                  elegido: _productoNombre,
                  onElegido: (id, nombre) => setState(() {
                    _productoId = id;
                    _productoNombre = nombre;
                  }),
                ),
              ],
              if (_alcance == 'MARCA') ...[
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  initialValue: _marcaId,
                  decoration: const InputDecoration(labelText: 'Marca'),
                  items: [
                    for (final marca in marcas)
                      DropdownMenuItem(
                        value: (marca['id'] as num).toInt(),
                        child: Text(marca['nombre']?.toString() ?? 'Marca'),
                      ),
                  ],
                  onChanged: (valor) => setState(() => _marcaId = valor),
                  validator: (valor) =>
                      valor == null ? 'Elegí una marca' : null,
                ),
              ],
              const SizedBox(height: 12),
              _SelectorDeFecha(
                etiqueta: 'Empieza',
                valor: _desde,
                onCambio: (fecha) => setState(() => _desde = fecha),
              ),
              _SelectorDeFecha(
                etiqueta: 'Termina',
                valor: _hasta,
                onCambio: (fecha) => setState(() => _hasta = fecha),
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                children: [
                  TextButton(
                    onPressed: _guardando
                        ? null
                        : () => Navigator.of(context).pop(false),
                    child: const Text('Cancelar'),
                  ),
                  const Spacer(),
                  FilledButton(
                    onPressed: _guardando ? null : _guardar,
                    child: Text(_guardando ? 'Guardando…' : 'Crear'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _guardar() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    if (!_hasta.isAfter(_desde)) {
      setState(
        () => _error = 'La promoción tiene que terminar después de empezar.',
      );
      return;
    }
    if (_alcance == 'PRODUCTO' && _productoId == null) {
      setState(() => _error = 'Buscá y elegí el producto.');
      return;
    }
    setState(() {
      _guardando = true;
      _error = null;
    });
    final navigator = Navigator.of(context);
    try {
      await ref
          .read(apiClientProvider)
          .post<Map<String, Object?>>(
            '/api/v1/promociones',
            data: {
              'nombre': _nombre.text.trim(),
              'porcentaje': double.parse(_porcentaje.text.replaceAll(',', '.')),
              'alcance': _alcance,
              if (_alcance == 'MARCA') 'marcaId': _marcaId,
              if (_alcance == 'PRODUCTO') 'productoId': _productoId,
              'desde': _desde.toIso8601String(),
              'hasta': _hasta.toIso8601String(),
              'activa': true,
            },
          );
      navigator.pop(true);
    } on AppFailure catch (failure) {
      setState(() {
        _guardando = false;
        _error = failure.message;
      });
    }
  }
}

/// Busca un producto por nombre y devuelve el que se elija.
///
/// Se busca en vez de listar porque el catálogo tiene decenas de productos y
/// una lista desplegable con todos es imposible de manejar en un teléfono.
class _BuscadorDeProducto extends ConsumerStatefulWidget {
  const _BuscadorDeProducto({required this.elegido, required this.onElegido});

  final String? elegido;
  final void Function(int id, String nombre) onElegido;

  @override
  ConsumerState<_BuscadorDeProducto> createState() =>
      _BuscadorDeProductoState();
}

class _BuscadorDeProductoState extends ConsumerState<_BuscadorDeProducto> {
  final _texto = TextEditingController();
  List<Map<String, Object?>> _resultados = const [];
  bool _buscando = false;

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    final termino = _texto.text.trim();
    if (termino.isEmpty) return;
    setState(() => _buscando = true);
    try {
      final respuesta = await ref
          .read(apiClientProvider)
          .get<Map<String, Object?>>(
            '/api/v1/productos/admin?search=${Uri.encodeQueryComponent(termino)}&size=20',
          );
      final contenido = respuesta.data?['content'] as List? ?? const [];
      if (mounted) {
        setState(() {
          _resultados = contenido.cast<Map<String, Object?>>();
          _buscando = false;
        });
      }
    } on AppFailure {
      if (mounted) setState(() => _buscando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          controller: _texto,
          decoration: InputDecoration(
            labelText: 'Buscar el producto',
            helperText: widget.elegido == null
                ? 'Escribí parte del nombre y tocá buscar'
                : 'Elegido: ${widget.elegido}',
            suffixIcon: IconButton(
              tooltip: 'Buscar',
              onPressed: _buscando ? null : _buscar,
              icon: const Icon(Icons.search),
            ),
          ),
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _buscar(),
        ),
        if (_buscando)
          const Padding(
            padding: EdgeInsets.all(12),
            child: LinearProgressIndicator(),
          ),
        for (final producto in _resultados)
          ListTile(
            dense: true,
            title: Text(producto['nombre']?.toString() ?? ''),
            subtitle: Text(
              (producto['marca'] as Map?)?['nombre']?.toString() ?? '',
            ),
            trailing: const Icon(Icons.check),
            onTap: () {
              widget.onElegido(
                (producto['id'] as num).toInt(),
                producto['nombre']?.toString() ?? '',
              );
              setState(() => _resultados = const []);
            },
          ),
      ],
    );
  }
}

class _SelectorDeFecha extends StatelessWidget {
  const _SelectorDeFecha({
    required this.etiqueta,
    required this.valor,
    required this.onCambio,
  });

  final String etiqueta;
  final DateTime valor;
  final ValueChanged<DateTime> onCambio;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(etiqueta),
      subtitle: Text(_fecha(valor)),
      trailing: const Icon(Icons.event),
      onTap: () async {
        final fecha = await showDatePicker(
          context: context,
          initialDate: valor,
          firstDate: DateTime.now().subtract(const Duration(days: 365)),
          lastDate: DateTime.now().add(const Duration(days: 365 * 3)),
        );
        if (fecha == null || !context.mounted) return;
        final hora = await showTimePicker(
          context: context,
          initialTime: TimeOfDay.fromDateTime(valor),
        );
        onCambio(
          DateTime(
            fecha.year,
            fecha.month,
            fecha.day,
            hora?.hour ?? valor.hour,
            hora?.minute ?? valor.minute,
          ),
        );
      },
    );
  }
}
