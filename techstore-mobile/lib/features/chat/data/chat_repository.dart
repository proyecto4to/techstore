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

  /// A quiénes puede escribirles quien está en sesión.
  ///
  /// El filtrado por rol lo hace el servidor: acá solo se pasa lo que eligió el
  /// usuario en la pantalla. Un rol que no le corresponda vuelve como lista
  /// vacía, no como la lista real.
  Future<List<Map<String, Object?>>> recipients({
    String? query,
    String? role,
  }) async {
    final response = await _api.get<Object?>(
      '/api/v1/conversaciones/destinatarios',
      queryParameters: {
        if (query != null && query.trim().isNotEmpty) 'q': query.trim(),
        if (role != null && role.isNotEmpty) 'rol': role,
      },
    );
    return _list(response.data);
  }

  /// Abre una conversación, o retoma la que ya exista.
  ///
  /// Con [recipientId] en nulo abre la de soporte con la tienda, que es lo
  /// único que puede hacer un cliente.
  Future<int> create({
    required String message,
    int? orderId,
    int? recipientId,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/conversaciones',
      data: {
        'clientMessageId': const Uuid().v4(),
        'mensaje': message.trim(),
        'pedidoId': ?orderId,
        'destinatarioId': ?recipientId,
      },
    );
    final conversation = Map<String, Object?>.from(
      response.data!['conversacion'] as Map,
    );
    return (conversation['id'] as num).toInt();
  }

  /// Una conversación puntual, para saber con quién se está hablando.
  Future<Map<String, Object?>> conversation(int conversationId) async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/conversaciones/$conversationId',
    );
    return response.data ?? const {};
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
