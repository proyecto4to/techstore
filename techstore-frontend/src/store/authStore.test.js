import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('sesión', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, refreshToken: null, usuario: null })
  })

  it('guarda la autenticación y permite refrescar el usuario', () => {
    const sesion = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      usuario: { id: 1, nombre: 'Ana', rol: 'Admin' },
    }

    useAuthStore.getState().setAuth(sesion)
    useAuthStore.getState().setUsuario({ ...sesion.usuario, nombre: 'Ana María' })

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: sesion.accessToken,
      refreshToken: sesion.refreshToken,
      usuario: { id: 1, nombre: 'Ana María', rol: 'Admin' },
    })
  })

  it('elimina todos los datos sensibles al cerrar sesión', () => {
    useAuthStore.getState().setAuth({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      usuario: { id: 1 },
    })

    useAuthStore.getState().logout()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      refreshToken: null,
      usuario: null,
    })
  })
})
