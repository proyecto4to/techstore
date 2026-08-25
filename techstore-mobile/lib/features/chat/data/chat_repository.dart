import 'package:uuid/uuid.dart';

import '../../../core/network/api_client.dart';
import '../../../core/offline/offline_queue_service.dart';

final class ChatRepository {
  const ChatRepository(this._api, this._queue, this.scope);
  final ApiClient _api;
  final OfflineQueueService _queue;
  final String scope;

  Future<List<Map<String, Object?>>> conversations() async {
    final response = await _api.get<Object?>(
      '/api/v1/conversaciones',
      queryParameters: {'page': 0, 'size': 50},
    );
    return _list(response.data);
  }

  Future<int> create({required String message, int? orderId}) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/conversaciones',
      data: {
        'clientMessageId': const Uuid().v4(),
        'mensaje': message.trim(),
        'pedidoId': ?orderId,
      },
    );
    final conversation = Map<String, Object?>.from(
      response.data!['conversacion'] as Map,
    );
    return (conversation['id'] as num).toInt();
  }

  Future<List<Map<String, Object?>>> messages(int conversationId) async {
    final response = await _api.get<Object?>(
      '/api/v1/conversaciones/$conversationId/mensajes',
      queryParameters: {'page': 0, 'size': 100},
    );
    return _list(response.data);
  }

  Future<ChatSendResult> send(int conversationId, String content) =>
      _queue.sendChatMessage(
        scope: scope,
        conversationId: conversationId,
        content: content,
      );

  Stream<int> pendingCount() => _queue.pendingCount(scope);

  Future<void> retryPending() => _queue.process(scope);

  Future<void> markRead(int conversationId) =>
      _api.put<void>('/api/v1/conversaciones/$conversationId/leido');

  List<Map<String, Object?>> _list(Object? value) {
    final raw = value is List
        ? value
        : value is Map && value['content'] is List
        ? value['content']! as List
        : const [];
    return raw
        .map((item) => Map<String, Object?>.from(item as Map))
        .toList(growable: false);
  }
}
