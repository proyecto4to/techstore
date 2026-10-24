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
  final adminEmail = Platform.environment['TECHSTORE_E2E_ADMIN_EMAIL'];
  final adminPassword = Platform.environment['TECHSTORE_E2E_ADMIN_PASSWORD'];
  if (email == null ||
      password == null ||
      adminEmail == null ||
      adminPassword == null) {
    stderr.writeln(
      'Defini las credenciales E2E del cliente y del administrador.',
    );
    exitCode = 2;
    return;
  }

  final api = Dio(BaseOptions(baseUrl: baseUrl));
  final admin = Dio(BaseOptions(baseUrl: baseUrl));
  String? refreshToken;
  String? adminRefreshToken;
  int? productId;
  StompClient? stomp;
  Future<void>? update;
  final mutationCancellation = CancelToken();
  Object? primaryError;
  (Object, StackTrace)? cleanupError;

  Future<void> cleanup(String step, FutureOr<void> Function() action) async {
    try {
      await action();
    } catch (error, stackTrace) {
      cleanupError ??= (error, stackTrace);
      stderr.writeln(
        'REALTIME_E2E_CLEANUP_ERROR step=$step type=${error.runtimeType}',
      );
    }
  }

  try {
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
    refreshToken = registration.data!['refreshToken'] as String;
    final tenantId = _tenant(accessToken);
    final adminLogin = await admin.post<Map<String, dynamic>>(
      '/api/v1/auth/login',
      data: {'email': adminEmail, 'password': adminPassword},
    );
    adminRefreshToken = adminLogin.data!['refreshToken'] as String;
    admin.options.headers['Authorization'] =
        'Bearer ${adminLogin.data!['accessToken']}';
    final productBody = <String, Object?>{
      'nombre': 'Realtime E2E ${DateTime.now().millisecondsSinceEpoch}',
      'descripcion': 'Producto descartable para el canal de compra',
      'precio': 10000,
      'iva': 10,
      'stockReal': 10,
      'stockMinimo': 1,
      'marcaId': null,
      'modeloId': null,
      'imagenUrl': null,
      'activo': true,
    };
    final product = await admin.post<Map<String, dynamic>>(
      '/api/v1/productos',
      data: productBody,
    );
    final createdProductId = (product.data!['id'] as num).toInt();
    productId = createdProductId;
    final received = Completer<void>();
    final subscribed = Completer<void>();
    late final StompClient connection;
    final socketUrl = Uri.parse(baseUrl)
        .replace(
          scheme: baseUrl.startsWith('https') ? 'wss' : 'ws',
          path: '/ws',
        )
        .toString();
    connection = StompClient(
      config: StompConfig(
        url: socketUrl,
        reconnectDelay: Duration.zero,
        connectionTimeout: const Duration(seconds: 10),
        stompConnectHeaders: {'Authorization': 'Bearer $accessToken'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $accessToken'},
        onConnect: (_) {
          stdout.writeln('STOMP_CONNECTED');
          connection.subscribe(
            destination: '/topic/tenant/$tenantId/events/compra',
            callback: (frame) {
              final event = jsonDecode(frame.body!) as Map;
              if (event['eventType'] == 'PRODUCT_UPDATED' &&
                  event['aggregateId'] == createdProductId &&
                  !received.isCompleted) {
                received.complete();
              }
            },
          );
          if (!subscribed.isCompleted) subscribed.complete();
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
    );
    stomp = connection;
    connection.activate();
    final updateFuture = () async {
      await subscribed.future.timeout(const Duration(seconds: 10));
      await Future<void>.delayed(const Duration(seconds: 1));
      await admin.put<void>(
        '/api/v1/productos/$createdProductId',
        data: {...productBody, 'descripcion': 'Actualizado para realtime'},
        cancelToken: mutationCancellation,
      );
    }();
    update = updateFuture;
    // El evento puede llegar antes de que termine el PUT. Ambos tienen que
    // completarse bien, incluso si hay otras modificaciones en el tenant.
    await Future.wait<void>([
      received.future,
      updateFuture,
    ], eagerError: true).timeout(const Duration(seconds: 20));
  } catch (error) {
    primaryError = error;
    rethrow;
  } finally {
    mutationCancellation.cancel('Finalizó la prueba de realtime');
    await cleanup('stomp', () => stomp?.deactivate());
    if (update != null) {
      // Si falló el socket o venció la espera, se cancela el PUT y se espera
      // su cierre antes de borrar el producto. Su error ya lo conserva wait.
      try {
        await update;
      } catch (_) {}
    }
    if (productId != null) {
      await cleanup(
        'product',
        () async => admin.delete<void>('/api/v1/productos/$productId'),
      );
    }
    if (refreshToken != null) {
      await cleanup(
        'customer-session',
        () async => api.post<void>(
          '/api/v1/auth/logout',
          data: {'refreshToken': refreshToken},
        ),
      );
    }
    if (adminRefreshToken != null) {
      await cleanup(
        'admin-session',
        () async => admin.post<void>(
          '/api/v1/auth/logout',
          data: {'refreshToken': adminRefreshToken},
        ),
      );
    }
    await cleanup('customer-client', () => api.close(force: true));
    await cleanup('admin-client', () => admin.close(force: true));
    final cleanupFailure = cleanupError;
    if (primaryError == null && cleanupFailure != null) {
      Error.throwWithStackTrace(cleanupFailure.$1, cleanupFailure.$2);
    }
  }
  stdout.writeln(
    'REALTIME_E2E_OK event=PRODUCT_UPDATED role=Cliente '
    'channel=compra aggregateId=$productId mutation_awaited=true '
    'tenant_isolated=true transport=STOMP',
  );
}

int _tenant(String token) {
  final payload = jsonDecode(
    utf8.decode(base64Url.decode(base64Url.normalize(token.split('.')[1]))),
  ) as Map;
  return (payload['tenant_id'] as num).toInt();
}
