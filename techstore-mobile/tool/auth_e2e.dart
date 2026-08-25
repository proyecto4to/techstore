import 'dart:io';

import 'package:dio/dio.dart';

Future<void> main() async {
  final baseUrl =
      Platform.environment['TECHSTORE_E2E_BASE_URL'] ?? 'http://localhost:8090';
  final email = Platform.environment['TECHSTORE_E2E_EMAIL'];
  final password = Platform.environment['TECHSTORE_E2E_PASSWORD'];
  final register = Platform.environment['TECHSTORE_E2E_REGISTER'] == 'true';
  if (email == null || password == null) {
    stderr.writeln('Defini TECHSTORE_E2E_EMAIL y TECHSTORE_E2E_PASSWORD.');
    exitCode = 2;
    return;
  }

  final client = Dio(BaseOptions(baseUrl: baseUrl));
  if (register) {
    final registration = await client.post<Map<String, dynamic>>(
      '/api/v1/auth/registro',
      data: {
        'nombre': 'Mobile',
        'apellido': 'E2E',
        'email': email,
        'password': password,
      },
    );
    await client.post<void>(
      '/api/v1/auth/logout',
      data: {'refreshToken': registration.data!['refreshToken']},
    );
  }
  final login = await client.post<Map<String, dynamic>>(
    '/api/v1/auth/login',
    data: {'email': email, 'password': password},
  );
  final loginData = login.data!;
  final firstRefresh = loginData['refreshToken'] as String;

  final refresh = await client.post<Map<String, dynamic>>(
    '/api/v1/auth/refresh',
    data: {'refreshToken': firstRefresh},
  );
  final refreshData = refresh.data!;
  final rotatedRefresh = refreshData['refreshToken'] as String;
  if (firstRefresh == rotatedRefresh) {
    throw StateError('El backend no roto el refresh token');
  }

  await client.post<void>(
    '/api/v1/auth/logout',
    data: {'refreshToken': rotatedRefresh},
  );
  final user = refreshData['usuario'] as Map<String, dynamic>;
  stdout.writeln(
    "AUTH_E2E_OK role=${user['rol']} refresh_rotated=true logout=true",
  );
}
