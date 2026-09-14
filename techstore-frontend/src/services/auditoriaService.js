import api from './api'

export const auditoriaService = {
  async listar({
    entidad = '',
    desde = '',
    hasta = '',
    // La auditoría de seguridad mira sobre todo lo que se rechazó y desde
    // dónde. Los filtros llegan vacíos por omisión, así que lo que ya
    // consultaba esta función sigue consultando exactamente lo mismo.
    resultado = '',
    plataforma = '',
    usuarioId = null,
    page = 0,
    size = 20,
  } = {}) {
    const params = { page, size }
    if (entidad) params.entidad = entidad
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    if (resultado) params.resultado = resultado
    if (plataforma) params.plataforma = plataforma
    if (usuarioId) params.usuarioId = usuarioId
    const { data } = await api.get('/auditoria', { params })
    return data
  },

  /** Para poblar el filtro sin adivinar qué se auditó. */
  async entidades() {
    const { data } = await api.get('/auditoria/entidades')
    return data
  },
}
