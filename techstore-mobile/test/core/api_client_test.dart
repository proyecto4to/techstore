import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/errors/app_failure.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';

void main() {
  test(
    'agrega correlación y expone conflictos como fallo de dominio',
    () async {
      final adapter = _ConflictAdapter();
      final dio = Dio()..httpClientAdapter = adapter;
      final client = ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      );

      await expectLater(
        client.get<Object>('/api/v1/productos/1'),
        throwsA(
          isA<ConflictFailure>().having(
            (failure) => failure.code,
            'code',
            'STOCK_CONFLICT',
          ),
        ),
      );
      expect(adapter.headers['X-Correlation-ID'], isNotEmpty);
    },
  );

  test(
    'descarga documentos como bytes sin depender del archivo destino',
    () async {
      final adapter = _BytesAdapter();
      final dio = Dio()..httpClientAdapter = adapter;
      final client = ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      );

      final bytes = await client.getBytes('/api/v1/facturas/44/pdf');

      expect(bytes, [0x25, 0x50, 0x44, 0x46]);
      expect(adapter.responseType, ResponseType.bytes);
    },
  );
}

final class _ConflictAdapter implements HttpClientAdapter {
  Map<String, Object?> headers = {};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    headers = options.headers;
    return ResponseBody.fromString(
      jsonEncode({
        'title': 'Conflicto',
        'status': 409,
        'detail': 'El stock cambió',
        'code': 'STOCK_CONFLICT',
      }),
      409,
      headers: {
        Headers.contentTypeHeader: ['application/problem+json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

final class _BytesAdapter implements HttpClientAdapter {
  ResponseType? responseType;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    responseType = options.responseType;
    return ResponseBody.fromBytes(
      const [0x25, 0x50, 0x44, 0x46],
      200,
      headers: {
        Headers.contentTypeHeader: ['application/pdf'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

final class _SilentLogger implements AppLogger {
  const _SilentLogger();

  @override
  void debug(String message) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
