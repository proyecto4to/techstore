import 'package:flutter/material.dart';

import '../format/money_input.dart';

/// Campo para cargar un monto, con el formato ya puesto.
///
/// Existe para que ninguna pantalla vuelva a armar a mano un `TextField`
/// numérico: el agrupado de miles, el teclado, el símbolo y la validación
/// viven acá. Lo que se lee con [parsearMonto] es siempre el número, nunca el
/// texto con puntos.
class MoneyField extends StatelessWidget {
  const MoneyField({
    required this.controller,
    required this.label,
    super.key,
    this.currency = 'PYG',
    this.decimales = 0,
    this.enabled = true,
    this.required = true,
    this.permitirCero = false,
    this.helperText,
    this.autofocus = false,
    this.textInputAction,
    this.onChanged,
    this.onSubmitted,
  });

  final TextEditingController controller;
  final String label;

  /// Solo para el símbolo que se muestra; el guaraní no lleva centavos.
  final String currency;
  final int decimales;
  final bool enabled;

  /// Si el campo tiene que estar cargado para poder continuar.
  final bool required;

  /// Si un monto de cero es una carga válida. Para un pago no lo es.
  final bool permitirCero;

  final String? helperText;
  final bool autofocus;
  final TextInputAction? textInputAction;
  final ValueChanged<double?>? onChanged;
  final ValueChanged<double?>? onSubmitted;

  /// El símbolo que corresponde a la moneda del campo.
  String get _simbolo =>
      currency.trim().toUpperCase() == 'PYG' ? '₲' : currency.trim();

  /// El motivo por el que el monto no sirve, o `null` si está bien.
  String? validar(String? texto) {
    final monto = parsearMonto(texto, decimales: decimales);
    if (monto == null) {
      return required ? 'Ingresá un monto' : null;
    }
    if (monto < 0) return 'El monto no puede ser negativo';
    if (!permitirCero && monto == 0) return 'El monto tiene que ser mayor a 0';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      enabled: enabled,
      autofocus: autofocus,
      textInputAction: textInputAction,
      keyboardType: TextInputType.numberWithOptions(decimal: decimales > 0),
      inputFormatters: [MoneyInputFormatter(decimales: decimales)],
      decoration: InputDecoration(
        labelText: label,
        helperText: helperText,
        prefixText: '$_simbolo ',
      ),
      validator: validar,
      onChanged: onChanged == null
          ? null
          : (texto) => onChanged!(parsearMonto(texto, decimales: decimales)),
      onFieldSubmitted: onSubmitted == null
          ? null
          : (texto) => onSubmitted!(parsearMonto(texto, decimales: decimales)),
    );
  }
}
