/**
 * Traduce el enlace `techstore://` que viaja en cada notificación a una ruta
 * de esta aplicación.
 *
 * No es un espejo del mapa de la app móvil: la web y el celular no tienen las
 * mismas pantallas. El reparto se opera solo desde el celular y el panel está
 * detrás de un rol, así que la resolución depende de quién mira. Devolver una
 * ruta que `ProtectedRoute` va a rebotar hacia el inicio sería peor que no
 * ofrecer el enlace: el usuario haría clic y terminaría en otro lado sin
 * explicación.
 *
 * Devuelve `null` cuando el aviso no tiene dónde abrirse acá.
 */
const OPERA_EL_PANEL = new Set(['Admin', 'Cajero'])

export function rutaDeEnlace(enlace, rol) {
  if (typeof enlace !== 'string' || !enlace.startsWith('techstore://')) return null

  let uri
  try {
    uri = new URL(enlace)
  } catch {
    return null
  }

  const panel = OPERA_EL_PANEL.has(rol)

  switch (uri.hostname) {
    case 'app':
      if (uri.pathname !== '/password/reset') return null
      return `/password/reset?token=${encodeURIComponent(uri.searchParams.get('token') ?? '')}`
    // No hay pantalla de detalle de pedido en la web: la lista es lo más cerca
    // que se puede llevar al usuario.
    case 'pedido':
      return '/pedidos'
    case 'chat':
      return panel ? '/admin/mensajes' : '/'
    case 'transferencia':
      return panel ? '/admin/solicitudes' : null
    case 'vehiculo':
      return rol === 'Admin' ? '/admin/vehiculos' : null
    case 'notificaciones':
      return '/notificaciones'
    // Las entregas se trabajan desde la app del repartidor; en la web no hay
    // dónde abrirlas.
    case 'reparto':
      return null
    default:
      return null
  }
}
