const formatoPYG = new Intl.NumberFormat('es-PY', {
  style: 'currency',
  currency: 'PYG',
  maximumFractionDigits: 0,
})

export function formatPrecio(valor) {
  if (valor === null || valor === undefined) return '—'
  return formatoPYG.format(valor)
}
