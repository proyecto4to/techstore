import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

import '../auth/auth_interceptor.dart';
import '../errors/app_failure.dart';
import '../logging/app_logger.dart';
import 'failure_mapper.dart';
import 'safe_network_log_interceptor.dart';

/// El backend responde 500 si se le pide un PDF con `Accept: application/json`,
/// que es el header global del cliente. Las descargas binarias lo reemplazan.
const _acceptBinario = {
  'Accept': 'application/pdf, application/octet-stream, */*',
};

final class ApiClient {
  ApiClient({
    required String baseUrl,
    required AppLogger logger,
    Dio? dio,
    Uuid uuid = const Uuid(),
  }) : _dio = dio ?? Dio() {
    _dio.options = BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      sendTimeout: const Duration(seconds: 20),
      responseType: ResponseType.json,
      headers: const {'Accept': 'application/json'},
    );
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          options.headers.putIfAbsent('X-Correlation-ID', uuid.v4);
          handler.next(options);
        },
      ),
    );
    _dio.interceptors.add(SafeNetworkLogInterceptor(logger));
  }

  final Dio _dio;
  AuthInterceptor? _authInterceptor;

  Dio get dio => _dio;

  void configureAuth({
    required String? Function() accessToken,
    required Future<String?> Function() refreshAccessToken,
  }) {
    final previous = _authInterceptor;
    if (previous != null) _dio.interceptors.remove(previous);
    final interceptor = AuthInterceptor(_dio, accessToken, refreshAccessToken);
    _authInterceptor = interceptor;
    _dio.interceptors.insert(1, interceptor);
  }

  Future<Response<T>> get<T>(
    String path, {
    Map<String, Object?>? queryParameters,
    Options? options,
  }) => _guard(
    () => _dio.get<T>(path, queryParameters: queryParameters, options: options),
  );

  Future<Response<T>> post<T>(String path, {Object? data, Options? options}) =>
      _guard(() => _dio.post<T>(path, data: data, options: options));

  Future<Response<T>> put<T>(String path, {Object? data, Options? options}) =>
      _guard(() => _dio.put<T>(path, data: data, options: options));

  Future<Response<T>> patch<T>(String path, {Object? data, Options? options}) =>
      _guard(() => _dio.patch<T>(path, data: data, options: options));

  Future<Response<T>> delete<T>(String path, {Options? options}) =>
      _guard(() => _dio.delete<T>(path, options: options));

  Future<Uint8List> getBytes(String path) async {
    final response = await _guard<List<int>>(
      () => _dio.get<List<int>>(
        path,
        options: Options(
          responseType: ResponseType.bytes,
          headers: _acceptBinario,
        ),
      ),
    );
    return Uint8List.fromList(response.data ?? const []);
  }

  Future<void> download(
    String path,
    String destination, {
    ProgressCallback? onReceiveProgress,
  }) async {
    await _guard<dynamic>(
      () => _dio.download(
        path,
        destination,
        options: Options(headers: _acceptBinario),
        onReceiveProgress: onReceiveProgress,
      ),
    );
  }

  Future<Response<T>> _guard<T>(Future<Response<T>> Function() request) async {
    try {
      return await request();
    } on DioException catch (error) {
      throw mapDioFailure(error);
    } on AppFailure {
      rethrow;
    } catch (_) {
      throw const UnexpectedFailure();
    }
  }
}
