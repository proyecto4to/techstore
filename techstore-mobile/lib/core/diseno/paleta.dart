import 'package:flutter/painting.dart';

/// Los colores de la marca, con su rol. La tabla completa y los contrastes
/// medidos están en `docs/marca/MANUAL.md`.
///
/// Las pantallas no usan esto directamente salvo que no haya un rol del tema
/// que sirva: lo normal es `Theme.of(context).colorScheme` o
/// `MarcaTema.of(context)`, así un cambio de tono se hace en un solo lugar.
abstract final class Paleta {
  // Negros, del fondo a la superficie más alta. Sobre negro no se eleva con
  // sombra, que no se ve: se sube un nivel de superficie y se agrega un filo.
  static const fondo = Color(0xFF000000);
  static const negroPrincipal = Color(0xFF080A10);
  static const negroAzulado = Color(0xFF0B1120);
  static const superficie = Color(0xFF111827);
  static const superficieElevada = Color(0xFF172033);

  // Azules: de la acción principal al acento de texto.
  static const azulElectrico = Color(0xFF2563EB);
  static const azulPrincipal = Color(0xFF3B82F6);
  static const azulLuminoso = Color(0xFF60A5FA);

  /// El cursor del isotipo: contadores, insignias, cursor de texto.
  static const cian = Color(0xFF38BDF8);

  static const texto = Color(0xFFF8FAFC);
  static const textoSecundario = Color(0xFF94A3B8);

  /// Filo decorativo. Con 1,6:1 contra el negro no alcanza para dibujar el
  /// contorno de un control: para eso está [bordeDeControl].
  static const borde = Color(0xFF253247);

  /// Contorno de campos y botones delineados: 3:1 contra el fondo, el mínimo
  /// para que un control se reconozca como tal.
  static const bordeDeControl = Color(0xFF4B5B75);

  /// Contenedor de lo seleccionado: un azul tan oscuro que todavía es negro.
  static const azulProfundo = Color(0xFF13233F);

  // Estados, con los colores clásicos de Monokai. Solo comunican un estado y
  // siempre van con texto o ícono: el color no carga el mensaje solo.
  static const exito = Color(0xFFA6E22E);
  static const aviso = Color(0xFFFD971F);
  static const error = Color(0xFFF92672);
  static const promocion = Color(0xFFE6DB74);

  /// Tinta oscura para escribir encima del cian, el verde o el amarillo.
  static const tinta = Color(0xFF04101F);

  // Contenedores de estado: el color de estado hundido en el negro.
  static const exitoHundido = Color(0xFF1A2608);
  static const avisoHundido = Color(0xFF2B1A05);
  static const errorHundido = Color(0xFF34091A);
  static const promocionHundida = Color(0xFF2A2608);
}
