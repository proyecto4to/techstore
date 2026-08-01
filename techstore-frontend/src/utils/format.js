const MONEDA_POR_DEFECTO = 'PYG'

// Un formateador por moneda: construirlos es caro y se usan en cada fila de cada tabla.
const formateadores = new Map()

function formateador(moneda) {
  const codigo = moneda || MONEDA_POR_DEFECTO
  if (!formateadores.has(codigo)) {
    formateadores.set(
      codigo,
      new Intl.NumberFormat('es-PY', { style: 'currency', currency: codigo }),
    )
  }
  return formateadores.get(codigo)
}

/**
 * Importe con su símbolo. Los decimales los decide la moneda: el guaraní no
 * tiene centavos y el dólar sí, así que no se fuerzan a mano.
 *
 * La moneda viaja en un campo aparte de cada respuesta (`moneda`), al lado del
 * importe. Si no se pasa, se asume la del sistema.
 */
export function formatPrecio(valor, moneda) {
  if (valor === null || valor === undefined) return '—'
  return formateador(moneda).format(valor)
}
