import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/// Tope por línea que acepta el checkout. Espeja `Producto.MAXIMO_POR_PEDIDO`
/// del backend: ofrecer más sería proponer algo que el servidor rechaza.
export const MAXIMO_POR_PEDIDO = 99

export const productoDisponible = (producto) =>
  producto.disponible ?? (Number.isFinite(producto.stockDisponible) && producto.stockDisponible > 0)

/**
 * Cuántas unidades admite hoy una línea de pedido.
 *
 * `maxPorPedido` es lo que publica la vidriera para todo el mundo;
 * `stockDisponible` solo llega en las vistas internas (admin y caja), donde
 * conviene topar contra el número real. Sin ninguno de los dos se deja pasar
 * hasta el máximo y decide el checkout, que es como se comportaba antes.
 */
export const maximoComprable = (producto) => {
  if (Number.isFinite(producto.maxPorPedido)) return Math.max(0, producto.maxPorPedido)
  if (Number.isFinite(producto.stockDisponible)) return Math.max(0, producto.stockDisponible)
  return MAXIMO_POR_PEDIDO
}

/** Si el tope refleja escasez real y no el límite fijo por pedido. */
export const muestraUnidadesRestantes = (producto) => {
  const tope = maximoComprable(producto)
  return tope > 0 && tope < MAXIMO_POR_PEDIDO
}

const unidades = (n) => (n === 1 ? 'unidad' : 'unidades')

const mensajeDe = ({ resultado, agregadas, enCarrito }) => {
  switch (resultado) {
    case 'agregado':
      return agregadas === 1 ? 'Agregado al carrito' : `Agregaste ${agregadas} unidades`
    case 'topado':
      return (
        `Solo quedaban ${agregadas} ${unidades(agregadas)} disponibles. ` +
        `Agregamos ${agregadas === 1 ? 'esa' : 'esas'} y tu carrito quedó en ${enCarrito}.`
      )
    case 'sin-margen':
      return (
        `Ya tenés ${enCarrito} ${unidades(enCarrito)} en el carrito, ` +
        'que es todo lo disponible de este producto.'
      )
    default:
      return 'Este producto se quedó sin unidades.'
  }
}

export const useCartStore = create(
  persist(
    (set, get) => ({
      items: [], // [{ producto, cantidad }]

      /**
       * Agrega respetando el tope y lo que ya está en el carrito. Devuelve qué
       * pasó, para que la pantalla pueda explicarlo en lugar de recortar en
       * silencio y dejar al cliente sin saber por qué se llevó menos.
       */
      agregar: (producto, cantidad = 1) => {
        const pedidas = Math.max(1, cantidad)
        const tope = maximoComprable(producto)
        const existente = get().items.find((i) => i.producto.id === producto.id)
        const actual = existente ? existente.cantidad : 0

        if (!productoDisponible(producto) || tope <= 0) {
          const salida = { resultado: 'sin-stock', agregadas: 0, enCarrito: actual, tope }
          return { ...salida, mensaje: mensajeDe(salida) }
        }
        if (actual >= tope) {
          const salida = { resultado: 'sin-margen', agregadas: 0, enCarrito: actual, tope }
          return { ...salida, mensaje: mensajeDe(salida) }
        }

        const agregadas = Math.min(pedidas, tope - actual)
        const enCarrito = actual + agregadas

        set((state) => ({
          items: existente
            ? state.items.map((i) =>
                i.producto.id === producto.id ? { ...i, producto, cantidad: enCarrito } : i,
              )
            : [...state.items, { producto, cantidad: enCarrito }],
        }))

        const salida = {
          resultado: agregadas < pedidas ? 'topado' : 'agregado',
          agregadas,
          enCarrito,
          tope,
        }
        return { ...salida, mensaje: mensajeDe(salida) }
      },

      setCantidad: (productoId, cantidad) =>
        set((state) => ({
          items: state.items.flatMap((i) => {
            if (i.producto.id !== productoId) return i
            if (!productoDisponible(i.producto)) return []
            return [{ ...i, cantidad: Math.max(1, Math.min(cantidad, maximoComprable(i.producto))) }]
          }),
        })),

      quitar: (productoId) =>
        set((state) => ({ items: state.items.filter((i) => i.producto.id !== productoId) })),

      vaciar: () => set({ items: [] }),

      /**
       * Aplica la disponibilidad que acaba de informar el servidor: recorta lo
       * que quedó largo y saca lo que ya no se puede comprar. Devuelve el
       * detalle para poder explicárselo al cliente.
       */
      aplicarDisponibilidad: (topePorProducto) => {
        const ajustados = []
        const quitados = []
        const siguientes = []

        for (const item of get().items) {
          const tope = topePorProducto[item.producto.id]
          if (tope === undefined) {
            siguientes.push(item)
            continue
          }
          if (tope <= 0) {
            quitados.push(item.producto.nombre)
            continue
          }
          const producto = { ...item.producto, maxPorPedido: tope, disponible: true }
          if (item.cantidad > tope) {
            ajustados.push({ nombre: item.producto.nombre, de: item.cantidad, a: tope })
            siguientes.push({ producto, cantidad: tope })
          } else {
            siguientes.push({ ...item, producto })
          }
        }

        set({ items: siguientes })

        const partes = ajustados.map(
          (a) =>
            `De ${a.nombre} quedan ${a.a} ${unidades(a.a)}, ` +
            `así que bajamos tu cantidad de ${a.de} a ${a.a}.`,
        )
        partes.push(...quitados.map((n) => `${n} se quedó sin stock y lo sacamos del carrito.`))

        return {
          ajustados,
          quitados,
          huboCambios: ajustados.length > 0 || quitados.length > 0,
          mensaje: `${partes.join(' ')} Podés continuar con la compra.`,
        }
      },
    }),
    { name: 'techstore-cart' },
  ),
)

export const selectTotalItems = (state) => state.items.reduce((acc, i) => acc + i.cantidad, 0)

export const selectTotalMonto = (state) =>
  state.items.reduce((acc, i) => acc + i.producto.precio * i.cantidad, 0)

/** Cuántas unidades de un producto ya hay en el carrito. */
export const selectCantidadDe = (productoId) => (state) =>
  state.items.find((i) => i.producto.id === productoId)?.cantidad ?? 0
