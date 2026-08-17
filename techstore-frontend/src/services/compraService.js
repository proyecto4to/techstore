import api from './api'

export const proveedorService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/proveedores', { params: { page, size } })
    return data
  },

  async activos() {
    const { data } = await api.get('/proveedores/activos')
    return data
  },

  async crear(proveedor) {
    const { data } = await api.post('/proveedores', proveedor)
    return data
  },

  async cambiarActivo(id, activo) {
    const { data } = await api.put(`/proveedores/${id}/activo`, { activo })
    return data
  },
}

export const compraService = {
  async listar({ page = 0, size = 10 } = {}) {
    const { data } = await api.get('/compras', { params: { page, size } })
    return data
  },

  /**
   * Qué proveedor conviene para estos productos. No crea nada: devuelve la
   * comparación para que la decisión la tome quien compra.
   */
  async comparar(items) {
    const { data } = await api.post('/compras/comparar', { items })
    return data
  },

  async crear(compra) {
    const { data } = await api.post('/compras', compra)
    return data
  },

  async recibir(id) {
    const { data } = await api.put(`/compras/${id}/recibir`)
    return data
  },

  /** Puede repartirse entre efectivo y transferencia; la suma debe dar el total. */
  async pagar(id, lineas) {
    const { data } = await api.put(`/compras/${id}/pagar`, { lineas })
    return data
  },

  async anular(id) {
    const { data } = await api.put(`/compras/${id}/anular`)
    return data
  },

  /**
   * Le manda el pedido al proveedor. La respuesta trae `enviado`, que distingue
   * lo que salió de verdad de lo que quedó nada más registrado en la bandeja de
   * salida por no haber SMTP configurado.
   */
  async enviarAlProveedor(id, canal) {
    const { data } = await api.post(`/compras/${id}/enviar`, { canal })
    return data
  },
}
