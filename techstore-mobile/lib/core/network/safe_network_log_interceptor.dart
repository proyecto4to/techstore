import 'package:dio/dio.dart';

import '../logging/app_logger.dart';

final class SafeNetworkLogInterceptor extends Interceptor {
  SafeNetworkLogInterceptor(this._logger);

  final AppLogger _logger;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    _logger.debug('HTTP ${options.method} ${options.uri.path}');
    handler.next(options);
  }

  @override
  void onResponse(
    Response<Object?> response,
    ResponseInterceptorHandler handler,
  ) {
    _logger.debug(
      'HTTP ${response.statusCode} ${response.requestOptions.uri.path}',
    );
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    _logger.warning(
      'HTTP ${err.response?.statusCode ?? 0} ${err.requestOptions.uri.path}',
      err.type,
    );
    handler.next(err);
  }
}
