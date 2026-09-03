import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/format/status_labels.dart';
import '../application/courier_providers.dart';

class CourierVehiclesPage extends ConsumerStatefulWidget {
  const CourierVehiclesPage({super.key});

  @override
  ConsumerState<CourierVehiclesPage> createState() =>
      _CourierVehiclesPageState();
}

class _CourierVehiclesPageState extends ConsumerState<CourierVehiclesPage> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final vehicles = ref.watch(courierVehiclesProvider);
    final current = ref.watch(courierCurrentVehicleProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Vehículos')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            current.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, _) => const ListTile(
                title: Text('No se pudo consultar el uso actual'),
              ),
              data: (use) => use == null
                  ? const Card(
                      child: ListTile(
                        leading: Icon(Icons.no_crash_outlined),
                        title: Text('No tenés un vehículo activo'),
                        subtitle: Text(
                          'Podés seleccionar uno disponible de la lista.',
                        ),
                      ),
                    )
                  : _CurrentUseCard(
                      use: use,
                      busy: _busy,
                      onEvent: () => _addEvent(use),
                      onFinish: () => _finish(use),
                    ),
            ),
            const SizedBox(height: 16),
            Text(
              'Flota habilitada',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            vehicles.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) =>
                  const ListTile(title: Text('No se pudo cargar la flota')),
              data: (items) => items.isEmpty
                  ? const ListTile(title: Text('No hay vehículos registrados'))
                  : Column(
                      children: [
                        for (final item in items)
                          Card(
                            child: ListTile(
                              leading: const CircleAvatar(
                                child: Icon(Icons.directions_car_outlined),
                              ),
                              title: Text(
                                '${item['matricula']} · ${item['marca']} ${item['modelo']}',
                              ),
                              subtitle: Text(
                                '${vehicleStatusLabel(item['estado'] as String?).label}'
                                ' · ${item['kilometrajeActual']} km\n'
                                '${item['utilizadoPor'] == null ? item['tipo'] : 'En uso por ${item['utilizadoPor']}'}',
                              ),
                              isThreeLine: true,
                              trailing: item['seleccionable'] == true
                                  ? FilledButton(
                                      onPressed: _busy || current.value != null
                                          ? null
                                          : () => _start(item),
                                      child: const Text('Usar'),
                                    )
                                  : const Icon(Icons.lock_outline),
                            ),
                          ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _start(Map<String, Object?> vehicle) async {
    final result = await _vehicleReadingDialog(
      title: 'Iniciar uso · ${vehicle['matricula']}',
      mileageLabel: 'Kilometraje inicial',
      minimumMileage: _number(vehicle['kilometrajeActual']),
      confirmLabel: 'Comenzar uso',
    );
    if (result == null) return;
    await _run(
      () => ref
          .read(courierRepositoryProvider)
          .startVehicle(
            vehicle['id'] as int,
            mileage: result.$1,
            fuel: result.$2,
            note: result.$3,
          ),
      'Vehículo asignado a tu jornada',
    );
  }

  Future<void> _finish(Map<String, Object?> use) async {
    final result = await _vehicleReadingDialog(
      title: 'Dejar vehículo',
      mileageLabel: 'Kilometraje final',
      minimumMileage: _number(use['kmInicio']),
      confirmLabel: 'Finalizar uso',
    );
    if (result == null) return;
    await _run(
      () => ref
          .read(courierRepositoryProvider)
          .finishVehicle(mileage: result.$1, fuel: result.$2, note: result.$3),
      'Uso finalizado y kilometraje actualizado',
    );
  }

  Future<(double, double, String?)?> _vehicleReadingDialog({
    required String title,
    required String mileageLabel,
    required double minimumMileage,
    required String confirmLabel,
  }) async {
    final mileage = TextEditingController(
      text: minimumMileage.toStringAsFixed(1),
    );
    final fuel = TextEditingController();
    final note = TextEditingController();
    String? mileageError;
    String? fuelError;
    final result = await showDialog<(double, double, String?)>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(title),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: mileage,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: InputDecoration(
                    labelText: mileageLabel,
                    helperText:
                        'Mínimo ${minimumMileage.toStringAsFixed(1)} km',
                    errorText: mileageError,
                  ),
                  // El error se va apenas corrige: dejarlo pegado al campo ya
                  // corregido confunde más que ayudar.
                  onChanged: mileageError == null
                      ? null
                      : (_) => setDialogState(() => mileageError = null),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: fuel,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: InputDecoration(
                    labelText: 'Combustible (0 a 100 %)',
                    errorText: fuelError,
                  ),
                  onChanged: fuelError == null
                      ? null
                      : (_) => setDialogState(() => fuelError = null),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: note,
                  maxLength: 1000,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'Observación opcional',
                  ),
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
              onPressed: () {
                final km = double.tryParse(mileage.text.replaceAll(',', '.'));
                final fuelLevel = double.tryParse(
                  fuel.text.replaceAll(',', '.'),
                );
                if (km == null ||
                    km < minimumMileage ||
                    fuelLevel == null ||
                    fuelLevel < 0 ||
                    fuelLevel > 100) {
                  // El mensaje va pegado a cada campo: como error suelto al pie
                  // del formulario quedaba tapado por el teclado numérico y el
                  // botón parecía no responder.
                  setDialogState(() {
                    mileageError = km == null
                        ? 'Ingresá el kilometraje.'
                        : km < minimumMileage
                        ? 'No puede ser menor a ${minimumMileage.toStringAsFixed(1)} km.'
                        : null;
                    fuelError = fuelLevel == null
                        ? 'Ingresá el combustible.'
                        : (fuelLevel < 0 || fuelLevel > 100)
                        ? 'Tiene que estar entre 0 y 100 %.'
                        : null;
                  });
                  return;
                }
                Navigator.pop(context, (
                  km,
                  fuelLevel,
                  note.text.trim().isEmpty ? null : note.text.trim(),
                ));
              },
              child: Text(confirmLabel),
            ),
          ],
        ),
      ),
    );
    mileage.dispose();
    fuel.dispose();
    note.dispose();
    return result;
  }

  Future<void> _addEvent(Map<String, Object?> use) async {
    final form = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (context) =>
          _VehicleEventDialog(minimumMileage: _number(use['kmInicio'])),
    );
    if (form == null) return;
    await _run(
      () => ref.read(courierRepositoryProvider).addVehicleEvent(form),
      'Evento registrado. Los eventos críticos fueron notificados al administrador.',
    );
  }

  Future<void> _run(Future<Object?> Function() action, String success) async {
    setState(() => _busy = true);
    try {
      await action();
      await _refresh();
      _message(success);
    } on AppFailure catch (failure) {
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _refresh() async {
    ref.invalidate(courierVehiclesProvider);
    ref.invalidate(courierCurrentVehicleProvider);
    await Future.wait([
      ref.read(courierVehiclesProvider.future),
      ref.read(courierCurrentVehicleProvider.future),
    ]);
  }

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}

class _CurrentUseCard extends StatelessWidget {
  const _CurrentUseCard({
    required this.use,
    required this.busy,
    required this.onEvent,
    required this.onFinish,
  });
  final Map<String, Object?> use;
  final bool busy;
  final VoidCallback onEvent;
  final VoidCallback onFinish;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Vehículo actual',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Text(
            '${use['vehiculo']}',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          Text(
            'Inicio: ${use['kmInicio']} km · combustible ${use['combustibleInicio']} %',
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: busy ? null : onEvent,
            icon: const Icon(Icons.add_alert_outlined),
            label: const Text('Agregar evento'),
          ),
          FilledButton.icon(
            onPressed: busy ? null : onFinish,
            icon: const Icon(Icons.key_off_outlined),
            label: const Text('DEJAR VEHÍCULO'),
          ),
        ],
      ),
    ),
  );
}

class _VehicleEventDialog extends StatefulWidget {
  const _VehicleEventDialog({required this.minimumMileage});
  final double minimumMileage;

  @override
  State<_VehicleEventDialog> createState() => _VehicleEventDialogState();
}

class _VehicleEventDialogState extends State<_VehicleEventDialog> {
  String _type = 'OBSERVACION';
  bool _maintenance = false;
  bool _canContinue = true;
  String _severity = 'MEDIA';
  String? _error;
  late final TextEditingController _mileage;
  final _description = TextEditingController();
  final _location = TextEditingController();
  final _amount = TextEditingController();
  final _note = TextEditingController();
  final _fuelType = TextEditingController();
  final _quantity = TextEditingController();
  final _unit = TextEditingController(text: 'LITROS');
  final _fuelAfter = TextEditingController();
  final _problem = TextEditingController();
  final _repair = TextEditingController();
  final _provider = TextEditingController();
  final _damage = TextEditingController();

  @override
  void initState() {
    super.initState();
    _mileage = TextEditingController(
      text: widget.minimumMileage.toStringAsFixed(1),
    );
  }

  @override
  void dispose() {
    for (final controller in [
      _mileage,
      _description,
      _location,
      _amount,
      _note,
      _fuelType,
      _quantity,
      _unit,
      _fuelAfter,
      _problem,
      _repair,
      _provider,
      _damage,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Agregar evento'),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            DropdownButtonFormField<String>(
              initialValue: _type,
              decoration: const InputDecoration(labelText: 'Tipo de evento'),
              items: [
                for (final value in _eventTypes)
                  DropdownMenuItem(
                    value: value,
                    child: Text(value.replaceAll('_', ' ')),
                  ),
              ],
              onChanged: (value) => setState(() => _type = value!),
            ),
            const SizedBox(height: 10),
            _field(_mileage, 'Kilometraje', numeric: true),
            _field(_description, 'Descripción', lines: 3),
            _field(_location, 'Ubicación (opcional)'),
            _field(_amount, 'Importe (opcional)', numeric: true),
            if (_type == 'RECARGA_COMBUSTIBLE') ...[
              _field(_fuelType, 'Tipo de combustible'),
              _field(_quantity, 'Cantidad', numeric: true),
              _field(_unit, 'Unidad'),
              _field(_fuelAfter, 'Nivel posterior (0 a 100)', numeric: true),
            ],
            if (_type == 'REPARACION') ...[
              _field(_problem, 'Problema'),
              _field(_repair, 'Reparación realizada'),
              _field(_provider, 'Taller / proveedor'),
            ],
            if (_type == 'AVERIA' || _type == 'ESTANCAMIENTO') ...[
              DropdownButtonFormField<String>(
                initialValue: _severity,
                decoration: const InputDecoration(labelText: 'Severidad'),
                items: const [
                  DropdownMenuItem(value: 'BAJA', child: Text('Baja')),
                  DropdownMenuItem(value: 'MEDIA', child: Text('Media')),
                  DropdownMenuItem(value: 'ALTA', child: Text('Alta')),
                  DropdownMenuItem(value: 'CRITICA', child: Text('Crítica')),
                ],
                onChanged: (value) => setState(() => _severity = value!),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Puede continuar'),
                value: _canContinue,
                onChanged: (value) => setState(() => _canContinue = value),
              ),
            ],
            if (_type == 'ACCIDENTE')
              _field(_damage, 'Daños observados', lines: 2),
            if (_type == 'REPARACION' || _type == 'MANTENIMIENTO')
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enviar vehículo a mantenimiento'),
                value: _maintenance,
                onChanged: (value) => setState(() => _maintenance = value),
              ),
            _field(_note, 'Observación (opcional)', lines: 2),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Registrar evento')),
    ],
  );

  Widget _field(
    TextEditingController controller,
    String label, {
    bool numeric = false,
    int lines = 1,
  }) => Padding(
    padding: const EdgeInsets.only(top: 10),
    child: TextField(
      controller: controller,
      keyboardType: numeric
          ? const TextInputType.numberWithOptions(decimal: true)
          : null,
      maxLines: lines,
      decoration: InputDecoration(labelText: label),
    ),
  );

  void _submit() {
    final mileage = double.tryParse(_mileage.text.replaceAll(',', '.'));
    if (mileage == null ||
        mileage < widget.minimumMileage ||
        _description.text.trim().isEmpty) {
      setState(
        () => _error = 'El kilometraje y la descripción son obligatorios.',
      );
      return;
    }
    final additional = <String, Object?>{};
    if (_type == 'RECARGA_COMBUSTIBLE') {
      if ([
        _fuelType,
        _quantity,
        _unit,
        _fuelAfter,
      ].any((c) => c.text.trim().isEmpty)) {
        setState(() => _error = 'Completá todos los datos de la recarga.');
        return;
      }
      additional.addAll({
        'tipoCombustible': _fuelType.text.trim(),
        'cantidad': double.tryParse(_quantity.text.replaceAll(',', '.')),
        'unidad': _unit.text.trim(),
        'nivelCombustiblePosterior': double.tryParse(
          _fuelAfter.text.replaceAll(',', '.'),
        ),
      });
    }
    if (_type == 'REPARACION') {
      if ([_problem, _repair, _provider].any((c) => c.text.trim().isEmpty)) {
        setState(() => _error = 'Completá problema, reparación y proveedor.');
        return;
      }
      additional.addAll({
        'problema': _problem.text.trim(),
        'reparacionRealizada': _repair.text.trim(),
        'tallerProveedor': _provider.text.trim(),
      });
    }
    if (_type == 'AVERIA' || _type == 'ESTANCAMIENTO') {
      additional.addAll({
        'severidad': _severity,
        'puedeContinuar': _canContinue,
      });
    }
    if (_type == 'ACCIDENTE') {
      if (_damage.text.trim().isEmpty) {
        setState(() => _error = 'Indicá los daños observados.');
        return;
      }
      additional['danosObservados'] = _damage.text.trim();
    }
    Navigator.pop(context, <String, Object?>{
      'tipo': _type,
      'kilometraje': mileage,
      'descripcion': _description.text.trim(),
      'ubicacion': _optional(_location.text),
      'importe': double.tryParse(_amount.text.replaceAll(',', '.')),
      'datosAdicionales': additional,
      'observacion': _optional(_note.text),
      'enviarAMantenimiento': _maintenance,
    });
  }
}

const _eventTypes = [
  'RECARGA_COMBUSTIBLE',
  'REPARACION',
  'AVERIA',
  'ESTANCAMIENTO',
  'ACCIDENTE',
  'MANTENIMIENTO',
  'OBSERVACION',
  'OTRO',
];

double _number(Object? value) =>
    value is num ? value.toDouble() : double.tryParse('$value') ?? 0;
String? _optional(String value) => value.trim().isEmpty ? null : value.trim();
