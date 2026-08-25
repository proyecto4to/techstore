import 'dart:convert';
import 'dart:typed_data';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/network/connectivity_service.dart';
import 'package:techstore_mobile/core/offline/offline_queue_service.dart';
import 'package:techstore_mobile/features/chat/data/chat_repository.dart';

void main() {
  test(
    'lee el id anidado del contrato real al crear una conversacion',
    () async {
      final database = AppDatabase(NativeDatabase.memory());
      addTearDown(database.close);
      final dio = Dio()..httpClientAdapter = const _ConversationAdapter();
      final api = ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      );
      final queue = OfflineQueueService(
        database,
        api,
        ConnectivityService(Connectivity(), dio),
        const _SilentLogger(),
        backendReachable: () async => true,
      );
      final repository = ChatRepository(api, queue, 'tenant:1:user:test');

      expect(await repository.create(message: 'Hola'), 44);
    },
  );
}

final class _ConversationAdapter implements HttpClientAdapter {
  const _ConversationAdapter();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode({
      'conversacion': {'id': 44},
      'mensaje': {'id': 91},
    }),
    201,
    headers: {
      Headers.contentTypeHeader: ['application/json'],
    },
  );

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
