import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/errors/app_failure.dart';
import 'package:techstore_mobile/core/network/failure_mapper.dart';

void main() {
  test('mapea Problem Detail 422 a validación tipada', () {
    final request = RequestOptions(path: '/api/v1/productos');
    final failure = mapDioFailure(
      DioException(
        requestOptions: request,
        response: Response<Object?>(
          requestOptions: request,
          statusCode: 422,
          data: {
            'title': 'Datos inválidos',
            'status': 422,
            'detail': 'Revisá los campos',
            'code': 'VALIDATION_ERROR',
            'errors': {'nombre': 'Es obligatorio'},
          },
        ),
      ),
    );

    expect(failure, isA<ValidationFailure>());
    final validation = failure as ValidationFailure;
    expect(validation.code, 'VALIDATION_ERROR');
    expect(validation.fields['nombre'], 'Es obligatorio');
  });

  test('mapea timeout a fallo de red sin filtrar detalles internos', () {
    final failure = mapDioFailure(
      DioException(
        requestOptions: RequestOptions(path: '/api/v1/productos'),
        type: DioExceptionType.connectionTimeout,
      ),
    );

    expect(failure, isA<NetworkFailure>());
    expect(failure.message, 'No se pudo conectar con TechStore');
  });
}
