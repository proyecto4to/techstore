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

  AuthRepository get _repository => ref.read(authRepositoryProvider);
  String? get accessToken => _accessToken;

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

  Future<bool> login({required String email, required String password}) async {
    state = const AuthState.authenticating();
    try {
      await _apply(await _repository.login(email: email, password: password));
      return true;
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
      await _apply(
        await _repository.register(
          firstName: firstName,
          lastName: lastName,
          email: email,
          password: password,
        ),
      );
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
      await _repository.clearRefreshToken();
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
    _refreshToken = stored;
    await refreshAccessToken();
  }

  Future<String?> _performRefresh() async {
    final token = _refreshToken;
    if (token == null) return null;
    try {
      await _apply(await _repository.refresh(token));
      return _accessToken;
    } on AppFailure {
      _clearMemory();
      await _repository.clearRefreshToken();
      state = const AuthState.unauthenticated(
        'Tu sesión expiró. Iniciá sesión nuevamente.',
      );
      return null;
    }
  }

  Future<void> _apply(AuthTokens tokens) async {
    _accessToken = tokens.accessToken;
    _refreshToken = tokens.refreshToken;
    await _repository.storeRefreshToken(tokens.refreshToken);
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
