import api from './api'

export const facturaService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/facturas', { params: { page, size } })
    return data
  },

  async obtener(id) {
    const { data } = await api.get(`/facturas/${id}`)
    return data
  },

  async abrirPdf(id) {
    const { data } = await api.get(`/facturas/${id}/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },

  async abrirKude(id) {
    const { data } = await api.get(`/facturas/${id}/kude`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },

  async descargarXml(id, numero) {
    const { data } = await api.get(`/facturas/${id}/documento-electronico`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/xml' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `DE-factura-${numero}.xml`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },
}

export const notaCreditoService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/notas-credito', { params: { page, size } })
    return data
  },

  async emitir(pedidoId, motivo, items) {
    const { data } = await api.post(`/pedidos/${pedidoId}/nota-credito`, { motivo, items })
    return data
  },

  async abrirPdf(id) {
    const { data } = await api.get(`/notas-credito/${id}/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },
}
