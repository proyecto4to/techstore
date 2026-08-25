import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

Future<void> main() async {
  final baseUrl =
      Platform.environment['TECHSTORE_E2E_BASE_URL'] ?? 'http://127.0.0.1:8092';
  final adminEmail = Platform.environment['TECHSTORE_E2E_ADMIN_EMAIL'];
  final adminPassword = Platform.environment['TECHSTORE_E2E_ADMIN_PASSWORD'];
  if (adminEmail == null || adminPassword == null) {
    stderr.writeln(
      'Defini TECHSTORE_E2E_ADMIN_EMAIL y TECHSTORE_E2E_ADMIN_PASSWORD.',
    );
    exitCode = 2;
    return;
  }

  final anonymous = Dio(BaseOptions(baseUrl: baseUrl));
  final webLogin = _map(
    await anonymous.post<dynamic>(
      '/api/v1/auth/login',
      data: {'email': adminEmail, 'password': adminPassword},
    ),
  );
  final webToken = webLogin['accessToken'] as String;
  final web = _api(baseUrl, webToken);
  final runId = DateTime.now().toUtc().millisecondsSinceEpoch;
  final mobileEmail = 'phase20.sync.mobile.$runId@example.invalid';
  const mobilePassword = 'Phase20Sync9!';
  await web.post<dynamic>(
    '/api/v1/usuarios',
    data: {
      'nombre': 'Mobile',
      'apellido': 'Sync',
      'email': mobileEmail,
      'password': mobilePassword,
      'rol': 'Admin',
    },
  );
  final mobileLogin = _map(
    await anonymous.post<dynamic>(
      '/api/v1/auth/login',
      data: {'email': mobileEmail, 'password': mobilePassword},
    ),
  );
  final mobileToken = mobileLogin['accessToken'] as String;
  final mobile = _api(baseUrl, mobileToken);
  final tenantId = _tenant(webToken);
  if (_tenant(mobileToken) != tenantId) {
    throw StateError('Los clientes no comparten el mismo tenant.');
  }

  final webReady = Completer<void>();
  final mobileReady = Completer<void>();
  final mobileSawWebCreate = Completer<int>();
  final webSawMobileUpdate = Completer<int>();
  late final StompClient webSocket;
  late final StompClient mobileSocket;

  webSocket = _socket(
    baseUrl: baseUrl,
    token: webToken,
    tenantId: tenantId,
    ready: webReady,
    onEvent: (event) {
      if (event['eventType'] == 'PRODUCT_UPDATED' &&
          !webSawMobileUpdate.isCompleted) {
        webSawMobileUpdate.complete((event['aggregateId'] as num).toInt());
      }
    },
  );
  mobileSocket = _socket(
    baseUrl: baseUrl,
    token: mobileToken,
    tenantId: tenantId,
    ready: mobileReady,
    onEvent: (event) {
      if (event['eventType'] == 'PRODUCT_CREATED' &&
          !mobileSawWebCreate.isCompleted) {
        mobileSawWebCreate.complete((event['aggregateId'] as num).toInt());
      }
    },
  );
  webSocket.activate();
  mobileSocket.activate();

  int? productId;
  try {
    await Future.wait([webReady.future, mobileReady.future])
        .timeout(const Duration(seconds: 15));
    final syncCursor = await _latestEventId(mobile);
    final brands = _list(await web.get<dynamic>('/api/v1/marcas'));
    final brandId = _id(_asMap(brands.first));
    final productBody = <String, dynamic>{
      'nombre': 'Phase 20 Cross Client $runId',
      'descripcion': 'Creado desde el cliente Web',
      'precio': 150000,
      'iva': 10,
      'stockReal': 8,
      'stockMinimo': 2,
      'pesoKg': 0.5,
      'marcaId': brandId,
      'activo': true,
      'ocultarSinStock': false,
    };

    final created = _map(
      await web.post<dynamic>('/api/v1/productos', data: productBody),
    );
    productId = _id(created);
    final createdEventId = await mobileSawWebCreate.future.timeout(
      const Duration(seconds: 15),
    );
    if (createdEventId != productId) {
      throw StateError('Mobile recibio el alta de otro producto.');
    }
    final mobileRead = _map(
      await mobile.get<dynamic>('/api/v1/productos/admin/$productId'),
    );
    if (mobileRead['descripcion'] != 'Creado desde el cliente Web') {
      throw StateError('Mobile no pudo reconsultar el cambio de Web.');
    }
    stdout.writeln(
      'CROSS_CLIENT_OK direction=WEB_TO_MOBILE event=PRODUCT_CREATED refetch=true',
    );

    await mobile.put<dynamic>(
      '/api/v1/productos/$productId',
      data: {
        ...productBody,
        'descripcion': 'Actualizado desde el cliente Mobile',
        'stockReal': 9,
      },
    );
    final updatedEventId = await webSawMobileUpdate.future.timeout(
      const Duration(seconds: 15),
    );
    if (updatedEventId != productId) {
      throw StateError('Web recibio la actualizacion de otro producto.');
    }
    final webRead = _map(
      await web.get<dynamic>('/api/v1/productos/admin/$productId'),
    );
    if (webRead['descripcion'] != 'Actualizado desde el cliente Mobile' ||
        (webRead['stockReal'] as num).toInt() != 9) {
      throw StateError('Web no pudo reconsultar el cambio de Mobile.');
    }
    stdout.writeln(
      'CROSS_CLIENT_OK direction=MOBILE_TO_WEB event=PRODUCT_UPDATED refetch=true',
    );

    final recovery = _list(
      await mobile.get<dynamic>(
        '/api/v1/sync/events',
        queryParameters: {'after': syncCursor, 'limit': 200},
      ),
    );
    final eventTypes = recovery
        .where((item) => _asMap(item)['aggregateId'] == productId)
        .map((item) => _asMap(item)['eventType'])
        .toSet();
    if (!eventTypes.contains('PRODUCT_CREATED') ||
        !eventTypes.contains('PRODUCT_UPDATED') ||
        !eventTypes.contains('STOCK_UPDATED')) {
      throw StateError('La recuperacion sync no contiene todos los eventos.');
    }
    stdout.writeln(
      'CROSS_CLIENT_SYNC_E2E_OK tenant_isolated=true recovery=true stock_event=true',
    );
  } finally {
    webSocket.deactivate();
    mobileSocket.deactivate();
    if (productId != null) {
      await web.delete<dynamic>('/api/v1/productos/$productId');
    }
  }
}

StompClient _socket({
  required String baseUrl,
  required String token,
  required int tenantId,
  required Completer<void> ready,
  required void Function(Map<String, dynamic>) onEvent,
}) {
  late final StompClient socket;
  final url = Uri.parse(baseUrl)
      .replace(scheme: baseUrl.startsWith('https') ? 'wss' : 'ws', path: '/ws')
      .toString();
  socket = StompClient(
    config: StompConfig(
      url: url,
      reconnectDelay: Duration.zero,
      connectionTimeout: const Duration(seconds: 10),
      stompConnectHeaders: {'Authorization': 'Bearer $token'},
      webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
      onConnect: (_) {
        socket.subscribe(
          destination: '/topic/tenant/$tenantId/events',
          callback: (frame) {
            if (frame.body == null) return;
            onEvent((jsonDecode(frame.body!) as Map).cast<String, dynamic>());
          },
        );
        if (!ready.isCompleted) ready.complete();
      },
      onStompError: (frame) {
        if (!ready.isCompleted) {
          ready.completeError(StateError('STOMP ${frame.command}'));
        }
      },
      onWebSocketError: (error) {
        if (!ready.isCompleted) ready.completeError(error);
      },
    ),
  );
  return socket;
}

Dio _api(String baseUrl, String token) => Dio(
  BaseOptions(baseUrl: baseUrl, headers: {'Authorization': 'Bearer $token'}),
);

Map<String, dynamic> _map(Response<dynamic> response) => _asMap(response.data);

Map<String, dynamic> _asMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return value.cast<String, dynamic>();
  throw StateError('Se esperaba un objeto JSON.');
}

List<dynamic> _list(Response<dynamic> response) {
  if (response.data is List) return response.data as List<dynamic>;
  throw StateError('Se esperaba una lista JSON.');
}

int _id(Map<String, dynamic> value) => (value['id'] as num).toInt();

int _tenant(String token) {
  final payload = jsonDecode(
    utf8.decode(base64Url.decode(base64Url.normalize(token.split('.')[1]))),
  ) as Map;
  return (payload['tenant_id'] as num).toInt();
}

Future<int> _latestEventId(Dio api) async {
  var cursor = 0;
  for (var page = 0; page < 100; page++) {
    final events = _list(
      await api.get<dynamic>(
        '/api/v1/sync/events',
        queryParameters: {'after': cursor, 'limit': 200},
      ),
    );
    if (events.isEmpty) return cursor;
    cursor = events
        .map((event) => (_asMap(event)['id'] as num).toInt())
        .reduce((left, right) => left > right ? left : right);
    if (events.length < 200) return cursor;
  }
  throw StateError('No se pudo alcanzar el final del historial sync.');
}
