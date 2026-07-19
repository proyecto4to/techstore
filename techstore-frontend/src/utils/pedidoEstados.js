export const ESTADOS_PEDIDO = {
  P: { label: 'Pendiente', clase: 'bg-yellow-400/20 text-yellow-400' },
  C: { label: 'Confirmado', clase: 'bg-emerald-400/20 text-emerald-400' },
  G: { label: 'Pagado', clase: 'bg-accent-400/20 text-accent-400' },
  N: { label: 'Entregado', clase: 'bg-primary-400/20 text-primary-300' },
  R: { label: 'Devuelto', clase: 'bg-orange-400/20 text-orange-400' },
  X: { label: 'Anulado', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

export function estadoPedido(codigo) {
  return ESTADOS_PEDIDO[codigo] ?? { label: codigo, clase: 'bg-dark-lighter text-neutral-secondary' }
}
