import api from './api'

/**
 * El cobro por transferencia: a dónde transferir, el comprobante que se sube
 * antes de confirmar la compra y la verificación que hace la tienda.
 */
export const pagoService = {
  async datosTransferencia() {
    const { data } = await api.get('/pagos/transferencia')
    return data
  },

  /** El QR del banco como URL local; quien la pide la libera con revokeObjectURL. */
  async urlDelQr() {
    const { data } = await api.get('/pagos/transferencia/qr', { responseType: 'blob' })
    return URL.createObjectURL(data)
  },

  /** Sube el comprobante antes del checkout. Queda solo de quien lo subió. */
  async subirComprobante(archivo) {
    const form = new FormData()
    form.append('archivo', archivo)
    const { data } = await api.post('/pagos/comprobantes', form)
    return data
  },

  async quitarComprobante(id) {
    await api.delete(`/pagos/comprobantes/${id}`)
  },

  async adjuntarAPedido(pedidoId, archivo) {
    const form = new FormData()
    form.append('archivo', archivo)
    const { data } = await api.post(`/pedidos/${pedidoId}/comprobantes`, form)
    return data
  },

  // Lo que sigue es de quien cobra.

  async pendientes(todos = false) {
    const { data } = await api.get('/pagos/verificaciones', { params: { todos } })
    return data
  },

  async comprobantesDelPedido(pedidoId) {
    const { data } = await api.get(`/pedidos/${pedidoId}/comprobantes`)
    return data
  },

  async urlDelComprobante(pedidoId, comprobanteId) {
    const { data } = await api.get(`/pedidos/${pedidoId}/comprobantes/${comprobanteId}/archivo`, {
      responseType: 'blob',
    })
    return URL.createObjectURL(data)
  },

  async aprobar(pedidoId, referencia) {
    const { data } = await api.put(`/pedidos/${pedidoId}/pago/aprobar`, referencia ? { referencia } : {})
    return data
  },

  async rechazar(pedidoId, motivo) {
    const { data } = await api.put(`/pedidos/${pedidoId}/pago/rechazar`, { motivo })
    return data
  },
}
