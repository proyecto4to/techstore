import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import 'detail_fields.dart';

/// Qué se le pidió a la auditoría.
@immutable
class FiltroAuditoria {
  const FiltroAuditoria({this.entidad, this.accion, this.rol, this.desde, this.hasta});

  final String? entidad;
  final String? accion;
  final String? rol;
  final DateTime? desde;
  final DateTime? hasta;

  bool get vacio =>
      entidad == null &&
      accion == null &&
      rol == null &&
      desde == null &&
      hasta == null;

  int get cuantos => [entidad, accion, rol, desde, hasta].where((v) => v != null).length;

  FiltroAuditoria copiar({
    Object? entidad = _sinCambio,
    Object? accion = _sinCambio,
    Object? rol = _sinCambio,
    Object? desde = _sinCambio,
    Object? hasta = _sinCambio,
  }) => FiltroAuditoria(
    entidad: identical(entidad, _sinCambio) ? this.entidad : entidad as String?,
    accion: identical(accion, _sinCambio) ? this.accion : accion as String?,
    rol: identical(rol, _sinCambio) ? this.rol : rol as String?,
    desde: identical(desde, _sinCambio) ? this.desde : desde as DateTime?,
    hasta: identical(hasta, _sinCambio) ? this.hasta : hasta as DateTime?,
  );

  /// Centinela para distinguir «no lo toques» de «ponelo en nulo».
  static const _sinCambio = Object();

  Map<String, Object?> get parametros => {
    'page': 0,
    'size': 50,
    if (entidad != null) 'entidad': entidad,
    if (accion != null) 'accion': accion,
    if (rol != null) 'rol': rol,
    if (desde != null) 'desde': DateFormat('yyyy-MM-dd').format(desde!),
    if (hasta != null) 'hasta': DateFormat('yyyy-MM-dd').format(hasta!),
  };

  @override
  bool operator ==(Object other) =>
      other is FiltroAuditoria &&
      other.entidad == entidad &&
      other.accion == accion &&
      other.rol == rol &&
      other.desde == desde &&
      other.hasta == hasta;

  @override
  int get hashCode => Object.hash(entidad, accion, rol, desde, hasta);
}

final auditoriaProvider = FutureProvider.autoDispose
    .family<List<Map<String, Object?>>, FiltroAuditoria>((ref, filtro) async {
      final respuesta = await ref
          .read(apiClientProvider)
          .get<Map<String, Object?>>(
            '/api/v1/auditoria',
            queryParameters: filtro.parametros,
          );
      final contenido = respuesta.data?['content'];
      if (contenido is! List) return const [];
      return contenido
          .map((e) => Map<String, Object?>.from(e as Map))
          .toList(growable: false);
    });

/// Las entidades que realmente tienen registros, para no ofrecer filtros vacíos.
final entidadesAuditadasProvider = FutureProvider.autoDispose((ref) async {
  final respuesta = await ref
      .read(apiClientProvider)
      .get<List<dynamic>>('/api/v1/auditoria/entidades');
  return (respuesta.data ?? const []).map((e) => e.toString()).toList();
});

const _acciones = ['Crear', 'Editar', 'Eliminar', 'Anular', 'Emitir'];
const _roles = ['Admin', 'Cajero', 'Repartidor', 'Cliente'];

/// Quién hizo qué, cuándo, y qué cambió exactamente.
class AuditPage extends ConsumerStatefulWidget {
  const AuditPage({super.key});

  @override
  ConsumerState<AuditPage> createState() => _AuditPageState();
}

class _AuditPageState extends ConsumerState<AuditPage> {
  FiltroAuditoria _filtro = const FiltroAuditoria();

  @override
  Widget build(BuildContext context) {
    final registros = ref.watch(auditoriaProvider(_filtro));
    return Scaffold(
      appBar: AppBar(
        title: const Text('Auditoría'),
        actions: [
          IconButton(
            tooltip: 'Filtros',
            onPressed: _abrirFiltros,
            icon: Badge(
              isLabelVisible: !_filtro.vacio,
              label: Text('${_filtro.cuantos}'),
              child: const Icon(Icons.filter_list),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          if (!_filtro.vacio) _ResumenDeFiltros(filtro: _filtro, onQuitar: _quitar),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => ref.refresh(auditoriaProvider(_filtro).future),
              child: registros.when(
                loading: () => const AppLoading(label: 'Cargando la auditoría'),
                error: (error, _) => AppErrorState(
                  message: error is AppFailure
                      ? error.message
                      : 'No pudimos cargar la auditoría.',
                  onRetry: () => ref.invalidate(auditoriaProvider(_filtro)),
                ),
                data: (items) => items.isEmpty
                    ? _SinRegistros(filtrando: !_filtro.vacio)
                    : ListView.separated(
                        itemCount: items.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (context, index) =>
                            _Registro(datos: items[index]),
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _quitar(String cual) {
    setState(() {
      _filtro = switch (cual) {
        'entidad' => _filtro.copiar(entidad: null),
        'accion' => _filtro.copiar(accion: null),
        'rol' => _filtro.copiar(rol: null),
        'desde' => _filtro.copiar(desde: null),
        'hasta' => _filtro.copiar(hasta: null),
        _ => _filtro,
      };
    });
  }

  Future<void> _abrirFiltros() async {
    final elegido = await showModalBottomSheet<FiltroAuditoria>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _HojaDeFiltros(inicial: _filtro),
    );
    if (elegido != null) setState(() => _filtro = elegido);
  }
}

class _ResumenDeFiltros extends StatelessWidget {
  const _ResumenDeFiltros({required this.filtro, required this.onQuitar});

  final FiltroAuditoria filtro;
  final void Function(String) onQuitar;

  @override
  Widget build(BuildContext context) {
    final formato = DateFormat('d MMM y', 'es_PY');
    return SizedBox(
      height: 52,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        children: [
          if (filtro.entidad case final valor?)
            _Quitable(texto: humanizarCodigo(valor), onQuitar: () => onQuitar('entidad')),
          if (filtro.accion case final valor?)
            _Quitable(texto: valor, onQuitar: () => onQuitar('accion')),
          if (filtro.rol case final valor?)
            _Quitable(texto: valor, onQuitar: () => onQuitar('rol')),
          if (filtro.desde case final valor?)
            _Quitable(
              texto: 'Desde ${formato.format(valor)}',
              onQuitar: () => onQuitar('desde'),
            ),
          if (filtro.hasta case final valor?)
            _Quitable(
              texto: 'Hasta ${formato.format(valor)}',
              onQuitar: () => onQuitar('hasta'),
            ),
        ],
      ),
    );
  }
}

class _Quitable extends StatelessWidget {
  const _Quitable({required this.texto, required this.onQuitar});

  final String texto;
  final VoidCallback onQuitar;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: InputChip(label: Text(texto), onDeleted: onQuitar),
    );
  }
}

class _Registro extends StatelessWidget {
  const _Registro({required this.datos});

  final Map<String, Object?> datos;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final accion = datos['accion']?.toString() ?? '';
    final entidad = datos['entidad']?.toString() ?? '';
    final entidadId = (datos['entidadId'] as num?)?.toInt();
    final rol = datos['usuarioRol']?.toString();
    final fecha = DateTime.tryParse(datos['fecha']?.toString() ?? '')?.toLocal();

    return ListTile(
      isThreeLine: true,
      leading: CircleAvatar(
        backgroundColor: _colorDeAccion(accion, theme),
        child: Icon(_iconoDeAccion(accion), size: 20),
      ),
      title: Text(
        '$accion · ${humanizarCodigo(entidad)}'
        '${entidadId == null ? '' : ' N.º $entidadId'}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            datos['detalle']?.toString() ?? 'Sin detalle',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 2),
          Text(
            [
              datos['usuarioEmail']?.toString() ?? 'sistema',
              if (rol != null && rol.isNotEmpty) rol,
              if (fecha != null)
                DateFormat('d MMM y · HH:mm', 'es_PY').format(fecha),
            ].join(' · '),
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
      onTap: () => showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (_) => _DetalleDelRegistro(datos: datos),
      ),
    );
  }

  static IconData _iconoDeAccion(String accion) => switch (accion) {
    'Crear' => Icons.add,
    'Editar' => Icons.edit_outlined,
    'Eliminar' => Icons.delete_outline,
    'Anular' => Icons.block,
    'Emitir' => Icons.receipt_long_outlined,
    _ => Icons.history,
  };

  static Color _colorDeAccion(String accion, ThemeData theme) => switch (accion) {
    'Crear' => theme.colorScheme.primaryContainer,
    'Eliminar' || 'Anular' => theme.colorScheme.errorContainer,
    _ => theme.colorScheme.secondaryContainer,
  };
}

/// El detalle: qué pasó, quién lo hizo y qué campos cambiaron.
///
/// Los cambios se muestran como «antes → después» en una tabla, no como el
/// JSON en crudo que guardaba la base: para entender qué se tocó no debería
/// hacer falta leer llaves y comillas.
class _DetalleDelRegistro extends StatelessWidget {
  const _DetalleDelRegistro({required this.datos});

  final Map<String, Object?> datos;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fecha = DateTime.tryParse(datos['fecha']?.toString() ?? '')?.toLocal();
    final cambios = datos['cambios'];
    final rol = datos['usuarioRol']?.toString();

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.7,
      maxChildSize: 0.95,
      builder: (context, scroll) => ListView(
        controller: scroll,
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
        children: [
          Text(
            '${datos['accion']} · '
            '${humanizarCodigo(datos['entidad']?.toString() ?? '')}',
            style: theme.textTheme.headlineSmall,
          ),
          const SizedBox(height: 4),
          Text(
            datos['detalle']?.toString() ?? 'Sin detalle',
            style: theme.textTheme.bodyLarge?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 20),
          _Dato(
            etiqueta: 'Quién',
            valor: datos['usuarioEmail']?.toString() ?? 'sistema',
            theme: theme,
          ),
          if (rol != null && rol.isNotEmpty)
            _Dato(etiqueta: 'Rol', valor: rol, theme: theme),
          if (datos['entidadId'] != null)
            _Dato(
              etiqueta: 'Registro afectado',
              valor: 'N.º ${datos['entidadId']}',
              theme: theme,
            ),
          if (fecha != null) ...[
            _Dato(
              etiqueta: 'Fecha',
              valor: DateFormat('EEEE d \'de\' MMMM \'de\' y', 'es_PY').format(fecha),
              theme: theme,
            ),
            _Dato(
              etiqueta: 'Hora',
              valor: DateFormat('HH:mm:ss', 'es_PY').format(fecha),
              theme: theme,
            ),
          ],
          if (cambios is Map && cambios.isNotEmpty) ...[
            const SizedBox(height: 24),
            Text('Qué cambió', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            for (final entrada in cambios.entries)
              _Cambio(
                campo: entrada.key.toString(),
                valor: entrada.value,
                theme: theme,
              ),
          ],
        ],
      ),
    );
  }
}

class _Dato extends StatelessWidget {
  const _Dato({
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
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130,
            child: Text(
              etiqueta,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(child: Text(valor, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}

/// Un campo que cambió, con su valor anterior y el nuevo.
class _Cambio extends StatelessWidget {
  const _Cambio({
    required this.campo,
    required this.valor,
    required this.theme,
  });

  final String campo;
  final Object? valor;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    // La auditoría guarda cada cambio como {antes, despues}. Si viniera de otra
    // forma se muestra tal cual en lugar de quedar en blanco.
    final mapa = valor is Map ? Map<String, Object?>.from(valor as Map) : null;
    final antes = mapa?['antes'];
    final despues = mapa?['despues'];

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(etiquetaDeCampo(campo), style: theme.textTheme.labelLarge),
          const SizedBox(height: 6),
          if (mapa == null)
            Text(formatearValor(campo, valor), style: theme.textTheme.bodyMedium)
          else
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    antes == null || antes.toString().isEmpty
                        ? 'Sin valor'
                        : formatearValor(campo, antes),
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      decoration: TextDecoration.lineThrough,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Icon(
                    Icons.arrow_forward,
                    size: 16,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                Expanded(
                  child: Text(
                    despues == null || despues.toString().isEmpty
                        ? 'Sin valor'
                        : formatearValor(campo, despues),
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _HojaDeFiltros extends ConsumerStatefulWidget {
  const _HojaDeFiltros({required this.inicial});

  final FiltroAuditoria inicial;

  @override
  ConsumerState<_HojaDeFiltros> createState() => _HojaDeFiltrosState();
}

class _HojaDeFiltrosState extends ConsumerState<_HojaDeFiltros> {
  late FiltroAuditoria _filtro = widget.inicial;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final entidades = ref.watch(entidadesAuditadasProvider);
    final formato = DateFormat('d MMM y', 'es_PY');

    return Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        0,
        20,
        MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Filtrar la auditoría', style: theme.textTheme.titleLarge),
            const SizedBox(height: 20),

            Text('Módulo', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            entidades.when(
              loading: () => const LinearProgressIndicator(),
              // Sin la lista igual se puede filtrar por lo demás: no tiene
              // sentido bloquear toda la hoja por un desplegable.
              error: (_, _) => const SizedBox.shrink(),
              data: (lista) => Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final entidad in lista)
                    FilterChip(
                      label: Text(humanizarCodigo(entidad)),
                      selected: _filtro.entidad == entidad,
                      onSelected: (elegido) => setState(() {
                        _filtro = _filtro.copiar(
                          entidad: elegido ? entidad : null,
                        );
                      }),
                    ),
                ],
              ),
            ),

            const SizedBox(height: 20),
            Text('Acción', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final accion in _acciones)
                  FilterChip(
                    label: Text(accion),
                    selected: _filtro.accion == accion,
                    onSelected: (elegido) => setState(() {
                      _filtro = _filtro.copiar(accion: elegido ? accion : null);
                    }),
                  ),
              ],
            ),

            const SizedBox(height: 20),
            Text('Rol de quien lo hizo', style: theme.textTheme.labelLarge),
            const SizedBox(height: 4),
            Text(
              'El rol que tenía al momento de la acción, no el de hoy.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final rol in _roles)
                  FilterChip(
                    label: Text(rol),
                    selected: _filtro.rol == rol,
                    onSelected: (elegido) => setState(() {
                      _filtro = _filtro.copiar(rol: elegido ? rol : null);
                    }),
                  ),
              ],
            ),

            const SizedBox(height: 20),
            Text('Fechas', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _elegirFecha(desde: true),
                    icon: const Icon(Icons.calendar_today, size: 18),
                    label: Text(
                      _filtro.desde == null
                          ? 'Desde'
                          : formato.format(_filtro.desde!),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _elegirFecha(desde: false),
                    icon: const Icon(Icons.event, size: 18),
                    label: Text(
                      _filtro.hasta == null
                          ? 'Hasta'
                          : formato.format(_filtro.hasta!),
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 28),
            Row(
              children: [
                Expanded(
                  child: TextButton(
                    onPressed: () => Navigator.pop(
                      context,
                      const FiltroAuditoria(),
                    ),
                    child: const Text('Limpiar todo'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: () => Navigator.pop(context, _filtro),
                    child: const Text('Aplicar'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _elegirFecha({required bool desde}) async {
    final hoy = DateTime.now();
    final elegida = await showDatePicker(
      context: context,
      initialDate: (desde ? _filtro.desde : _filtro.hasta) ?? hoy,
      firstDate: DateTime(hoy.year - 5),
      lastDate: hoy,
      locale: const Locale('es', 'PY'),
    );
    if (elegida == null) return;
    setState(() {
      _filtro = desde
          ? _filtro.copiar(desde: elegida)
          : _filtro.copiar(hasta: elegida);
    });
  }
}

class _SinRegistros extends StatelessWidget {
  const _SinRegistros({required this.filtrando});

  final bool filtrando;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const SizedBox(height: 120),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                ExcludeSemantics(
                  child: Icon(
                    filtrando ? Icons.search_off : Icons.history_toggle_off,
                    size: 52,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  filtrando
                      ? 'Ningún registro coincide'
                      : 'Todavía no hay nada auditado',
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 6),
                Text(
                  filtrando
                      ? 'Probá quitando algún filtro o ampliando las fechas.'
                      : 'Las acciones sobre los datos quedan registradas acá.',
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
