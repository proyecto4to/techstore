import 'dart:async';
import 'dart:convert';

import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../cache/cache_store.dart';
import '../logging/app_logger.dart';
import '../network/api_client.dart';
import 'realtime_event.dart';

final class RealtimeService {
  RealtimeService(this._api, this._cache, this._logger);

  final ApiClient _api;
  final CacheStore _cache;
  final AppLogger _logger;
  final _events = StreamController<RealtimeEvent>.broadcast();
  StompClient? _client;
  int _cursor = 0;
  int? _tenantId;
  Future<void> _processing = Future.value();

  Stream<RealtimeEvent> get events => _events.stream;

  Future<void> connect({
    required String baseUrl,
    required String accessToken,
  }) async {
    disconnect();
    final tenantId = _tenantFromJwt(accessToken);
    if (tenantId == null) {
      _logger.warning('Realtime omitido: JWT sin tenant válido');
      return;
    }
    _tenantId = tenantId;
    final stored = await _cache.read(scope: 'sync:$tenantId', key: 'cursor');
    _cursor = int.tryParse(stored ?? '') ?? 0;
    final uri = Uri.parse(baseUrl);
    final socketUrl = uri
        .replace(
          scheme: uri.scheme == 'https' ? 'wss' : 'ws',
          path: '/ws',
          query: null,
          fragment: null,
        )
        .toString();
    final bearer = 'Bearer $accessToken';
    _client = StompClient(
      config: StompConfig(
        url: socketUrl,
        reconnectDelay: const Duration(seconds: 3),
        heartbeatIncoming: const Duration(seconds: 10),
        heartbeatOutgoing: const Duration(seconds: 10),
        connectionTimeout: const Duration(seconds: 10),
        stompConnectHeaders: {'Authorization': bearer},
        webSocketConnectHeaders: {'Authorization': bearer},
        onConnect: (frame) {
          _client?.subscribe(
            destination: '/topic/tenant/$tenantId/events',
            callback: (frame) {
              final body = frame.body;
              if (body == null) return;
              _enqueue(Map<String, Object?>.from(jsonDecode(body) as Map));
            },
          );
          unawaited(_catchUp());
        },
        onStompError: (frame) =>
            _logger.warning('STOMP rechazó la conexión: ${frame.command}'),
        onWebSocketError: (error) => _logger.warning(
          'WebSocket no disponible; se usará recuperación por cursor',
        ),
      ),
    )..activate();
  }

  void disconnect() {
    _client?.deactivate();
    _client = null;
    _tenantId = null;
  }

  Future<void> dispose() async {
    disconnect();
    await _events.close();
  }

  Future<void> _catchUp() async {
    while (_tenantId != null) {
      try {
        final response = await _api.get<List<dynamic>>(
          '/api/v1/sync/events',
          queryParameters: {'after': _cursor, 'limit': 200},
        );
        final page = response.data ?? const [];
        for (final raw in page) {
          _enqueue(Map<String, Object?>.from(raw as Map));
        }
        await _processing;
        if (page.length < 200) return;
      } catch (_) {
        _logger.warning(
          'No se pudo recuperar el cursor realtime; se reintentará al reconectar',
        );
        return;
      }
    }
  }

  void _enqueue(Map<String, Object?> raw) {
    _processing = _processing.then((_) => _process(raw));
  }

  Future<void> _process(Map<String, Object?> raw) async {
    final event = RealtimeEvent.fromJson(raw);
    if (event.id <= _cursor || _tenantId == null) return;
    _cursor = event.id;
    await _cache.write(
      scope: 'sync:$_tenantId',
      key: 'cursor',
      json: '$_cursor',
      ttl: const Duration(days: 3650),
    );
    _events.add(event);
  }

  int? _tenantFromJwt(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return null;
      final payload = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      ) as Map;
      return (payload['tenant_id'] as num?)?.toInt();
    } catch (_) {
      return null;
    }
  }
}
