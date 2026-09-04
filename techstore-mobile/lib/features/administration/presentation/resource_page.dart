import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/errors/user_message.dart';
import '../../../core/format/money_input.dart';
import '../../../core/native/document_service.dart';
import '../../../core/widgets/image_origin_sheet.dart';
import '../domain/resource_definition.dart';
import 'detail_fields.dart';

final resourceProvider = FutureProvider.autoDispose
    .family<Object?, ResourceDefinition>((ref, definition) async {
      final query = <String, Object?>{...definition.query};
      final response = await ref
          .watch(apiClientProvider)
          .get<Object?>(definition.path, queryParameters: query);
      return response.data;
    });

class ResourcePage extends ConsumerWidget {
  const ResourcePage({required this.definition, super.key});

  final ResourceDefinition definition;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(resourceProvider(definition));
    return Scaffold(
      appBar: AppBar(
        title: Text(definition.title),
        actions: [
          if (_resourceDocument(definition.key) case final document?)
            IconButton(
              tooltip: 'Documento',
              onPressed: () => _runDocument(context, ref, document),
              icon: const Icon(Icons.picture_as_pdf_outlined),
            ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(resourceProvider(definition).future),
        child: data.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: [
              const SizedBox(height: 160),
              Icon(
                Icons.cloud_off_outlined,
                size: 48,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 12),
              Center(
                child: Text(
                  error is AppFailure
                      ? error.message
                      : 'No se pudieron cargar los datos',
                ),
              ),
              Center(
                child: FilledButton(
                  onPressed: () => ref.invalidate(resourceProvider(definition)),
                  child: const Text('Reintentar'),
                ),
              ),
            ],
          ),
          data: (value) {
            final rows = _rows(value);
            if (rows.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 160),
                  Center(child: Text('Sin datos')),
                ],
              );
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: rows.length,
              itemBuilder: (context, index) {
                final row = rows[index];
                return _RecordCard(
                  value: row,
                  definition: definition,
                  actions: row is Map
                      ? _actionsFor(
                          definition.key,
                          Map<String, Object?>.from(row),
                        )
                      : const [],
                  onAction: (action) => _runItemAction(
                    context,
                    ref,
                    action,
                    Map<String, Object?>.from(row! as Map),
                  ),
                );
              },
            );
          },
        ),
      ),
      floatingActionButton: _createMutation(definition.key) == null
          ? null
          : FloatingActionButton.extended(
              onPressed: () => _runCreate(context, ref),
              icon: const Icon(Icons.add),
              label: const Text('Crear'),
            ),
    );
  }

  Future<void> _runCreate(BuildContext context, WidgetRef ref) async {
    final mutation = _createMutation(definition.key)!;
    final data = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => _MutationDialog(mutation: mutation),
    );
    if (data == null || !context.mounted) return;
    await _execute(context, ref, mutation.method, mutation.path, data);
  }

  Future<void> _runItemAction(
    BuildContext context,
    WidgetRef ref,
    String action,
    Map<String, Object?> row,
  ) async {
    final id = (row['id'] as num?)?.toInt();
    if (id == null) return;
    switch (action) {
      case 'confirm-order':
        await _confirmAndExecute(
          context,
          ref,
          'Confirmar pedido',
          'PUT',
          '/api/v1/pedidos/$id/confirmar',
        );
      case 'cancel-order':
        await _confirmAndExecute(
          context,
          ref,
          'Cancelar pedido',
          'PUT',
          '/api/v1/pedidos/$id/cancelar',
        );
      case 'receive-purchase':
        await _confirmAndExecute(
          context,
          ref,
          'Recibir compra e ingresar stock',
          'PUT',
          '/api/v1/compras/$id/recibir',
        );
      case 'cancel-purchase':
        await _confirmAndExecute(
          context,
          ref,
          'Anular compra',
          'PUT',
          '/api/v1/compras/$id/anular',
        );
      case 'send-purchase':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Enviar compra al proveedor',
            path: '/api/v1/compras/$id/enviar',
            fields: const [
              _Field('canal', 'Canal', choices: ['EMAIL', 'WHATSAPP', 'AMBOS']),
            ],
          ),
        );
      case 'pay-purchase':
        await _paymentDialog(
          context,
          ref,
          '/api/v1/compras/$id/pagar',
          method: 'PUT',
        );
      case 'purchase-receipt':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Comprobante del proveedor',
            method: 'PUT',
            path: '/api/v1/compras/$id/comprobante',
            fields: const [
              _Field('fechaEmision', 'Fecha (AAAA-MM-DD)'),
              _Field('timbrado', 'Timbrado', required: false),
              _Field('numero', 'Número', required: false),
              _Field('condicion', 'Condición', choices: ['Contado', 'Credito']),
              _Field('total', 'Total', money: true, required: false),
            ],
          ),
        );
      case 'purchase-receipt-image':
        await _uploadPurchaseReceipt(context, ref, id);
      case 'purchase-receipt-open':
        await _runDocument(
          context,
          ref,
          _DocumentSpec(
            '/api/v1/compras/$id/comprobante/archivo',
            'comprobante-compra-$id.jpg',
            printable: false,
          ),
        );
      case 'toggle-user':
        await _execute(context, ref, 'PUT', '/api/v1/usuarios/$id/activo', {
          'activo': row['activo'] != true,
        });
      case 'reset-user':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Restablecer contraseña',
            method: 'PUT',
            path: '/api/v1/usuarios/$id/password',
            fields: const [
              _Field('password', 'Nueva contraseña', secret: true),
            ],
          ),
        );
      case 'assign-user-point':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Asignar punto de expedición',
            method: 'PUT',
            path: '/api/v1/usuarios/$id/punto-expedicion',
            fields: const [
              _Field('puntoExpedicionId', 'ID del punto', integer: true),
            ],
          ),
        );
      case 'toggle-supplier':
        await _execute(context, ref, 'PUT', '/api/v1/proveedores/$id/activo', {
          'activo': row['activo'] != true,
        });
      case 'edit-supplier':
        await _dialogAndExecute(
          context,
          ref,
          _createMutation('suppliers')!.copyWith(
            title: 'Editar proveedor',
            method: 'PUT',
            path: '/api/v1/proveedores/$id',
            initialValues: row,
          ),
        );
      case 'supplier-offer':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Agregar o actualizar oferta',
            method: 'PUT',
            path: '/api/v1/proveedores/$id/ofertas',
            fields: const [
              _Field('productoId', 'ID producto', integer: true),
              _Field('precio', 'Precio', money: true),
              _Field(
                'disponible',
                'Disponible',
                boolean: true,
                initialBool: true,
              ),
            ],
          ),
        );
      case 'delete-product':
        await _confirmAndExecute(
          context,
          ref,
          'Dar de baja el producto',
          'DELETE',
          '/api/v1/productos/$id',
        );
      case 'edit-product':
        final productInitial = {...row};
        final brand = row['marca'];
        final model = row['modelo'];
        if (brand is Map) productInitial['marcaId'] = brand['id'];
        if (model is Map) productInitial['modeloId'] = model['id'];
        await _dialogAndExecute(
          context,
          ref,
          _createMutation('products-admin')!.copyWith(
            title: 'Editar producto',
            method: 'PUT',
            path: '/api/v1/productos/$id',
            initialValues: productInitial,
          ),
        );
      case 'product-image':
        await _uploadProductImage(context, ref, id);
      case 'edit-rate':
        await _dialogAndExecute(
          context,
          ref,
          _createMutation('shipping-rates')!.copyWith(
            title: 'Editar tarifa',
            method: 'PUT',
            path: '/api/v1/envios/tarifas/$id',
            initialValues: row,
          ),
        );
      case 'toggle-point':
        await _execute(
          context,
          ref,
          'PUT',
          '/api/v1/empresa/puntos-expedicion/$id/activo',
          {'activo': row['activo'] != true},
        );
      case 'pay-order':
        await _paymentDialog(context, ref, '/api/v1/pedidos/$id/pago');
      case 'credit-note':
        await _creditNoteDialog(context, ref, id);
      case 'shipping-state':
        await _dialogAndExecute(
          context,
          ref,
          _Mutation(
            title: 'Actualizar envío',
            method: 'PUT',
            path: '/api/v1/envios/pedidos/$id/estado',
            fields: const [
              _Field(
                'estado',
                'Estado',
                choices: [
                  'PENDIENTE',
                  'PREPARANDO',
                  'LISTO_PARA_ENVIO',
                  'DESPACHADO',
                  'EN_CAMINO',
                  'INTENTO_ENTREGA',
                  'ENTREGADO',
                  'CANCELADO',
                ],
              ),
              _Field('descripcion', 'Descripción', required: false),
              _Field('ubicacion', 'Ubicación', required: false),
              _Field(
                'codigoSeguimiento',
                'Código seguimiento',
                required: false,
              ),
              _Field('transportista', 'Transportista', required: false),
            ],
          ),
        );
      case 'invoice-pdf':
        await _runDocument(
          context,
          ref,
          _DocumentSpec('/api/v1/facturas/$id/pdf', 'factura-$id.pdf'),
        );
      case 'invoice-kude':
        await _runDocument(
          context,
          ref,
          _DocumentSpec('/api/v1/facturas/$id/kude', 'kude-$id.pdf'),
        );
      case 'invoice-xml':
        await _runDocument(
          context,
          ref,
          _DocumentSpec(
            '/api/v1/facturas/$id/documento-electronico',
            'documento-electronico-$id.xml',
            printable: false,
          ),
        );
      case 'credit-note-pdf':
        await _runDocument(
          context,
          ref,
          _DocumentSpec(
            '/api/v1/notas-credito/$id/pdf',
            'nota-credito-$id.pdf',
          ),
        );
      case 'read-notification':
        await _execute(
          context,
          ref,
          'PUT',
          '/api/v1/notificaciones/$id/leida',
          const {},
        );
      case 'close-conversation':
        await _confirmAndExecute(
          context,
          ref,
          'Cerrar conversación',
          'PUT',
          '/api/v1/conversaciones/$id/cerrar',
        );
    }
  }

  Future<void> _paymentDialog(
    BuildContext context,
    WidgetRef ref,
    String path, {
    String method = 'POST',
  }) async {
    final data = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => const _MutationDialog(
        mutation: _Mutation(
          title: 'Registrar pago',
          path: '',
          fields: [
            _Field(
              'tipoPago',
              'Medio',
              choices: ['Efectivo', 'Tarjeta', 'Transferencia', 'Cheque'],
            ),
            _Field('monto', 'Monto', money: true),
            _Field('numeroComprobante', 'Comprobante', required: false),
          ],
        ),
      ),
    );
    if (data != null && context.mounted) {
      await _execute(context, ref, method, path, {
        'lineas': [data],
      });
    }
  }

  Future<void> _uploadProductImage(
    BuildContext context,
    WidgetRef ref,
    int productId,
  ) async {
    final origin = await pickImageOrigin(context);
    if (origin == null || !context.mounted) return;
    try {
      final uploaded = await ref
          .read(imageUploadServiceProvider)
          .pickAndUploadProduct(productId, origin);
      if (!context.mounted || !uploaded) return;
      ref.invalidate(resourceProvider(definition));
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Imagen actualizada')));
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              userMessageFor(
                error,
                fallback:
                    'No pudimos subir la imagen. '
                    'Probá con otra o revisá tu conexión.',
              ),
            ),
          ),
        );
      }
    }
  }

  Future<void> _uploadPurchaseReceipt(
    BuildContext context,
    WidgetRef ref,
    int purchaseId,
  ) async {
    final origin = await pickImageOrigin(
      context,
      cameraLabel: 'Fotografiar comprobante',
      cameraIcon: Icons.document_scanner_outlined,
    );
    if (origin == null || !context.mounted) return;
    try {
      final uploaded = await ref
          .read(imageUploadServiceProvider)
          .pickAndUploadPurchaseReceipt(purchaseId, origin);
      if (!context.mounted || !uploaded) return;
      ref.invalidate(resourceProvider(definition));
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Comprobante adjuntado')));
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              userMessageFor(
                error,
                fallback:
                    'No pudimos adjuntar el comprobante. '
                    'Probá de nuevo en un momento.',
              ),
            ),
          ),
        );
      }
    }
  }

  Future<void> _runDocument(
    BuildContext context,
    WidgetRef ref,
    _DocumentSpec document,
  ) async {
    final action = await showModalBottomSheet<DocumentAction>(
      context: context,
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.open_in_new),
              title: const Text('Abrir'),
              onTap: () => Navigator.pop(context, DocumentAction.open),
            ),
            ListTile(
              leading: const Icon(Icons.share_outlined),
              title: const Text('Compartir'),
              onTap: () => Navigator.pop(context, DocumentAction.share),
            ),
            if (document.printable)
              ListTile(
                leading: const Icon(Icons.print_outlined),
                title: const Text('Imprimir'),
                onTap: () => Navigator.pop(context, DocumentAction.print),
              ),
          ],
        ),
      ),
    );
    if (action == null || !context.mounted) return;
    try {
      await ref
          .read(documentServiceProvider)
          .handle(document.path, document.filename, action);
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              userMessageFor(
                error,
                fallback:
                    'No pudimos abrir el documento. '
                    'Revisá tu conexión e intentá de nuevo.',
              ),
            ),
          ),
        );
      }
    }
  }

  Future<void> _creditNoteDialog(
    BuildContext context,
    WidgetRef ref,
    int orderId,
  ) async {
    final data = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => const _MutationDialog(
        mutation: _Mutation(
          title: 'Emitir nota de crédito',
          path: '',
          fields: [
            _Field('productoId', 'ID producto', integer: true),
            _Field('cantidad', 'Cantidad', integer: true),
            _Field('motivo', 'Motivo'),
          ],
        ),
      ),
    );
    if (data != null && context.mounted) {
      await _execute(
        context,
        ref,
        'POST',
        '/api/v1/pedidos/$orderId/nota-credito',
        {
          'motivo': data.remove('motivo'),
          'items': [data],
        },
      );
    }
  }

  Future<void> _dialogAndExecute(
    BuildContext context,
    WidgetRef ref,
    _Mutation mutation,
  ) async {
    final data = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => _MutationDialog(mutation: mutation),
    );
    if (data != null && context.mounted) {
      await _execute(context, ref, mutation.method, mutation.path, data);
    }
  }

  Future<void> _confirmAndExecute(
    BuildContext context,
    WidgetRef ref,
    String title,
    String method,
    String path,
  ) async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: const Text('La regla final será validada por el servidor.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    );
    if ((accepted ?? false) && context.mounted) {
      await _execute(context, ref, method, path, const {});
    }
  }

  Future<void> _execute(
    BuildContext context,
    WidgetRef ref,
    String method,
    String path,
    Map<String, Object?> data,
  ) async {
    try {
      final api = ref.read(apiClientProvider);
      switch (method) {
        case 'PUT':
          await api.put<Object?>(path, data: data);
        case 'DELETE':
          await api.delete<Object?>(path);
        default:
          await api.post<Object?>(path, data: data);
      }
      ref.invalidate(resourceProvider(definition));
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Operación completada')));
      }
    } on AppFailure catch (failure) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    }
  }
}

class _RecordCard extends StatelessWidget {
  const _RecordCard({
    required this.value,
    required this.definition,
    required this.actions,
    required this.onAction,
  });

  final Object? value;
  final ResourceDefinition definition;
  final List<(String, String)> actions;
  final ValueChanged<String> onAction;

  @override
  Widget build(BuildContext context) {
    final map = value is Map ? Map<String, Object?>.from(value! as Map) : null;
    final primary = map == null
        ? value.toString()
        : _first(map, definition.primaryFields);
    final secondary = map == null
        ? ''
        : _secondary(map, definition.secondaryFields);
    return Card(
      child: ExpansionTile(
        leading: Icon(definition.icon),
        title: Text(primary.isEmpty ? definition.title : primary),
        subtitle: secondary.isEmpty
            ? null
            : Text(secondary, maxLines: 2, overflow: TextOverflow.ellipsis),
        trailing: actions.isEmpty
            ? const Icon(Icons.expand_more)
            : PopupMenuButton<String>(
                onSelected: onAction,
                itemBuilder: (context) => [
                  for (final action in actions)
                    PopupMenuItem(value: action.$1, child: Text(action.$2)),
                ],
              ),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            // Antes acá se volcaba el JSON con un JsonEncoder: para saber el
            // total de una compra habia que leer llaves y nombres de columna.
            child: map == null
                ? SelectableText(
                    value.toString(),
                    style: Theme.of(context).textTheme.bodyMedium,
                  )
                : DetailFields(registro: map),
          ),
        ],
      ),
    );
  }
}

class _MutationDialog extends StatefulWidget {
  const _MutationDialog({required this.mutation});
  final _Mutation mutation;

  @override
  State<_MutationDialog> createState() => _MutationDialogState();
}

class _MutationDialogState extends State<_MutationDialog> {
  final _form = GlobalKey<FormState>();
  final _values = <String, Object?>{};

  @override
  void initState() {
    super.initState();
    for (final field in widget.mutation.fields) {
      if (field.boolean) _values[field.name] = field.initialBool;
      if (field.choices.isNotEmpty) _values[field.name] = field.choices.first;
    }
    _values.addAll(widget.mutation.initialValues);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.mutation.title),
    content: Form(
      key: _form,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [for (final field in widget.mutation.fields) _input(field)],
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
          _form.currentState!.save();
          Navigator.pop(context, _values);
        },
        child: const Text('Guardar'),
      ),
    ],
  );

  Widget _input(_Field field) {
    if (field.boolean) {
      return SwitchListTile(
        title: Text(field.label),
        value: _values[field.name] as bool? ?? false,
        onChanged: (value) => setState(() => _values[field.name] = value),
      );
    }
    if (field.choices.isNotEmpty) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: DropdownButtonFormField<String>(
          initialValue: _values[field.name] as String?,
          decoration: InputDecoration(labelText: field.label),
          items: [
            for (final choice in field.choices)
              DropdownMenuItem(value: choice, child: Text(choice)),
          ],
          onChanged: (value) => _values[field.name] = value,
        ),
      );
    }
    if (field.money) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: TextFormField(
          initialValue: _values[field.name] == null
              ? null
              : formatearEntradaMonetaria(
                  _values[field.name].toString(),
                  decimales: 2,
                ),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          inputFormatters: const [MoneyInputFormatter(decimales: 2)],
          decoration: InputDecoration(labelText: field.label, prefixText: '₲ '),
          validator: (value) => _errorDeMonto(value, field),
          onSaved: (value) {
            final monto = parsearMonto(value, decimales: 2);
            if (monto == null) return;
            _values[field.name] = monto;
          },
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextFormField(
        initialValue: _values[field.name]?.toString(),
        obscureText: field.secret,
        keyboardType: field.number || field.integer
            ? TextInputType.numberWithOptions(decimal: field.number)
            : TextInputType.text,
        decoration: InputDecoration(labelText: field.label),
        // Antes solo se comprobaba que no estuviera vacío y después `onSaved`
        // hacía `int.parse`/`double.parse`: escribir cualquier cosa que no
        // fuera un número reventaba al guardar en lugar de avisar.
        validator: (value) => _errorDeCampo(value, field),
        onSaved: (value) {
          final text = value?.trim() ?? '';
          if (text.isEmpty) return;
          if (field.integer) {
            final entero = int.tryParse(text);
            if (entero != null) _values[field.name] = entero;
            return;
          }
          if (field.number) {
            final numero = double.tryParse(text.replaceAll(',', '.'));
            if (numero != null) _values[field.name] = numero;
            return;
          }
          _values[field.name] = text;
        },
      ),
    );
  }

  String? _errorDeMonto(String? value, _Field field) {
    final monto = parsearMonto(value, decimales: 2);
    if (monto == null) return field.required ? 'Ingresá un monto' : null;
    if (monto < 0) return 'El monto no puede ser negativo';
    return null;
  }

  String? _errorDeCampo(String? value, _Field field) {
    final text = value?.trim() ?? '';
    if (text.isEmpty) return field.required ? 'Requerido' : null;
    if (field.integer && int.tryParse(text) == null) {
      return 'Escribí un número entero';
    }
    if (field.number && double.tryParse(text.replaceAll(',', '.')) == null) {
      return 'Escribí un número';
    }
    return null;
  }
}

final class _Mutation {
  const _Mutation({
    required this.title,
    required this.path,
    required this.fields,
    this.method = 'POST',
    this.initialValues = const {},
  });
  final String title;
  final String path;
  final List<_Field> fields;
  final String method;
  final Map<String, Object?> initialValues;

  _Mutation copyWith({
    String? title,
    String? path,
    String? method,
    Map<String, Object?>? initialValues,
  }) => _Mutation(
    title: title ?? this.title,
    path: path ?? this.path,
    method: method ?? this.method,
    fields: fields,
    initialValues: initialValues ?? this.initialValues,
  );
}

final class _Field {
  const _Field(
    this.name,
    this.label, {
    this.required = true,
    this.number = false,
    this.money = false,
    this.integer = false,
    this.boolean = false,
    this.initialBool = false,
    this.secret = false,
    this.choices = const [],
  });
  final String name;
  final String label;
  final bool required;
  final bool number;

  /// Un importe: se agrupa mientras se escribe y lleva el simbolo de moneda.
  final bool money;
  final bool integer;
  final bool boolean;
  final bool initialBool;
  final bool secret;
  final List<String> choices;
}

_Mutation? _createMutation(String key) => switch (key) {
  'suppliers' => const _Mutation(
    title: 'Nuevo proveedor',
    path: '/api/v1/proveedores',
    fields: [
      _Field('razonSocial', 'Razón social'),
      _Field('nombreFantasia', 'Nombre de fantasía', required: false),
      _Field('ruc', 'RUC', required: false),
      _Field('email', 'Correo', required: false),
      _Field('telefono', 'Teléfono', required: false),
      _Field('direccion', 'Dirección', required: false),
      _Field('ciudad', 'Ciudad', required: false),
      _Field('pais', 'País', required: false),
    ],
  ),
  'users' => const _Mutation(
    title: 'Nuevo usuario',
    path: '/api/v1/usuarios',
    fields: [
      _Field('nombre', 'Nombre'),
      _Field('apellido', 'Apellido'),
      _Field('email', 'Correo'),
      _Field('password', 'Contraseña', secret: true),
      _Field('rol', 'Rol', choices: ['Cliente', 'Cajero', 'Admin']),
    ],
  ),
  'products-admin' => const _Mutation(
    title: 'Nuevo producto',
    path: '/api/v1/productos',
    fields: [
      _Field('nombre', 'Nombre'),
      _Field('descripcion', 'Descripción', required: false),
      _Field('precio', 'Precio', money: true),
      _Field('iva', 'IVA', integer: true),
      _Field('stockReal', 'Stock real', integer: true),
      _Field('stockMinimo', 'Stock mínimo', integer: true, required: false),
      _Field('pesoKg', 'Peso kg', number: true, required: false),
      _Field('marcaId', 'ID marca', integer: true, required: false),
      _Field('modeloId', 'ID modelo', integer: true, required: false),
      _Field('activo', 'Activo', boolean: true, initialBool: true),
      _Field('ocultarSinStock', 'Ocultar sin stock', boolean: true),
    ],
  ),
  'shipping-rates' => const _Mutation(
    title: 'Nueva tarifa',
    path: '/api/v1/envios/tarifas',
    fields: [
      _Field('codigo', 'Código'),
      _Field('nombre', 'Nombre'),
      _Field(
        'tipo',
        'Tipo',
        choices: [
          'RETIRO_TIENDA',
          'ENTREGA_LOCAL',
          'OTRA_CIUDAD',
          'OTRO_DEPARTAMENTO',
          'TRANSPORTADORA',
        ],
      ),
      _Field('providerCode', 'Código proveedor'),
      _Field('costo', 'Costo', money: true),
      _Field('iva', 'IVA', integer: true),
      _Field('diasEstimadosMin', 'Días mínimos', integer: true),
      _Field('diasEstimadosMax', 'Días máximos', integer: true),
      _Field('prioridad', 'Prioridad', integer: true),
      _Field('activa', 'Activa', boolean: true, initialBool: true),
    ],
  ),
  'expedition-points' => const _Mutation(
    title: 'Nuevo punto de expedición',
    path: '/api/v1/empresa/puntos-expedicion',
    fields: [
      _Field('nombre', 'Nombre'),
      _Field('establecimiento', 'Establecimiento (3 dígitos)', required: false),
      _Field('punto', 'Punto (3 dígitos)', required: false),
      _Field('activo', 'Activo', boolean: true, initialBool: true),
    ],
  ),
  'company' => const _Mutation(
    title: 'Editar empresa',
    method: 'PUT',
    path: '/api/v1/empresa',
    fields: [
      _Field('nombre', 'Nombre'),
      _Field('ruc', 'RUC'),
      _Field('direccion', 'Dirección', required: false),
      _Field('ciudad', 'Ciudad', required: false),
      _Field('telefono', 'Teléfono', required: false),
      _Field('email', 'Correo', required: false),
      _Field('actividad', 'Actividad', required: false),
      _Field('timbrado', 'Timbrado', required: false),
      _Field(
        'timbradoInicioVigencia',
        'Inicio vigencia AAAA-MM-DD',
        required: false,
      ),
      _Field(
        'tipoContribuyente',
        'Tipo contribuyente',
        integer: true,
        required: false,
      ),
      _Field('establecimiento', 'Establecimiento', required: false),
      _Field('puntoExpedicion', 'Punto expedición', required: false),
    ],
  ),
  'smtp-config' => const _Mutation(
    title: 'Editar SMTP',
    method: 'PUT',
    path: '/api/v1/correos/config',
    fields: [
      _Field('host', 'Servidor', required: false),
      _Field('port', 'Puerto', integer: true, required: false),
      _Field('username', 'Usuario', required: false),
      _Field('password', 'Contraseña', required: false, secret: true),
      _Field('from', 'Remitente', required: false),
    ],
  ),
  'mail' => const _Mutation(
    title: 'Enviar correo de prueba',
    path: '/api/v1/correos/prueba',
    fields: [_Field('destinatario', 'Destinatario')],
  ),
  _ => null,
};

List<(String, String)> _actionsFor(String key, Map<String, Object?> row) =>
    switch (key) {
      'all-orders' => const [
        ('confirm-order', 'Confirmar'),
        ('cancel-order', 'Cancelar'),
        ('pay-order', 'Registrar pago'),
        ('credit-note', 'Nota de crédito'),
        ('shipping-state', 'Actualizar envío'),
      ],
      'purchases' => const [
        ('receive-purchase', 'Recibir'),
        ('send-purchase', 'Enviar al proveedor'),
        ('pay-purchase', 'Pagar'),
        ('purchase-receipt', 'Comprobante'),
        ('purchase-receipt-image', 'Adjuntar foto'),
        ('purchase-receipt-open', 'Ver archivo'),
        ('cancel-purchase', 'Anular'),
      ],
      'users' => const [
        ('toggle-user', 'Activar/desactivar'),
        ('reset-user', 'Restablecer contraseña'),
        ('assign-user-point', 'Asignar punto de expedición'),
      ],
      'suppliers' => const [
        ('edit-supplier', 'Editar'),
        ('supplier-offer', 'Oferta de producto'),
        ('toggle-supplier', 'Activar/desactivar'),
      ],
      'products-admin' => const [
        ('edit-product', 'Editar'),
        ('product-image', 'Cámara/galería'),
        ('delete-product', 'Dar de baja'),
      ],
      'shipping-rates' => const [('edit-rate', 'Editar')],
      'invoices' => const [
        ('invoice-pdf', 'Factura PDF'),
        ('invoice-kude', 'KuDE'),
        ('invoice-xml', 'Documento XML'),
      ],
      'credit-notes' => const [('credit-note-pdf', 'Nota de crédito PDF')],
      'expedition-points' => const [('toggle-point', 'Activar/desactivar')],
      'notifications' =>
        row['leida'] == true
            ? const []
            : const [('read-notification', 'Marcar leída')],
      'conversations' => const [('close-conversation', 'Cerrar')],
      _ => const [],
    };

_DocumentSpec? _resourceDocument(String key) => switch (key) {
  'cash' => const _DocumentSpec('/api/v1/caja/resumen/pdf', 'resumen-caja.pdf'),
  'sales-report' => const _DocumentSpec(
    '/api/v1/reportes/ventas/pdf',
    'reporte-ventas.pdf',
  ),
  'low-stock-report' => const _DocumentSpec(
    '/api/v1/reportes/stock-bajo/pdf',
    'stock-bajo.pdf',
  ),
  _ => null,
};

final class _DocumentSpec {
  const _DocumentSpec(this.path, this.filename, {this.printable = true});
  final String path;
  final String filename;
  final bool printable;
}

List<Object?> _rows(Object? value) {
  if (value is List) return value;
  if (value is Map) {
    final content = value['content'];
    if (content is List) return content;
  }
  return value == null ? const [] : [value];
}

String _first(Map<String, Object?> map, List<String> fields) {
  for (final field in fields) {
    final value = map[field];
    if (value != null && value.toString().isNotEmpty) return value.toString();
  }
  return map['id'] == null ? '' : '#${map['id']}';
}

String _secondary(Map<String, Object?> map, List<String> fields) => fields
    .where((field) => map[field] != null)
    .map((field) => '${_label(field)}: ${map[field]}')
    .join(' · ');

String _label(String value) => value
    .replaceAllMapped(RegExp(r'([A-Z])'), (match) => ' ${match.group(1)}')
    .toLowerCase();
