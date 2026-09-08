import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/ubicacion/directorio_de_lugares.dart';
import 'package:techstore_mobile/core/ubicacion/ubicacion_service.dart';
import 'package:techstore_mobile/features/checkout/presentation/address_map_page.dart';

import '../../support/sesion_de_prueba.dart';

/// Marcar una dirección en el mapa y guardarla.
///
/// Lo que se comprueba es que lo que llega al servidor sea la ubicación y no un
/// texto: las coordenadas del punto marcado, y la calle deducida de ese punto
/// en vez de escrita a mano. El formulario anterior tenía doce campos, dos de
/// ellos «Latitud» y «Longitud», y quedaban casi siempre vacíos.
void main() {
  const marcado = LatLng(-25.2988, -57.5745);

  ({Widget vista, List<Map<String, Object?>> guardado}) pantalla() {
    final guardado = <Map<String, Object?>>[];
    final vista = ProviderScope(
      overrides: [
        secureStoreProvider.overrideWithValue(StoreConSesion()),
        apiClientProvider.overrideWithValue(
          ApiClient(
            baseUrl: 'https://techstore.local',
            logger: const LoggerMudo(),
            dio: Dio()..httpClientAdapter = _AdaptadorQueGuarda(guardado),
          ),
        ),
        ubicacionServiceProvider.overrideWithValue(_UbicacionFija(marcado)),
        directorioDeLugaresProvider.overrideWithValue(_DirectorioFijo()),
      ],
      child: const MaterialApp(home: AddressMapPage()),
    );
    return (vista: vista, guardado: guardado);
  }

  /// Una pantalla alta, para que entre el formulario entero.
  ///
  /// La de por omisión mide 800 por 600 y deja los últimos campos sin construir,
  /// así que el caso no los encuentra. Se queda angosta a propósito: pasando de
  /// 720 la pantalla se acomoda en dos columnas, que es otro caso.
  void agrandar(WidgetTester tester) {
    tester.view.physicalSize = const Size(700, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  testWidgets('guarda las coordenadas del punto, no un texto escrito', (
    tester,
  ) async {
    agrandar(tester);
    final caso = pantalla();
    await tester.pumpWidget(caso.vista);
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Teléfono de contacto'),
      '0981 123 456',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Nombre de quien recibe'),
      'Ana Giménez',
    );
    await tester.tap(find.text('Casa'));
    await tester.pump();

    await tester.tap(find.text('Guardar dirección'));
    // Sin pumpAndSettle: el mapa nunca se queda quieto del todo y la espera se
    // agota sola.
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(caso.guardado, hasLength(1));
    final enviado = caso.guardado.single;
    expect(enviado['latitud'], closeTo(marcado.latitude, 0.000001));
    expect(enviado['longitud'], closeTo(marcado.longitude, 0.000001));
    expect(enviado['descripcion'], 'Casa');
    expect(
      enviado['direccionLinea1'],
      'Avenida Mariscal López',
      reason: 'la calle sale del punto marcado, no de lo que alguien escriba',
    );
    expect(enviado['ciudad'], 'Asunción');
    expect(enviado['departamento'], 'Asunción');
  });

  testWidgets('no se guarda sin saber a quién ni a qué teléfono', (
    tester,
  ) async {
    agrandar(tester);
    final caso = pantalla();
    await tester.pumpWidget(caso.vista);
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Nombre de quien recibe'),
      '',
    );
    await tester.tap(find.text('Guardar dirección'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(caso.guardado, isEmpty);
    expect(find.text('Escribí a quién le entregamos'), findsOneWidget);
    expect(find.text('Escribí un teléfono para el reparto'), findsOneWidget);
  });

  testWidgets('se ve la dirección que se dedujo del punto', (tester) async {
    await tester.pumpWidget(pantalla().vista);
    await tester.pumpAndSettle();

    expect(find.textContaining('Avenida Mariscal López'), findsOneWidget);
    expect(find.textContaining('Lat -25.298800'), findsOneWidget);
  });

  /// Con el teléfono acostado, y más aún con el teclado abierto, el mapa queda
  /// en una franja de pocos puntos.
  ///
  /// Se vio en el equipo real dos veces. Primero la lista de resultados
  /// desbordaba esa franja y el borde la cortaba, con la última fila partida al
  /// medio. Después, ya sin cortarse, quedaba aplastada y no se leía ninguno.
  /// Por eso el buscador no vive dentro del mapa: se dibuja sobre la pantalla
  /// entera y puede taparle un rato al formulario, como cualquier buscador.
  testWidgets('los resultados se leen aunque el mapa sea una franja', (
    tester,
  ) async {
    // Acostado y con el teclado ocupando la mitad de abajo, que es como se
    // busca de verdad: se escribe y se mira la lista sin cerrar el teclado.
    const alto = 380.0;
    const teclado = 220.0;
    tester.view.physicalSize = const Size(700, alto);
    tester.view.devicePixelRatio = 1;
    tester.view.viewInsets = const FakeViewPadding(bottom: teclado);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpWidget(pantalla().vista);
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).first, 'shopping');
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump();

    final mapa = tester.getRect(find.byType(FlutterMap));
    // Por el resultado y no por tipo: el formulario también es un ListView y
    // está antes en el árbol, así que `byType(...).first` mide el equivocado.
    final lista = tester.getRect(
      find.ancestor(
        of: find.text(
          'Shopping del Sol, Avenida Aviadores del Chaco, Asuncion',
        ),
        matching: find.byType(ListView),
      ),
    );
    expect(
      lista.bottom,
      greaterThan(mapa.bottom),
      reason:
          'la lista sigue encerrada en la franja del mapa: con el teclado '
          'abierto esa franja no da ni para un renglón',
    );
    expect(
      lista.bottom,
      lessThanOrEqualTo(alto - teclado),
      reason: 'y tampoco puede quedar debajo del teclado',
    );
  });
}

/// Un teléfono que siempre sabe dónde está.
final class _UbicacionFija extends UbicacionService {
  _UbicacionFija(this.punto);

  final LatLng punto;

  @override
  Future<ResultadoDeUbicacion> donde() async => UbicacionObtenida(punto);
}

/// Un servicio de direcciones que contesta siempre lo mismo.
final class _DirectorioFijo extends DirectorioDeLugares {
  _DirectorioFijo() : super(dio: Dio());

  @override
  Future<DireccionDetectada> describir(LatLng punto) async =>
      const DireccionDetectada(
        departamento: 'Asunción',
        ciudad: 'Asunción',
        barrio: 'Villa Morra',
        calle: 'Avenida Mariscal López',
        numero: '1234',
      );

  @override
  Future<List<LugarEncontrado>> buscar(String consulta) async => const [
    LugarEncontrado(
      nombre: 'Shopping del Sol, Avenida Aviadores del Chaco, Asuncion',
      punto: LatLng(-25.2828, -57.5674),
    ),
    LugarEncontrado(
      nombre: 'Shopping Mariscal, Quesada, Asuncion',
      punto: LatLng(-25.2901, -57.5772),
    ),
    LugarEncontrado(
      nombre: 'Shopping Villa Morra, Mariscal Lopez, Asuncion',
      punto: LatLng(-25.2988, -57.5745),
    ),
    LugarEncontrado(
      nombre: 'Shopping Multiplaza, Eusebio Ayala, Asuncion',
      punto: LatLng(-25.3255, -57.5601),
    ),
  ];
}

/// Anota lo que se manda al servidor al crear la dirección.
///
/// Lo demás se lo pasa al adaptador compartido, que sabe contestar el refresco
/// de sesión con el que la aplicación arranca.
final class _AdaptadorQueGuarda implements HttpClientAdapter {
  _AdaptadorQueGuarda(this.guardado);

  final List<Map<String, Object?>> guardado;
  final AdaptadorDeRol _sesion = AdaptadorDeRol('Cliente');

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.method == 'POST' && options.path.contains('/direcciones')) {
      final enviado = Map<String, Object?>.from(options.data as Map);
      guardado.add(enviado);
      // El servidor contesta la dirección ya guardada, con su id: la app la
      // lee de vuelta y sin eso revienta al convertirla.
      return ResponseBody.fromString(
        jsonEncode({...enviado, 'id': 1}),
        201,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    return _sesion.fetch(options, requestStream, cancelFuture);
  }

  @override
  void close({bool force = false}) {}
}
