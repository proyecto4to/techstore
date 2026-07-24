import api from './api'

export const correoService = {
  async listar({ page = 0, size = 15 } = {}) {
    const { data } = await api.get('/correos', { params: { page, size } })
    return data
  },

  async getConfig() {
    const { data } = await api.get('/correos/config')
    return data
  },

  async guardarConfig(config) {
    const { data } = await api.put('/correos/config', config)
    return data
  },

  async enviarPrueba(destinatario) {
    const { data } = await api.post('/correos/prueba', { destinatario })
    return data
  },
}
