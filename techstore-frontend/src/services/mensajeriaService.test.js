import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('./api', () => ({ default: api }))

import { mensajeriaService } from './mensajeriaService'

describe('mensajería administrativa', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lista el inbox ordenado por actividad reciente', async () => {
    api.get.mockResolvedValue({ data: { content: [], totalElements: 0 } })

    await mensajeriaService.listar({ page: 1, size: 25 })

    expect(api.get).toHaveBeenCalledWith('/conversaciones', {
      params: { page: 1, size: 25, sort: 'ultimoMensajeEn,desc' },
    })
  })

  it('envía con clientMessageId idempotente y marca lectura', async () => {
    api.post.mockResolvedValue({ data: { id: 81 } })
    api.put.mockResolvedValue({ data: { mensajes: 2 } })

    await mensajeriaService.enviar(12, 'Respuesta desde administración', 'admin-1')
    await mensajeriaService.marcarLeido(12)

    expect(api.post).toHaveBeenCalledWith('/conversaciones/12/mensajes', {
      clientMessageId: 'admin-1',
      contenido: 'Respuesta desde administración',
    })
    expect(api.put).toHaveBeenCalledWith('/conversaciones/12/leido')
  })
})
