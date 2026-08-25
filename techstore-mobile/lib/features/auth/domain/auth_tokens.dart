import 'auth_user.dart';

final class AuthTokens {
  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });

  factory AuthTokens.fromJson(Map<String, Object?> json) => AuthTokens(
    accessToken: json['accessToken']! as String,
    refreshToken: json['refreshToken']! as String,
    expiresIn: Duration(milliseconds: (json['expiresInMs'] as num).toInt()),
    user: AuthUser.fromJson(
      (json['usuario']! as Map).map(
        (key, value) => MapEntry(key.toString(), value),
      ),
    ),
  );

  final String accessToken;
  final String refreshToken;
  final Duration expiresIn;
  final AuthUser user;
}
