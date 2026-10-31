import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../data/auth_repository.dart';
import '../domain/auth_tokens.dart';
import 'auth_state.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.watch(apiClientProvider),
    ref.watch(secureStoreProvider),
  );
});

final authSessionProvider = NotifierProvider<SessionController, AuthState>(
  SessionController.new,
);

final class SessionController extends Notifier<AuthState> {
  String? _accessToken;
  String? _refreshToken;
  Future<String?>? _refreshInFlight;
  Timer? _refreshTimer;
  bool _keepSignedIn = false;

  AuthRepository get _repository => ref.read(authRepositoryProvider);
  String? get accessToken => _accessToken;

  /// Si la persona pidió mantener la sesión iniciada en este teléfono.
  ///
  /// Con esto apagado —lo de siempre— la sesión vive solo mientras la app está
  /// abierta: el token no se guarda en el teléfono y se corta al pasar a
  /// segundo plano, que es lo que necesita un equipo compartido en el
  /// mostrador. Encendido, el token de renovación se guarda cifrado y la
  /// sesión sobrevive a cerrar la app y a reiniciar el teléfono, hasta que la
  /// persona salga o el servidor la dé por vencida.
  bool get keepSignedIn => _keepSignedIn;

  @override
  AuthState build() {
    ref.onDispose(() => _refreshTimer?.cancel());
    ref
        .read(apiClientProvider)
        .configureAuth(
          accessToken: () => _accessToken,
          refreshAccessToken: refreshAccessToken,
        );
    unawaited(_restore());
    return const AuthState.initializing();
  }

  Future<bool> login({
    required String email,
    required String password,
    bool keepSignedIn = false,
  }) async {
    state = const AuthState.authenticating();
    try {
      final tokens = await _repository.login(email: email, password: password);
      await _setKeepSignedIn(keepSignedIn);
      await _apply(tokens);
      return true;
    } on UnauthorizedFailure {
      state = const AuthState.unauthenticated(
        'El correo o la contraseña no son correctos. Revisalos e intentá de nuevo.',
      );
      return false;
    } on AppFailure catch (failure) {
      state = AuthState.unauthenticated(failure.message);
      return false;
    }
  }

  Future<bool> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
  }) async {
    state = const AuthState.authenticating();
    try {
      final tokens = await _repository.register(
        firstName: firstName,
        lastName: lastName,
        email: email,
        password: password,
      );
      await _setKeepSignedIn(false);
      await _apply(tokens);
      return true;
    } on AppFailure catch (failure) {
      state = AuthState.unauthenticated(failure.message);
      return false;
    }
  }

  Future<void> updateTaxData({String? ruc, String? businessName}) async {
    final user = await _repository.updateTaxData(
      ruc: ruc,
      businessName: businessName,
    );
    state = AuthState.authenticated(user);
  }

  Future<void> logout() async {
    final token = _refreshToken;
    await ref.read(pushServiceProvider).stop(deactivate: true);
    _clearMemory();
    state = const AuthState.unauthenticated();
    try {
      if (token != null) await _repository.logout(token);
    } finally {
      // Salir es salir: también se olvida el «mantener sesión», para que la
      // próxima persona que use el teléfono no entre como la anterior.
      await _repository.clearRefreshToken();
      await _setKeepSignedIn(false);
    }
  }

  /// Cierra la sesión porque la app pasó a segundo plano.
  ///
  /// Se separa de [logout] en dos cosas: el mensaje explica por qué hay que
  /// volver a entrar, y no se desactiva el envío de notificaciones —el equipo
  /// sigue siendo el mismo, lo que se corta es la sesión.
  ///
  /// El token guardado se borra antes de avisarle al servidor: si el sistema
  /// mata la app en el medio, lo que no puede quedar es la credencial en el
  /// teléfono. La baja en el servidor es el mejor esfuerzo posible.
  Future<void> closeForBackground() async {
    if (!state.isAuthenticated) return;
    // La persona eligió seguir con la sesión iniciada: salir de la app no la
    // corta.
    if (_keepSignedIn) return;
    final token = _refreshToken;
    _clearMemory();
    await _repository.clearRefreshToken();
    state = const AuthState.unauthenticated(
      'Cerramos tu sesión al salir de la aplicación. Ingresá de nuevo.',
    );
    if (token == null) return;
    try {
      await _repository.logout(token);
    } on AppFailure {
      // Si no hay red, el token local ya se borró: la sesión en este teléfono
      // está cerrada igual y el refresh caduca solo del lado del servidor.
    }
  }

  Future<String?> refreshAccessToken() async {
    final active = _refreshInFlight;
    if (active != null) return active;
    final refresh = _performRefresh();
    _refreshInFlight = refresh;
    try {
      return await refresh;
    } finally {
      if (identical(_refreshInFlight, refresh)) _refreshInFlight = null;
    }
  }

  Future<void> _restore() async {
    final stored = await _repository.storedRefreshToken();
    if (stored == null || stored.isEmpty) {
      state = const AuthState.unauthenticated();
      return;
    }
    // Un token guardado sin el «mantener sesión» es de una versión anterior,
    // que lo guardaba siempre: no se usa para entrar solo.
    if (!await _repository.keepSession()) {
      await _repository.clearRefreshToken();
      state = const AuthState.unauthenticated();
      return;
    }
    _keepSignedIn = true;
    _refreshToken = stored;
    await refreshAccessToken();
  }

  Future<String?> _performRefresh() async {
    final token = _refreshToken;
    if (token == null) return null;
    try {
      await _apply(await _repository.refresh(token));
      return _accessToken;
    } on AppFailure catch (failure) {
      // Que el servidor no conteste no quiere decir que la sesión haya
      // caducado. Antes, abrir la app con el backend apagado borraba el token
      // guardado y avisaba que la sesión había expirado: la sesión se perdía
      // por un problema de red. Solo el rechazo del servidor cierra sesión.
      if (failure is NetworkFailure || failure is ServerFailure) {
        _refreshTimer?.cancel();
        _accessToken = null;
        state = const AuthState.unauthenticated(
          'No pudimos conectarnos con el servidor. Revisá tu conexión.',
        );
        return null;
      }
      _clearMemory();
      await _repository.clearRefreshToken();
      await _setKeepSignedIn(false);
      state = const AuthState.unauthenticated(
        'Tu sesión expiró. Iniciá sesión nuevamente.',
      );
      return null;
    }
  }

  Future<void> _setKeepSignedIn(bool keep) async {
    _keepSignedIn = keep;
    await _repository.storeKeepSession(keep: keep);
  }

  Future<void> _apply(AuthTokens tokens) async {
    _accessToken = tokens.accessToken;
    _refreshToken = tokens.refreshToken;
    // Sin «mantener sesión», el token vive solo en memoria: cerrar la app es
    // cerrar la sesión, aunque el sistema la mate sin pasar por segundo plano.
    if (_keepSignedIn) {
      await _repository.storeRefreshToken(tokens.refreshToken);
    } else {
      await _repository.clearRefreshToken();
    }
    _refreshTimer?.cancel();
    final delay = tokens.expiresIn > const Duration(seconds: 45)
        ? tokens.expiresIn - const Duration(seconds: 30)
        : tokens.expiresIn;
    _refreshTimer = Timer(delay, () => unawaited(refreshAccessToken()));
    state = AuthState.authenticated(tokens.user);
  }

  void _clearMemory() {
    _refreshTimer?.cancel();
    _accessToken = null;
    _refreshToken = null;
  }
}
