import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/documento_page.dart';
import '../../checkout/application/checkout_providers.dart';
import '../application/payments_providers.dart';
import '../domain/payment_models.dart';
import 'receipt_widgets.dart';

/// La bandeja de quien cobra: las transferencias que esperan una decisión.
///
/// Va del pedido que más espera al más nuevo. Al lado, los que esperan algo
/// del cliente —nunca mandó el comprobante o se le rechazó—, para saber a
/// quién escribirle.
class VerifyPaymentsPage extends ConsumerStatefulWidget {
  const VerifyPaymentsPage({super.key});

  @override
  ConsumerState<VerifyPaymentsPage> createState() => _VerifyPaymentsPageState();
}

class _VerifyPaymentsPageState extends ConsumerState<VerifyPaymentsPage> {
  bool _esperandoCliente = false;

  @override
  Widget build(BuildContext context) {
    final todos = ref.watch(pendingPaymentsProvider(true));
    return Scaffold(
      appBar: AppBar(title: const Text('Verificar pagos')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: SegmentedButton<bool>(
              segments: [
                ButtonSegment(
                  value: false,
                  icon: const Icon(Icons.fact_check_outlined),
                  label: Text(
                    'Para revisar${_cuenta(todos.value, revisar: true)}',
                  ),
                ),
                ButtonSegment(
                  value: true,
                  icon: const Icon(Icons.hourglass_bottom),
                  label: Text(
                    'Esperando cliente${_cuenta(todos.value, revisar: false)}',
                  ),
                ),
              ],
              selected: {_esperandoCliente},
              onSelectionChanged: (s) =>
                  setState(() => _esperandoCliente = s.first),
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () =>
                  ref.refresh(pendingPaymentsProvider(true).future),
              child: todos.when(
                skipLoadingOnRefresh: true,
                loading: () => const AppLoading(label: 'Cargando los pagos'),
                error: (error, _) => AppErrorState(
                  message: error is AppFailure
                      ? error.message
                      : 'No pudimos cargar los pagos para verificar.',
                  onRetry: () => ref.invalidate(pendingPaymentsProvider(true)),
                ),
                data: (items) {
                  final visibles = [
                    for (final item in items)
                      if ((item.state == PaymentState.inVerification) !=
                          _esperandoCliente)
                        item,
                  ];
                  if (visibles.isEmpty) {
                    return ListView(
                      children: [
                        const SizedBox(height: 100),
                        AppEmptyState(
                          icon: _esperandoCliente
                              ? Icons.mark_email_read_outlined
                              : Icons.task_alt,
                          message: _esperandoCliente
                              ? 'Ningún cliente tiene un pago pendiente de su lado.'
                              : 'No hay transferencias para revisar.\n'
                                    'Te avisamos cuando llegue un comprobante.',
                        ),
                      ],
                    );
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
                    itemCount: visibles.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, index) => Aparicion(
                      orden: index.clamp(0, 8),
                      child: _PagoPendiente(
                        item: visibles[index],
                        onTap: () =>
                            context.push('/pagos/${visibles[index].orderId}'),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _cuenta(List<PendingPayment>? items, {required bool revisar}) {
    if (items == null) return '';
    final n = items
        .where((i) => (i.state == PaymentState.inVerification) == revisar)
        .length;
    return n == 0 ? '' : ' ($n)';
  }
}

class _PagoPendiente extends StatelessWidget {
  const _PagoPendiente({required this.item, required this.onTap});

  final PendingPayment item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final espera = DateTime.now().difference(item.date.toLocal());
    final (etiqueta, color) = switch (item.state) {
      PaymentState.inVerification => (
        item.receiptsInReview == 1
            ? '1 comprobante'
            : '${item.receiptsInReview} comprobantes',
        Paleta.cian,
      ),
      PaymentState.rejected => (
        'Rechazado, espera otro',
        theme.colorScheme.error,
      ),
      _ => ('Sin comprobante', Paleta.aviso),
    };
    return Material(
      color: Paleta.superficie,
      shape: RoundedRectangleBorder(
        borderRadius: Medidas.bordeTarjeta,
        side: const BorderSide(color: Paleta.borde),
      ),
      child: InkWell(
        borderRadius: Medidas.bordeTarjeta,
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: color.withValues(alpha: .15),
                child: Icon(
                  item.delivery == 'ENVIO_DOMICILIO'
                      ? Icons.local_shipping_outlined
                      : Icons.storefront_outlined,
                  color: color,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Pedido N.º ${item.number}',
                      style: theme.textTheme.titleSmall,
                    ),
                    Text(
                      item.customerName.isEmpty
                          ? item.customerEmail
                          : item.customerName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '$etiqueta · ${_hace(espera)}',
                      style: theme.textTheme.labelSmall?.copyWith(color: color),
                    ),
                  ],
                ),
              ),
              Text(
                formatMoney(item.total, item.currency),
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _hace(Duration espera) {
  if (espera.inMinutes < 1) return 'recién';
  if (espera.inMinutes < 60) return 'hace ${espera.inMinutes} min';
  if (espera.inHours < 24) return 'hace ${espera.inHours} h';
  final dias = espera.inDays;
  return dias == 1 ? 'hace 1 día' : 'hace $dias días';
}

/// Un pago por revisar: el monto, el comprobante grande y la decisión.
class VerifyPaymentDetailPage extends ConsumerStatefulWidget {
  const VerifyPaymentDetailPage({required this.orderId, super.key});

  final int orderId;

  @override
  ConsumerState<VerifyPaymentDetailPage> createState() =>
      _VerifyPaymentDetailPageState();
}

class _VerifyPaymentDetailPageState
    extends ConsumerState<VerifyPaymentDetailPage> {
  bool _busy = false;

  static const _motivos = [
    'El monto no coincide con el total del pedido',
    'La transferencia todavía no se acreditó en la cuenta',
    'El comprobante no se lee bien',
    'La transferencia fue a otra cuenta',
  ];

  @override
  Widget build(BuildContext context) {
    final order = ref.watch(orderProvider(widget.orderId));
    final receipts = ref.watch(orderReceiptsProvider(widget.orderId));
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Revisar pago')),
      body: order.when(
        loading: () => const AppLoading(label: 'Cargando el pedido'),
        error: (error, _) => AppErrorState(
          message: 'No pudimos cargar este pedido.',
          onRetry: () => ref.invalidate(orderProvider(widget.orderId)),
        ),
        data: (data) {
          final currency = data['moneda']?.toString() ?? 'PYG';
          final total = (data['total'] as num?)?.toDouble() ?? 0;
          final state = PaymentState.parse(data['estadoPago']?.toString());
          final fecha = DateTime.tryParse(data['fecha']?.toString() ?? '');
          final detalles = data['detalles'] as List? ?? const [];
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 120),
            children: [
              Text(
                'Pedido N.º ${data['numero']}',
                style: theme.textTheme.headlineSmall,
              ),
              Text(
                [
                  data['usuarioEmail']?.toString() ?? '',
                  if (fecha != null)
                    DateFormat(
                      "d 'de' MMMM, HH:mm",
                      'es',
                    ).format(fecha.toLocal()),
                ].join(' · '),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  borderRadius: Medidas.bordeTarjeta,
                  gradient: const LinearGradient(
                    colors: [Paleta.azulProfundo, Paleta.superficie],
                  ),
                  border: Border.all(color: Paleta.borde),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Tiene que haber llegado',
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: Paleta.textoSecundario,
                      ),
                    ),
                    Text(
                      formatMoney(total, currency),
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      data['metodoEntrega'] == 'ENVIO_DOMICILIO'
                          ? 'Envío a domicilio: sale apenas lo apruebes.'
                          : 'Retiro en la tienda.',
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Text('Comprobantes', style: theme.textTheme.titleSmall),
              const SizedBox(height: 10),
              receipts.when(
                loading: () => const LinearProgressIndicator(),
                error: (_, _) =>
                    const Text('No pudimos cargar los comprobantes.'),
                data: (lista) => lista.isEmpty
                    ? const Text('El cliente todavía no adjuntó ninguno.')
                    : Column(
                        children: [
                          for (final receipt in lista.reversed) ...[
                            _ComprobanteGrande(
                              orderId: widget.orderId,
                              receipt: receipt,
                            ),
                            const SizedBox(height: 12),
                          ],
                        ],
                      ),
              ),
              const SizedBox(height: 8),
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: Text('${detalles.length} productos'),
                children: [
                  for (final raw in detalles)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        (raw as Map)['productoNombre']?.toString() ?? '',
                      ),
                      trailing: Text('× ${raw['cantidad']}'),
                    ),
                ],
              ),
              if (state != PaymentState.inVerification) ...[
                const SizedBox(height: 12),
                Text(
                  state == PaymentState.approved
                      ? 'Este pago ya está aprobado.'
                      : 'Este pago no está esperando revisión.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          );
        },
      ),
      bottomNavigationBar:
          order.value == null ||
              PaymentState.parse(order.value!['estadoPago']?.toString()) !=
                  PaymentState.inVerification
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _busy ? null : _rechazar,
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size.fromHeight(52),
                          foregroundColor: theme.colorScheme.error,
                        ),
                        icon: const Icon(Icons.block),
                        label: const Text('Rechazar'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: FilledButton.icon(
                        onPressed: _busy ? null : _aprobar,
                        style: FilledButton.styleFrom(
                          minimumSize: const Size.fromHeight(52),
                        ),
                        icon: _busy
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.verified_outlined),
                        label: const Text('Aprobar pago'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Future<void> _aprobar() async {
    final referencia = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿La transferencia llegó?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Aprobar confirma el pedido, registra el cobro por transferencia, '
              'emite la factura y lo pasa a preparación.',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: referencia,
              maxLength: 50,
              decoration: const InputDecoration(
                labelText: 'N.º de operación del banco',
                helperText: 'Opcional. Sirve para conciliar con el extracto.',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Aprobar'),
          ),
        ],
      ),
    );
    final texto = referencia.text;
    referencia.dispose();
    if (ok != true || !mounted) return;
    final confirmado = await confirmarOperacion(
      context,
      ref,
      reason: 'Confirmá la aprobación del pago',
      titulo: 'Aprobar pago',
    );
    if (!confirmado) return;
    await _ejecutar(
      () => ref
          .read(paymentsRepositoryProvider)
          .approve(widget.orderId, reference: texto),
      'Pago aprobado. El pedido pasó a preparación.',
    );
  }

  Future<void> _rechazar() async {
    final motivo = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => const _MotivoDeRechazo(motivos: _motivos),
    );
    if (motivo == null || motivo.trim().isEmpty || !mounted) return;
    await _ejecutar(
      () => ref.read(paymentsRepositoryProvider).reject(widget.orderId, motivo),
      'Le avisamos al cliente para que mande otro comprobante.',
    );
  }

  Future<void> _ejecutar(Future<void> Function() accion, String exito) async {
    setState(() => _busy = true);
    final haptics = ref.read(hapticsServiceProvider);
    try {
      await accion();
      unawaited(haptics.play(HapticCue.success));
      ref
        ..invalidate(orderProvider(widget.orderId))
        ..invalidate(orderReceiptsProvider(widget.orderId))
        ..invalidate(pendingPaymentsProvider)
        ..invalidate(pendingPaymentsCountProvider)
        ..invalidate(ordersProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(exito)));
      if (context.canPop()) context.pop();
    } on AppFailure catch (failure) {
      await haptics.play(HapticCue.error);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(failure.message)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

class _MotivoDeRechazo extends StatefulWidget {
  const _MotivoDeRechazo({required this.motivos});

  final List<String> motivos;

  @override
  State<_MotivoDeRechazo> createState() => _MotivoDeRechazoState();
}

class _MotivoDeRechazoState extends State<_MotivoDeRechazo> {
  final _texto = TextEditingController();

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        16 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '¿Por qué no se puede validar?',
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          Text(
            'El cliente lo va a leer para corregirlo. El pedido sigue reservado.',
            style: theme.textTheme.bodySmall,
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final motivo in widget.motivos)
                ActionChip(
                  label: Text(motivo),
                  onPressed: () => setState(() => _texto.text = motivo),
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _texto,
            maxLength: 300,
            maxLines: 3,
            minLines: 2,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(labelText: 'Motivo'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _texto.text.trim().isEmpty
                ? null
                : () => Navigator.pop(context, _texto.text.trim()),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
            ),
            child: const Text('Rechazar y avisar al cliente'),
          ),
        ],
      ),
    );
  }
}

class _ComprobanteGrande extends ConsumerWidget {
  const _ComprobanteGrande({required this.orderId, required this.receipt});

  final int orderId;
  final PaymentReceipt receipt;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final archivo = receipt.isPdf
        ? null
        : ref.watch(orderReceiptFileProvider((orderId, receipt.id)));
    final subido = receipt.uploadedAt;
    return Container(
      decoration: BoxDecoration(
        color: Paleta.superficie,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: Paleta.borde),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            onTap: () {
              if (receipt.isPdf) {
                Navigator.of(context).push<void>(
                  MaterialPageRoute(
                    builder: (_) => DocumentoPage(
                      titulo: 'Comprobante del pedido',
                      endpoint:
                          '/api/v1/pedidos/$orderId/comprobantes/${receipt.id}/archivo',
                      nombreDeArchivo: 'comprobante-$orderId-${receipt.id}.pdf',
                      sePuedeImprimir: false,
                    ),
                  ),
                );
              } else if (archivo?.value != null) {
                showReceiptImage(context, archivo!.value!);
              }
            },
            child: SizedBox(
              height: 260,
              child: receipt.isPdf
                  ? const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.picture_as_pdf_outlined, size: 56),
                          SizedBox(height: 8),
                          Text('Tocá para abrir el PDF'),
                        ],
                      ),
                    )
                  : archivo!.when(
                      loading: () =>
                          const Center(child: CircularProgressIndicator()),
                      error: (_, _) => const Center(
                        child: Text('No se pudo cargar la imagen'),
                      ),
                      data: (bytes) => Image.memory(bytes, fit: BoxFit.contain),
                    ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(10),
            child: Row(
              children: [
                ReceiptReviewBadge(review: receipt.review),
                const SizedBox(width: 8),
                if (subido != null)
                  Expanded(
                    child: Text(
                      'Subido ${DateFormat("d MMM, HH:mm", 'es').format(subido.toLocal())}',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
              ],
            ),
          ),
          if (receipt.rejectionReason != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 0, 10, 10),
              child: Text(
                'Motivo: ${receipt.rejectionReason}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
