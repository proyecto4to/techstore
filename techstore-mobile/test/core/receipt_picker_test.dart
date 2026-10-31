import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/native/receipt_picker.dart';

/// El tipo del comprobante se deduce del contenido, como lo hace el servidor:
/// si se mandara lo que dice la extensión, un archivo renombrado se rechazaría
/// recién después de subirlo entero.
void main() {
  Uint8List bytes(List<int> v) => Uint8List.fromList([...v, 0, 0, 0, 0]);

  test('reconoce JPEG, PNG y PDF por sus primeros bytes', () {
    expect(
      ReceiptPicker.mimeTypeOf(bytes([0xff, 0xd8, 0xff, 0xe0])),
      'image/jpeg',
    );
    expect(
      ReceiptPicker.mimeTypeOf(
        bytes([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      ),
      'image/png',
    );
    expect(
      ReceiptPicker.mimeTypeOf(bytes('%PDF-1.7'.codeUnits)),
      'application/pdf',
    );
  });

  test('un archivo que no es foto ni PDF no tiene tipo', () {
    expect(
      ReceiptPicker.mimeTypeOf(bytes('no soy una imagen'.codeUnits)),
      isNull,
    );
    expect(ReceiptPicker.mimeTypeOf(Uint8List(0)), isNull);
  });
}
