import 'package:dio/dio.dart';

import '../errors/app_failure.dart';
import '../errors/problem_detail.dart';

AppFailure mapDioFailure(DioException exception) {
  if (exception.type == DioExceptionType.connectionError ||
      exception.type == DioExceptionType.connectionTimeout ||
      exception.type == DioExceptionType.receiveTimeout ||
      exception.type == DioExceptionType.sendTimeout) {
    return const NetworkFailure('No se pudo conectar con TechStore');
  }

  final status = exception.response?.statusCode ?? 0;
  final data = exception.response?.data;
  final problem = data is Map
      ? ProblemDetail.fromJson(
          data.map((key, value) => MapEntry(key.toString(), value)),
        )
      : ProblemDetail(title: 'Error', status: status);

  return switch (status) {
    400 || 422 => ValidationFailure(
      problem.userMessage,
      code: problem.code,
      fields: problem.errors,
    ),
    401 => const UnauthorizedFailure(),
    403 => const ForbiddenFailure(),
    404 => NotFoundFailure(problem.userMessage),
    409 => ConflictFailure(problem.userMessage, code: problem.code),
    >= 500 => ServerFailure(
      'TechStore no pudo procesar la solicitud',
      code: problem.code,
    ),
    _ => UnexpectedFailure(problem.userMessage),
  };
}
