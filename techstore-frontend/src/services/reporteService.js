import api from './api'

async function abrirPdf(url, params) {
  const { data } = await api.get(url, { params, responseType: 'blob' })
  const blobUrl = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
  window.open(blobUrl, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
}

export const reporteService = {
  async ventas({ desde = null, hasta = null, top = 5 } = {}) {
    const params = { top }
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    const { data } = await api.get('/reportes/ventas', { params })
    return data
  },

  async stockBajo(umbral = 5) {
    const { data } = await api.get('/reportes/stock-bajo', { params: { umbral } })
    return data
  },

  async ventasPdf({ desde = null, hasta = null } = {}) {
    const params = {}
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    await abrirPdf('/reportes/ventas/pdf', params)
  },

  async stockBajoPdf(umbral = 5) {
    await abrirPdf('/reportes/stock-bajo/pdf', { umbral })
  },
}
