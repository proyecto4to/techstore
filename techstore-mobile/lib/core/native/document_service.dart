import 'dart:io';
import 'dart:typed_data';

import 'package:open_filex/open_filex.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:printing/printing.dart';
import 'package:share_plus/share_plus.dart';

import '../network/api_client.dart';
import 'app_excursion.dart';

enum DocumentAction { open, share, print }

final class DocumentService {
  const DocumentService(this._api, this._excursion);

  final ApiClient _api;

  /// Abrir, compartir o imprimir manda la app a segundo plano: la abre el
  /// visor de PDF, el selector de aplicaciones o el diálogo de impresión del
  /// sistema. Sin marcarlo, la guarda de sesión lo confunde con que la persona
  /// se fue de la aplicación y la cierra en mitad de la operación.
  final AppExcursion _excursion;

  Future<File> download(String endpoint, String filename) async {
    final directory = await getTemporaryDirectory();
    final safeName = sanitizeFilename(filename);
    final file = File(path.join(directory.path, safeName));
    final bytes = await _api.getBytes(endpoint);
    if (bytes.isEmpty) {
      throw StateError('El documento descargado esta vacio.');
    }
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  Future<Uint8List> bytes(String endpoint) => _api.getBytes(endpoint);

  Future<void> handle(
    String endpoint,
    String filename,
    DocumentAction action,
  ) async {
    // La descarga queda fuera de la marca a propósito: bajar el archivo no
    // saca a nadie de la app; lo que sale es lo que viene después.
    final file = await download(endpoint, filename);
    await _excursion.durante(() async {
      switch (action) {
        case DocumentAction.open:
          final result = await OpenFilex.open(file.path);
          if (result.type != ResultType.done) {
            throw StateError(result.message);
          }
        case DocumentAction.share:
          await SharePlus.instance.share(
            ShareParams(files: [XFile(file.path)], title: filename),
          );
        case DocumentAction.print:
          final bytes = await file.readAsBytes();
          await Printing.layoutPdf(
            onLayout: (_) async => Uint8List.fromList(bytes),
          );
      }
    });
  }

  static String sanitizeFilename(String value) {
    final sanitized = value.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_');
    return sanitized.isEmpty ? 'techstore-documento' : sanitized;
  }
}
