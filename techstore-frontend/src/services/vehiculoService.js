import api from './api'

export const vehiculoService = {
  async listar() {
    const { data } = await api.get('/vehiculos')
    return data
  },

  async crear(payload) {
    const { data } = await api.post('/vehiculos', payload)
    return data
  },

  async actualizar(id, payload) {
    const { data } = await api.put(`/vehiculos/${id}`, payload)
    return data
  },

  async cambiarActivo(id, activo) {
    const { data } = await api.put(`/vehiculos/${id}/activo`, { activo })
    return data
  },

  async historial(id) {
    const { data } = await api.get(`/vehiculos/${id}/historial`)
    return data
  },

  async resolverEvento(id, estadoVehiculo, observacion) {
    const { data } = await api.put(`/vehiculos/eventos/${id}/resolver`, {
      estadoVehiculo,
      observacion: observacion?.trim() || null,
    })
    return data
  },
}
