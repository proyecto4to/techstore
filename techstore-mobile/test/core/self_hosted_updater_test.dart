import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/native/app_excursion.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/update/apk_installer.dart';
import 'package:techstore_mobile/core/update/app_update_service.dart';
import 'package:techstore_mobile/core/update/self_hosted_updater.dart';

void main() {
  late Directory temporal;

  setUp(() => temporal = Directory.systemTemp.createTempSync('techstore-upd'));
  tearDown(() => temporal.deleteSync(recursive: true));

  final contenido = Uint8List.fromList(utf8.encode('esto hace de APK'));
  final hashReal = sha256.convert(contenido).toString();

  UpdatePolicy politica({String? sha, int? tamano}) => UpdatePolicy(
    currentVersionCode: 12,
    minimumVersionCode: 1,
    latestVersionCode: 13,
    updateAvailable: true,
    mandatory: false,
    storeUrl: Uri.parse('https://play.google.com/store/apps/details?id=x'),
    distribution: 'SELF_HOSTED',
    latestVersionName: '1.0.12',
    downloadPath: '/api/v1/app/download',
    sha256: sha ?? hashReal,
    sizeBytes: tamano ?? contenido.length,
  );

  SelfHostedUpdater updater(_FakeInstaller instalador) {
    final dio = Dio()..httpClientAdapter = _ApkAdapter(contenido);
    return SelfHostedUpdater(
      ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      ),
      instalador,
      const _SilentLogger(),
      AppExcursion(),
      cacheDir: () async => temporal,
    );
  }

  test('descarga, verifica el hash y se lo entrega a Android', () async {
    final instalador = _FakeInstaller();

    final resultado = await updater(instalador).actualizar(politica());

    expect(resultado, isA<UpdateHandedToAndroid>());
    expect(instalador.instalado, isNotNull);
    expect(File(instalador.instalado!).existsSync(), isTrue);
  });

  test('informa el progreso con el total del servidor', () async {
    final avances = <(int, int)>[];

    await updater(_FakeInstaller()).actualizar(
      politica(),
      onProgress: (recibidos, total) => avances.add((recibidos, total)),
    );

    expect(avances, isNotEmpty);
    expect(avances.last.$2, contenido.length);
  });

  test('no instala nada si el hash no coincide y borra el archivo', () async {
    final instalador = _FakeInstaller();

    final resultado = await updater(instalador)
        .actualizar(politica(sha: 'f' * 64));

    expect(resultado, isA<UpdateFailed>());
    expect(instalador.instalado, isNull);
    // El archivo corrupto no puede quedar tentando a nadie.
    expect(Directory('${temporal.path}/actualizaciones').listSync(), isEmpty);
  });

  test('rechaza un archivo con tamaño distinto al publicado', () async {
    final instalador = _FakeInstaller();

    final resultado = await updater(instalador)
        .actualizar(politica(tamano: 9999));

    expect(resultado, isA<UpdateFailed>());
    expect(instalador.instalado, isNull);
  });

  test('pide el permiso antes de gastar la descarga', () async {
    final instalador = _FakeInstaller(puede: false);

    final resultado = await updater(instalador).actualizar(politica());

    expect(resultado, isA<UpdateNeedsPermission>());
    expect(instalador.instalado, isNull);
    // Ni siquiera se creó la carpeta: no se descargó nada.
    expect(Directory('${temporal.path}/actualizaciones').existsSync(), isFalse);
  });

  test('no intenta instalar una política que reparte Google Play', () async {
    final instalador = _FakeInstaller();
    final play = UpdatePolicy(
      currentVersionCode: 12,
      minimumVersionCode: 1,
      latestVersionCode: 13,
      updateAvailable: true,
      mandatory: false,
      storeUrl: Uri.parse('https://play.google.com/store/apps/details?id=x'),
    );

    expect(play.seInstalaDesdeLaApp, isFalse);
    final resultado = await updater(instalador).actualizar(play);
    expect(resultado, isA<UpdateFailed>());
    expect(instalador.instalado, isNull);
  });

  test('una respuesta vieja sin datos del artefacto no rompe nada', () {
    // El servidor puede seguir repartiendo por Google Play: la app tiene que
    // entender esa respuesta sin los campos nuevos.
    final policy = UpdatePolicy.fromJson({
      'currentVersionCode': 12,
      'minimumVersionCode': 1,
      'latestVersionCode': 13,
      'updateAvailable': true,
      'mandatory': false,
      'storeUrl': 'https://play.google.com/store/apps/details?id=x',
    });

    expect(policy.distribution, 'GOOGLE_PLAY');
    expect(policy.seInstalaDesdeLaApp, isFalse);
    expect(policy.notes, isEmpty);
  });
}

final class _FakeInstaller implements ApkInstaller {
  _FakeInstaller({this.puede = true});

  final bool puede;
  String? instalado;
  bool ajustesAbiertos = false;

  @override
  Future<bool> puedeInstalar() async => puede;

  @override
  Future<bool> abrirAjustes() async {
    ajustesAbiertos = true;
    return true;
  }

  @override
  Future<void> instalar(String ruta) async => instalado = ruta;
}

final class _ApkAdapter implements HttpClientAdapter {
  _ApkAdapter(this.cuerpo);

  final Uint8List cuerpo;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromBytes(
    cuerpo,
    200,
    headers: {
      Headers.contentLengthHeader: ['${cuerpo.length}'],
    },
  );

  @override
  void close({bool force = false}) {}
}

final class _SilentLogger implements AppLogger {
  const _SilentLogger();

  @override
  void debug(String message) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}
}
