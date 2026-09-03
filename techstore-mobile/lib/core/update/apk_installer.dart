import 'package:flutter/services.dart';

/// Acceso al instalador de paquetes de Android.
///
/// Se mantiene como interfaz para poder sustituirla en las pruebas y, si algún
/// día la aplicación se publica en Google Play, para que el resto del sistema
/// no tenga que enterarse: cambia la implementación, no quien la usa.
abstract interface class ApkInstaller {
  /// ¿Android autoriza a esta aplicación como origen de instalación?
  Future<bool> puedeInstalar();

  /// Abre la pantalla del sistema donde se concede ese permiso.
  Future<bool> abrirAjustes();

  /// Entrega el archivo al instalador oficial, que pedirá confirmación.
  Future<void> instalar(String ruta);
}

final class AndroidApkInstaller implements ApkInstaller {
  const AndroidApkInstaller([
    this._canal = const MethodChannel('py.com.techstore.app/instalador'),
  ]);

  final MethodChannel _canal;

  @override
  Future<bool> puedeInstalar() async =>
      await _canal.invokeMethod<bool>('puedeInstalar') ?? false;

  @override
  Future<bool> abrirAjustes() async =>
      await _canal.invokeMethod<bool>('abrirAjustesDeInstalacion') ?? false;

  @override
  Future<void> instalar(String ruta) =>
      _canal.invokeMethod<void>('instalar', {'ruta': ruta});
}
