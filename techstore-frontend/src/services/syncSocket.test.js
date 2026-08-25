import { describe, expect, it } from 'vitest'

import { queryKeysForEvent, tenantFromToken } from './syncSocket'

describe('sincronización de dominio', () => {
  it('extrae el tenant firmado que el servidor incluyó en el JWT', () => {
    const payload = btoa(JSON.stringify({ tenant_id: 7 })).replaceAll('=', '')
    expect(tenantFromToken(`header.${payload}.signature`)).toBe(7)
    expect(tenantFromToken('inválido')).toBeNull()
  })

  it('invalida catálogo y administración ante cambios de stock', () => {
    expect(queryKeysForEvent({ eventType: 'STOCK_UPDATED', aggregateType: 'PRODUCT' }))
      .toEqual([['productos'], ['admin-productos'], ['venta-directa-productos']])
  })
})
