import api from './api'

export const checkoutService = {
  async direcciones() {
    const { data } = await api.get('/direcciones')
    return data
  },

  async crearDireccion(payload) {
    const { data } = await api.post('/direcciones', payload)
    return data
  },

  async cotizar(direccionEntregaId, items) {
    const { data } = await api.post('/envios/cotizar', {
      direccionEntregaId,
      items: items.map(({ producto, cantidad }) => ({ productoId: producto.id, cantidad })),
    })
    return data
  },
}
