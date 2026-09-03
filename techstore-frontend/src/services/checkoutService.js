import api from './api'
import { MAXIMO_POR_PEDIDO } from '../store/cartStore'

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

  /**
   * Revalida el carrito contra el catálogo vigente y traduce la respuesta a
   * cuántas unidades acepta hoy el servidor de cada producto.
   *
   * Los códigos de estado del backend se consumen acá y no salen de esta
   * función: quien la llama recibe números, nunca un identificador interno que
   * después tendría que interpretar el cliente.
   */
  async disponibilidad(items) {
    if (items.length === 0) return { valido: true, topePorProducto: {} }

    const { data } = await api.post('/carrito/validar', {
      items: items.map(({ producto, cantidad }) => ({ productoId: producto.id, cantidad })),
    })

    const topePorProducto = {}
    for (const linea of data.items ?? []) {
      if (linea.producto) {
        topePorProducto[linea.productoId] = linea.producto.maxPorPedido ?? 0
      } else if (linea.estado === 'CANTIDAD_EXCESIVA') {
        // El producto existe y hay stock: lo que sobra es la cantidad pedida.
        topePorProducto[linea.productoId] = MAXIMO_POR_PEDIDO
      } else {
        // Sin producto en la respuesta ya no se puede comprar.
        topePorProducto[linea.productoId] = 0
      }
    }
    return { valido: data.valido === true, topePorProducto }
  },
}
