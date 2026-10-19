import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:image/image.dart' as img;
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/foto_de_producto.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/rasgos_de_imagen.dart';

const _canal = MethodChannel('py.com.techstore.app/miniaturas');

/// Una foto de estudio de verdad, codificada en PNG: fondo blanco y el
/// producto, azul, en el centro.
Uint8List fotoDeEstudio() {
  final foto = img.Image(width: 120, height: 80)
    ..clear(img.ColorRgb8(250, 250, 250));
  img.fillRect(
    foto,
    x1: 40,
    y1: 25,
    x2: 80,
    y2: 55,
    color: img.ColorRgb8(37, 99, 235),
  );
  return img.encodePng(foto);
}

/// La foto de estudio guardada en una carpeta temporal, como la deja el caché.
File archivoDeEstudio() {
  final carpeta = Directory.systemTemp.createTempSync('rasgos');
  addTearDown(() => carpeta.deleteSync(recursive: true));
  return File('${carpeta.path}${Platform.pathSeparator}ups.png')
    ..writeAsBytesSync(fotoDeEstudio());
}

ProviderContainer contenedorCon(Future<File> Function(String url) archivo) {
  final base = AppDatabase(NativeDatabase.memory());
  addTearDown(base.close);
  final contenedor = ProviderContainer(
    overrides: [
      databaseProvider.overrideWithValue(base),
      archivoDeFotoProvider.overrideWithValue(archivo),
    ],
  );
  addTearDown(contenedor.dispose);
  return contenedor;
}

void main() {
  // Con el binding de pruebas, un canal sin respuesta avisa que no existe y la
  // foto se decodifica en Dart, como en el banco de capturas.
  TestWidgetsFlutterBinding.ensureInitialized();

  test('lo que se sabe de una foto se guarda y se recupera igual', () {
    const rasgos = RasgosDeImagen(
      tipo: TipoDeFoto.estudio,
      proporcion: 1.25,
      tono: Color(0xFF112233),
      fondo: Color(0xFFFAFAFA),
    );
    expect(RasgosDeImagen.fromJson(rasgos.toJson()), rasgos);
    expect(
      RasgosDeImagen.fromJson(RasgosDeImagen.ausente.toJson()),
      RasgosDeImagen.ausente,
    );
  });

  test('sin el decodificador de Android, analiza en Dart, guarda y no vuelve '
      'a pedir la foto', () async {
    final archivo = archivoDeEstudio();
    var pedidos = 0;
    final contenedor = contenedorCon((url) async {
      pedidos++;
      return archivo;
    });
    const url = 'http://10.0.2.2:8090/img/productos/ups.png';

    final rasgos = await contenedor.read(rasgosDeFotoProvider(url).future);
    expect(rasgos.tipo, TipoDeFoto.estudio);
    expect(rasgos.fondoClaro, isTrue);
    expect(rasgos.proporcion, closeTo(1.5, 0.05));
    expect(pedidos, 1);

    // Otra instalación del provider —como una app recién abierta— encuentra el
    // análisis guardado y no baja ni decodifica nada.
    contenedor.invalidate(rasgosDeFotoProvider(url));
    final otraVez = await contenedor.read(rasgosDeFotoProvider(url).future);
    expect(otraVez, rasgos);
    expect(pedidos, 1);
  });

  test('en el teléfono mira la miniatura que arma Android', () async {
    final archivo = archivoDeEstudio();
    final llamadas = <MethodCall>[];
    // Android devuelve un recorte: borde transparente y el centro opaco. Si el
    // resultado sale «recortada» y no «estudio», se usó la miniatura nativa y
    // no el archivo.
    const ancho = 48, alto = 32;
    final rgba = Uint8List(ancho * alto * 4);
    for (var y = 12; y < 20; y++) {
      for (var x = 16; x < 32; x++) {
        final i = (y * ancho + x) * 4;
        rgba
          ..[i] = 37
          ..[i + 1] = 99
          ..[i + 2] = 235
          ..[i + 3] = 255;
      }
    }
    final mensajero =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    mensajero.setMockMethodCallHandler(_canal, (llamada) async {
      llamadas.add(llamada);
      return {'ancho': ancho, 'alto': alto, 'rgba': rgba};
    });
    addTearDown(() => mensajero.setMockMethodCallHandler(_canal, null));

    final contenedor = contenedorCon((url) async => archivo);
    final rasgos = await contenedor.read(
      rasgosDeFotoProvider('http://10.0.2.2:8090/img/productos/ups.png').future,
    );

    expect(rasgos.tipo, TipoDeFoto.recortada);
    expect(rasgos.proporcion, closeTo(1.5, 0.01));
    expect(rasgos.tono, const Color(0xFF2563EB));
    expect(llamadas.single.method, 'rgba');
    expect(llamadas.single.arguments, {'ruta': archivo.path, 'ancho': 48});
  });

  test('si Android no puede leer la foto, cuenta como ausente y no se '
      'guarda', () async {
    final archivo = archivoDeEstudio();
    var llamadas = 0;
    final mensajero =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    mensajero.setMockMethodCallHandler(_canal, (llamada) async {
      llamadas++;
      throw PlatformException(code: 'ilegible', message: 'No es una imagen');
    });
    addTearDown(() => mensajero.setMockMethodCallHandler(_canal, null));

    final contenedor = contenedorCon((url) async => archivo);
    const url = 'http://10.0.2.2:8090/img/productos/rota.png';
    expect(
      (await contenedor.read(rasgosDeFotoProvider(url).future)).tipo,
      TipoDeFoto.ausente,
    );
    contenedor.invalidate(rasgosDeFotoProvider(url));
    await contenedor.read(rasgosDeFotoProvider(url).future);
    expect(llamadas, 2);
  });

  test('una foto bajada vale una semana aunque el servidor diga no-cache', () async {
    // Así responde el backend: Spring Security agrega estas cabeceras a todo.
    final servicio = ServicioDeFotos(
      httpClient: MockClient(
        (_) async => http.Response.bytes(
          fotoDeEstudio(),
          200,
          headers: {
            'cache-control': 'no-cache, no-store, max-age=0, must-revalidate',
            'content-type': 'image/png',
          },
        ),
      ),
    );
    final antes = DateTime.now();
    final respuesta = await servicio.get(
      'http://10.0.2.2:8090/img/productos/ups.png',
    );
    expect(respuesta.statusCode, 200);
    expect(respuesta.fileExtension, '.png');
    expect(
      respuesta.validTill.isAfter(antes.add(const Duration(days: 6))),
      isTrue,
      reason: 'con vigencia cero, cada tarjeta volvía a bajar su foto',
    );
    expect(await respuesta.content.expand((parte) => parte).length, greaterThan(0));
  });

  test('una foto que no baja cuenta como ausente y no se guarda', () async {
    var pedidos = 0;
    final contenedor = contenedorCon((url) async {
      pedidos++;
      throw const SocketException('sin red');
    });
    const url = 'http://10.0.2.2:8090/img/productos/cortada.jpg';

    expect(
      (await contenedor.read(rasgosDeFotoProvider(url).future)).tipo,
      TipoDeFoto.ausente,
    );
    contenedor.invalidate(rasgosDeFotoProvider(url));
    await contenedor.read(rasgosDeFotoProvider(url).future);
    // Se reintentó: la falla no quedó guardada como si fuera el dato.
    expect(pedidos, 2);
  });
}
