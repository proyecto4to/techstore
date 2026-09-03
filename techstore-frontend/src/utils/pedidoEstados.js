export const ESTADOS_PEDIDO = {
  P: { label: 'Pendiente', clase: 'bg-yellow-400/20 text-yellow-400' },
  C: { label: 'Confirmado', clase: 'bg-emerald-400/20 text-emerald-400' },
  G: { label: 'Pagado', clase: 'bg-accent-400/20 text-accent-400' },
  N: { label: 'Entregado', clase: 'bg-primary-400/20 text-primary-300' },
  R: { label: 'Devuelto', clase: 'bg-orange-400/20 text-orange-400' },
  X: { label: 'Anulado', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

/**
 * Vuelve legible cualquier código del backend (`EN_RUTA` → `En ruta`).
 *
 * Es la red de seguridad para un estado todavía sin traducir: sin esto el
 * cliente vería el identificador interno tal cual. Si aparece en pantalla,
 * falta agregar ese estado al diccionario de arriba.
 */
export function humanizarCodigo(codigo) {
  const texto = String(codigo ?? '').trim()
  if (!texto) return 'Sin estado'
  const palabras = texto.replaceAll('_', ' ').toLowerCase()
  return palabras.charAt(0).toUpperCase() + palabras.slice(1)
}

export function estadoPedido(codigo) {
  return (
    ESTADOS_PEDIDO[codigo] ?? {
      label: humanizarCodigo(codigo),
      clase: 'bg-dark-lighter text-neutral-secondary',
    }
  )
}

/** Estados de la entrega a domicilio (`EstadoReparto` en el backend). */
export const ESTADOS_ENTREGA = {
  ASIGNADO: { label: 'Asignada', clase: 'bg-neutral-muted/20 text-neutral-secondary' },
  ACEPTADO: { label: 'Aceptada', clase: 'bg-primary-400/20 text-primary-300' },
  EN_RUTA: { label: 'En camino', clase: 'bg-primary-400/20 text-primary-300' },
  EN_DESTINO: { label: 'En el domicilio', clase: 'bg-accent-400/20 text-accent-400' },
  ENTREGADO: { label: 'Entregada', clase: 'bg-emerald-400/20 text-emerald-400' },
  NO_ENTREGADO: { label: 'No se pudo entregar', clase: 'bg-red-400/20 text-red-400' },
  CANCELADO: { label: 'Cancelada', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

export function estadoEntrega(codigo) {
  return (
    ESTADOS_ENTREGA[codigo] ?? {
      label: humanizarCodigo(codigo),
      clase: 'bg-dark-lighter text-neutral-secondary',
    }
  )
}

/** Estados del envío (`EstadoEnvio` en el backend). */
export const ESTADOS_ENVIO = {
  PENDIENTE: { label: 'Pendiente de preparación', clase: 'bg-yellow-400/20 text-yellow-400' },
  PREPARANDO: { label: 'Preparando', clase: 'bg-primary-400/20 text-primary-300' },
  LISTO_PARA_ENVIO: { label: 'Listo para despachar', clase: 'bg-primary-400/20 text-primary-300' },
  DESPACHADO: { label: 'Despachado', clase: 'bg-accent-400/20 text-accent-400' },
  EN_CAMINO: { label: 'En camino', clase: 'bg-accent-400/20 text-accent-400' },
  INTENTO_ENTREGA: { label: 'Intentamos entregarlo', clase: 'bg-orange-400/20 text-orange-400' },
  ENTREGADO: { label: 'Entregado', clase: 'bg-emerald-400/20 text-emerald-400' },
  CANCELADO: { label: 'Cancelado', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

export function estadoEnvio(codigo) {
  return (
    ESTADOS_ENVIO[codigo] ?? {
      label: humanizarCodigo(codigo),
      clase: 'bg-dark-lighter text-neutral-secondary',
    }
  )
}

/** Estados de un vehículo de reparto (`EstadoVehiculo` en el backend). */
export const ESTADOS_VEHICULO = {
  DISPONIBLE: { label: 'Disponible', clase: 'bg-emerald-400/20 text-emerald-400' },
  EN_USO: { label: 'En uso', clase: 'bg-primary-400/20 text-primary-300' },
  MANTENIMIENTO: { label: 'En mantenimiento', clase: 'bg-yellow-400/20 text-yellow-400' },
  FUERA_DE_SERVICIO: { label: 'Fuera de servicio', clase: 'bg-red-400/20 text-red-400' },
  INACTIVO: { label: 'Inactivo', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

export function estadoVehiculo(codigo) {
  return (
    ESTADOS_VEHICULO[codigo] ?? {
      label: humanizarCodigo(codigo),
      clase: 'bg-dark-lighter text-neutral-secondary',
    }
  )
}

/** Qué le pasó al vehículo (`TipoEventoVehiculo` en el backend). */
const EVENTOS_VEHICULO = {
  RECARGA_COMBUSTIBLE: 'Carga de combustible',
  REPARACION: 'Reparación',
  AVERIA: 'Avería',
  ESTANCAMIENTO: 'Quedó varado',
  ACCIDENTE: 'Accidente',
  MANTENIMIENTO: 'Mantenimiento',
  OBSERVACION: 'Observación',
  OTRO: 'Otro',
}

export function eventoVehiculo(codigo) {
  return EVENTOS_VEHICULO[codigo] ?? humanizarCodigo(codigo)
}
