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

  async crear(compra) {
    const { data } = await api.post('/compras', compra)
    return data
  },

  async recibir(id) {
    const { data } = await api.put(`/compras/${id}/recibir`)
    return data
  },

  async anular(id) {
    const { data } = await api.put(`/compras/${id}/anular`)
    return data
  },
}
