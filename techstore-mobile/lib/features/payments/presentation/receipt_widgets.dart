import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/native/receipt_picker.dart';
import '../domain/payment_models.dart';

/// Hoja para elegir de dónde sale el comprobante.
Future<ReceiptSource?> pickReceiptSource(BuildContext context) {
  return showModalBottomSheet<ReceiptSource>(
    context: context,
    showDragHandle: true,
    builder: (context) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
            child: Text(
              'Adjuntá el comprobante',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          for (final (fuente, icono, titulo, detalle) in const [
            (
              ReceiptSource.gallery,
              Icons.photo_library_outlined,
              'Captura de pantalla',
              'La que sacaste en la app del banco',
            ),
            (
              ReceiptSource.file,
              Icons.picture_as_pdf_outlined,
              'Archivo PDF',
              'El comprobante que descargaste',
            ),
            (
              ReceiptSource.camera,
              Icons.photo_camera_outlined,
              'Sacar una foto',
              'De la boleta en papel',
            ),
          ])
            ListTile(
              leading: Icon(icono),
              title: Text(titulo),
              subtitle: Text(detalle),
              onTap: () => Navigator.pop(context, fuente),
            ),
          const SizedBox(height: 8),
        ],
      ),
    ),
  );
}

/// La revisión de un comprobante, dicha para el cliente.
class ReceiptReviewBadge extends StatelessWidget {
  const ReceiptReviewBadge({required this.review, super.key});

  final ReceiptReview review;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    final (texto, color, icono) = switch (review) {
      ReceiptReview.inReview => ('En revisión', Paleta.cian, Icons.schedule),
      ReceiptReview.approved => ('Aprobado', marca.exito, Icons.verified),
      ReceiptReview.rejected => ('Rechazado', marca.error, Icons.block),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .14),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icono, size: 13, color: color),
          const SizedBox(width: 4),
          Text(
            texto,
            style: Theme.of(context).textTheme.labelSmall
                ?.copyWith(color: color, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

/// La miniatura de un comprobante: la foto, o un ícono si es un PDF.
///
/// Que se vea la foto es lo que permite darse cuenta antes de mandarla de que
/// se eligió la captura equivocada.
class ReceiptThumbnail extends StatelessWidget {
  const ReceiptThumbnail({
    required this.isPdf,
    super.key,
    this.bytes,
    this.loading = false,
    this.progress,
    this.size = 92,
    this.onTap,
  });

  final bool isPdf;
  final Uint8List? bytes;
  final bool loading;

  /// Cuánto se subió, de 0 a 1. Nulo si no se está subiendo.
  final double? progress;
  final double size;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final contenido = isPdf
        ? const Center(
            child: Icon(
              Icons.picture_as_pdf_outlined,
              size: 36,
              color: Paleta.textoSecundario,
            ),
          )
        : bytes != null
        ? Image.memory(
            bytes!,
            fit: BoxFit.cover,
            cacheWidth: (size * 3).round(),
            gaplessPlayback: true,
          )
        : Center(
            child: loading
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(
                    Icons.image_outlined,
                    color: Paleta.textoSecundario,
                  ),
          );
    return Semantics(
      button: onTap != null,
      label: isPdf ? 'Comprobante en PDF' : 'Foto del comprobante',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Medidas.radioChico),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(Medidas.radioChico),
          child: Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              color: Paleta.superficieElevada,
              border: Border.all(color: Paleta.borde),
              borderRadius: BorderRadius.circular(Medidas.radioChico),
            ),
            child: Stack(
              fit: StackFit.expand,
              children: [
                contenido,
                if (progress != null)
                  Container(
                    color: Colors.black54,
                    alignment: Alignment.center,
                    child: SizedBox.square(
                      dimension: 34,
                      child: CircularProgressIndicator(
                        value: progress! <= 0 ? null : progress,
                        strokeWidth: 3,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Muestra la foto de un comprobante entera, con acercamiento.
Future<void> showReceiptImage(BuildContext context, Uint8List bytes) {
  return Navigator.of(context).push<void>(
    MaterialPageRoute(
      fullscreenDialog: true,
      builder: (context) => Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          backgroundColor: Colors.black,
          title: const Text('Comprobante'),
        ),
        body: InteractiveViewer(
          maxScale: 6,
          child: Center(child: Image.memory(bytes, fit: BoxFit.contain)),
        ),
      ),
    ),
  );
}
