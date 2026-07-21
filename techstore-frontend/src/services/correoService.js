import api from './api'

export const correoService = {
  async listar({ page = 0, size = 15 } = {}) {
    const { data } = await api.get('/correos', { params: { page, size } })
    return data
  },
}
