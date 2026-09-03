import api from './api'

export const repartoService = {
  async asignar(pedidoId, { repartidorId, ordenEntrega, observaciones }) {
    const { data } = await api.post(`/repartos/pedidos/${pedidoId}/asignar`, {
      repartidorId,
      ordenEntrega,
      observaciones: observaciones?.trim() || null,
    })
    return data
  },

  async asignaciones(pedidoId) {
    const { data } = await api.get(`/repartos/pedidos/${pedidoId}/asignaciones`)
    return data
  },

  async solicitudes({ page = 0, size = 30 } = {}) {
    const { data } = await api.get('/repartos/solicitudes', { params: { page, size } })
    return data
  },

  async aceptar(id) {
    const { data } = await api.put(`/repartos/solicitudes/${id}/aceptar`)
    return data
  },

  async rechazar(id, motivo) {
    const { data } = await api.put(`/repartos/solicitudes/${id}/rechazar`, { motivo })
    return data
  },
}
