import api from './api'

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
}
