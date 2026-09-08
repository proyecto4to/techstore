import 'package:dio/dio.dart';
import 'package:latlong2/latlong.dart';

/// Un resultado del buscador de calles.
final class LugarEncontrado {
  const LugarEncontrado({required this.nombre, required this.punto});

  /// Cómo se llama el lugar, para leerlo en la lista.
  final String nombre;
  final LatLng punto;
}

/// El texto de una dirección, deducido de un punto del mapa.
///
/// Todo es opcional a propósito: el servicio contesta con lo que sabe, y en un
/// camino sin nombre puede no saber la calle. Eso no invalida la dirección —el
/// punto sigue siendo exacto—, así que nada de esto se exige.
final class DireccionDetectada {
  const DireccionDetectada({
    this.departamento,
    this.ciudad,
    this.barrio,
    this.calle,
    this.numero,
    this.codigoPostal,
    this.textoCompleto,
  });

  final String? departamento;
  final String? ciudad;
  final String? barrio;
  final String? calle;
  final String? numero;
  final String? codigoPostal;

  /// La dirección entera en una línea, tal como la escribe el servicio.
  final String? textoCompleto;

  /// Si no se pudo deducir nada útil.
  bool get vacia =>
      calle == null && ciudad == null && departamento == null && barrio == null;

  /// Una línea corta para mostrar: la calle con su número.
  String? get linea {
    if (calle == null) return null;
    return numero == null ? calle : '$calle $numero';
  }
}

/// El servicio de direcciones de OpenStreetMap.
///
/// Busca calles por nombre y deduce la dirección de un punto. Va aparte del
/// [Dio] de la aplicación por dos motivos: ese lleva el token de sesión en cada
/// pedido y no hay razón para mandárselo a un tercero, y su URL base apunta al
/// backend. El mapa ya pide sus tiles al mismo servidor, así que no se le
/// cuenta nada nuevo a nadie.
class DirectorioDeLugares {
  DirectorioDeLugares({Dio? dio})
    : _dio =
          dio ??
          Dio(
            BaseOptions(
              baseUrl: 'https://nominatim.openstreetmap.org',
              // El servicio exige identificar la aplicación; sin esto contesta
              // 403 y el buscador queda mudo sin explicar por qué.
              headers: const {'User-Agent': _agente},
              connectTimeout: const Duration(seconds: 8),
              receiveTimeout: const Duration(seconds: 8),
            ),
          );

  static const _agente = 'py.com.techstore.app';

  /// Paraguay. Acota la búsqueda a donde la tienda entrega.
  static const _pais = 'py';

  final Dio _dio;

  /// Busca lugares por nombre. Devuelve vacío si no hay ninguno o si falla.
  ///
  /// Que falle no es un error que valga la pena mostrar: el buscador es un
  /// atajo, y quien no lo consigue puede mover el mapa con el dedo.
  Future<List<LugarEncontrado>> buscar(String consulta) async {
    final texto = consulta.trim();
    if (texto.length < 3) return const [];
    try {
      final respuesta = await _dio.get<List<dynamic>>(
        '/search',
        queryParameters: {
          'q': texto,
          'format': 'jsonv2',
          'addressdetails': 1,
          'limit': 6,
          'countrycodes': _pais,
          'accept-language': 'es',
        },
      );
      return (respuesta.data ?? const [])
          .map((crudo) => Map<String, Object?>.from(crudo as Map))
          .map(_lugar)
          .nonNulls
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  /// Deduce la dirección de un punto. Vuelve vacía si el servicio no contesta.
  Future<DireccionDetectada> describir(LatLng punto) async {
    try {
      final respuesta = await _dio.get<Map<String, Object?>>(
        '/reverse',
        queryParameters: {
          'lat': punto.latitude,
          'lon': punto.longitude,
          'format': 'jsonv2',
          'addressdetails': 1,
          'accept-language': 'es',
        },
      );
      final datos = respuesta.data ?? const {};
      final partes = datos['address'] is Map
          ? Map<String, Object?>.from(datos['address']! as Map)
          : const <String, Object?>{};
      return DireccionDetectada(
        departamento: _texto(partes['state']),
        // El nombre del pueblo aparece en una clave distinta según el tamaño
        // del lugar; sin mirarlas todas, en el interior queda siempre vacío.
        ciudad: _texto(
          partes['city'] ??
              partes['town'] ??
              partes['village'] ??
              partes['municipality'] ??
              partes['county'],
        ),
        barrio: _texto(partes['suburb'] ?? partes['neighbourhood']),
        calle: _texto(partes['road'] ?? partes['pedestrian']),
        numero: _texto(partes['house_number']),
        codigoPostal: _texto(partes['postcode']),
        textoCompleto: _texto(datos['display_name']),
      );
    } catch (_) {
      return const DireccionDetectada();
    }
  }

  LugarEncontrado? _lugar(Map<String, Object?> crudo) {
    final lat = double.tryParse('${crudo['lat']}');
    final lon = double.tryParse('${crudo['lon']}');
    final nombre = _texto(crudo['display_name']);
    if (lat == null || lon == null || nombre == null) return null;
    return LugarEncontrado(nombre: nombre, punto: LatLng(lat, lon));
  }

  static String? _texto(Object? valor) {
    final texto = valor?.toString().trim();
    return texto == null || texto.isEmpty ? null : texto;
  }
}
