import api from './api'

export const productoService = {
  async listar({ search = '', marcaId = null, page = 0, size = 12, incluirInactivos = false } = {}) {
    const params = { page, size }
    if (search) params.search = search
    if (marcaId) params.marcaId = marcaId
    if (incluirInactivos) params.incluirInactivos = true
    const { data } = await api.get('/productos', { params })
    return data
  },

  async obtener(id) {
    const { data } = await api.get(`/productos/${id}`)
    return data
  },

  async crear(producto) {
    const { data } = await api.post('/productos', producto)
    return data
  },

  async actualizar(id, producto) {
    const { data } = await api.put(`/productos/${id}`, producto)
    return data
  },

  async eliminar(id) {
    await api.delete(`/productos/${id}`)
  },

  async subirImagen(id, archivo) {
    const formData = new FormData()
    formData.append('archivo', archivo)
    const { data } = await api.post(`/productos/${id}/imagen`, formData)
    return data
  },

  async marcas() {
    const { data } = await api.get('/marcas')
    return data
  },
}
