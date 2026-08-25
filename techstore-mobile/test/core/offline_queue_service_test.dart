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

void main() {
  test('reintenta un mensaje con el mismo clientMessageId', () async {
    final database = AppDatabase(NativeDatabase.memory());
    addTearDown(database.close);
    final adapter = _OfflineThenOnlineAdapter();
    final dio = Dio()..httpClientAdapter = adapter;
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

    final result = await queue.sendChatMessage(
      scope: 'user:test@techstore.local',
      conversationId: 9,
      content: 'Hola',
    );
    expect(result, ChatSendResult.queued);
    final pending = await database.dueOperations('user:test@techstore.local');
    expect(pending, hasLength(1));
    final queuedBody = jsonDecode(pending.single.jsonValue) as Map;

    await queue.process('user:test@techstore.local');

    expect(await database.dueOperations('user:test@techstore.local'), isEmpty);
    expect(adapter.bodies, hasLength(2));
    expect(
      adapter.bodies.last['clientMessageId'],
      queuedBody['clientMessageId'],
    );
  });

  test('aísla la cola con el tenant del JWT', () {
    final payload = base64Url.encode(
      utf8.encode(jsonEncode({'tenant_id': 42})),
    );
    expect(
      offlineScopeForSession('x.$payload.y', 'Admin@Example.com'),
      'tenant:42:user:admin@example.com',
    );
  });
}

final class _OfflineThenOnlineAdapter implements HttpClientAdapter {
  final List<Map<String, Object?>> bodies = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    bodies.add(Map<String, Object?>.from(options.data as Map));
    if (bodies.length == 1) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'offline',
      );
    }
    return ResponseBody.fromString(
      '{}',
      201,
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
