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
}
