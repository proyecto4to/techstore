import 'package:dio/dio.dart';

final class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._dio, this._accessToken, this._refreshAccessToken);

  final Dio _dio;
  final String? Function() _accessToken;
  final Future<String?> Function() _refreshAccessToken;

  static const _retried = 'auth_retried';

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = _accessToken();
    if (token != null && token.isNotEmpty && !_isPublicAuth(options.path)) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final request = err.requestOptions;
    if (err.response?.statusCode != 401 ||
        request.extra[_retried] == true ||
        _isPublicAuth(request.path)) {
      handler.next(err);
      return;
    }

    final token = await _refreshAccessToken();
    if (token == null) {
      handler.next(err);
      return;
    }

    request.extra[_retried] = true;
    request.headers['Authorization'] = 'Bearer $token';
    try {
      handler.resolve(await _dio.fetch<Object?>(request));
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }

  bool _isPublicAuth(String path) =>
      path.endsWith('/auth/login') ||
      path.endsWith('/auth/registro') ||
      path.endsWith('/auth/refresh') ||
      path.endsWith('/auth/logout') ||
      path.contains('/auth/password/forgot') ||
      path.contains('/auth/password/reset');
}
