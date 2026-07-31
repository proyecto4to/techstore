import api from './api'

export const auditoriaService = {
  async listar({ entidad = '', desde = '', hasta = '', page = 0, size = 20 } = {}) {
    const params = { page, size }
    if (entidad) params.entidad = entidad
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    const { data } = await api.get('/auditoria', { params })
    return data
  },

  /** Para poblar el filtro sin adivinar qué se auditó. */
  async entidades() {
    const { data } = await api.get('/auditoria/entidades')
    return data
  },
}
