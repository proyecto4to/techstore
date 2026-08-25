import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../application/checkout_providers.dart';

class AddressPage extends ConsumerWidget {
  const AddressPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final addresses = ref.watch(addressesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Mis direcciones')),
      body: addresses.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: FilledButton(
            onPressed: () => ref.invalidate(addressesProvider),
            child: const Text('Reintentar'),
          ),
        ),
        data: (items) => items.isEmpty
            ? const Center(child: Text('Todavía no guardaste direcciones'))
            : ListView.builder(
                padding: const EdgeInsets.all(12),
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final address = items[index];
                  return Card(
                    child: ListTile(
                      leading: const Icon(Icons.location_on_outlined),
                      title: Text(address.recipient),
                      subtitle: Text(
                        '${address.line1}\n${address.city}, ${address.department}',
                      ),
                      isThreeLine: true,
                      trailing: Wrap(
                        children: [
                          IconButton(
                            tooltip: 'Editar',
                            onPressed: () async {
                              final changed = await showDialog<bool>(
                                context: context,
                                builder: (_) => _AddressDialog(
                                  addressId: address.id,
                                  initial: {
                                    'nombreDestinatario': address.recipient,
                                    'telefono': address.phone,
                                    'departamento': address.department,
                                    'ciudad': address.city,
                                    'direccionLinea1': address.line1,
                                    'barrio': address.neighborhood ?? '',
                                    'direccionLinea2': address.line2 ?? '',
                                    'numeroCasa': address.houseNumber ?? '',
                                    'referencia': address.reference ?? '',
                                    'codigoPostal': address.postalCode ?? '',
                                    'latitud':
                                        address.latitude?.toString() ?? '',
                                    'longitud':
                                        address.longitude?.toString() ?? '',
                                  },
                                  initialPrimary: address.primary,
                                ),
                              );
                              if (changed ?? false) {
                                ref.invalidate(addressesProvider);
                              }
                            },
                            icon: const Icon(Icons.edit_outlined),
                          ),
                          IconButton(
                            tooltip: 'Eliminar',
                            onPressed: () async {
                              await ref
                                  .read(checkoutRepositoryProvider)
                                  .deleteAddress(address.id);
                              ref.invalidate(addressesProvider);
                            },
                            icon: const Icon(Icons.delete_outline),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final created = await showDialog<bool>(
            context: context,
            builder: (_) => const _AddressDialog(),
          );
          if (created ?? false) ref.invalidate(addressesProvider);
        },
        icon: const Icon(Icons.add_location_alt_outlined),
        label: const Text('Agregar'),
      ),
    );
  }
}

class _AddressDialog extends ConsumerStatefulWidget {
  const _AddressDialog({
    this.addressId,
    this.initial = const {},
    this.initialPrimary = false,
  });

  final int? addressId;
  final Map<String, String> initial;
  final bool initialPrimary;

  @override
  ConsumerState<_AddressDialog> createState() => _AddressDialogState();
}

class _AddressDialogState extends ConsumerState<_AddressDialog> {
  final _form = GlobalKey<FormState>();
  final _values = <String, String>{};
  bool _busy = false;
  late bool _primary = widget.initialPrimary;

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      widget.addressId == null ? 'Nueva dirección' : 'Editar dirección',
    ),
    content: Form(
      key: _form,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final field in const [
              ('nombreDestinatario', 'Destinatario'),
              ('telefono', 'Teléfono'),
              ('departamento', 'Departamento'),
              ('ciudad', 'Ciudad'),
              ('direccionLinea1', 'Dirección'),
              ('barrio', 'Barrio (opcional)'),
              ('direccionLinea2', 'Complemento (opcional)'),
              ('numeroCasa', 'Número de casa (opcional)'),
              ('referencia', 'Referencia (opcional)'),
              ('codigoPostal', 'Código postal (opcional)'),
              ('latitud', 'Latitud (opcional)'),
              ('longitud', 'Longitud (opcional)'),
            ])
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: TextFormField(
                  initialValue: widget.initial[field.$1],
                  decoration: InputDecoration(labelText: field.$2),
                  validator: (value) => _validate(field.$1, field.$2, value),
                  onSaved: (value) => _values[field.$1] = value?.trim() ?? '',
                ),
              ),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: _primary,
              onChanged: (value) => setState(() => _primary = value ?? false),
              title: const Text('Dirección principal'),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: _busy ? null : () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: _busy
            ? null
            : () async {
                if (!_form.currentState!.validate()) return;
                _form.currentState!.save();
                setState(() => _busy = true);
                try {
                  final data = <String, Object?>{
                    for (final entry in _values.entries)
                      entry.key: entry.value.isEmpty ? null : entry.value,
                    'latitud': _coordinate('latitud'),
                    'longitud': _coordinate('longitud'),
                    'principal': _primary,
                  };
                  final repository = ref.read(checkoutRepositoryProvider);
                  if (widget.addressId == null) {
                    await repository.createAddress(data);
                  } else {
                    await repository.updateAddress(widget.addressId!, data);
                  }
                  if (context.mounted) Navigator.pop(context, true);
                } finally {
                  if (mounted) setState(() => _busy = false);
                }
              },
        child: const Text('Guardar'),
      ),
    ],
  );

  String? _validate(String key, String label, String? value) {
    final text = value?.trim() ?? '';
    if (!label.contains('opcional') && text.isEmpty) return 'Requerido';
    if (text.isEmpty || (key != 'latitud' && key != 'longitud')) return null;
    final coordinate = double.tryParse(text.replaceAll(',', '.'));
    final limit = key == 'latitud' ? 90 : 180;
    if (coordinate == null || coordinate < -limit || coordinate > limit) {
      return 'Valor entre -$limit y $limit';
    }
    return null;
  }

  double? _coordinate(String key) {
    final text = _values[key];
    return text == null || text.isEmpty
        ? null
        : double.parse(text.replaceAll(',', '.'));
  }
}
