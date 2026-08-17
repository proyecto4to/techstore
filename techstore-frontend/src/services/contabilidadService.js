import api from './api'

async function abrirPdf(url, params) {
  const { data } = await api.get(url, { params, responseType: 'blob' })
  const blobUrl = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
  window.open(blobUrl, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
}

/**
 * El nombre del archivo que le manda el backend importa: Marangatu lo usa para
 * saber de qué contribuyente y de qué período es lo que se sube. Por eso se lee
 * del Content-Disposition en vez de inventarlo acá.
 */
function nombreDelServidor(headers, porDefecto) {
  const disposicion = headers?.['content-disposition'] ?? ''
  const encontrado = /filename="?([^";]+)"?/i.exec(disposicion)
  return encontrado ? encontrado[1] : porDefecto
}

export const contabilidadService = {
  async libroVentas({ desde, hasta }) {
    const { data } = await api.get('/contabilidad/libro-ventas', { params: { desde, hasta } })
    return data
  },

  async libroCompras({ desde, hasta }) {
    const { data } = await api.get('/contabilidad/libro-compras', { params: { desde, hasta } })
    return data
  },

  async resumenIva({ desde, hasta }) {
    const { data } = await api.get('/contabilidad/resumen-iva', { params: { desde, hasta } })
    return data
  },

  async libroVentasPdf({ desde, hasta }) {
    await abrirPdf('/contabilidad/libro-ventas/pdf', { desde, hasta })
  },

  async libroComprasPdf({ desde, hasta }) {
    await abrirPdf('/contabilidad/libro-compras/pdf', { desde, hasta })
  },

  async resumenIvaPdf({ desde, hasta }) {
    await abrirPdf('/contabilidad/resumen-iva/pdf', { desde, hasta })
  },

  async archivoDnit({ anio, mes, formato, identificador, renta }) {
    const params = { anio, formato, identificador, renta }
    if (mes) params.mes = mes
    const respuesta = await api.get('/contabilidad/registro-comprobantes', {
      params,
      responseType: 'blob',
    })
    const nombre = nombreDelServidor(respuesta.headers, 'registro-comprobantes.zip')
    const url = URL.createObjectURL(new Blob([respuesta.data], { type: 'application/zip' }))
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = nombre
    enlace.click()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
    return nombre
  },

  async comprobanteDeCompra(compraId) {
    const { data } = await api.get(`/compras/${compraId}/comprobante`)
    return data
  },

  async guardarComprobante(compraId, comprobante) {
    const { data } = await api.put(`/compras/${compraId}/comprobante`, comprobante)
    return data
  },
}
