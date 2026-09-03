import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/native/voice_input_service.dart';
import 'package:techstore_mobile/core/widgets/voice_dictation_button.dart';

void main() {
  testWidgets('mostrar el botón no toca el micrófono ni pide permiso', (
    tester,
  ) async {
    final speech = _SilentSpeech();
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          voiceInputServiceProvider.overrideWithValue(
            VoiceInputService(const _SilentLogger(), speech: speech),
          ),
        ],
        child: MaterialApp(
          home: Scaffold(
            body: TextField(
              controller: controller,
              decoration: InputDecoration(
                suffixIcon: VoiceDictationButton(controller: controller),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.mic_none_outlined), findsOneWidget);
    expect(speech.initializations, 0);
  });

  testWidgets('al tocarlo pide el motor y se retira si no hay dictado', (
    tester,
  ) async {
    final speech = _SilentSpeech();
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          voiceInputServiceProvider.overrideWithValue(
            VoiceInputService(const _SilentLogger(), speech: speech),
          ),
        ],
        child: MaterialApp(
          home: Scaffold(
            body: VoiceDictationButton(controller: controller),
          ),
        ),
      ),
    );
    await tester.tap(find.byIcon(Icons.mic_none_outlined));
    await tester.pumpAndSettle();

    expect(speech.initializations, 1);
    expect(find.byIcon(Icons.mic_none_outlined), findsNothing);
  });
}

/// Motor de voz que siempre falla al inicializar, como un equipo sin
/// reconocimiento instalado.
final class _SilentSpeech extends SpeechToText {
  _SilentSpeech() : super.withMethodChannel();

  int initializations = 0;

  @override
  Future<bool> initialize({
    SpeechErrorListener? onError,
    SpeechStatusListener? onStatus,
    dynamic debugLogging = false,
    Duration finalTimeout = SpeechToText.defaultFinalTimeout,
    List<SpeechConfigOption>? options,
  }) async {
    initializations++;
    return false;
  }

  @override
  Future<bool> get hasPermission async => true;
}

final class _SilentLogger implements AppLogger {
  const _SilentLogger();

  @override
  void debug(String message) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}
}
