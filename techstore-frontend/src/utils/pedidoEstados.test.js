import { describe, expect, it } from 'vitest'
import { estadoPedido } from './pedidoEstados'

describe('estadoPedido', () => {
  it('resuelve estados conocidos y conserva códigos desconocidos', () => {
    expect(estadoPedido('G')).toMatchObject({ label: 'Pagado' })
    expect(estadoPedido('Z')).toEqual({
      label: 'Z',
      clase: 'bg-dark-lighter text-neutral-secondary',
    })
  })
})
