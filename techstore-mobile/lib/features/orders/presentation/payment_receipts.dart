import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/image_origin_sheet.dart';

/// Cuántos respaldos admite un pedido. Lo mismo que valida el servidor.
const int maximoComprobantes = 3;

final comprobantesProvider = FutureProvider.autoDispose
    .family<List<Map<String, Object?>>, int>((ref, pedidoId) async {
      final respuesta = await ref
          .watch(apiClientProvider)
          .get<List<dynamic>>('/api/v1/pedidos/$pedidoId/comprobantes');
      return (respuesta.data ?? const [])
          .map((e) => Map<String, Object?>.from(e as Map))
          .toList(growable: false);
    });

/// Los comprobantes con los que se respalda el pago de un pedido.
///
/// Aparece solo cuando el pedido se paga por transferencia: en los otros
/// métodos no hay nada que adjuntar, y ofrecerlo sería pedir algo que no
/// corresponde.
class PaymentReceipts extends ConsumerStatefulWidget {
  const PaymentReceipts({required this.orderId, super.key});

  final int orderId;

  @override
  ConsumerState<PaymentReceipts> createState() => _PaymentReceiptsState();
}

class _PaymentReceiptsState extends ConsumerState<PaymentReceipts> {
  bool _ocupado = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final comprobantes = ref.watch(comprobantesProvider(widget.orderId));
    return comprobantes.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: LinearProgressIndicator(),
      ),
      // Si no se pudo leer la lista, no se bloquea el pedido: es información
      // complementaria, igual que el envío.
      error: (error, _) => const SizedBox.shrink(),
      data: (lista) {
        final cuantos = lista.length;
        final completo = cuantos >= maximoComprobantes;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Divider(height: 32),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Comprobantes del pago',
                  style: theme.textTheme.titleSmall,
                ),
                Text(
                  '$cuantos/$maximoComprobantes',
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: completo
                        ? theme.colorScheme.onSurfaceVariant
                        : theme.colorScheme.primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              completo
                  ? 'Llegaste al máximo. Quitá uno si querés cambiarlo.'
                  : 'Podés adjuntar hasta $maximoComprobantes fotos del pago.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            if (lista.isEmpty)
              Text(
                'Todavía no adjuntaste ninguno.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              )
            else
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  for (final comprobante in lista)
                    _Miniatura(
                      comprobante: comprobante,
                      onQuitar: _ocupado
                          ? null
                          : () => _quitar((comprobante['id'] as num).toInt()),
                    ),
                ],
              ),
            const SizedBox(height: 12),
            FilledButton.tonalIcon(
              onPressed: _ocupado || completo ? null : _adjuntar,
              icon: const Icon(Icons.add_a_photo_outlined),
              label: Text(
                lista.isEmpty ? 'Adjuntar comprobante' : 'Adjuntar otro',
              ),
            ),
            if (_ocupado)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: LinearProgressIndicator(),
              ),
          ],
        );
      },
    );
  }

  Future<void> _adjuntar() async {
    final origen = await pickImageOrigin(
      context,
      cameraLabel: 'Sacar foto del comprobante',
      galleryLabel: 'Elegir de la galería',
    );
    if (origen == null || !mounted) return;
    setState(() => _ocupado = true);
    try {
      final subido = await ref
          .read(imageUploadServiceProvider)
          .pickAndUploadPaymentReceipt(widget.orderId, origen);
      if (subido) {
        ref.invalidate(comprobantesProvider(widget.orderId));
        _avisar('Comprobante adjuntado.');
      }
    } on AppFailure catch (falla) {
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _quitar(int comprobanteId) async {
    final confirmado = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿Quitar este comprobante?'),
        content: const Text(
          'Se borra del pedido. Vas a poder adjuntar otro en su lugar.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Quitar'),
          ),
        ],
      ),
    );
    if (confirmado != true || !mounted) return;
    setState(() => _ocupado = true);
    try {
      await ref
          .read(apiClientProvider)
          .delete<void>(
            '/api/v1/pedidos/${widget.orderId}/comprobantes/$comprobanteId',
          );
      ref.invalidate(comprobantesProvider(widget.orderId));
      _avisar('Comprobante quitado.');
    } on AppFailure catch (falla) {
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

class _Miniatura extends StatelessWidget {
  const _Miniatura({required this.comprobante, required this.onQuitar});

  final Map<String, Object?> comprobante;
  final VoidCallback? onQuitar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final esPdf = comprobante['tipo']?.toString() == 'application/pdf';
    final kb = ((comprobante['tamanoBytes'] as num?)?.toDouble() ?? 0) / 1024;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Container(
          width: 96,
          height: 96,
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: theme.colorScheme.outlineVariant),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                esPdf ? Icons.picture_as_pdf_outlined : Icons.image_outlined,
                size: 30,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 4),
              Text(
                '${kb.round()} KB',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        Positioned(
          top: -8,
          right: -8,
          child: IconButton.filledTonal(
            tooltip: 'Quitar comprobante',
            visualDensity: VisualDensity.compact,
            onPressed: onQuitar,
            icon: const Icon(Icons.close, size: 16),
          ),
        ),
      ],
    );
  }
}
