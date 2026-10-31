import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';

import '../errors/app_failure.dart';
import 'app_excursion.dart';

/// De dónde sale el comprobante.
enum ReceiptSource { camera, gallery, file }

/// Un comprobante elegido en el teléfono, todavía sin subir.
final class PickedReceipt {
  const PickedReceipt({
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  final Uint8List bytes;
  final String filename;

  /// `image/jpeg`, `image/png` o `application/pdf`: los únicos que acepta el
  /// servidor. Se deduce del contenido, no de la extensión que traía el archivo.
  final String mimeType;

  bool get isPdf => mimeType == 'application/pdf';
}

/// Elige el comprobante de un pago: una foto, una captura de la galería o el
/// PDF que manda la app del banco.
///
/// Las tres salidas mandan la app a segundo plano; van marcadas con
/// [AppExcursion] para que el cierre de sesión por segundo plano no se lleve
/// puesta la compra a mitad de camino.
final class ReceiptPicker {
  ReceiptPicker(this._excursion, {ImagePicker? picker})
    : _picker = picker ?? ImagePicker();

  static const maxBytes = 5 * 1024 * 1024;

  final AppExcursion _excursion;
  final ImagePicker _picker;

  /// Nulo si la persona canceló.
  Future<PickedReceipt?> pick(ReceiptSource source) =>
      _excursion.durante(() => _pick(source));

  Future<PickedReceipt?> _pick(ReceiptSource source) async {
    final Uint8List bytes;
    final String name;
    if (source == ReceiptSource.file) {
      final file = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: const ['pdf', 'jpg', 'jpeg', 'png'],
      );
      if (file == null) return null;
      bytes = await file.readAsBytes();
      name = file.name;
    } else {
      // Se achica en el teléfono: una foto de 12 MP pesa más que el tope y la
      // transferencia se lee igual de bien a 1800 px.
      final photo = await _picker.pickImage(
        source: source == ReceiptSource.camera
            ? ImageSource.camera
            : ImageSource.gallery,
        imageQuality: 85,
        maxWidth: 1800,
        maxHeight: 1800,
      );
      if (photo == null) return null;
      bytes = await photo.readAsBytes();
      name = photo.name;
    }
    final mime = mimeTypeOf(bytes);
    if (mime == null) {
      throw const ValidationFailure(
        'Ese archivo no es una foto ni un PDF. Elegí la captura o el PDF '
        'del comprobante que te dio el banco.',
      );
    }
    if (bytes.length > maxBytes) {
      throw const ValidationFailure(
        'El comprobante pesa más de 5 MB. Probá con una captura de pantalla '
        'en vez de una foto, o con el PDF del banco.',
      );
    }
    return PickedReceipt(bytes: bytes, filename: name, mimeType: mime);
  }

  /// El tipo real del archivo, mirando sus primeros bytes.
  ///
  /// Es la misma comprobación que hace el servidor: si acá se mandara lo que
  /// dice la extensión, un HEIC renombrado se rechazaría recién después de
  /// subirlo entero.
  static String? mimeTypeOf(Uint8List bytes) {
    if (bytes.length >= 3 &&
        bytes[0] == 0xff &&
        bytes[1] == 0xd8 &&
        bytes[2] == 0xff) {
      return 'image/jpeg';
    }
    if (bytes.length >= 8 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4e &&
        bytes[3] == 0x47) {
      return 'image/png';
    }
    if (bytes.length >= 5 &&
        bytes[0] == 0x25 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x44 &&
        bytes[3] == 0x46 &&
        bytes[4] == 0x2d) {
      return 'application/pdf';
    }
    return null;
  }
}
