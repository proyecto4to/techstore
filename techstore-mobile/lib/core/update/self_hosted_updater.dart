import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../errors/app_failure.dart';
import '../logging/app_logger.dart';
import '../network/api_client.dart';
import 'apk_installer.dart';
import 'app_update_service.dart';

/// Cómo terminó un intento de actualizar desde la propia aplicación.
sealed class UpdateResult {
  const UpdateResult();
}

/// El archivo quedó verificado y se entregó al instalador de Android, que a
/// partir de acá muestra su propia confirmación.
final class UpdateHandedToAndroid extends UpdateResult {
  const UpdateHandedToAndroid();
}

/// Android todavía no autoriza a esta aplicación como origen de instalación.
final class UpdateNeedsPermission extends UpdateResult {
  const UpdateNeedsPermission();
}

/// Algo falló. [motivo] es un texto ya redactado para mostrarle al usuario.
final class UpdateFailed extends UpdateResult {
  const UpdateFailed(this.motivo);

  final String motivo;
}

/// Descarga, verifica e instala el APK que publica el propio servidor.
///
/// Todo lo que baja se comprueba antes de tocar el instalador: hash SHA-256 y
/// tamaño. Un archivo que no cuadra se borra y no se ejecuta nunca. Una
/// actualización que falla deja la aplicación instalada intacta: lo peor que
/// puede pasar es que el usuario siga en la versión que ya tenía.
final class SelfHostedUpdater {
  SelfHostedUpdater(
    this._api,
    this._installer,
    this._logger, {
    Future<Directory> Function()? cacheDir,
  }) : _cacheDir = cacheDir ?? getTemporaryDirectory;

  final ApiClient _api;
  final ApkInstaller _installer;
  final AppLogger _logger;
  final Future<Directory> Function() _cacheDir;

  /// Carpeta compartida con el `FileProvider` declarado en el manifiesto.
  static const _carpeta = 'actualizaciones';

  Future<UpdateResult> actualizar(
    UpdatePolicy policy, {
    void Function(int recibidos, int total)? onProgress,
  }) async {
    if (!policy.seInstalaDesdeLaApp) {
      return const UpdateFailed('Esta versión no se puede instalar desde la app.');
    }
    // El permiso se comprueba antes de gastar la descarga: no tiene sentido
    // bajar 64 MB para descubrir al final que Android no nos deja instalar.
    if (!await _installer.puedeInstalar()) {
      return const UpdateNeedsPermission();
    }

    File? archivo;
    try {
      archivo = await _descargar(policy, onProgress);
      final valido = await _verificar(archivo, policy);
      if (!valido) {
        await _borrar(archivo);
        return const UpdateFailed(
          'La descarga llegó incompleta o alterada. Probá de nuevo.',
        );
      }
      await _installer.instalar(archivo.path);
      return const UpdateHandedToAndroid();
    } on NetworkFailure catch (_) {
      await _borrar(archivo);
      return const UpdateFailed(
        'Se cortó la conexión durante la descarga. Reintentá cuando tengas señal.',
      );
    } on AppFailure catch (error) {
      await _borrar(archivo);
      return UpdateFailed(error.message);
    } on Object catch (error, stackTrace) {
      _logger.warning('Falló la actualización desde la app', error, stackTrace);
      await _borrar(archivo);
      return const UpdateFailed(
        'No se pudo completar la actualización. Revisá tu conexión y reintentá.',
      );
    }
  }

  /// Abre la pantalla de Android donde se concede el permiso de instalación.
  Future<bool> pedirPermiso() => _installer.abrirAjustes();

  Future<File> _descargar(
    UpdatePolicy policy,
    void Function(int, int)? onProgress,
  ) async {
    final directorio = Directory(p.join((await _cacheDir()).path, _carpeta));
    await directorio.create(recursive: true);
    // Un archivo por versión: si quedó a medias el de una versión anterior, no
    // se confunde con este ni se reutiliza por error.
    final destino = File(
      p.join(directorio.path, 'techstore-${policy.latestVersionCode}.apk'),
    );
    if (await destino.exists()) {
      await destino.delete();
    }
    await _api.download(
      policy.downloadPath!,
      destino.path,
      onReceiveProgress: (recibidos, total) => onProgress?.call(
        recibidos,
        // Dio informa -1 si el servidor no manda Content-Length; ahí se usa el
        // tamaño que ya venía en la política.
        total > 0 ? total : policy.sizeBytes,
      ),
    );
    return destino;
  }

  Future<bool> _verificar(File archivo, UpdatePolicy policy) async {
    if (!await archivo.exists()) return false;
    final tamano = await archivo.length();
    if (policy.sizeBytes > 0 && tamano != policy.sizeBytes) {
      _logger.warning(
        'El APK descargado mide $tamano y se esperaban ${policy.sizeBytes}',
      );
      return false;
    }
    final digest = await sha256.bind(archivo.openRead()).first;
    final calculado = digest.toString().toLowerCase();
    if (calculado != policy.sha256) {
      _logger.warning('Hash del APK descargado distinto al publicado');
      return false;
    }
    return true;
  }

  Future<void> _borrar(File? archivo) async {
    if (archivo == null) return;
    try {
      if (await archivo.exists()) await archivo.delete();
    } on Object {
      // Un temporal que no se puede borrar no es motivo para romper nada.
    }
  }
}
