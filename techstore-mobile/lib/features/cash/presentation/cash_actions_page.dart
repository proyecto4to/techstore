import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/format/money_input.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/money_field.dart';
import '../domain/denominaciones.dart';

/// El turno de caja abierto, o la ausencia de turno.
final cajaAbiertaProvider = FutureProvider.autoDispose<Map<String, Object?>>((
  ref,
) async {
  final respuesta = await ref
      .watch(apiClientProvider)
      .get<Map<String, Object?>>('/api/v1/caja/apertura');
  return respuesta.data ?? const {'abierta': false};
});

/// Operar caja, mostrando solo lo que se puede hacer en este momento.
///
/// Antes las tres acciones estaban siempre a la vista: con la caja cerrada se
/// ofrecía «Cerrar caja» y «Registrar movimiento», que solo podían terminar en
/// un error del servidor. El estado del turno decide qué aparece.
class CashActionsPage extends ConsumerWidget {
  const CashActionsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apertura = ref.watch(cajaAbiertaProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Caja')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(cajaAbiertaProvider.future),
        child: apertura.when(
          loading: () => const AppLoading(label: 'Consultando la caja'),
          error: (error, _) => AppErrorState(
            message: error is AppFailure
                ? error.message
                : 'No pudimos consultar el estado de la caja.',
            onRetry: () => ref.invalidate(cajaAbiertaProvider),
          ),
          data: (turno) => turno['abierta'] == true
              ? _CajaAbierta(turno: turno)
              : const _CajaCerrada(),
        ),
      ),
    );
  }
}

/// Con la caja cerrada hay una sola cosa que hacer, y ocupa la pantalla.
class _CajaCerrada extends ConsumerStatefulWidget {
  const _CajaCerrada();

  @override
  ConsumerState<_CajaCerrada> createState() => _CajaCerradaState();
}

class _CajaCerradaState extends ConsumerState<_CajaCerrada> {
  bool _enviando = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        const SizedBox(height: 48),
        ExcludeSemantics(
          child: Icon(
            Icons.lock_outline,
            size: 72,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          'La caja está cerrada',
          textAlign: TextAlign.center,
          style: theme.textTheme.headlineSmall,
        ),
        const SizedBox(height: 8),
        Text(
          'Abrila con el efectivo con el que empezás el turno. '
          'Hasta entonces no se pueden registrar movimientos.',
          textAlign: TextAlign.center,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 32),
        FilledButton.icon(
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(56),
            textStyle: theme.textTheme.titleMedium,
          ),
          onPressed: _enviando ? null : _abrir,
          icon: const Icon(Icons.lock_open_outlined),
          label: const Text('Abrir caja'),
        ),
        if (_enviando)
          const Padding(
            padding: EdgeInsets.only(top: 24),
            child: Center(child: CircularProgressIndicator()),
          ),
      ],
    );
  }

  Future<void> _abrir() async {
    final datos = await showDialog<({double saldo, String notas})>(
      context: context,
      builder: (_) => const _AbrirCajaDialog(),
    );
    if (datos == null || !mounted) return;
    setState(() => _enviando = true);
    try {
      await ref
          .read(apiClientProvider)
          .post<void>(
            '/api/v1/caja/apertura',
            data: {'saldoInicial': datos.saldo, 'notas': datos.notas},
          );
      await ref.read(hapticsServiceProvider).play(HapticCue.success);
      ref.invalidate(cajaAbiertaProvider);
      _avisar('Caja abierta. Ya podés registrar movimientos.');
    } on AppFailure catch (falla) {
      await ref.read(hapticsServiceProvider).play(HapticCue.error);
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

/// Con la caja abierta aparecen las operaciones válidas de ese estado.
class _CajaAbierta extends ConsumerStatefulWidget {
  const _CajaAbierta({required this.turno});

  final Map<String, Object?> turno;

  @override
  ConsumerState<_CajaAbierta> createState() => _CajaAbiertaState();
}

class _CajaAbiertaState extends ConsumerState<_CajaAbierta> {
  bool _enviando = false;

  String get _moneda => widget.turno['moneda']?.toString() ?? 'PYG';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final desde = DateTime.tryParse(
      widget.turno['fechaApertura']?.toString() ?? '',
    );
    final saldo = (widget.turno['saldoInicial'] as num?)?.toDouble();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: theme.colorScheme.secondaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.lock_open_outlined,
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      'Caja abierta',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onSecondaryContainer,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                if (desde != null)
                  Text(
                    'Turno iniciado el '
                    '${DateFormat.yMMMd('es_PY').add_Hm().format(desde.toLocal())}',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                  ),
                if (saldo != null)
                  Text(
                    'Empezaste con ${formatMoney(saldo, _moneda)}',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _AccionCaja(
          icon: Icons.swap_vert,
          title: 'Registrar movimiento',
          subtitle: 'Un ingreso o un egreso de la caja.',
          onTap: _enviando ? null : _movimiento,
        ),
        _AccionCaja(
          icon: Icons.local_shipping_outlined,
          title: 'Pago a proveedores',
          subtitle: 'Sale de la caja y queda registrado a tu nombre.',
          onTap: _enviando ? null : _pagoProveedor,
        ),
        _AccionCaja(
          icon: Icons.receipt_long_outlined,
          title: 'Pedidos',
          subtitle: 'Los pedidos para atender en el mostrador.',
          onTap: _enviando ? null : () => context.push('/resources/all-orders'),
        ),
        const Divider(height: 32),
        _AccionCaja(
          icon: Icons.lock_outline,
          title: 'Cerrar caja',
          subtitle: 'Contá el efectivo por denominación y cerrá el turno.',
          destacada: true,
          onTap: _enviando ? null : _cerrar,
        ),
        if (_enviando)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          ),
      ],
    );
  }

  Future<void> _movimiento() async {
    final datos = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => _MovimientoDialog(moneda: _moneda),
    );
    if (datos == null) return;
    await _enviar('/api/v1/caja/movimientos', datos, 'Movimiento registrado.');
  }

  Future<void> _pagoProveedor() async {
    final datos = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => _PagoProveedorDialog(moneda: _moneda),
    );
    if (datos == null) return;
    await _enviar(
      '/api/v1/caja/movimientos',
      datos,
      'Pago registrado y descontado de la caja.',
    );
  }

  Future<void> _cerrar() async {
    // El cierre pide la biometría igual que antes: es la operación que fija el
    // arqueo del turno y no puede quedar a mano de quien agarre el equipo.
    final total = await showDialog<int>(
      context: context,
      builder: (_) => _CierrePorDenominacionesDialog(moneda: _moneda),
    );
    if (total == null || !mounted) return;
    final confirmado = await confirmarOperacion(
      context,
      ref,
      reason: 'Confirmá el cierre de caja',
      titulo: 'Confirmar cierre de caja',
    );
    if (!confirmado) {
      await ref.read(hapticsServiceProvider).play(HapticCue.error);
      _avisar('Cierre cancelado: no se validó la identidad.');
      return;
    }
    await _enviar('/api/v1/caja/cierres', {
      'efectivoContado': total,
    }, 'Caja cerrada. Registramos lo que declaraste.');
  }

  Future<void> _enviar(
    String ruta,
    Map<String, Object?> datos,
    String exito,
  ) async {
    setState(() => _enviando = true);
    try {
      await ref.read(apiClientProvider).post<void>(ruta, data: datos);
      await ref.read(hapticsServiceProvider).play(HapticCue.success);
      ref.invalidate(cajaAbiertaProvider);
      _avisar(exito);
    } on AppFailure catch (falla) {
      await ref.read(hapticsServiceProvider).play(HapticCue.error);
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

class _AccionCaja extends StatelessWidget {
  const _AccionCaja({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.onTap,
    this.destacada = false,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;
  final bool destacada;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: ListTile(
        leading: Icon(
          icon,
          color: destacada ? theme.colorScheme.primary : null,
        ),
        title: Text(
          title,
          style: destacada
              ? theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.primary,
                )
              : null,
        ),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _AbrirCajaDialog extends StatefulWidget {
  const _AbrirCajaDialog();

  @override
  State<_AbrirCajaDialog> createState() => _AbrirCajaDialogState();
}

class _AbrirCajaDialogState extends State<_AbrirCajaDialog> {
  final _form = GlobalKey<FormState>();
  final _saldo = TextEditingController();
  final _notas = TextEditingController();

  @override
  void dispose() {
    _saldo.dispose();
    _notas.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Abrir caja'),
    content: Form(
      key: _form,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            MoneyField(
              controller: _saldo,
              label: 'Efectivo con el que empezás',
              autofocus: true,
              permitirCero: true,
              helperText: 'Si arrancás sin fondo, dejá 0.',
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _notas,
              maxLength: 1000,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Notas (opcional)'),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: () {
          if (!_form.currentState!.validate()) return;
          Navigator.pop(context, (
            saldo: parsearMonto(_saldo.text) ?? 0,
            notas: _notas.text.trim(),
          ));
        },
        child: const Text('Abrir caja'),
      ),
    ],
  );
}

/// Un movimiento manual de caja.
///
/// El tipo y el medio eran campos de texto libre donde había que escribir
/// exactamente «Ingreso» o «Transferencia»: cualquier otra cosa la rechazaba el
/// servidor después de enviar. Ahora se eligen.
class _MovimientoDialog extends StatefulWidget {
  const _MovimientoDialog({required this.moneda});

  final String moneda;

  @override
  State<_MovimientoDialog> createState() => _MovimientoDialogState();
}

class _MovimientoDialogState extends State<_MovimientoDialog> {
  final _form = GlobalKey<FormState>();
  final _monto = TextEditingController();
  final _concepto = TextEditingController();
  final _referencia = TextEditingController();
  String _tipo = 'Ingreso';
  String _medio = 'Efectivo';

  @override
  void dispose() {
    _monto.dispose();
    _concepto.dispose();
    _referencia.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Movimiento de caja'),
    content: Form(
      key: _form,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(
                  value: 'Ingreso',
                  label: Text('Entra'),
                  icon: Icon(Icons.south_west),
                ),
                ButtonSegment(
                  value: 'Egreso',
                  label: Text('Sale'),
                  icon: Icon(Icons.north_east),
                ),
              ],
              selected: {_tipo},
              onSelectionChanged: (valor) =>
                  setState(() => _tipo = valor.first),
            ),
            const SizedBox(height: 12),
            MoneyField(
              controller: _monto,
              label: 'Monto',
              currency: widget.moneda,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _medio,
              decoration: const InputDecoration(labelText: 'Medio'),
              items: const [
                DropdownMenuItem(value: 'Efectivo', child: Text('Efectivo')),
                DropdownMenuItem(
                  value: 'Transferencia',
                  child: Text('Transferencia'),
                ),
              ],
              onChanged: (valor) =>
                  setState(() => _medio = valor ?? 'Efectivo'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _concepto,
              maxLength: 255,
              decoration: const InputDecoration(labelText: 'Concepto'),
              validator: (valor) => valor == null || valor.trim().isEmpty
                  ? 'Escribí de qué se trata'
                  : null,
            ),
            TextFormField(
              controller: _referencia,
              maxLength: 50,
              decoration: const InputDecoration(
                labelText: 'Referencia (opcional)',
              ),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: () {
          if (!_form.currentState!.validate()) return;
          Navigator.pop(context, <String, Object?>{
            'tipo': _tipo,
            'monto': parsearMonto(_monto.text),
            'concepto': _concepto.text.trim(),
            'referencia': _referencia.text.trim().isEmpty
                ? null
                : _referencia.text.trim(),
            'medio': _medio,
          });
        },
        child: const Text('Registrar'),
      ),
    ],
  );
}

/// Pago a un proveedor: siempre es plata que sale de la caja.
class _PagoProveedorDialog extends ConsumerStatefulWidget {
  const _PagoProveedorDialog({required this.moneda});

  final String moneda;

  @override
  ConsumerState<_PagoProveedorDialog> createState() =>
      _PagoProveedorDialogState();
}

class _PagoProveedorDialogState extends ConsumerState<_PagoProveedorDialog> {
  final _form = GlobalKey<FormState>();
  final _monto = TextEditingController();
  final _referencia = TextEditingController();
  List<Map<String, Object?>> _proveedores = const [];
  int? _proveedorId;
  String? _error;
  bool _cargando = true;

  @override
  void initState() {
    super.initState();
    _cargarProveedores();
  }

  @override
  void dispose() {
    _monto.dispose();
    _referencia.dispose();
    super.dispose();
  }

  Future<void> _cargarProveedores() async {
    try {
      final respuesta = await ref
          .read(apiClientProvider)
          .get<Object?>('/api/v1/proveedores/activos');
      final crudo = respuesta.data;
      final lista = crudo is List
          ? crudo
          : crudo is Map && crudo['content'] is List
          ? crudo['content']! as List
          : const [];
      if (!mounted) return;
      setState(() {
        _proveedores = lista
            .map((e) => Map<String, Object?>.from(e as Map))
            .toList(growable: false);
        _cargando = false;
      });
    } on AppFailure catch (falla) {
      if (!mounted) return;
      setState(() {
        _error = falla.message;
        _cargando = false;
      });
    }
  }

  String _nombre(Map<String, Object?> proveedor) =>
      proveedor['razonSocial']?.toString() ??
      proveedor['nombre']?.toString() ??
      'Proveedor';

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Pago a proveedor'),
    content: SizedBox(
      width: double.maxFinite,
      child: _cargando
          ? const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            )
          : _error != null
          ? Text(_error!)
          : _proveedores.isEmpty
          ? const Text(
              'No hay proveedores activos cargados. '
              'Se dan de alta desde Administración.',
            )
          : Form(
              key: _form,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DropdownButtonFormField<int>(
                      initialValue: _proveedorId,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Proveedor'),
                      items: [
                        for (final proveedor in _proveedores)
                          DropdownMenuItem(
                            value: (proveedor['id'] as num).toInt(),
                            child: Text(
                              _nombre(proveedor),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      validator: (valor) =>
                          valor == null ? 'Elegí a quién le pagás' : null,
                      onChanged: (valor) =>
                          setState(() => _proveedorId = valor),
                    ),
                    const SizedBox(height: 12),
                    MoneyField(
                      controller: _monto,
                      label: 'Monto a pagar',
                      currency: widget.moneda,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _referencia,
                      maxLength: 50,
                      decoration: const InputDecoration(
                        labelText: 'Comprobante (opcional)',
                      ),
                    ),
                  ],
                ),
              ),
            ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      if (!_cargando && _error == null && _proveedores.isNotEmpty)
        FilledButton(
          onPressed: () {
            if (!_form.currentState!.validate()) return;
            final proveedor = _proveedores.firstWhere(
              (p) => (p['id'] as num).toInt() == _proveedorId,
            );
            Navigator.pop(context, <String, Object?>{
              // Un pago a proveedor es un egreso en efectivo de la caja; el
              // servidor le pone el usuario y la fecha.
              'tipo': 'Egreso',
              'monto': parsearMonto(_monto.text),
              'concepto': 'Pago a proveedor: ${_nombre(proveedor)}',
              'referencia': _referencia.text.trim().isEmpty
                  ? null
                  : _referencia.text.trim(),
              'medio': 'Efectivo',
            });
          },
          child: const Text('Registrar pago'),
        ),
    ],
  );
}

/// Cierre contando billetes y monedas.
///
/// Deliberadamente no muestra el efectivo esperado ni ninguna diferencia: lo
/// que se pide es una declaración de lo que hay en el cajón. Si el cajero viera
/// el número que tiene que dar, contar dejaría de ser contar. El servidor
/// tampoco se lo envía.
class _CierrePorDenominacionesDialog extends StatefulWidget {
  const _CierrePorDenominacionesDialog({required this.moneda});

  final String moneda;

  @override
  State<_CierrePorDenominacionesDialog> createState() =>
      _CierrePorDenominacionesDialogState();
}

class _CierrePorDenominacionesDialogState
    extends State<_CierrePorDenominacionesDialog> {
  final _planilla = planillaEnBlanco();
  late final List<TextEditingController> _campos = [
    for (final _ in _planilla) TextEditingController(),
  ];

  @override
  void dispose() {
    for (final campo in _campos) {
      campo.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = totalContado(_planilla);
    final unidades = unidadesContadas(_planilla);
    return AlertDialog(
      title: const Text('Cerrar caja'),
      content: SizedBox(
        width: double.maxFinite,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Contá lo que hay en el cajón y cargá cuántos hay de cada uno.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            const SizedBox(height: 12),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: _planilla.length,
                itemBuilder: (context, indice) {
                  final fila = _planilla[indice];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 96,
                          child: Text(
                            formatMoney(fila.valor.toDouble(), widget.moneda),
                            style: theme.textTheme.bodyMedium,
                          ),
                        ),
                        const SizedBox(width: 8),
                        SizedBox(
                          width: 68,
                          child: TextField(
                            controller: _campos[indice],
                            keyboardType: TextInputType.number,
                            textAlign: TextAlign.center,
                            decoration: const InputDecoration(
                              isDense: true,
                              hintText: '0',
                            ),
                            onChanged: (valor) => setState(
                              () => fila.cantidad = int.tryParse(valor) ?? 0,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            fila.cantidad == 0
                                ? '—'
                                : formatMoney(
                                    fila.subtotal.toDouble(),
                                    widget.moneda,
                                  ),
                            textAlign: TextAlign.right,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: fila.cantidad == 0
                                  ? theme.colorScheme.onSurfaceVariant
                                  : null,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            const Divider(),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Total contado', style: theme.textTheme.titleMedium),
                Text(
                  formatMoney(total.toDouble(), widget.moneda),
                  style: theme.textTheme.titleMedium,
                ),
              ],
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          // Con la planilla en blanco no se cierra: un cierre en cero por
          // olvidarse de contar es peor que no cerrar.
          onPressed: unidades == 0 ? null : () => Navigator.pop(context, total),
          child: const Text('Cerrar caja'),
        ),
      ],
    );
  }
}
