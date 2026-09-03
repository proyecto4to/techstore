import { beforeEach, describe, expect, it } from 'vitest'
import {
  MAXIMO_POR_PEDIDO,
  muestraUnidadesRestantes,
  selectTotalItems,
  selectTotalMonto,
  useCartStore,
} from './cartStore'

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

  it('respeta el tope publicado por la vidriera', () => {
    const escaso = { ...producto, maxPorPedido: 3 }

    expect(useCartStore.getState().agregar(escaso, 8).resultado).toBe('topado')
    expect(useCartStore.getState().items[0].cantidad).toBe(3)
    expect(useCartStore.getState().agregar(escaso).resultado).toBe('sin-margen')
  })

  it('descuenta lo que ya está en el carrito del margen disponible', () => {
    // Hay 10, ya tiene 7, intenta agregar 5: solo pueden entrar 3.
    const item = { ...producto, maxPorPedido: 10 }
    useCartStore.getState().agregar(item, 7)

    const resultado = useCartStore.getState().agregar(item, 5)

    expect(resultado.agregadas).toBe(3)
    expect(useCartStore.getState().items[0].cantidad).toBe(10)
    expect(resultado.mensaje).toContain('3 unidades')
  })

  it('no agrega un producto sin unidades y lo explica', () => {
    const resultado = useCartStore.getState().agregar({ ...producto, maxPorPedido: 0 })

    expect(resultado.resultado).toBe('sin-stock')
    expect(useCartStore.getState().items).toEqual([])
    expect(resultado.mensaje).toContain('sin unidades')
  })

  it('revalidar recorta lo que quedó largo y explica el ajuste', () => {
    useCartStore.getState().agregar({ ...producto, maxPorPedido: 10 }, 6)

    const reporte = useCartStore.getState().aplicarDisponibilidad({ 7: 3 })

    expect(useCartStore.getState().items[0].cantidad).toBe(3)
    expect(reporte.huboCambios).toBe(true)
    expect(reporte.mensaje).toContain('Teclado mecánico')
    expect(reporte.mensaje).toContain('Podés continuar')
  })

  it('revalidar saca del carrito lo que se agotó', () => {
    useCartStore.getState().agregar({ ...producto, maxPorPedido: 5 }, 2)

    const reporte = useCartStore.getState().aplicarDisponibilidad({ 7: 0 })

    expect(useCartStore.getState().items).toEqual([])
    expect(reporte.quitados).toEqual(['Teclado mecánico'])
  })

  it('el tope fijo no se anuncia como escasez', () => {
    expect(muestraUnidadesRestantes({ maxPorPedido: MAXIMO_POR_PEDIDO })).toBe(false)
    expect(muestraUnidadesRestantes({ maxPorPedido: 4 })).toBe(true)
  })
})
