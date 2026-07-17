export const ESTADOS_PEDIDO = {
  P: { label: 'Pendiente', clase: 'bg-yellow-100 text-yellow-800' },
  C: { label: 'Confirmado', clase: 'bg-green-100 text-green-800' },
  G: { label: 'Pagado', clase: 'bg-blue-100 text-blue-800' },
  N: { label: 'Entregado', clase: 'bg-emerald-100 text-emerald-800' },
  R: { label: 'Rechazado', clase: 'bg-red-100 text-red-800' },
  X: { label: 'Anulado', clase: 'bg-gray-200 text-gray-600' },
}

export function estadoPedido(codigo) {
  return ESTADOS_PEDIDO[codigo] ?? { label: codigo, clase: 'bg-gray-100 text-gray-700' }
}
