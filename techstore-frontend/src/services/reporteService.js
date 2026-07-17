import api from './api'

export const reporteService = {
  async ventas(top = 5) {
    const { data } = await api.get('/reportes/ventas', { params: { top } })
    return data
  },
}
