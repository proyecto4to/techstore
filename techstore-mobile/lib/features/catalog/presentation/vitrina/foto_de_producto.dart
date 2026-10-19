import 'dart:async';
import 'dart:convert';
import 'dart:io' show File;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image/image.dart' as img;

import '../../../../core/cache/cache_store.dart';
import '../../../../core/di/providers.dart';
import '../../domain/product.dart';
import 'rasgos_de_imagen.dart';

/// Las fotos de producto se guardan en disco.
///
/// Antes se bajaban con `Image.network`, que solo guarda en memoria: cada vez
/// que se abría la app, el catálogo entero volvía a bajar por el túnel, que es
/// lo más lento y lo que más datos gasta. Una semana de vigencia alcanza: si
/// alguien cambia la foto de un producto, el nombre del archivo nuevo es otro.
final gestorDeFotos = CacheManager(
  Config(
    'techstore-fotos',
    stalePeriod: const Duration(days: 7),
    maxNrOfCacheObjects: 400,
    fileService: ServicioDeFotos(),
  ),
);

/// Baja las fotos y decide por su cuenta cuánto valen.
///
/// El servidor las manda con `Cache-Control: no-cache` —Spring Security se lo
/// agrega a toda respuesta— y el gestor de caché lo obedece al pie de la
/// letra: cada vez que una tarjeta mostraba su foto, la volvía a bajar entera
/// aunque la tuviera en disco. Era la mitad de la traba de la primera pasada
/// y gastaba datos en cada apertura. La dirección de una foto no cambia de
/// contenido, así que se la da por buena una semana diga lo que diga el
/// servidor.
final class ServicioDeFotos extends HttpFileService {
  ServicioDeFotos({super.httpClient});

  static const vigencia = Duration(days: 7);

  @override
  Future<FileServiceResponse> get(
    String url, {
    Map<String, String>? headers,
  }) async => _ConVigenciaPropia(await super.get(url, headers: headers));
}

final class _ConVigenciaPropia implements FileServiceResponse {
  _ConVigenciaPropia(this._respuesta);

  final FileServiceResponse _respuesta;

  @override
  Stream<List<int>> get content => _respuesta.content;

  @override
  int? get contentLength => _respuesta.contentLength;

  @override
  int get statusCode => _respuesta.statusCode;

  @override
  DateTime get validTill => DateTime.now().add(ServicioDeFotos.vigencia);

  @override
  String? get eTag => _respuesta.eTag;

  @override
  String get fileExtension => _respuesta.fileExtension;
}

/// De dónde sale cada foto. Está en un provider para que las pruebas, donde no
/// hay disco ni red, lo cambien por fotos en memoria.
final fabricaDeFotosProvider = Provider<ImageProvider Function(String url)>(
  (ref) =>
      (url) => CachedNetworkImageProvider(url, cacheManager: gestorDeFotos),
);

/// La dirección completa de una foto, o nula si el producto no tiene.
///
/// El servidor manda rutas relativas (`/img/productos/gpu.jpg`); se resuelven
/// contra la dirección de la API del sabor que esté corriendo.
final urlDeFotoProvider = Provider.family<String?, String?>((ref, relativa) {
  if (relativa == null || relativa.trim().isEmpty) return null;
  final base = ref.watch(environmentProvider).apiBaseUrl;
  return Uri.parse(base).resolve(relativa.trim()).toString();
});

/// El archivo de una foto, bajado al mismo caché en disco que usan las
/// tarjetas: cuando la tarjeta la pide, ya está. En un provider para que las
/// pruebas, sin red, lo lean de otro lado.
final archivoDeFotoProvider = Provider<Future<File> Function(String url)>(
  (ref) => gestorDeFotos.getSingleFile,
);

/// Cuánto vale lo que se sabe de una foto. El archivo de una dirección no
/// cambia —una foto nueva sube con otro nombre—, así que se guarda por mucho.
const _vigenciaDeRasgos = Duration(days: 30);
const _alcanceDeRasgos = 'rasgos-de-fotos';

/// Lo que dice cada foto de sí misma, mirado una sola vez por instalación.
///
/// Se analiza una miniatura armada en la CPU y no la imagen que ya decodificó
/// Flutter: leer los píxeles de una imagen que ya está en la GPU obliga a
/// traerla de vuelta, y mientras tanto el dibujo se frena (en el emulador,
/// hasta 330 ms por foto: la primera pasada del catálogo se congelaba). El
/// resultado se guarda en el caché local.
final rasgosDeFotoProvider = FutureProvider.family<RasgosDeImagen, String>((
  ref,
  url,
) async {
  final cache = _cacheODesactivado(ref);
  try {
    final guardado = await cache?.read(scope: _alcanceDeRasgos, key: url);
    if (guardado != null) {
      return RasgosDeImagen.fromJson(
        Map<String, Object?>.from(jsonDecode(guardado) as Map),
      );
    }
  } on Object {
    // Un caché ilegible no impide mirar la foto de nuevo.
  }

  try {
    final archivo = await ref.read(archivoDeFotoProvider)(url);
    final rasgos = await _analizar(archivo);
    // Lo que no se pudo leer no se guarda: la próxima vez se reintenta.
    if (rasgos.tipo != TipoDeFoto.ausente) {
      try {
        await cache?.write(
          scope: _alcanceDeRasgos,
          key: url,
          json: jsonEncode(rasgos.toJson()),
          ttl: _vigenciaDeRasgos,
        );
      } on Object {
        // Sin caché se vuelve a analizar en la próxima apertura; nada más.
      }
    }
    return rasgos;
  } on Object {
    // Una foto que no baja no es un error de la pantalla: la vitrina la
    // presenta como «sin foto» y sigue.
    return RasgosDeImagen.ausente;
  }
});

/// El caché local, o nulo donde no hay base de datos (una prueba suelta).
CacheStore? _cacheODesactivado(Ref ref) {
  try {
    return ref.read(cacheStoreProvider);
  } on Object {
    return null;
  }
}

/// Ancho de la miniatura con la que se mira cada foto: alcanza para distinguir
/// un fondo parejo de una escena y se recorre en microsegundos.
const _anchoDeMiniatura = 48;

/// El decodificador de Android, del lado nativo (MainActivity.kt).
const _canalDeMiniaturas = MethodChannel('py.com.techstore.app/miniaturas');

/// Achica la foto y la mira.
///
/// En el teléfono la achica Android, en un hilo propio: su decodificador es
/// nativo y, con el muestreo, nunca arma la foto entera en memoria.
/// Decodificarla en Dart, aun en un isolate aparte, ocupaba un núcleo entero
/// por cada foto de 1,5 MB, y la primera pasada del catálogo se trababa igual.
/// Donde no existe el canal —las pruebas, el banco de capturas— se decodifica
/// en Dart.
Future<RasgosDeImagen> _analizar(File archivo) async {
  try {
    final miniatura = await _canalDeMiniaturas.invokeMapMethod<String, Object?>(
      'rgba',
      {'ruta': archivo.path, 'ancho': _anchoDeMiniatura},
    );
    if (miniatura == null) return RasgosDeImagen.ausente;
    return AnalizadorDeImagen.analizar(
      miniatura['rgba']! as Uint8List,
      miniatura['ancho']! as int,
      miniatura['alto']! as int,
    );
  } on MissingPluginException {
    final bytes = await archivo.readAsBytes();
    return _limitador.correr(() => compute(_analizarEnSegundoPlano, bytes));
  }
}

/// El mismo análisis, con la foto decodificada en Dart. Corre en un isolate:
/// una foto de 1,5 MB tarda lo suyo y no puede frenar la interfaz.
RasgosDeImagen _analizarEnSegundoPlano(Uint8List bytes) {
  final original = img.decodeImage(bytes);
  if (original == null) return RasgosDeImagen.ausente;
  final chica = img.copyResize(
    original,
    width: _anchoDeMiniatura,
    interpolation: img.Interpolation.average,
  );
  final rgba = chica
      .convert(numChannels: 4, format: img.Format.uint8)
      .getBytes(order: img.ChannelOrder.rgba);
  return AnalizadorDeImagen.analizar(rgba, chica.width, chica.height);
}

/// Dos análisis en Dart a la vez como mucho: con la precarga, si no, se
/// abrirían decenas de isolates juntos y la CPU no daría abasto para la
/// interfaz.
final _limitador = _Limitador(2);

final class _Limitador {
  _Limitador(this._maximo);

  final int _maximo;
  int _enCurso = 0;
  final _esperando = <Completer<void>>[];

  Future<T> correr<T>(Future<T> Function() tarea) async {
    if (_enCurso >= _maximo) {
      final turno = Completer<void>();
      _esperando.add(turno);
      await turno.future;
    }
    _enCurso++;
    try {
      return await tarea();
    } finally {
      _enCurso--;
      if (_esperando.isNotEmpty) _esperando.removeAt(0).complete();
    }
  }
}

/// Baja y analiza, en los ratos libres, las fotos de productos que todavía no
/// están en pantalla.
///
/// La primera vez que se desplaza un catálogo, cada tarjeta que entra baja su
/// foto, la analiza y la decodifica en el mismo momento: esa pasada era la
/// única que se trababa, y por el túnel, en un teléfono, lo hace más. Así,
/// cuando la tarjeta llega, la foto ya está en disco y su tratamiento elegido.
///
/// Corre con prioridad ociosa: nunca mientras haya una animación en curso. Usa
/// el contenedor de la app y no el de la pantalla, que puede cerrarse antes de
/// que termine.
void precalentarFotos(BuildContext context, Iterable<Product> productos) {
  final contenedor = ProviderScope.containerOf(context, listen: false);
  final urls = <String>{
    for (final producto in productos)
      ?contenedor.read(urlDeFotoProvider(producto.imageUrl)),
  };
  for (final url in urls) {
    SchedulerBinding.instance.scheduleTask<void>(
      () => unawaited(contenedor.read(rasgosDeFotoProvider(url).future)),
      Priority.idle,
    );
  }
}

