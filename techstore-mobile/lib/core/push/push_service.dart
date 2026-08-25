import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:uuid/uuid.dart';

import '../logging/app_logger.dart';
import '../network/api_client.dart';
import '../storage/secure_store.dart';

final class PushService {
  PushService(this._api, this._store, this._logger);

  static const _deviceKey = 'techstore.device_identifier';

  final ApiClient _api;
  final SecureStore _store;
  final AppLogger _logger;
  final _deepLinks = StreamController<String>.broadcast();
  final _foregroundMessages = StreamController<void>.broadcast();
  StreamSubscription<String>? _tokenSubscription;
  StreamSubscription<RemoteMessage>? _openedSubscription;
  StreamSubscription<RemoteMessage>? _messageSubscription;
  bool _started = false;

  Stream<String> get deepLinks => _deepLinks.stream;
  Stream<void> get foregroundMessages => _foregroundMessages.stream;
  bool get available => Firebase.apps.isNotEmpty;

  Future<bool> start() async {
    if (_started) return available;
    if (!available) {
      _logger.info('FCM no configurado para este flavor.');
      return false;
    }
    _started = true;
    try {
      final messaging = FirebaseMessaging.instance;
      final permission = await messaging.requestPermission();
      if (permission.authorizationStatus == AuthorizationStatus.denied) {
        _logger.info('Permiso push rechazado por el usuario.');
        return false;
      }
      final token = await messaging.getToken();
      if (token != null) await _registerSafely(token);
      _tokenSubscription = messaging.onTokenRefresh.listen(_registerSafely);
      _openedSubscription = FirebaseMessaging.onMessageOpenedApp.listen(
        _emitDeepLink,
      );
      _messageSubscription = FirebaseMessaging.onMessage.listen((message) {
        _foregroundMessages.add(null);
        _emitDeepLink(message, navigate: false);
      });
      final initial = await messaging.getInitialMessage();
      if (initial != null) _emitDeepLink(initial);
      return true;
    } catch (error, stackTrace) {
      _started = false;
      _logger.warning('No se pudo iniciar FCM.', error, stackTrace);
      return false;
    }
  }

  Future<void> stop({bool deactivate = false}) async {
    if (deactivate && available) {
      final identifier = await _store.read(_deviceKey);
      if (identifier != null) {
        try {
          await _api.delete<void>('/api/v1/dispositivos/actual/$identifier');
        } catch (error, stackTrace) {
          _logger.warning(
            'No se pudo desactivar el dispositivo.',
            error,
            stackTrace,
          );
        }
      }
    }
    await _tokenSubscription?.cancel();
    await _openedSubscription?.cancel();
    await _messageSubscription?.cancel();
    _tokenSubscription = null;
    _openedSubscription = null;
    _messageSubscription = null;
    _started = false;
  }

  Future<void> dispose() async {
    await stop();
    await _deepLinks.close();
    await _foregroundMessages.close();
  }

  Future<void> _register(String token) async {
    final identifier = await _deviceIdentifier();
    await _api.put<void>(
      '/api/v1/dispositivos/actual',
      data: {
        'plataforma': 'ANDROID',
        'pushToken': token,
        'deviceIdentifier': identifier,
      },
    );
  }

  Future<void> _registerSafely(String token) async {
    try {
      await _register(token);
    } catch (error, stackTrace) {
      _logger.warning(
        'No se pudo registrar el token FCM en TechStore.',
        error,
        stackTrace,
      );
    }
  }

  Future<String> _deviceIdentifier() async {
    final stored = await _store.read(_deviceKey);
    if (stored != null && stored.length >= 16) return stored;
    final created = 'flutter-${const Uuid().v4()}';
    await _store.write(_deviceKey, created);
    return created;
  }

  void _emitDeepLink(RemoteMessage message, {bool navigate = true}) {
    final link = (message.data['url'] ?? message.data['deepLink'])?.toString();
    if (navigate && link != null && link.isNotEmpty) _deepLinks.add(link);
  }
}
