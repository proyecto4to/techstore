import api from './api'

export const notificacionService = {
  async listar({ page = 0, size = 50 } = {}) {
    const { data } = await api.get('/notificaciones', { params: { page, size } })
    return data
  },

  async noLeidas() {
    const { data } = await api.get('/notificaciones/no-leidas')
    return data
  },

  async marcarLeida(id) {
    const { data } = await api.put(`/notificaciones/${id}/leida`)
    return data
  },

  async marcarTodasLeidas() {
    const { data } = await api.put('/notificaciones/leidas')
    return data
  },
}
