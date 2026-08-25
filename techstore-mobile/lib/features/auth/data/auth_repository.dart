import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_store.dart';
import '../domain/auth_tokens.dart';
import '../domain/auth_user.dart';

final class AuthRepository {
  const AuthRepository(this._api, this._secureStore);

  static const _refreshKey = 'techstore.refresh_token';

  final ApiClient _api;
  final SecureStore _secureStore;

  Future<AuthTokens> login({
    required String email,
    required String password,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/auth/login',
      data: {'email': email.trim(), 'password': password},
    );
    return AuthTokens.fromJson(response.data!);
  }

  Future<AuthTokens> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/auth/registro',
      data: {
        'nombre': firstName.trim(),
        'apellido': lastName.trim(),
        'email': email.trim(),
        'password': password,
      },
    );
    return AuthTokens.fromJson(response.data!);
  }

  Future<AuthUser> updateTaxData({String? ruc, String? businessName}) async {
    final response = await _api.put<Map<String, Object?>>(
      '/api/v1/auth/datos-fiscales',
      data: {'ruc': ruc?.trim(), 'razonSocial': businessName?.trim()},
    );
    return AuthUser.fromJson(response.data!);
  }

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) => _api.put<void>(
    '/api/v1/auth/password',
    data: {'passwordActual': currentPassword, 'passwordNueva': newPassword},
  );

  Future<AuthTokens> refresh(String refreshToken) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/auth/refresh',
      data: {'refreshToken': refreshToken},
    );
    return AuthTokens.fromJson(response.data!);
  }

  Future<void> logout(String refreshToken) async {
    await _api.post<void>(
      '/api/v1/auth/logout',
      data: {'refreshToken': refreshToken},
    );
  }

  Future<void> requestPasswordRecovery(String email) async {
    await _api.post<void>(
      '/api/v1/auth/password/forgot',
      data: {'email': email.trim()},
    );
  }

  Future<void> resetPassword({
    required String token,
    required String password,
  }) async {
    await _api.post<void>(
      '/api/v1/auth/password/reset',
      data: {'token': token, 'password': password},
    );
  }

  Future<String?> storedRefreshToken() => _secureStore.read(_refreshKey);

  Future<void> storeRefreshToken(String token) =>
      _secureStore.write(_refreshKey, token);

  Future<void> clearRefreshToken() => _secureStore.delete(_refreshKey);
}
