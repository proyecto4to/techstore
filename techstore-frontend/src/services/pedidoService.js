import api from './api'

export const pedidoService = {
  async checkout(items, notas) {
    const { data } = await api.post('/pedidos', {
      items: items.map(({ producto, cantidad }) => ({ productoId: producto.id, cantidad })),
      notas: notas || null,
    })
    return data
  },

  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/pedidos', { params: { page, size } })
    return data
  },

  async obtener(id) {
    const { data } = await api.get(`/pedidos/${id}`)
    return data
  },

  async cancelar(id) {
    const { data } = await api.put(`/pedidos/${id}/cancelar`)
    return data
  },

  async confirmar(id) {
    const { data } = await api.put(`/pedidos/${id}/confirmar`)
    return data
  },

  async registrarPago(id, lineas, receptor = null) {
    const { data } = await api.post(`/pedidos/${id}/pago`, { lineas, receptor })
    return data
  },

  async abrirFacturaPdf(id) {
    const { data } = await api.get(`/pedidos/${id}/factura/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },
}
