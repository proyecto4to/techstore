import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/format/money.dart';
import '../application/payments_providers.dart';
import '../domain/payment_models.dart';

/// A dónde transferir y cuánto, con cada dato listo para copiar.
///
/// Es lo que la persona tiene abierto al lado de la app del banco: por eso el
/// monto va arriba y grande, cada dato tiene su botón de copiar y el QR se
/// puede agrandar para escanearlo desde otro teléfono.
class TransferInstructions extends ConsumerWidget {
  const TransferInstructions({
    required this.details,
    required this.amount,
    required this.currency,
    super.key,
  });

  final TransferDetails details;
  final double amount;
  final String currency;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final marca = MarcaTema.of(context);
    final montoVisible = formatMoney(amount, currency);
    return Container(
      decoration: BoxDecoration(
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: Paleta.borde),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Paleta.azulProfundo, Paleta.superficie],
        ),
      ),
      padding: const EdgeInsets.all(Medidas.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Transferí exactamente', style: marca.rotulo),
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(
                child: FittedBox(
                  alignment: Alignment.centerLeft,
                  fit: BoxFit.scaleDown,
                  child: Text(
                    montoVisible,
                    style: theme.textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: Paleta.texto,
                    ),
                  ),
                ),
              ),
              _CopiarBoton(
                // A la app del banco se le pega el número sin puntos: con el
                // separador de miles, algunas lo toman como decimales.
                valor: _montoParaCopiar(amount, currency),
                aviso: 'Monto copiado',
                etiqueta: 'Copiar monto',
              ),
            ],
          ),
          const SizedBox(height: Medidas.md),
          const Divider(height: 1),
          const SizedBox(height: Medidas.sm),
          for (final (etiqueta, valor) in details.rows)
            _Dato(etiqueta: etiqueta, valor: valor),
          if (details.instructions != null) ...[
            const SizedBox(height: Medidas.sm),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.info_outline, size: 18, color: Paleta.cian),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    details.instructions!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Paleta.textoSecundario,
                    ),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: Medidas.md),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: () => _copiar(
                  context,
                  details.asText(amount: _montoParaCopiar(amount, currency)),
                  'Datos copiados. Pegalos en la app de tu banco.',
                ),
                icon: const Icon(Icons.copy_all_outlined, size: 18),
                label: const Text('Copiar todo'),
              ),
              if (details.hasQr)
                OutlinedButton.icon(
                  onPressed: () => _verQr(context),
                  icon: const Icon(Icons.qr_code_2, size: 18),
                  label: const Text('Ver QR'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _verQr(BuildContext context) => showDialog<void>(
    context: context,
    builder: (context) => const _QrDialog(),
  );
}

String _montoParaCopiar(double amount, String currency) =>
    currency == 'PYG' ? amount.round().toString() : amount.toStringAsFixed(2);

void _copiar(BuildContext context, String valor, String aviso) {
  Clipboard.setData(ClipboardData(text: valor));
  HapticFeedback.selectionClick();
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(content: Text(aviso), duration: const Duration(seconds: 2)),
    );
}

class _Dato extends StatelessWidget {
  const _Dato({required this.etiqueta, required this.valor});

  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  etiqueta,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: Paleta.textoSecundario,
                  ),
                ),
                SelectableText(
                  valor,
                  style: theme.textTheme.bodyLarge?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          _CopiarBoton(
            valor: valor,
            aviso: '$etiqueta copiado',
            etiqueta: 'Copiar $etiqueta',
          ),
        ],
      ),
    );
  }
}

class _CopiarBoton extends StatelessWidget {
  const _CopiarBoton({
    required this.valor,
    required this.aviso,
    required this.etiqueta,
  });

  final String valor;
  final String aviso;
  final String etiqueta;

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: etiqueta,
    onPressed: () => _copiar(context, valor, aviso),
    icon: const Icon(Icons.content_copy_rounded, size: 20),
  );
}

class _QrDialog extends ConsumerWidget {
  const _QrDialog();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final qr = ref.watch(transferQrProvider);
    return AlertDialog(
      title: const Text('Escaneá con tu banco'),
      content: SizedBox(
        width: 280,
        height: 280,
        child: qr.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => const Center(
            child: Text(
              'No pudimos cargar el QR. Usá los datos de la cuenta.',
              textAlign: TextAlign.center,
            ),
          ),
          // Fondo blanco: un QR sobre el negro del tema no lo lee ninguna
          // cámara.
          data: (bytes) => Container(
            color: Colors.white,
            padding: const EdgeInsets.all(12),
            child: Image.memory(bytes, fit: BoxFit.contain),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Listo'),
        ),
      ],
    );
  }
}
