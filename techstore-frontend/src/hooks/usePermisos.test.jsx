import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { usePermisos } from './usePermisos'
import { seguridadService } from '../services/seguridadService'

vi.mock('../services/seguridadService', () => ({
  seguridadService: { misPermisos: vi.fn() },
}))

function envoltorio() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  )
}

describe('permisos de la sesión', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'token',
      refreshToken: 'refresh',
      usuario: { id: 7, nombre: 'Ana', rol: 'Cajero' },
    })
  })

  it('responde según lo que dijo el backend, sin reglas propias', async () => {
    seguridadService.misPermisos.mockResolvedValue({
      rol: 'Cajero',
      permitidos: ['productos.ver', 'facturas.crear'],
      pantallas: ['catalogo', 'caja'],
    })

    const { result } = renderHook(() => usePermisos(), { wrapper: envoltorio() })
    await waitFor(() => expect(result.current.cargando).toBe(false))

    expect(result.current.puede('PRODUCTOS', 'VER')).toBe(true)
    expect(result.current.puede('FACTURAS', 'CREAR')).toBe(true)
    expect(result.current.puede('FACTURAS', 'ELIMINAR')).toBe(false)
    expect(result.current.puede('SEGURIDAD', 'VER')).toBe(false)
    expect(result.current.vePantalla('caja')).toBe(true)
    expect(result.current.vePantalla('seguridad')).toBe(false)
  })

  it('mientras no sabe, no habilita nada', async () => {
    let resolver
    seguridadService.misPermisos.mockReturnValue(new Promise((r) => { resolver = r }))

    const { result } = renderHook(() => usePermisos(), { wrapper: envoltorio() })

    // Ante la duda no se ofrece una acción que después va a rebotar contra el
    // backend: `cargando` existe justamente para distinguir esto de un "no".
    expect(result.current.puede('PRODUCTOS', 'VER')).toBe(false)
    expect(result.current.cargando).toBe(true)

    resolver({ rol: 'Cajero', permitidos: ['productos.ver'], pantallas: [] })
    await waitFor(() => expect(result.current.puede('PRODUCTOS', 'VER')).toBe(true))
  })

  it('sin sesión no pregunta ni permite', () => {
    useAuthStore.setState({ accessToken: null, refreshToken: null, usuario: null })

    const { result } = renderHook(() => usePermisos(), { wrapper: envoltorio() })

    expect(seguridadService.misPermisos).not.toHaveBeenCalled()
    expect(result.current.puede('PRODUCTOS', 'VER')).toBe(false)
    expect(result.current.cargando).toBe(false)
  })

  it('si la consulta falla, no inventa permisos', async () => {
    seguridadService.misPermisos.mockRejectedValue(new Error('sin red'))

    const { result } = renderHook(() => usePermisos(), { wrapper: envoltorio() })
    // El hook reintenta una vez antes de darse por vencido, así que la espera
    // tiene que cubrir ese segundo intento.
    await waitFor(() => expect(result.current.error).toBe(true), { timeout: 5000 })

    expect(result.current.puede('PRODUCTOS', 'VER')).toBe(false)
  })
})
