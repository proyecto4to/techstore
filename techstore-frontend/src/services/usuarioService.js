import api from './api'

export const usuarioService = {
  async listar({ page = 0, size = 10, q = '', rol = '', activo = null } = {}) {
    const params = { page, size }
    if (q) params.q = q
    if (rol) params.rol = rol
    if (activo !== null && activo !== '') params.activo = activo
    const { data } = await api.get('/usuarios', { params })
    return data
  },

  /** Le cambia el rol. Cambia de golpe todo lo que esa persona puede hacer. */
  async asignarRol(id, rol) {
    const { data } = await api.put(`/usuarios/${id}/rol`, { rol })
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

  /** Desde qué punto emite. `null` = el punto por defecto de la empresa. */
  async asignarPuntoExpedicion(id, puntoExpedicionId) {
    const { data } = await api.put(`/usuarios/${id}/punto-expedicion`, {
      puntoExpedicionId: puntoExpedicionId || null,
    })
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
