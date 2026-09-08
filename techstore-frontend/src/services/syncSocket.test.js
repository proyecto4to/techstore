import { describe, expect, it } from 'vitest'

import { destinoDeEventos, queryKeysForEvent, tenantFromToken } from './syncSocket'

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

  it('agotarse y cambiar de precio refrescan el catálogo igual que el stock', () => {
    const esperado = [['productos'], ['admin-productos'], ['venta-directa-productos']]
    for (const eventType of ['PRODUCT_OUT_OF_STOCK', 'PRODUCT_BACK_IN_STOCK', 'PRICE_UPDATED']) {
      expect(queryKeysForEvent({ eventType, aggregateType: 'PRODUCT' })).toEqual(esperado)
    }
  })

  // El servidor tiene dos canales por empresa y rechaza al que se suscribe al
  // que no le toca. Pedir el equivocado no degrada el tiempo real: lo apaga.
  it('quien opera la tienda escucha el canal completo', () => {
    expect(destinoDeEventos(7, { rol: 'Admin' })).toBe('/topic/tenant/7/events')
    expect(destinoDeEventos(7, { rol: 'Cajero' })).toBe('/topic/tenant/7/events')
  })

  it('quien compra escucha el canal acotado', () => {
    expect(destinoDeEventos(7, { rol: 'Cliente' })).toBe('/topic/tenant/7/events/compra')
    expect(destinoDeEventos(7, { rol: 'Repartidor' })).toBe('/topic/tenant/7/events/compra')
    expect(destinoDeEventos(7, null)).toBe('/topic/tenant/7/events/compra')
  })
})
