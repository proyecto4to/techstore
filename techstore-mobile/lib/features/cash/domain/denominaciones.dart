/// Las denominaciones de guaraníes en circulación, de mayor a menor.
///
/// El cierre de caja se hace contando billetes y monedas, no escribiendo un
/// total: quien cuenta apila por denominación, y pedirle que además haga la
/// multiplicación y la suma es pedirle que se equivoque.
library;

/// Un corte de guaraní y cuántos hay.
final class ConteoDenominacion {
  ConteoDenominacion(this.valor, [this.cantidad = 0]);

  /// El valor del billete o la moneda.
  final int valor;

  /// Cuántas unidades declaró el cajero.
  int cantidad;

  /// Lo que suma esta fila. Se calcula, nunca se escribe a mano.
  int get subtotal => valor * cantidad;
}

/// Los cortes vigentes, del más grande al más chico.
const denominacionesGuaranies = <int>[
  100000,
  50000,
  20000,
  10000,
  5000,
  2000,
  1000,
  500,
  100,
  50,
];

/// Una planilla de conteo en blanco.
List<ConteoDenominacion> planillaEnBlanco() => [
  for (final valor in denominacionesGuaranies) ConteoDenominacion(valor),
];

/// El total declarado, sumando los subtotales.
int totalContado(List<ConteoDenominacion> planilla) =>
    planilla.fold(0, (suma, fila) => suma + fila.subtotal);

/// Cuántas unidades se contaron en total, para saber si la planilla está vacía.
int unidadesContadas(List<ConteoDenominacion> planilla) =>
    planilla.fold(0, (suma, fila) => suma + fila.cantidad);
