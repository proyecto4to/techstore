import '../../../core/network/api_client.dart';

final class CourierRepository {
  const CourierRepository(this._api);

  final ApiClient _api;

  Future<Map<String, Object?>> dashboard() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/repartos/dashboard',
    );
    return response.data ?? const {};
  }

  Future<List<Map<String, Object?>>> deliveries() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/repartos/mios',
      queryParameters: const {
        'page': 0,
        'size': 100,
        'sort': 'ordenEntrega,ASC',
      },
    );
    return _page(response.data);
  }

  Future<List<Map<String, Object?>>> deliveriesByClient() async {
    final response = await _api.get<List<dynamic>>('/api/v1/repartos/clientes');
    return _list(response.data);
  }

  Future<Map<String, Object?>> route({
    double? latitude,
    double? longitude,
  }) async {
    final query = <String, Object?>{};
    if (latitude != null) {
      query['latitud'] = latitude;
    }
    if (longitude != null) {
      query['longitud'] = longitude;
    }
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/repartos/ruta',
      queryParameters: query,
    );
    return response.data ?? const {};
  }

  Future<Map<String, Object?>> changeState(
    int assignmentId,
    String state, {
    String? note,
    String? result,
  }) async {
    final response = await _api.put<Map<String, Object?>>(
      '/api/v1/repartos/$assignmentId/estado',
      data: {
        'estado': state,
        'observacion': _optional(note),
        'resultadoFinal': _optional(result),
      },
    );
    return response.data!;
  }

  Future<Map<String, Object?>> collect(int assignmentId) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/repartos/$assignmentId/cobro',
    );
    return response.data!;
  }

  Future<List<Map<String, Object?>>> cashMovements() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/repartos/caja/movimientos',
      queryParameters: const {'page': 0, 'size': 100, 'sort': 'fecha,DESC'},
    );
    return _page(response.data);
  }

  Future<List<Map<String, Object?>>> transfers() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/repartos/caja/transferencias',
      queryParameters: const {
        'page': 0,
        'size': 100,
        'sort': 'fechaSolicitud,DESC',
      },
    );
    return _page(response.data);
  }

  Future<List<Map<String, Object?>>> cashiers() async {
    final response = await _api.get<List<dynamic>>(
      '/api/v1/repartos/caja/cajeros',
    );
    return _list(response.data);
  }

  Future<Map<String, Object?>> requestCashTransfer(
    int cashierId,
    String? note,
  ) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/repartos/caja/vaciar',
      data: {'cajeroId': cashierId, 'observacion': _optional(note)},
    );
    return response.data!;
  }

  Future<List<Map<String, Object?>>> vehicles() async {
    final response = await _api.get<List<dynamic>>('/api/v1/vehiculos');
    return _list(response.data);
  }

  Future<Map<String, Object?>?> currentVehicleUse() async {
    try {
      final response = await _api.get<Map<String, Object?>>(
        '/api/v1/vehiculos/mi-uso',
      );
      return response.data;
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, Object?>> startVehicle(
    int vehicleId, {
    required double mileage,
    required double fuel,
    String? note,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/vehiculos/$vehicleId/usar',
      data: {
        'kilometrajeInicial': mileage,
        'combustibleInicial': fuel,
        'observacion': _optional(note),
      },
    );
    return response.data!;
  }

  Future<Map<String, Object?>> addVehicleEvent(
    Map<String, Object?> data,
  ) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/vehiculos/mi-uso/eventos',
      data: data,
    );
    return response.data!;
  }

  Future<Map<String, Object?>> finishVehicle({
    required double mileage,
    required double fuel,
    String? note,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/vehiculos/mi-uso/finalizar',
      data: {
        'kilometrajeFinal': mileage,
        'combustibleFinal': fuel,
        'observacion': _optional(note),
      },
    );
    return response.data!;
  }

  static List<Map<String, Object?>> _page(Map<String, Object?>? data) =>
      _list(data?['content'] as List<dynamic>?);

  static List<Map<String, Object?>> _list(List<dynamic>? data) =>
      (data ?? const [])
          .map((item) => Map<String, Object?>.from(item as Map))
          .toList(growable: false);

  static String? _optional(String? value) {
    final clean = value?.trim();
    return clean == null || clean.isEmpty ? null : clean;
  }
}
