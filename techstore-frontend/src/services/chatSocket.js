import { Client } from '@stomp/stompjs'

import { API_BASE_URL } from './api'
import { useAuthStore } from '../store/authStore'

function websocketUrl() {
  const url = new URL(API_BASE_URL, window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = '/ws'
  url.search = ''
  url.hash = ''
  return url.toString()
}

function parse(frame, callback) {
  try {
    callback?.(JSON.parse(frame.body))
  } catch {
    // REST es la fuente recuperable si un frame no cumple el contrato.
  }
}

export function connectAdminChat({ onMessage, onReading, onError, onConnectionChange }) {
  const client = new Client({
    brokerURL: websocketUrl(),
    reconnectDelay: 1000,
    maxReconnectDelay: 15000,
    connectionTimeout: 8000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => undefined,
    beforeConnect: async () => {
      const { accessToken } = useAuthStore.getState()
      if (!accessToken) throw new Error('No hay sesión para conectar el inbox.')
      client.connectHeaders = { Authorization: `Bearer ${accessToken}` }
    },
    onConnect: () => {
      onConnectionChange?.(true)
      client.subscribe('/user/queue/chat', (frame) => parse(frame, onMessage))
      client.subscribe('/user/queue/chat-read', (frame) => parse(frame, onReading))
      client.subscribe('/user/queue/chat-errors', (frame) => parse(frame, onError))
    },
    onWebSocketClose: () => onConnectionChange?.(false),
    onStompError: (frame) => onError?.({ mensaje: frame.headers.message || 'No se pudo mantener el inbox en vivo.' }),
  })
  client.activate()
  return client
}
