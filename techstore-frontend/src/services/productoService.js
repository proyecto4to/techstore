import api from './api'

export const productoService = {
  async listar({ search = '', marcaId = null, disponible = null, page = 0, size = 12, sort = null } = {}) {
    const params = { page, size }
    if (search) params.search = search
    if (marcaId) params.marcaId = marcaId
    if (disponible !== null) params.disponible = disponible
    if (sort) params.sort = sort
    const { data } = await api.get('/productos', { params })
    return data
  },

  /** Listado con datos internos, incluidos costo y margen. Solo para Admin. */
  async listarAdmin({ search = '', marcaId = null, page = 0, size = 12, incluirInactivos = false } = {}) {
    const params = { page, size, incluirInactivos }
    if (search) params.search = search
    if (marcaId) params.marcaId = marcaId
    const { data } = await api.get('/productos/admin', { params })
    return data
  },

  /**
   * Listado para operar el mostrador y las compras: trae el inventario real,
   * sin costo ni margen.
   *
   * La venta directa y las compras usaban `listarAdmin`, que el backend reserva
   * a Admin, así que a un cajero le devolvían 403 y la lista de productos nunca
   * cargaba: no podía vender ni cargar una compra.
   */
  async listarParaOperar({
    search = '',
    marcaId = null,
    page = 0,
    size = 12,
    incluirInactivos = false,
  } = {}) {
    const params = { page, size, incluirInactivos }
    if (search) params.search = search
    if (marcaId) params.marcaId = marcaId
    const { data } = await api.get('/productos/operacion', { params })
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

  /** Los que llegaron a su punto de reposicion, del mas urgente al menos. */
  async reposicion() {
    const { data } = await api.get("/productos/reposicion")
    return data
  },

  async marcas() {
    const { data } = await api.get('/marcas')
    return data
  },
}
