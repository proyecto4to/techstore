import '../domain/auth_user.dart';

enum AuthStatus { initializing, unauthenticated, authenticating, authenticated }

final class AuthState {
  const AuthState._({required this.status, this.user, this.message});

  const AuthState.initializing() : this._(status: AuthStatus.initializing);

  const AuthState.unauthenticated([String? message])
    : this._(status: AuthStatus.unauthenticated, message: message);

  const AuthState.authenticating() : this._(status: AuthStatus.authenticating);

  const AuthState.authenticated(AuthUser user)
    : this._(status: AuthStatus.authenticated, user: user);

  final AuthStatus status;
  final AuthUser? user;
  final String? message;

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isBusy =>
      status == AuthStatus.initializing || status == AuthStatus.authenticating;
}
