import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const productoDisponible = (producto) =>
  producto.disponible ?? (Number.isFinite(producto.stockDisponible) && producto.stockDisponible > 0)

export const maximoComprable = (producto) =>
  Number.isFinite(producto.stockDisponible) ? Math.max(0, producto.stockDisponible) : 99

export const useCartStore = create(
  persist(
    (set) => ({
      items: [], // [{ producto, cantidad }]

      // Topa la cantidad al stock disponible del producto (no se puede agregar
      // más de lo que hay). El backend igual valida al finalizar la compra.
      agregar: (producto, cantidad = 1) =>
        set((state) => {
          if (!productoDisponible(producto)) return state
          const tope = maximoComprable(producto)
          const existente = state.items.find((i) => i.producto.id === producto.id)
          if (existente) {
            return {
              items: state.items.map((i) =>
                i.producto.id === producto.id
                  ? { ...i, producto, cantidad: Math.min(i.cantidad + cantidad, tope) }
                  : i,
              ),
            }
          }
          return { items: [...state.items, { producto, cantidad: Math.min(cantidad, tope) }] }
        }),

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
    }),
    { name: 'techstore-cart' },
  ),
)

export const selectTotalItems = (state) => state.items.reduce((acc, i) => acc + i.cantidad, 0)

export const selectTotalMonto = (state) =>
  state.items.reduce((acc, i) => acc + i.producto.precio * i.cantidad, 0)
