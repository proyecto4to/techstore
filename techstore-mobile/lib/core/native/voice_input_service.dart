import 'dart:async';

import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../logging/app_logger.dart';

/// Estado del reconocimiento de voz en el dispositivo actual.
enum VoiceInputAvailability {
  /// Hay motor de dictado y permiso de micrófono concedido.
  ready,

  /// El equipo no expone reconocimiento de voz (emuladores sin Google app).
  unsupported,

  /// El usuario rechazó el permiso de micrófono.
  denied,
}

/// Dictado por voz para rellenar campos de texto existentes.
///
/// El audio no se graba ni se sube: el motor del sistema devuelve texto y la
/// app solo escribe ese texto en el campo que pidió el dictado.
final class VoiceInputService {
  VoiceInputService(this._logger, {SpeechToText? speech})
    : _speech = speech ?? SpeechToText();

  /// Español rioplatense primero; se cae al locale del sistema si no está.
  static const preferredLocales = ['es_PY', 'es_AR', 'es_ES', 'es_US', 'es'];

  static const listenWindow = Duration(seconds: 30);
  static const silenceWindow = Duration(seconds: 4);

  final AppLogger _logger;
  final SpeechToText _speech;

  VoiceInputAvailability? _availability;
  String? _localeId;

  bool get isListening => _speech.isListening;

  /// Inicializa el motor y cachea el resultado.
  ///
  /// Pide el permiso de micrófono, así que solo debe invocarse cuando el
  /// usuario pidió dictar; nunca al construir una pantalla. El rechazo no se
  /// cachea, para que un segundo intento pueda volver a preguntar.
  Future<VoiceInputAvailability> availability() async {
    final cached = _availability;
    if (cached != null) return cached;
    try {
      final ready = await _speech.initialize(
        onError: (error) => _logger.debug('Dictado: ${error.errorMsg}'),
        finalTimeout: const Duration(seconds: 2),
      );
      if (!ready) {
        return await _speech.hasPermission
            ? _availability = VoiceInputAvailability.unsupported
            : VoiceInputAvailability.denied;
      }
      _localeId = await _resolveLocale();
      return _availability = VoiceInputAvailability.ready;
    } catch (error, stackTrace) {
      _logger.warning('No se pudo iniciar el dictado', error, stackTrace);
      return _availability = VoiceInputAvailability.unsupported;
    }
  }

  /// Arranca una sesión de dictado. Devuelve `false` si no se pudo escuchar.
  ///
  /// [onResult] recibe el texto parcial mientras el usuario habla y luego el
  /// texto final con `isFinal` en `true`.
  Future<bool> start({
    required void Function(String text, bool isFinal) onResult,
    bool dictation = false,
  }) async {
    if (await availability() != VoiceInputAvailability.ready) return false;
    if (_speech.isListening) return false;
    try {
      await _speech.listen(
        onResult: (SpeechRecognitionResult result) =>
            onResult(result.recognizedWords, result.finalResult),
        listenOptions: SpeechListenOptions(
          localeId: _localeId,
          listenMode: dictation ? ListenMode.dictation : ListenMode.search,
          partialResults: true,
          cancelOnError: true,
          listenFor: listenWindow,
          pauseFor: silenceWindow,
        ),
      );
      return true;
    } catch (error, stackTrace) {
      _logger.warning('No se pudo escuchar el micrófono', error, stackTrace);
      return false;
    }
  }

  /// Cierra la sesión conservando el último texto reconocido.
  Future<void> stop() async {
    if (!_speech.isListening) return;
    try {
      await _speech.stop();
    } catch (error, stackTrace) {
      _logger.debug('No se pudo detener el dictado: $error $stackTrace');
    }
  }

  /// Cierra la sesión descartando lo dictado.
  Future<void> cancel() async {
    if (!_speech.isListening) return;
    try {
      await _speech.cancel();
    } catch (error, stackTrace) {
      _logger.debug('No se pudo cancelar el dictado: $error $stackTrace');
    }
  }

  Future<String?> _resolveLocale() async {
    try {
      final locales = await _speech.locales();
      final available = {for (final locale in locales) locale.localeId};
      for (final candidate in preferredLocales) {
        final match = available.firstWhere(
          (locale) => locale.replaceAll('-', '_').startsWith(candidate),
          orElse: () => '',
        );
        if (match.isNotEmpty) return match;
      }
      return (await _speech.systemLocale())?.localeId;
    } catch (_) {
      return null;
    }
  }
}
