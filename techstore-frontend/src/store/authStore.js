import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const useAuthStore = create(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      usuario: null,
      setAuth: ({ accessToken, refreshToken, usuario }) =>
        set({ accessToken, refreshToken, usuario }),
      logout: () => set({ accessToken: null, refreshToken: null, usuario: null }),
    }),
    { name: 'techstore-auth' },
  ),
)
