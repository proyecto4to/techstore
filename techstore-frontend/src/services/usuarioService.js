import api from './api'

export const usuarioService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/usuarios', { params: { page, size } })
    return data
  },

  async crear(usuario) {
    const { data } = await api.post('/usuarios', usuario)
    return data
  },

  async cambiarActivo(id, activo) {
    const { data } = await api.put(`/usuarios/${id}/activo`, { activo })
    return data
  },

  async resetearPassword(id, password) {
    const { data } = await api.put(`/usuarios/${id}/password`, { password })
    return data
  },

  async cambiarMiPassword(passwordActual, passwordNueva) {
    await api.put('/auth/password', { passwordActual, passwordNueva })
  },
}
