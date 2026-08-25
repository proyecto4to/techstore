sealed class AppFailure implements Exception {
  const AppFailure(this.message, {this.code});

  final String message;
  final String? code;

  @override
  String toString() => message;
}

final class NetworkFailure extends AppFailure {
  const NetworkFailure(super.message, {super.code});
}

final class UnauthorizedFailure extends AppFailure {
  const UnauthorizedFailure([super.message = 'La sesión no es válida']);
}

final class ForbiddenFailure extends AppFailure {
  const ForbiddenFailure([super.message = 'No tenés permiso para esta acción']);
}

final class NotFoundFailure extends AppFailure {
  const NotFoundFailure([super.message = 'No se encontró el recurso']);
}

final class ConflictFailure extends AppFailure {
  const ConflictFailure(super.message, {super.code});
}

final class ValidationFailure extends AppFailure {
  const ValidationFailure(super.message, {super.code, this.fields = const {}});

  final Map<String, String> fields;
}

final class ServerFailure extends AppFailure {
  const ServerFailure(super.message, {super.code});
}

final class UnexpectedFailure extends AppFailure {
  const UnexpectedFailure([super.message = 'Ocurrió un error inesperado']);
}
