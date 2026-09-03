import 'app_failure.dart';

/// Convierte cualquier error en algo que una persona pueda entender.
///
/// [AppFailure] ya trae un texto pensado para el usuario: o lo escribió el
/// backend en su catálogo de mensajes, o es uno de los nuestros. Cualquier otra
/// cosa —una excepción de plataforma, un error de tipos, un fallo de disco— es
/// para el log, no para la pantalla: ahí se usa [fallback], que dice qué pasó y
/// qué puede hacer la persona.
String userMessageFor(Object error, {required String fallback}) {
  if (error is AppFailure) {
    final message = error.message.trim();
    if (message.isNotEmpty) return message;
  }
  return fallback;
}
