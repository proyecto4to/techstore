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

const formateadorFecha = new Intl.DateTimeFormat('es-PY', {
  day: 'numeric',
  month: 'short',
})

/**
 * Una fecha como la lee una persona.
 *
 * El servidor manda ISO (`2026-09-05`); mostrarlo tal cual es correcto pero no
 * es castellano. Devuelve `null` si no hay fecha válida, para que quien llama
 * decida qué poner en su lugar.
 */
export function formatFechaCorta(valor) {
  if (!valor) return null
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? null : formateadorFecha.format(fecha)
}

/** Rango de llegada estimada, en una frase. */
export function formatRangoEntrega(desde, hasta) {
  const inicio = formatFechaCorta(desde)
  if (!inicio) return 'Fecha a confirmar'
  const fin = formatFechaCorta(hasta)
  return !fin || fin === inicio ? `Llega el ${inicio}` : `Llega entre el ${inicio} y el ${fin}`
}
