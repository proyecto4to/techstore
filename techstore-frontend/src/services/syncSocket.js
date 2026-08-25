import { Client } from '@stomp/stompjs'

import api, { API_BASE_URL } from './api'
import { useAuthStore } from '../store/authStore'

function websocketUrl() {
  const url = new URL(API_BASE_URL, window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = '/ws'
  url.search = ''
  return url.toString()
}

export function tenantFromToken(token) {
  try {
    const body = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return Number(JSON.parse(atob(body)).tenant_id) || null
  } catch {
    return null
  }
}

export const queryKeysForEvent = (event) => {
  const keys = {
    PRODUCT: [['productos'], ['admin-productos'], ['venta-directa-productos']],
    BRAND: [['marcas'], ['admin-marcas']],
    MODEL: [['modelos'], ['admin-modelos']],
    ORDER: [['pedidos'], ['admin-pedidos'], ['dashboard']],
    ORDER_ITEM: [['pedidos'], ['admin-pedidos'], ['productos'], ['admin-productos']],
    CLIENT: [['clientes'], ['admin-clientes']],
    SUPPLIER: [['proveedores-activos'], ['admin-proveedores']],
    SUPPLIER_OFFER: [['proveedores-activos'], ['admin-compras'], ['admin-productos']],
    PURCHASE_DOCUMENT: [['admin-compras'], ['admin-productos'], ['caja-resumen']],
    PAYMENT: [['admin-facturas'], ['caja-resumen'], ['caja-movimientos']],
    INVOICE: [['admin-facturas'], ['dashboard']],
    CREDIT_NOTE: [['admin-facturas'], ['caja-resumen'], ['caja-movimientos']],
    CASH_MOVEMENT: [['caja-resumen'], ['caja-movimientos'], ['caja-cierre-preview']],
    CASH_OPENING: [['caja-resumen'], ['caja-movimientos']],
    CASH_CLOSING: [['caja-resumen'], ['caja-movimientos']],
    USER: [['admin-usuarios']],
    CONFIGURATION: [['empresa-pendientes'], ['correos-config']],
    SHIPMENT: [['pedidos'], ['admin-pedidos']],
    SHIPPING_RATE: [['tarifas-envio']],
    ISSUING_POINT: [['puntos-expedicion']],
  }
  if (event.eventType === 'STOCK_UPDATED') return keys.PRODUCT
  return keys[event.aggregateType] ?? []
}

export function connectDomainEvents({ onEvent, onConnectionChange, onError }) {
  const token = useAuthStore.getState().accessToken
  const tenantId = tenantFromToken(token ?? '')
  if (!token || !tenantId) return null
  const cursorKey = `techstore-sync-cursor:${tenantId}`
  let cursor = Number(localStorage.getItem(cursorKey)) || 0
  let recovering = true
  const pending = []

  const consume = (event) => {
    if (!event?.id || event.id <= cursor) return
    cursor = event.id
    localStorage.setItem(cursorKey, String(cursor))
    onEvent?.(event)
  }

  const recover = async () => {
    try {
      for (let batch = 0; batch < 20; batch += 1) {
        const { data } = await api.get('/sync/events', { params: { after: cursor, limit: 200 } })
        data.forEach(consume)
        if (data.length < 200) break
      }
      recovering = false
      pending.sort((a, b) => a.id - b.id).forEach(consume)
    } catch (error) {
      recovering = false
      onError?.(error)
    }
  }

  const client = new Client({
    brokerURL: websocketUrl(),
    reconnectDelay: 1000,
    maxReconnectDelay: 15000,
    connectionTimeout: 8000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => undefined,
    beforeConnect: async () => {
      const current = useAuthStore.getState().accessToken
      if (!current) throw new Error('No hay sesión para sincronizar.')
      client.connectHeaders = { Authorization: `Bearer ${current}` }
    },
    onConnect: () => {
      onConnectionChange?.(true)
      client.subscribe(`/topic/tenant/${tenantId}/events`, (frame) => {
        try {
          const event = JSON.parse(frame.body)
          if (recovering) pending.push(event)
          else consume(event)
        } catch {
          onError?.(new Error('Evento de sincronización inválido'))
        }
      })
      void recover()
    },
    onWebSocketClose: () => onConnectionChange?.(false),
    onStompError: (frame) => onError?.(new Error(frame.headers.message || 'Error de sincronización')),
  })
  client.activate()
  return client
}
