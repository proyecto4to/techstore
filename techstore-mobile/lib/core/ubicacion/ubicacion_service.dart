import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../native/app_excursion.dart';

/// Dónde está el teléfono, o por qué no se pudo saber.
///
/// El resultado nunca es «null y averiguá vos»: cada negativa trae un texto
/// para mostrar y, cuando corresponde, la manera de resolverla. La ubicación
/// automática es una comodidad, no un requisito: si falla, el punto se marca
/// igual tocando el mapa o buscando la calle.
sealed class ResultadoDeUbicacion {
  const ResultadoDeUbicacion();
}

final class UbicacionObtenida extends ResultadoDeUbicacion {
  const UbicacionObtenida(this.punto);

  final LatLng punto;
}

/// Qué hacer cuando no se pudo leer la ubicación.
enum SalidaSugerida {
  /// Nada que tocar: se puede volver a intentar sin más.
  reintentar,

  /// La ubicación del teléfono está apagada.
  ajustesDelTelefono,

  /// El permiso quedó denegado para siempre; solo se cambia desde los ajustes.
  ajustesDeLaApp,
}

final class UbicacionNoDisponible extends ResultadoDeUbicacion {
  const UbicacionNoDisponible(this.motivo, this.salida);

  /// Qué mostrarle a la persona, en su idioma y sin jerga.
  final String motivo;
  final SalidaSugerida salida;
}

/// Lee la ubicación del teléfono, pidiendo permiso si hace falta.
class UbicacionService {
  UbicacionService([AppExcursion? excursion])
    : _excursion = excursion ?? AppExcursion();

  /// Los ajustes del sistema son otra aplicación. Sin marcar esa salida, la
  /// guarda de sesión la toma por «la persona se fue» y cierra la sesión: se
  /// vuelve de conceder el permiso y hay que iniciarla de nuevo.
  final AppExcursion _excursion;

  /// Cuánto esperar antes de rendirse.
  ///
  /// Bajo techo el GPS puede no fijar nunca. Sin límite, el botón se queda
  /// girando para siempre y la persona no sabe si esperar o marcar a mano.
  static const _limite = Duration(seconds: 12);

  Future<ResultadoDeUbicacion> donde() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      return const UbicacionNoDisponible(
        'La ubicación del teléfono está apagada.',
        SalidaSugerida.ajustesDelTelefono,
      );
    }
    var permiso = await Geolocator.checkPermission();
    if (permiso == LocationPermission.denied) {
      permiso = await Geolocator.requestPermission();
    }
    if (permiso == LocationPermission.deniedForever) {
      return const UbicacionNoDisponible(
        'La app no tiene permiso para usar tu ubicación.',
        SalidaSugerida.ajustesDeLaApp,
      );
    }
    if (permiso == LocationPermission.denied) {
      return const UbicacionNoDisponible(
        'Sin permiso de ubicación no podemos ubicarte solos.',
        SalidaSugerida.reintentar,
      );
    }
    try {
      final posicion = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: _limite,
        ),
      );
      return UbicacionObtenida(LatLng(posicion.latitude, posicion.longitude));
    } catch (_) {
      return const UbicacionNoDisponible(
        'No pudimos leer tu ubicación en este momento.',
        SalidaSugerida.reintentar,
      );
    }
  }

  /// Abre la pantalla de ajustes que corresponde a la negativa.
  Future<void> abrirAjustes(SalidaSugerida salida) async {
    switch (salida) {
      case SalidaSugerida.ajustesDelTelefono:
        await _excursion.durante(Geolocator.openLocationSettings);
      case SalidaSugerida.ajustesDeLaApp:
        await _excursion.durante(Geolocator.openAppSettings);
      case SalidaSugerida.reintentar:
        break;
    }
  }
}
