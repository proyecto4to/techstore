import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const useCartStore = create(
  persist(
    (set) => ({
      items: [], // [{ producto, cantidad }]

      agregar: (producto, cantidad = 1) =>
        set((state) => {
          const existente = state.items.find((i) => i.producto.id === producto.id)
          if (existente) {
            return {
              items: state.items.map((i) =>
                i.producto.id === producto.id ? { ...i, cantidad: i.cantidad + cantidad } : i,
              ),
            }
          }
          return { items: [...state.items, { producto, cantidad }] }
        }),

      setCantidad: (productoId, cantidad) =>
        set((state) => ({
          items: state.items.map((i) =>
            i.producto.id === productoId ? { ...i, cantidad: Math.max(1, cantidad) } : i,
          ),
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
