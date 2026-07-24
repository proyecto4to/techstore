import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const useCartStore = create(
  persist(
    (set) => ({
      items: [], // [{ producto, cantidad }]

      // Topa la cantidad al stock disponible del producto (no se puede agregar
      // más de lo que hay). El backend igual valida al finalizar la compra.
      agregar: (producto, cantidad = 1) =>
        set((state) => {
          const tope = producto.stockDisponible > 0 ? producto.stockDisponible : 1
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
          items: state.items.map((i) => {
            if (i.producto.id !== productoId) return i
            const tope = i.producto.stockDisponible > 0 ? i.producto.stockDisponible : 1
            return { ...i, cantidad: Math.max(1, Math.min(cantidad, tope)) }
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
