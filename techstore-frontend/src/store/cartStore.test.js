import { beforeEach, describe, expect, it } from 'vitest'
import { selectTotalItems, selectTotalMonto, useCartStore } from './cartStore'

const producto = {
  id: 7,
  nombre: 'Teclado mecánico',
  precio: 250_000,
  disponible: true,
}

describe('carrito', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [] })
  })

  it('acumula unidades sin superar el límite seguro por línea', () => {
    useCartStore.getState().agregar(producto, 60)
    useCartStore.getState().agregar(producto, 60)

    expect(useCartStore.getState().items).toEqual([{ producto, cantidad: 99 }])
  })

  it('normaliza cantidades y elimina artículos sin stock', () => {
    useCartStore.getState().agregar(producto)
    useCartStore.getState().setCantidad(producto.id, 0)
    expect(useCartStore.getState().items[0].cantidad).toBe(1)

    useCartStore.setState({
      items: [{ producto: { ...producto, disponible: false }, cantidad: 1 }],
    })
    useCartStore.getState().setCantidad(producto.id, 2)
    expect(useCartStore.getState().items).toEqual([])
  })

  it('calcula cantidad e importe totales', () => {
    const state = {
      items: [
        { producto, cantidad: 2 },
        { producto: { id: 8, precio: 100_000 }, cantidad: 1 },
      ],
    }

    expect(selectTotalItems(state)).toBe(3)
    expect(selectTotalMonto(state)).toBe(600_000)
  })
})
