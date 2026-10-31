import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/operation_confirm.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/receipt_picker.dart';
import '../../../core/widgets/async_states.dart';
import '../application/payments_providers.dart';
import '../domain/payment_models.dart';

/// A qué cuenta transfieren los clientes.
///
/// Sin estos datos no se ofrece la transferencia, y sin transferencia no hay
/// envío a domicilio: por eso la pantalla dice arriba si la tienda ya puede
/// cobrar así. Guardar pide confirmar la identidad, porque cambiar la cuenta es
/// lo primero que haría alguien para desviar los pagos.
class TransferSettingsPage extends ConsumerStatefulWidget {
  const TransferSettingsPage({super.key});

  @override
  ConsumerState<TransferSettingsPage> createState() =>
      _TransferSettingsPageState();
}

class _TransferSettingsPageState extends ConsumerState<TransferSettingsPage> {
  final _form = GlobalKey<FormState>();
  final _banco = TextEditingController();
  final _titular = TextEditingController();
  final _documento = TextEditingController();
  final _cuenta = TextEditingController();
  final _alias = TextEditingController();
  final _instrucciones = TextEditingController();
  String? _tipoCuenta;
  bool _cargado = false;
  bool _busy = false;

  static const _tipos = ['Cuenta corriente', 'Caja de ahorro'];

  @override
  void dispose() {
    for (final c in [
      _banco,
      _titular,
      _documento,
      _cuenta,
      _alias,
      _instrucciones,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _llenar(TransferDetails d) {
    if (_cargado) return;
    _cargado = true;
    _banco.text = d.bank ?? '';
    _titular.text = d.holder ?? '';
    _documento.text = d.document ?? '';
    _cuenta.text = d.accountNumber ?? '';
    _alias.text = d.alias ?? '';
    _instrucciones.text = d.instructions ?? '';
    _tipoCuenta = _tipos.contains(d.accountType) ? d.accountType : null;
  }

  @override
  Widget build(BuildContext context) {
    final details = ref.watch(transferDetailsProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Cobro por transferencia')),
      body: details.when(
        loading: () => const AppLoading(label: 'Cargando los datos'),
        error: (error, _) => AppErrorState(
          message: 'No pudimos cargar los datos para transferir.',
          onRetry: () => ref.invalidate(transferDetailsProvider),
        ),
        data: (d) {
          _llenar(d);
          return Form(
            key: _form,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 120),
              children: [
                _Disponibilidad(disponible: d.available),
                const SizedBox(height: 20),
                TextFormField(
                  controller: _banco,
                  maxLength: 80,
                  decoration: const InputDecoration(labelText: 'Banco'),
                  validator: _obligatorio,
                ),
                TextFormField(
                  controller: _titular,
                  maxLength: 120,
                  decoration: const InputDecoration(
                    labelText: 'Titular de la cuenta',
                  ),
                  validator: _obligatorio,
                ),
                TextFormField(
                  controller: _documento,
                  maxLength: 30,
                  decoration: const InputDecoration(
                    labelText: 'RUC o CI del titular',
                  ),
                ),
                DropdownButtonFormField<String>(
                  initialValue: _tipoCuenta,
                  decoration: const InputDecoration(
                    labelText: 'Tipo de cuenta',
                  ),
                  items: [
                    for (final tipo in _tipos)
                      DropdownMenuItem(value: tipo, child: Text(tipo)),
                  ],
                  onChanged: (v) => setState(() => _tipoCuenta = v),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _cuenta,
                  maxLength: 40,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Número de cuenta',
                  ),
                  validator: (_) =>
                      _cuenta.text.trim().isEmpty && _alias.text.trim().isEmpty
                      ? 'Cargá el número de cuenta o un alias'
                      : null,
                ),
                TextFormField(
                  controller: _alias,
                  maxLength: 80,
                  decoration: const InputDecoration(
                    labelText: 'Alias (SIPAP)',
                    helperText:
                        'Cédula, celular o correo, si la cuenta tiene uno',
                  ),
                ),
                TextFormField(
                  controller: _instrucciones,
                  maxLength: 300,
                  maxLines: 3,
                  minLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'Aclaración para el cliente',
                    helperText: 'Opcional. Por ejemplo: «Poné el número de pedido en el concepto».',
                  ),
                ),
                const SizedBox(height: 20),
                Text('QR del banco', style: theme.textTheme.titleSmall),
                const SizedBox(height: 4),
                Text(
                  'La imagen del QR que genera la app de tu banco para cobrar. '
                  'Es opcional: sin QR, el cliente transfiere con los datos de arriba.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                _QrActual(
                  tieneQr: d.hasQr,
                  busy: _busy,
                  onSubir: _subirQr,
                  onQuitar: _quitarQr,
                ),
              ],
            ),
          );
        },
      ),
      bottomNavigationBar: details.hasValue
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: FilledButton.icon(
                  onPressed: _busy ? null : _guardar,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                  ),
                  icon: _busy
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.save_outlined),
                  label: const Text('Guardar datos'),
                ),
              ),
            )
          : null,
    );
  }

  String? _obligatorio(String? v) =>
      v == null || v.trim().isEmpty ? 'Este dato es obligatorio' : null;

  Future<void> _guardar() async {
    if (!(_form.currentState?.validate() ?? false)) return;
    final confirmado = await confirmarOperacion(
      context,
      ref,
      reason: 'Confirmá el cambio de la cuenta para cobrar',
      titulo: 'Cambiar datos de cobro',
    );
    if (!confirmado) return;
    await _ejecutar(() async {
      await ref
          .read(paymentsRepositoryProvider)
          .saveTransferDetails(
            TransferDetails(
              available: true,
              bank: _banco.text.trim(),
              holder: _titular.text.trim(),
              document: _documento.text.trim(),
              accountNumber: _cuenta.text.trim(),
              accountType: _tipoCuenta,
              alias: _alias.text.trim(),
              instructions: _instrucciones.text.trim(),
            ),
          );
    }, 'Datos guardados. Los clientes ya pueden pagar por transferencia.');
  }

  Future<void> _subirQr() async {
    PickedReceipt? imagen;
    try {
      imagen = await ref
          .read(receiptPickerProvider)
          .pick(ReceiptSource.gallery);
    } on AppFailure catch (failure) {
      _avisar(failure.message);
      return;
    }
    if (imagen == null) return;
    if (imagen.isPdf) {
      _avisar('El QR tiene que ser una imagen.');
      return;
    }
    final elegida = imagen;
    await _ejecutar(
      () => ref.read(paymentsRepositoryProvider).uploadTransferQr(elegida),
      'QR actualizado.',
    );
    ref.invalidate(transferQrProvider);
  }

  Future<void> _quitarQr() async {
    await _ejecutar(
      () => ref.read(paymentsRepositoryProvider).deleteTransferQr(),
      'QR quitado.',
    );
    ref.invalidate(transferQrProvider);
  }

  Future<void> _ejecutar(Future<void> Function() accion, String exito) async {
    setState(() => _busy = true);
    try {
      await accion();
      _cargado = false;
      ref.invalidate(transferDetailsProvider);
      _avisar(exito);
    } on AppFailure catch (failure) {
      _avisar(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

class _Disponibilidad extends StatelessWidget {
  const _Disponibilidad({required this.disponible});

  final bool disponible;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = disponible ? Paleta.exito : Paleta.aviso;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: disponible ? Paleta.exitoHundido : Paleta.avisoHundido,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: color.withValues(alpha: .5)),
      ),
      child: Row(
        children: [
          Icon(
            disponible ? Icons.check_circle : Icons.warning_amber_rounded,
            color: color,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              disponible
                  ? 'Los clientes pueden pagar por transferencia y pedir envíos a domicilio.'
                  : 'Todavía no se puede cobrar por transferencia, y por eso tampoco '
                        'hay envíos a domicilio. Cargá banco, titular y una cuenta o un alias.',
              style: theme.textTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }
}

class _QrActual extends ConsumerWidget {
  const _QrActual({
    required this.tieneQr,
    required this.busy,
    required this.onSubir,
    required this.onQuitar,
  });

  final bool tieneQr;
  final bool busy;
  final VoidCallback onSubir;
  final VoidCallback onQuitar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final qr = tieneQr ? ref.watch(transferQrProvider) : null;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 120,
          height: 120,
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: tieneQr ? Colors.white : Paleta.superficie,
            borderRadius: BorderRadius.circular(Medidas.radioChico),
            border: Border.all(color: Paleta.borde),
          ),
          child: qr == null
              ? const Center(
                  child: Icon(
                    Icons.qr_code_2,
                    size: 48,
                    color: Paleta.textoSecundario,
                  ),
                )
              : qr.when(
                  loading: () =>
                      const Center(child: CircularProgressIndicator()),
                  error: (_, _) => const Icon(Icons.broken_image_outlined),
                  data: (bytes) => Image.memory(bytes, fit: BoxFit.contain),
                ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              OutlinedButton.icon(
                onPressed: busy ? null : onSubir,
                icon: const Icon(Icons.upload_outlined),
                label: Text(tieneQr ? 'Cambiar QR' : 'Subir QR'),
              ),
              if (tieneQr)
                TextButton.icon(
                  onPressed: busy ? null : onQuitar,
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Quitar'),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
