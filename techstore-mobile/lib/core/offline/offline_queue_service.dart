import 'dart:convert';

import 'package:uuid/uuid.dart';

import '../database/app_database.dart';
import '../errors/app_failure.dart';
import '../logging/app_logger.dart';
import '../network/api_client.dart';
import '../network/connectivity_service.dart';

enum ChatSendResult { sent, queued }

String offlineScopeForSession(String? accessToken, String email) {
  var tenant = 'unknown';
  if (accessToken != null) {
    try {
      final parts = accessToken.split('.');
      final payload = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      ) as Map;
      tenant = payload['tenant_id']?.toString() ?? tenant;
    } on Object {
      // Auth handles malformed tokens; local data still gets an isolated scope.
    }
  }
  return 'tenant:$tenant:user:${email.trim().toLowerCase()}';
}

final class OfflineQueueService {
  OfflineQueueService(
    this._database,
    this._api,
    ConnectivityService connectivity,
    this._logger, {
    this._uuid = const Uuid(),
    Future<bool> Function()? backendReachable,
  }) : _backendReachable = backendReachable ?? connectivity.backendReachable;

  static const chatMessageKind = 'CHAT_MESSAGE';

  final AppDatabase _database;
  final ApiClient _api;
  final AppLogger _logger;
  final Uuid _uuid;
  final Future<bool> Function() _backendReachable;
  bool _processing = false;

  Future<ChatSendResult> sendChatMessage({
    required String scope,
    required int conversationId,
    required String content,
  }) async {
    final id = _uuid.v4();
    final payload = <String, Object?>{
      'conversationId': conversationId,
      'clientMessageId': id,
      'contenido': content.trim(),
    };
    try {
      await _api.post<Object?>(
        '/api/v1/conversaciones/$conversationId/mensajes',
        data: {'clientMessageId': id, 'contenido': content.trim()},
      );
      return ChatSendResult.sent;
    } on NetworkFailure {
      await _database.enqueueOperation(
        PendingOperationsCompanion.insert(
          id: id,
          scope: scope,
          kind: chatMessageKind,
          jsonValue: jsonEncode(payload),
        ),
      );
      return ChatSendResult.queued;
    }
  }

  Future<void> process(String scope) async {
    if (_processing || !await _backendReachable()) return;
    _processing = true;
    try {
      for (final operation in await _database.dueOperations(scope)) {
        try {
          await _dispatch(operation);
          await _database.deleteOperation(operation.id);
        } on NetworkFailure catch (error) {
          await _scheduleRetry(operation, error.message);
          break;
        } on AppFailure catch (error) {
          // 4xx/business errors cannot heal through blind retries. Keep the
          // operation visible for manual recovery, with a long retry delay.
          await _scheduleRetry(operation, error.message, permanent: true);
        }
      }
    } finally {
      _processing = false;
    }
  }

  Stream<int> pendingCount(String scope) => _database.watchPendingCount(scope);

  Future<void> _dispatch(PendingOperation operation) async {
    if (operation.kind != chatMessageKind) {
      throw const UnexpectedFailure('Operación offline no compatible');
    }
    final payload = Map<String, Object?>.from(
      jsonDecode(operation.jsonValue) as Map,
    );
    final conversationId = (payload['conversationId'] as num).toInt();
    await _api.post<Object?>(
      '/api/v1/conversaciones/$conversationId/mensajes',
      data: {
        'clientMessageId': payload['clientMessageId'],
        'contenido': payload['contenido'],
      },
    );
  }

  Future<void> _scheduleRetry(
    PendingOperation operation,
    String error, {
    bool permanent = false,
  }) async {
    final attempts = operation.attempts + 1;
    final exponent = attempts.clamp(1, 8);
    final seconds = permanent ? 86400 : (1 << exponent);
    await _database.retryOperation(
      id: operation.id,
      attempts: attempts,
      nextAttemptAt: DateTime.now().add(Duration(seconds: seconds)),
      error: error,
    );
    _logger.warning(
      'Operación offline ${operation.id} pendiente (intento $attempts)',
    );
  }
}
