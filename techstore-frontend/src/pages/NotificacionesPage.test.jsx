import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import NotificacionesPage from './NotificacionesPage'
import { notificacionService } from '../services/notificacionService'
import { useAuthStore } from '../store/authStore'

vi.mock('../services/notificacionService', () => ({
  notificacionService: {
    listar: vi.fn(),
    noLeidas: vi.fn(),
    marcarLeida: vi.fn(),
    marcarTodasLeidas: vi.fn(),
  },
}))

const AVISOS = [
  {
    id: 1,
    titulo: 'Problema con vehículo AB-123',
    mensaje: 'Cubierta trasera pinchada.',
    deepLink: 'techstore://vehiculo/3',
    leida: false,
    createdAt: '2026-09-02T10:00:00',
  },
  {
    id: 2,
    titulo: 'Entrega asignada',
    mensaje: 'Pedido #57 asignado.',
    deepLink: 'techstore://reparto/9',
    leida: true,
    createdAt: '2026-09-02T09:00:00',
  },
]

function montar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/notificaciones']}>
        <Routes>
          <Route path="/notificaciones" element={<NotificacionesPage />} />
          <Route path="/admin/vehiculos" element={<p>Panel de vehículos</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('NotificacionesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ usuario: { nombre: 'Ada', apellido: 'L', rol: 'Admin' } })
    notificacionService.listar.mockResolvedValue({ content: AVISOS })
    notificacionService.marcarLeida.mockResolvedValue({})
    notificacionService.marcarTodasLeidas.mockResolvedValue({})
  })

  it('marca el aviso como leído y abre la pantalla que lo originó', async () => {
    montar()
    const aviso = await screen.findByRole('button', { name: /Problema con veh.culo AB-123 \(sin leer\)/i })

    await userEvent.click(aviso)

    // React Query agrega su propio contexto como segundo argumento, así que
    // se comprueba el id, que es lo que importa.
    await waitFor(() => expect(notificacionService.marcarLeida).toHaveBeenCalled())
    expect(notificacionService.marcarLeida.mock.calls[0][0]).toBe(1)
    expect(await screen.findByText('Panel de vehículos')).toBeInTheDocument()
  })

  it('no vuelve a marcar uno ya leído, pero sigue siendo navegable', async () => {
    // Un aviso leído tiene que poder abrirse igual: es la forma natural de
    // volver a lo que lo originó.
    notificacionService.listar.mockResolvedValue({
      content: [{ ...AVISOS[0], leida: true }],
    })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Problema con veh.culo AB-123$/i }))

    expect(notificacionService.marcarLeida).not.toHaveBeenCalled()
    expect(await screen.findByText('Panel de vehículos')).toBeInTheDocument()
  })

  it('deja quieto el aviso que no tiene dónde abrirse en la web', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Entrega asignada/i }))

    expect(notificacionService.marcarLeida).not.toHaveBeenCalled()
    expect(screen.queryByText('Panel de vehículos')).not.toBeInTheDocument()
  })

  it('ofrece marcar todas solo cuando hay pendientes', async () => {
    montar()
    await userEvent.click(await screen.findByRole('button', { name: /Marcar todas \(1\)/i }))
    expect(notificacionService.marcarTodasLeidas).toHaveBeenCalledOnce()
  })

  it('avisa cuando la bandeja está vacía', async () => {
    notificacionService.listar.mockResolvedValue({ content: [] })
    montar()
    expect(await screen.findByText('No tenés notificaciones.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Marcar todas/i })).not.toBeInTheDocument()
  })
})
