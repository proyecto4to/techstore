import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:printing/printing.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/document_service.dart';
import '../application/invoice_providers.dart';
import '../domain/invoice.dart';

class InvoicesPage extends ConsumerStatefulWidget {
  const InvoicesPage({super.key});

  @override
  ConsumerState<InvoicesPage> createState() => _InvoicesPageState();
}

class _InvoicesPageState extends ConsumerState<InvoicesPage> {
  int _page = 0;

  @override
  Widget build(BuildContext context) {
    final result = ref.watch(invoicesProvider(_page));
    return Scaffold(
      appBar: AppBar(title: const Text('Facturas')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(invoicesProvider(_page).future),
        child: result.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _MessageList(
            icon: Icons.error_outline,
            message: 'No se pudieron cargar las facturas.',
            actionLabel: 'Reintentar',
            onAction: () => ref.invalidate(invoicesProvider(_page)),
          ),
          data: (data) => data.items.isEmpty
              ? const _MessageList(
                  icon: Icons.receipt_long_outlined,
                  message: 'Todavía no hay facturas para mostrar.',
                )
              : ListView(
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
                      child: Text(
                        '${data.totalElements} ${data.totalElements == 1 ? 'factura' : 'facturas'}',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ),
                    for (final invoice in data.items)
                      _InvoiceCard(
                        invoice: invoice,
                        onTap: () => context.push('/invoices/${invoice.id}'),
                      ),
                    if (data.totalPages > 1)
                      _Pagination(
                        currentPage: data.page,
                        totalPages: data.totalPages,
                        onPrevious: data.page > 0
                            ? () => setState(() => _page--)
                            : null,
                        onNext: data.page + 1 < data.totalPages
                            ? () => setState(() => _page++)
                            : null,
                      ),
                  ],
                ),
        ),
      ),
    );
  }
}

class InvoiceDetailPage extends ConsumerWidget {
  const InvoiceDetailPage({required this.invoiceId, super.key});

  final int invoiceId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final invoice = ref.watch(invoiceProvider(invoiceId));
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle de factura')),
      body: invoice.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _MessageList(
          icon: Icons.error_outline,
          message: 'No se pudo cargar esta factura.',
          actionLabel: 'Reintentar',
          onAction: () => ref.invalidate(invoiceProvider(invoiceId)),
        ),
        data: (value) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Factura ${value.number}',
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(_dateTime(value.date)),
                    ],
                  ),
                ),
                _StatusChip(status: value.status),
              ],
            ),
            const SizedBox(height: 20),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    _InfoRow(label: 'Pedido', value: '#${value.orderNumber}'),
                    _InfoRow(
                      label: 'Condición de venta',
                      value: value.saleCondition,
                    ),
                    if (value.stamping?.isNotEmpty ?? false)
                      _InfoRow(label: 'Timbrado', value: value.stamping!),
                    _InfoRow(label: 'Receptor', value: value.receiverLabel),
                    if (value.receiverRuc?.isNotEmpty ?? false)
                      _InfoRow(label: 'RUC', value: value.receiverRuc!),
                    if (value.receiverEmail?.isNotEmpty ?? false)
                      _InfoRow(label: 'Correo', value: value.receiverEmail!),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    _AmountRow(
                      label: 'Subtotal',
                      value: _money(value.subtotal, value.currency),
                    ),
                    _AmountRow(
                      label: 'IVA',
                      value: _money(value.tax, value.currency),
                    ),
                    const Divider(),
                    _AmountRow(
                      label: 'Total',
                      value: _money(value.total, value.currency),
                      emphasized: true,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: () => _showDocumentActions(context, ref, value),
              icon: const Icon(Icons.picture_as_pdf_outlined),
              label: const Text('Ver factura PDF'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showDocumentActions(
    BuildContext context,
    WidgetRef ref,
    Invoice invoice,
  ) async {
    final action = await showModalBottomSheet<DocumentAction>(
      context: context,
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            for (final item in const [
              (DocumentAction.open, Icons.visibility_outlined, 'Ver en la app'),
              (DocumentAction.share, Icons.share_outlined, 'Compartir'),
              (DocumentAction.print, Icons.print_outlined, 'Imprimir'),
            ])
              ListTile(
                leading: Icon(item.$2),
                title: Text(item.$3),
                onTap: () => Navigator.pop(context, item.$1),
              ),
          ],
        ),
      ),
    );
    if (action == null || !context.mounted) return;
    if (action == DocumentAction.open) {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (context) => InvoicePdfPreviewPage(invoice: invoice),
        ),
      );
      return;
    }
    try {
      await ref
          .read(documentServiceProvider)
          .handle(
            '/api/v1/facturas/${invoice.id}/pdf',
            'factura-${invoice.number}.pdf',
            action,
          );
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No se pudo abrir la factura.')),
        );
      }
    }
  }
}

class InvoicePdfPreviewPage extends ConsumerStatefulWidget {
  const InvoicePdfPreviewPage({required this.invoice, super.key});

  final Invoice invoice;

  @override
  ConsumerState<InvoicePdfPreviewPage> createState() =>
      _InvoicePdfPreviewPageState();
}

class _InvoicePdfPreviewPageState extends ConsumerState<InvoicePdfPreviewPage> {
  late Future<Uint8List> _document;

  @override
  void initState() {
    super.initState();
    _document = _download();
  }

  Future<Uint8List> _download() async {
    return ref
        .read(documentServiceProvider)
        .bytes('/api/v1/facturas/${widget.invoice.id}/pdf');
  }

  void _retry() => setState(() => _document = _download());

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('Factura ${widget.invoice.number}')),
    body: FutureBuilder<Uint8List>(
      future: _document,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError || !snapshot.hasData) {
          final message = snapshot.error is AppFailure
              ? (snapshot.error! as AppFailure).message
              : 'No se pudo descargar la factura.';
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(message, textAlign: TextAlign.center),
                  const SizedBox(height: 16),
                  FilledButton.tonalIcon(
                    onPressed: _retry,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Reintentar descarga'),
                  ),
                ],
              ),
            ),
          );
        }
        return PdfPreview(
          build: (_) async => snapshot.data!,
          pdfFileName: 'factura-${widget.invoice.number}.pdf',
          canChangeOrientation: false,
          canChangePageFormat: false,
          allowPrinting: true,
          allowSharing: true,
        );
      },
    ),
  );
}

class _InvoiceCard extends StatelessWidget {
  const _InvoiceCard({required this.invoice, required this.onTap});

  final Invoice invoice;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.receipt_long_outlined),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Factura ${invoice.number}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                _StatusChip(status: invoice.status),
              ],
            ),
            const SizedBox(height: 12),
            Text(invoice.receiverLabel),
            Text(
              '${_dateTime(invoice.date)} · Pedido #${invoice.orderNumber}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Text('Total', style: Theme.of(context).textTheme.bodyMedium),
                const Spacer(),
                Text(
                  _money(invoice.total, invoice.currency),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(width: 4),
                const Icon(Icons.chevron_right),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final normalized = status.toLowerCase();
    final color = normalized == 'pagada'
        ? scheme.primaryContainer
        : normalized == 'anulada' || normalized == 'cancelada'
        ? scheme.errorContainer
        : scheme.secondaryContainer;
    return Chip(
      visualDensity: VisualDensity.compact,
      backgroundColor: color,
      label: Text(status.isEmpty ? 'Sin estado' : status),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 132,
          child: Text(label, style: Theme.of(context).textTheme.bodySmall),
        ),
        Expanded(child: Text(value)),
      ],
    ),
  );
}

class _AmountRow extends StatelessWidget {
  const _AmountRow({
    required this.label,
    required this.value,
    this.emphasized = false,
  });

  final String label;
  final String value;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final style = emphasized ? Theme.of(context).textTheme.titleMedium : null;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Text(label, style: style),
          const Spacer(),
          Text(value, style: style),
        ],
      ),
    );
  }
}

class _Pagination extends StatelessWidget {
  const _Pagination({
    required this.currentPage,
    required this.totalPages,
    required this.onPrevious,
    required this.onNext,
  });

  final int currentPage;
  final int totalPages;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        IconButton.filledTonal(
          tooltip: 'Página anterior',
          onPressed: onPrevious,
          icon: const Icon(Icons.chevron_left),
        ),
        Text('Página ${currentPage + 1} de $totalPages'),
        IconButton.filledTonal(
          tooltip: 'Página siguiente',
          onPressed: onNext,
          icon: const Icon(Icons.chevron_right),
        ),
      ],
    ),
  );
}

class _MessageList extends StatelessWidget {
  const _MessageList({
    required this.icon,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    children: [
      const SizedBox(height: 160),
      Icon(icon, size: 48),
      const SizedBox(height: 12),
      Center(child: Text(message)),
      if (actionLabel != null && onAction != null) ...[
        const SizedBox(height: 16),
        Center(
          child: FilledButton.tonal(
            onPressed: onAction,
            child: Text(actionLabel!),
          ),
        ),
      ],
    ],
  );
}

String _dateTime(DateTime value) =>
    DateFormat.yMd('es_PY').add_Hm().format(value.toLocal());

String _money(double value, String currency) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: currency == 'PYG' ? '₲' : currency,
  decimalDigits: currency == 'PYG' ? 0 : 2,
).format(value);
