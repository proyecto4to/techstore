import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../di/providers.dart';
import '../native/haptics_service.dart';
import '../native/voice_input_service.dart';

/// Micrófono que dicta sobre un campo de texto ya existente.
///
/// Se comporta como un accesorio opcional: si el equipo no tiene motor de
/// dictado o el permiso está denegado no ocupa lugar, así ninguna pantalla
/// cambia de layout ni pierde funcionalidad en dispositivos sin soporte.
class VoiceDictationButton extends ConsumerStatefulWidget {
  const VoiceDictationButton({
    required this.controller,
    this.onFinalResult,
    this.append = false,
    this.dictation = false,
    this.tooltip = 'Dictar por voz',
    super.key,
  });

  /// Campo que recibe el texto reconocido.
  final TextEditingController controller;

  /// Se invoca con el texto final, para disparar la acción del campo
  /// (por ejemplo lanzar la búsqueda del catálogo).
  final ValueChanged<String>? onFinalResult;

  /// Suma al contenido actual en vez de reemplazarlo.
  final bool append;

  /// Ajusta el motor para frases largas en lugar de términos de búsqueda.
  final bool dictation;

  final String tooltip;

  @override
  ConsumerState<VoiceDictationButton> createState() =>
      _VoiceDictationButtonState();
}

class _VoiceDictationButtonState extends ConsumerState<VoiceDictationButton> {
  VoiceInputAvailability? _availability;
  bool _listening = false;
  String _base = '';

  @override
  void dispose() {
    if (_listening) unawaited(ref.read(voiceInputServiceProvider).cancel());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Mientras no se probó el micrófono el botón se ofrece; en cuanto se sabe
    // que el equipo no puede dictar, desaparece y no vuelve a molestar.
    if (_availability != null &&
        _availability != VoiceInputAvailability.ready) {
      return const SizedBox.shrink();
    }
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: _listening ? 'Detener dictado' : widget.tooltip,
      onPressed: _toggle,
      isSelected: _listening,
      color: _listening ? scheme.error : null,
      icon: Icon(_listening ? Icons.mic : Icons.mic_none_outlined),
    );
  }

  Future<void> _toggle() async {
    final voice = ref.read(voiceInputServiceProvider);
    final haptics = ref.read(hapticsServiceProvider);
    if (_listening) {
      await voice.stop();
      if (mounted) setState(() => _listening = false);
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    // El permiso se pide recién acá, cuando el usuario pidió dictar.
    final availability = await voice.availability();
    if (!mounted) return;
    if (availability != VoiceInputAvailability.ready) {
      setState(() => _availability = availability);
      unawaited(haptics.play(HapticCue.error));
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            availability == VoiceInputAvailability.denied
                ? 'Sin permiso de micrófono para dictar'
                : 'Este equipo no tiene dictado por voz',
          ),
        ),
      );
      return;
    }
    _base = widget.append ? widget.controller.text.trimRight() : '';
    final started = await voice.start(
      dictation: widget.dictation,
      onResult: _onResult,
    );
    if (!mounted) return;
    if (!started) {
      unawaited(haptics.play(HapticCue.error));
      messenger.showSnackBar(
        const SnackBar(content: Text('No se pudo activar el micrófono')),
      );
      return;
    }
    unawaited(haptics.play(HapticCue.tap));
    setState(() => _listening = true);
  }

  void _onResult(String text, bool isFinal) {
    if (!mounted) return;
    final merged = _base.isEmpty ? text : '$_base $text';
    widget.controller
      ..text = merged
      ..selection = TextSelection.collapsed(offset: merged.length);
    if (!isFinal) return;
    setState(() => _listening = false);
    unawaited(ref.read(hapticsServiceProvider).play(HapticCue.selection));
    if (merged.trim().isNotEmpty) widget.onFinalResult?.call(merged.trim());
  }
}
