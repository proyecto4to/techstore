import 'dart:developer' as developer;

abstract interface class AppLogger {
  void debug(String message);

  void info(String message);

  void warning(String message, [Object? error, StackTrace? stackTrace]);

  void error(String message, [Object? error, StackTrace? stackTrace]);
}

final class SafeAppLogger implements AppLogger {
  const SafeAppLogger();

  static final _secretPattern = RegExp(
    r'(authorization|password|token|secret|cookie)(\s*[:=]\s*)([^\s,;]+)',
    caseSensitive: false,
  );

  String _safe(Object? value) => value.toString().replaceAllMapped(
    _secretPattern,
    (match) => '${match[1]}${match[2]}[REDACTED]',
  );

  @override
  void debug(String message) =>
      developer.log(_safe(message), name: 'TechStore');

  @override
  void info(String message) =>
      developer.log(_safe(message), name: 'TechStore', level: 800);

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) =>
      developer.log(
        _safe(message),
        name: 'TechStore',
        level: 900,
        error: error == null ? null : _safe(error),
        stackTrace: stackTrace,
      );

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) =>
      developer.log(
        _safe(message),
        name: 'TechStore',
        level: 1000,
        error: error == null ? null : _safe(error),
        stackTrace: stackTrace,
      );
}
