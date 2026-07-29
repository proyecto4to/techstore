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

  /** RUC y razón social propios. Vacíos vuelven a facturar como consumidor final. */
  async guardarMisDatosFiscales(ruc, razonSocial) {
    const { data } = await api.put('/auth/datos-fiscales', {
      ruc: ruc || null,
      razonSocial: razonSocial || null,
    })
    return data
  },
}
