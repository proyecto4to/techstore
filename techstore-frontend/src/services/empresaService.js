import api from './api'

export const empresaService = {
  async actual() {
    const { data } = await api.get('/empresa')
    return data
  },

  async guardar(empresa) {
    const { data } = await api.put('/empresa', empresa)
    return data
  },

  /** Qué falta cargar para poder facturar: { completo, faltantes, campos }. */
  async pendientes() {
    const { data } = await api.get('/empresa/pendientes')
    return data
  },

  /** Las cajas o sucursales que emiten. Cada una lleva su propia numeración. */
  async puntosExpedicion() {
    const { data } = await api.get('/empresa/puntos-expedicion')
    return data
  },

  async crearPuntoExpedicion(punto) {
    const { data } = await api.post('/empresa/puntos-expedicion', punto)
    return data
  },

  async cambiarActivoPunto(id, activo) {
    const { data } = await api.put(`/empresa/puntos-expedicion/${id}/activo`, { activo })
    return data
  },
}
