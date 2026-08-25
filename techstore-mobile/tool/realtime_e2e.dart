import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

Future<void> main() async {
  final baseUrl =
      Platform.environment['TECHSTORE_E2E_BASE_URL'] ?? 'http://localhost:8090';
  final email = Platform.environment['TECHSTORE_E2E_EMAIL'];
  final password = Platform.environment['TECHSTORE_E2E_PASSWORD'];
  if (email == null || password == null) {
    stderr.writeln('Defini TECHSTORE_E2E_EMAIL y TECHSTORE_E2E_PASSWORD.');
    exitCode = 2;
    return;
  }

  final api = Dio(BaseOptions(baseUrl: baseUrl));
  final registration = await api.post<Map<String, dynamic>>(
    '/api/v1/auth/registro',
    data: {
      'nombre': 'Realtime',
      'apellido': 'E2E',
      'email': email,
      'password': password,
    },
  );
  final accessToken = registration.data!['accessToken'] as String;
  final refreshToken = registration.data!['refreshToken'] as String;
  final tenantId = _tenant(accessToken);
  final received = Completer<void>();
  late final StompClient stomp;
  final socketUrl = Uri.parse(baseUrl)
      .replace(scheme: baseUrl.startsWith('https') ? 'wss' : 'ws', path: '/ws')
      .toString();
  stomp = StompClient(
    config: StompConfig(
      url: socketUrl,
      reconnectDelay: Duration.zero,
      connectionTimeout: const Duration(seconds: 10),
      stompConnectHeaders: {'Authorization': 'Bearer $accessToken'},
      webSocketConnectHeaders: {'Authorization': 'Bearer $accessToken'},
      onConnect: (_) {
        stdout.writeln('STOMP_CONNECTED');
        stomp.subscribe(
          destination: '/topic/tenant/$tenantId/events',
          callback: (frame) {
            final event = jsonDecode(frame.body!) as Map;
            if (event['eventType'] == 'USER_UPDATED' && !received.isCompleted) {
              received.complete();
            }
          },
        );
        unawaited(() async {
          try {
            await Future<void>.delayed(const Duration(seconds: 1));
            await api.put<void>(
              '/api/v1/auth/datos-fiscales',
              options: Options(
                headers: {'Authorization': 'Bearer $accessToken'},
              ),
              data: {'ruc': '80011111-7', 'razonSocial': 'Realtime E2E'},
            );
          } catch (error, stackTrace) {
            if (!received.isCompleted) {
              received.completeError(error, stackTrace);
            }
          }
        }());
      },
      onStompError: (frame) {
        stderr.writeln('STOMP_ERROR command=${frame.command}');
        if (!received.isCompleted) {
          received.completeError(StateError('STOMP_ERROR ${frame.command}'));
        }
      },
      onWebSocketError: (error) {
        stderr.writeln('WEBSOCKET_ERROR type=${error.runtimeType}');
        if (!received.isCompleted) received.completeError(error);
      },
      onWebSocketDone: () => stderr.writeln('WEBSOCKET_DONE'),
      onDisconnect: (_) => stderr.writeln('STOMP_DISCONNECTED'),
    ),
  )..activate();

  await received.future.timeout(const Duration(seconds: 20));
  stomp.deactivate();
  await api.post<void>(
    '/api/v1/auth/logout',
    data: {'refreshToken': refreshToken},
  );
  stdout.writeln(
    'REALTIME_E2E_OK event=USER_UPDATED tenant_isolated=true transport=STOMP',
  );
}

int _tenant(String token) {
  final payload = jsonDecode(
    utf8.decode(base64Url.decode(base64Url.normalize(token.split('.')[1]))),
  ) as Map;
  return (payload['tenant_id'] as num).toInt();
}
