import 'package:flutter/material.dart';

import 'paleta.dart';
import 'tipografia.dart';

/// Lo que la marca necesita y Material no trae: el rótulo comentado, los
/// estilos de precio, los colores de estado y la luz azul.
///
/// Se lee con [MarcaTema.of]. Si la pantalla se monta sin el tema de la app
/// —pasa en los tests, que usan un `MaterialApp` pelado—, devuelve los valores
/// de la marca igual, en lugar de romper.
@immutable
class MarcaTema extends ThemeExtension<MarcaTema> {
  const MarcaTema({
    required this.rotulo,
    required this.precio,
    required this.precioGrande,
    required this.codigo,
    required this.exito,
    required this.aviso,
    required this.error,
    required this.promocion,
    required this.cursor,
    required this.halo,
    required this.degradeAccion,
  });

  static const porDefecto = MarcaTema(
    rotulo: TextStyle(
      fontFamily: Tipografia.mono,
      fontSize: 11.5,
      height: 1.3,
      fontWeight: FontWeight.w500,
      letterSpacing: 0.4,
      color: Paleta.textoSecundario,
    ),
    precio: TextStyle(
      fontFamily: Tipografia.titulares,
      fontSize: 17,
      height: 1.15,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.2,
      color: Paleta.texto,
      fontFeatures: Tipografia.cifrasTabulares,
    ),
    precioGrande: TextStyle(
      fontFamily: Tipografia.titulares,
      fontSize: 32,
      height: 1.05,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.8,
      color: Paleta.texto,
      fontFeatures: Tipografia.cifrasTabulares,
    ),
    codigo: TextStyle(
      fontFamily: Tipografia.mono,
      fontSize: 13,
      fontWeight: FontWeight.w500,
      color: Paleta.azulLuminoso,
    ),
    exito: Paleta.exito,
    aviso: Paleta.aviso,
    error: Paleta.error,
    promocion: Paleta.promocion,
    cursor: Paleta.cian,
    halo: [
      BoxShadow(
        color: Color(0x592563EB),
        blurRadius: 28,
        spreadRadius: -6,
        offset: Offset(0, 10),
      ),
    ],
    degradeAccion: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Paleta.azulElectrico, Paleta.azulPrincipal],
    ),
  );

  static MarcaTema of(BuildContext context) =>
      Theme.of(context).extension<MarcaTema>() ?? porDefecto;

  /// `// recién llegados`: el comentario del isotipo, llevado al texto.
  final TextStyle rotulo;

  /// Precio en tarjeta o fila; cifras tabulares, con el signo ₲ incluido.
  final TextStyle precio;

  /// Precio protagonista, en la ficha y en la vidriera.
  final TextStyle precioGrande;

  /// Números de pedido, de factura y códigos.
  final TextStyle codigo;

  final Color exito;
  final Color aviso;
  final Color error;
  final Color promocion;

  /// El color del cursor del isotipo.
  final Color cursor;

  /// Luz azul debajo de la acción principal y detrás de los productos.
  final List<BoxShadow> halo;
  final Gradient degradeAccion;

  @override
  MarcaTema copyWith({
    TextStyle? rotulo,
    TextStyle? precio,
    TextStyle? precioGrande,
    TextStyle? codigo,
    Color? exito,
    Color? aviso,
    Color? error,
    Color? promocion,
    Color? cursor,
    List<BoxShadow>? halo,
    Gradient? degradeAccion,
  }) => MarcaTema(
    rotulo: rotulo ?? this.rotulo,
    precio: precio ?? this.precio,
    precioGrande: precioGrande ?? this.precioGrande,
    codigo: codigo ?? this.codigo,
    exito: exito ?? this.exito,
    aviso: aviso ?? this.aviso,
    error: error ?? this.error,
    promocion: promocion ?? this.promocion,
    cursor: cursor ?? this.cursor,
    halo: halo ?? this.halo,
    degradeAccion: degradeAccion ?? this.degradeAccion,
  );

  /// La app tiene un solo tema, así que no hay entre qué interpolar: se
  /// cambia de golpe a la mitad, como pide el contrato de [ThemeExtension].
  @override
  MarcaTema lerp(MarcaTema? other, double t) =>
      other == null || t < 0.5 ? this : other;
}
