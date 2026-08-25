import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/invoices/data/invoices_repository.dart';

void main() {
  test('lista y convierte facturas usando el contrato paginado real', () async {
    final adapter = _InvoiceAdapter();
    final dio = Dio()..httpClientAdapter = adapter;
    final repository = InvoicesRepository(
      ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      ),
    );

    final result = await repository.list(page: 1, size: 20);

    expect(adapter.lastRequest?.path, '/api/v1/facturas');
    expect(adapter.lastRequest?.queryParameters['page'], 1);
    expect(adapter.lastRequest?.queryParameters['size'], 20);
    expect(adapter.lastRequest?.queryParameters['sort'], 'fecha,DESC');
    expect(result.page, 1);
    expect(result.totalElements, 21);
    expect(result.totalPages, 2);
    expect(result.items.single.number, '001-001-0000042');
    expect(result.items.single.receiverLabel, 'Consumidor final');
    expect(result.items.single.total, 2500000);
  });
}

final class _InvoiceAdapter implements HttpClientAdapter {
  RequestOptions? lastRequest;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    lastRequest = options;
    return ResponseBody.fromString(
      jsonEncode({
        'content': [
          {
            'id': 42,
            'numero': '001-001-0000042',
            'condicionVenta': 'Contado',
            'timbrado': '12345678',
            'pedidoNumero': 91,
            'fecha': '2026-08-24T10:30:00',
            'moneda': 'PYG',
            'subtotal': 2272727,
            'iva': 227273,
            'total': 2500000,
            'estado': 'Pagada',
            'receptorRuc': null,
            'receptorNombre': null,
            'receptorEmail': null,
          },
        ],
        'page': 1,
        'size': 20,
        'totalElements': 21,
        'totalPages': 2,
      }),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
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
