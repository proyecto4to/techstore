import 'dart:io';

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
  final baseUrl = Platform.environment['TECHSTORE_E2E_BASE_URL'];

  test('persiste durante el corte, drena al reconectar y no duplica', () async {
    final runId = DateTime.now().toUtc().millisecondsSinceEpoch;
    final email = 'phase20.offline.$runId@example.invalid';
    const password = 'Phase20Offline9!';
    final online = Dio(BaseOptions(baseUrl: baseUrl!));
    final registration = _map(
      await online.post<dynamic>(
        '/api/v1/auth/registro',
        data: {
          'nombre': 'Offline',
          'apellido': 'Reconnect',
          'email': email,
          'password': password,
        },
      ),
    );
    final token = registration['accessToken'] as String;
    online.options.headers['Authorization'] = 'Bearer $token';
    final created = _map(
      await online.post<dynamic>(
        '/api/v1/conversaciones',
        data: {
          'pedidoId': null,
          'clientMessageId': 'p20-offline-conversation-$runId',
          'mensaje': 'Conversacion para reconexion real',
        },
      ),
    );
    final conversation = Map<String, dynamic>.from(
      created['conversacion'] as Map,
    );
    final conversationId = (conversation['id'] as num).toInt();

    final database = AppDatabase(NativeDatabase.memory());
    final dio = Dio();
    final api = ApiClient(
      baseUrl: 'http://127.0.0.1:65534',
      logger: const _SilentLogger(),
      dio: dio,
    );
    api.configureAuth(
      accessToken: () => token,
      refreshAccessToken: () async => null,
    );
    final queue = OfflineQueueService(
      database,
      api,
      ConnectivityService(Connectivity(), dio),
      const _SilentLogger(),
      backendReachable: () async => true,
    );
    final scope = offlineScopeForSession(token, email);
    const content = 'Mensaje persistido durante corte y enviado al reconectar';

    try {
      final sendResult = await queue.sendChatMessage(
        scope: scope,
        conversationId: conversationId,
        content: content,
      );
      expect(sendResult, ChatSendResult.queued);
      final pendingBefore = await database.dueOperations(scope);
      expect(pendingBefore, hasLength(1));
      expect(pendingBefore.single.attempts, 0);
      // ignore: avoid_print
      print(
        'OFFLINE_E2E_OK network=down queued=1 persisted=true scope_isolated=true',
      );

      api.dio.options.baseUrl = baseUrl;
      await queue.process(scope);
      expect(await database.dueOperations(scope), isEmpty);
      await queue.process(scope);

      final history = _map(
        await online.get<dynamic>(
          '/api/v1/conversaciones/$conversationId/mensajes',
          queryParameters: {'page': 0, 'size': 100},
        ),
      );
      final messages = history['content'] as List<dynamic>;
      final matches = messages
          .map((item) => Map<String, dynamic>.from(item as Map))
          .where((item) => item['contenido'] == content)
          .toList();
      expect(matches, hasLength(1));
      // ignore: avoid_print
      print(
        'OFFLINE_RECONNECT_E2E_OK network=restored drained=true duplicate=false',
      );
    } finally {
      await database.close();
    }
  }, skip: baseUrl == null ? 'Requiere TECHSTORE_E2E_BASE_URL.' : false);
}

Map<String, dynamic> _map(Response<dynamic> response) {
  final value = response.data;
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return value.cast<String, dynamic>();
  throw StateError('Se esperaba un objeto JSON.');
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
