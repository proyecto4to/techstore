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

/**
 * Quién opera la tienda.
 *
 * <p>Hay dos canales de eventos por empresa: el completo, que lleva también
 * caja, pagos y usuarios, y el acotado, con lo que le toca a quien compra.
 * Suscribirse al que no corresponde termina en un rechazo del servidor y el
 * cliente se queda sin tiempo real, así que el rol se decide acá y se comprueba
 * allá.
 */
const ROLES_DE_MOSTRADOR = ['Admin', 'Cajero']

export function operaLaTienda(usuario) {
  return ROLES_DE_MOSTRADOR.includes(usuario?.rol)
}

/** El canal que le corresponde a este usuario. */
export function destinoDeEventos(tenantId, usuario) {
  return operaLaTienda(usuario)
    ? `/topic/tenant/${tenantId}/events`
    : `/topic/tenant/${tenantId}/events/compra`
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
    // Encender una promoción cambia el precio publicado sin tocar la fila del
    // producto, así que el catálogo hay que volver a pedirlo igual.
    PROMOTION: [['productos'], ['admin-productos']],
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

/**
 * La cola personal de la campana.
 *
 * <p>Va aparte del canal de la empresa porque una notificación es de una sola
 * persona, y porque el canal completo lo escucha únicamente quien opera la
 * tienda: un cliente jamás se enteraría de la suya.
 */
export const COLA_DE_NOTIFICACIONES = '/user/queue/notifications'

export function connectDomainEvents({ onEvent, onNotification, onConnectionChange, onError }) {
  const token = useAuthStore.getState().accessToken
  const tenantId = tenantFromToken(token ?? '')
  if (!token || !tenantId) return null
  const destino = destinoDeEventos(tenantId, useAuthStore.getState().usuario)
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
      client.subscribe(destino, (frame) => {
        try {
          const event = JSON.parse(frame.body)
          if (recovering) pending.push(event)
          else consume(event)
        } catch {
          onError?.(new Error('Evento de sincronización inválido'))
        }
      })
      // Los avisos personales viajan por la misma conexión: abrir un segundo
      // socket para la campana sería pagar dos veces el mismo enlace.
      client.subscribe(COLA_DE_NOTIFICACIONES, () => onNotification?.())
      void recover()
    },
    onWebSocketClose: () => onConnectionChange?.(false),
    onStompError: (frame) => onError?.(new Error(frame.headers.message || 'Error de sincronización')),
  })
  client.activate()
  return client
}
