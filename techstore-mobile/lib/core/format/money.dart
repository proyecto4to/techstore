import 'package:intl/intl.dart';

/// Formato de importes para toda la app.
///
/// Existe porque el mismo formateador estaba copiado en cada pantalla, y una
/// copia que se desactualiza hace que el mismo precio se vea distinto según
/// desde dónde se llegue.
String formatMoney(double value, String currency) {
  final code = currency.trim().isEmpty ? 'PYG' : currency.trim().toUpperCase();
  return NumberFormat.currency(
    locale: 'es_PY',
    symbol: code == 'PYG' ? '₲' : code,
    // El guaraní no usa centavos.
    decimalDigits: code == 'PYG' ? 0 : 2,
  ).format(value);
}

/// Suma importes que pueden venir en monedas distintas.
///
/// Sumar montos de monedas diferentes da un número sin significado, así que
/// acá se agrupan y el que muestra decide cómo presentarlos.
Map<String, double> totalsByCurrency(
  Iterable<({double amount, String currency})> amounts,
) {
  final totals = <String, double>{};
  for (final entry in amounts) {
    final code = entry.currency.trim().isEmpty
        ? 'PYG'
        : entry.currency.trim().toUpperCase();
    totals[code] = (totals[code] ?? 0) + entry.amount;
  }
  return totals;
}

/// Los totales ya formateados, unidos para mostrar en una sola línea.
String formatTotals(Map<String, double> totals) {
  if (totals.isEmpty) return formatMoney(0, 'PYG');
  return totals.entries
      .map((entry) => formatMoney(entry.value, entry.key))
      .join(' + ');
}
