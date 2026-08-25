import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';

final class ConnectivityService {
  ConnectivityService(this._connectivity, this._dio);

  final Connectivity _connectivity;
  final Dio _dio;

  Stream<bool> get networkAvailable =>
      _connectivity.onConnectivityChanged.map(_hasNetwork).distinct();

  Future<bool> hasNetwork() async =>
      _hasNetwork(await _connectivity.checkConnectivity());

  Future<bool> backendReachable() async {
    if (!await hasNetwork()) return false;
    try {
      final response = await _dio.get<Object>(
        '/api/v1/productos',
        queryParameters: const {'page': 0, 'size': 1},
        options: Options(
          receiveTimeout: const Duration(seconds: 4),
          sendTimeout: const Duration(seconds: 4),
        ),
      );
      return response.statusCode == 200;
    } on DioException {
      return false;
    }
  }

  bool _hasNetwork(List<ConnectivityResult> results) =>
      results.any((result) => result != ConnectivityResult.none);
}
