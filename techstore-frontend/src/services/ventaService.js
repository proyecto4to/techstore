import api from './api'

export const ventaService = {
  /** Venta en mostrador: descuenta stock, cobra y emite la factura en un solo paso. */
  async vender({ items, lineas, receptor = null, notas = null }) {
    const { data } = await api.post('/ventas', { items, lineas, receptor, notas })
    return data
  },
}
