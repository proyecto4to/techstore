import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:techstore_mobile/core/ubicacion/directorio_de_lugares.dart';

/// La lectura de lo que contesta el servicio de direcciones.
///
/// Es la pieza de la que depende que marcar un punto en el mapa se convierta en
/// una calle y una ciudad. Se prueba contra respuestas copiadas del servicio
/// real, porque el detalle que importa está en los nombres de las claves: un
/// pueblo del interior no viene en `city` sino en `town` o en `village`, y
/// mirando solo la primera la ciudad quedaba vacía justo donde más hace falta.
void main() {
  DirectorioDeLugares conRespuesta(Object? cuerpo, {int estado = 200}) =>
      DirectorioDeLugares(
        dio: Dio()..httpClientAdapter = _AdaptadorFijo(cuerpo, estado),
      );

  group('describir un punto', () {
    test('arma la dirección con lo que manda el servicio', () async {
      final directorio = conRespuesta({
        'display_name': 'Av. Mariscal López 1234, Villa Morra, Asunción',
        'address': {
          'road': 'Avenida Mariscal López',
          'house_number': '1234',
          'suburb': 'Villa Morra',
          'city': 'Asunción',
          'state': 'Asunción',
          'postcode': '1209',
        },
      });

      final detectada = await directorio.describir(
        const LatLng(-25.2988, -57.5745),
      );

      expect(detectada.calle, 'Avenida Mariscal López');
      expect(detectada.numero, '1234');
      expect(detectada.barrio, 'Villa Morra');
      expect(detectada.ciudad, 'Asunción');
      expect(detectada.departamento, 'Asunción');
      expect(detectada.codigoPostal, '1209');
      expect(detectada.linea, 'Avenida Mariscal López 1234');
      expect(detectada.vacia, isFalse);
    });

    test('un pueblo que no viene en «city» igual queda con ciudad', () async {
      final directorio = conRespuesta({
        'address': {'village': 'Piribebuy', 'state': 'Cordillera'},
      });

      final detectada = await directorio.describir(
        const LatLng(-25.4667, -57.05),
      );

      expect(detectada.ciudad, 'Piribebuy');
      expect(detectada.departamento, 'Cordillera');
    });

    test('en un camino sin nombre no inventa nada', () async {
      final directorio = conRespuesta({'address': <String, Object?>{}});

      final detectada = await directorio.describir(const LatLng(-23.4, -58.1));

      expect(detectada.vacia, isTrue);
      expect(detectada.linea, isNull);
    });

    test('si el servicio falla la dirección queda vacía, no explota', () async {
      final directorio = conRespuesta('no soy json', estado: 503);

      final detectada = await directorio.describir(const LatLng(-25.3, -57.6));

      expect(detectada.vacia, isTrue);
    });
  });

  group('buscar un lugar', () {
    test('devuelve los resultados con su punto', () async {
      final directorio = conRespuesta([
        {
          'display_name': 'Shopping del Sol, Asunción',
          'lat': '-25.2988',
          'lon': '-57.5745',
        },
        {
          'display_name': 'Plaza Uruguaya, Asunción',
          'lat': '-25.2842',
          'lon': '-57.6353',
        },
      ]);

      final encontrados = await directorio.buscar('shopping');

      expect(encontrados, hasLength(2));
      expect(encontrados.first.nombre, 'Shopping del Sol, Asunción');
      expect(encontrados.first.punto.latitude, closeTo(-25.2988, 0.0001));
    });

    test('descarta un resultado sin coordenadas en vez de fallar', () async {
      final directorio = conRespuesta([
        {'display_name': 'Sin punto'},
        {'display_name': 'Con punto', 'lat': '-25.1', 'lon': '-57.2'},
      ]);

      final encontrados = await directorio.buscar('algo');

      expect(encontrados.map((lugar) => lugar.nombre), ['Con punto']);
    });

    test('no molesta al servicio con dos letras', () async {
      var pedidos = 0;
      final dio = Dio()
        ..httpClientAdapter = _AdaptadorFijo(
          const [],
          200,
          alPedir: () => pedidos++,
        );

      final encontrados = await DirectorioDeLugares(dio: dio).buscar('as');

      expect(encontrados, isEmpty);
      expect(
        pedidos,
        0,
        reason:
            'El servicio pide no pasar de una consulta por segundo; mandarle '
            'dos letras es gastar el cupo en algo que no puede acertar.',
      );
    });
  });
}

final class _AdaptadorFijo implements HttpClientAdapter {
  _AdaptadorFijo(this.cuerpo, this.estado, {this.alPedir});

  final Object? cuerpo;
  final int estado;
  final void Function()? alPedir;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    alPedir?.call();
    return ResponseBody.fromString(
      cuerpo is String ? cuerpo! as String : jsonEncode(cuerpo),
      estado,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
