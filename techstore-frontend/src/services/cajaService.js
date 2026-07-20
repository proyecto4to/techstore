import api from './api'

export const cajaService = {
  async registrar(movimiento) {
    const { data } = await api.post('/caja/movimientos', movimiento)
    return data
  },

  async listar({ desde = null, hasta = null, page = 0, size = 10 } = {}) {
    const params = { page, size }
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    const { data } = await api.get('/caja/movimientos', { params })
    return data
  },

  async resumen({ desde = null, hasta = null } = {}) {
    const params = {}
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    const { data } = await api.get('/caja/resumen', { params })
    return data
  },

  async resumenPdf({ desde = null, hasta = null } = {}) {
    const params = {}
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta
    const { data } = await api.get('/caja/resumen/pdf', { params, responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },

  async previewCierre() {
    const { data } = await api.get('/caja/cierres/preview')
    return data
  },

  async cerrar(efectivoContado, notas) {
    const { data } = await api.post('/caja/cierres', { efectivoContado, notas: notas || null })
    return data
  },

  async listarCierres({ page = 0, size = 5 } = {}) {
    const { data } = await api.get('/caja/cierres', { params: { page, size } })
    return data
  },
}
