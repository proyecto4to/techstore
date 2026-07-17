import api from './api'

export const facturaService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/facturas', { params: { page, size } })
    return data
  },

  async obtener(id) {
    const { data } = await api.get(`/facturas/${id}`)
    return data
  },
}
