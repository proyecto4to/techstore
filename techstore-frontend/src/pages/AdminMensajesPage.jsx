import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessageSquare, Send, Wifi, WifiOff, XCircle } from 'lucide-react'

import AdminTabs from '../components/admin/AdminTabs'
import { connectAdminChat } from '../services/chatSocket'
import { mensajeriaService } from '../services/mensajeriaService'
import { useAuthStore } from '../store/authStore'

function fecha(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('es-PY', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function ConversationItem({ conversation, selected, onSelect }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`w-full border-b border-dark-border p-4 text-left transition-colors ${
        selected ? 'bg-primary-400/15' : 'hover:bg-white/5'
      }`}
    >
      <span className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block truncate font-semibold text-neutral-text">{conversation.clienteNombre}</span>
          <span className="block truncate text-xs text-neutral-muted">{conversation.clienteEmail}</span>
        </span>
        {conversation.noLeidos > 0 && (
          <span className="rounded-full bg-secondary-400 px-2 py-0.5 text-xs font-bold text-white">
            {conversation.noLeidos > 99 ? '99+' : conversation.noLeidos}
          </span>
        )}
      </span>
      <span className="mt-2 block truncate text-sm text-neutral-secondary">
        {conversation.ultimoMensaje?.contenido ?? 'Sin mensajes'}
      </span>
      <span className="mt-2 flex justify-between gap-2 text-xs text-neutral-muted">
        <span>{conversation.pedidoNumero ? `Pedido N.º ${conversation.pedidoNumero}` : 'Consulta general'}</span>
        <span>{fecha(conversation.ultimoMensajeEn ?? conversation.createdAt)}</span>
      </span>
    </button>
  )
}

function AdminMensajesPage() {
  const queryClient = useQueryClient()
  const usuario = useAuthStore((state) => state.usuario)
  const [selectedId, setSelectedId] = useState(null)
  const selectedRef = useRef(null)
  const [contenido, setContenido] = useState('')
  const [connected, setConnected] = useState(false)
  const [realtimeError, setRealtimeError] = useState(null)

  const inbox = useQuery({
    queryKey: ['admin-conversaciones'],
    queryFn: () => mensajeriaService.listar(),
    refetchInterval: connected ? false : 15000,
  })
  const conversation = useQuery({
    queryKey: ['admin-conversacion', selectedId],
    queryFn: () => mensajeriaService.obtener(selectedId),
    enabled: Boolean(selectedId),
  })
  const history = useQuery({
    queryKey: ['admin-mensajes', selectedId],
    queryFn: () => mensajeriaService.historial(selectedId),
    enabled: Boolean(selectedId),
    refetchInterval: connected ? false : 10000,
  })

  useEffect(() => {
    const conversations = inbox.data?.content ?? []
    if (conversations.length > 0 && !conversations.some((item) => item.id === selectedId)) {
      setSelectedId(conversations[0].id)
    }
  }, [inbox.data, selectedId])
  useEffect(() => {
    selectedRef.current = selectedId
    if (!selectedId) return
    void mensajeriaService.marcarLeido(selectedId).then(() => {
      void queryClient.invalidateQueries({ queryKey: ['admin-conversaciones'] })
    }).catch(() => undefined)
  }, [queryClient, selectedId])
  useEffect(() => {
    const client = connectAdminChat({
      onMessage: (message) => {
        void queryClient.invalidateQueries({ queryKey: ['admin-conversaciones'] })
        if (message.conversacionId === selectedRef.current) {
          void queryClient.invalidateQueries({ queryKey: ['admin-mensajes', message.conversacionId] })
          void mensajeriaService.marcarLeido(message.conversacionId).catch(() => undefined)
        }
      },
      onReading: (reading) => {
        if (reading.conversacionId === selectedRef.current) {
          void queryClient.invalidateQueries({ queryKey: ['admin-mensajes', reading.conversacionId] })
        }
      },
      onError: (error) => setRealtimeError(error.mensaje),
      onConnectionChange: setConnected,
    })
    return () => { void client.deactivate() }
  }, [queryClient])

  const send = useMutation({
    mutationFn: () => mensajeriaService.enviar(selectedId, contenido.trim()),
    onSuccess: () => {
      setContenido('')
      void queryClient.invalidateQueries({ queryKey: ['admin-mensajes', selectedId] })
      void queryClient.invalidateQueries({ queryKey: ['admin-conversaciones'] })
    },
  })
  const close = useMutation({
    mutationFn: () => mensajeriaService.cerrar(selectedId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-conversacion', selectedId] })
      void queryClient.invalidateQueries({ queryKey: ['admin-conversaciones'] })
    },
  })
  const messages = useMemo(() => [...(history.data?.content ?? [])].reverse(), [history.data])

  const submit = (event) => {
    event.preventDefault()
    if (contenido.trim() && selectedId && !send.isPending) send.mutate()
  }

  return (
    <div className="container mx-auto px-4 py-8 md:px-6">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-4xl font-bold text-neutral-text">
          ⚙️ Administración — <span className="gradient-text">Mensajes</span>
        </h1>
        <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-sm ${connected ? 'bg-emerald-400/15 text-emerald-300' : 'bg-amber-400/15 text-amber-300'}`}>
          {connected ? <Wifi size={16} /> : <WifiOff size={16} />}
          {connected ? 'Inbox en vivo' : 'Reconectando · respaldo REST'}
        </span>
      </div>
      <AdminTabs />
      {realtimeError && <p className="alerta-error" role="alert">{realtimeError}</p>}
      {inbox.isError && <p className="alerta-error" role="alert">No se pudo cargar el inbox.</p>}

      <div className="grid min-h-[620px] overflow-hidden rounded-lg border border-dark-border bg-dark-card lg:grid-cols-[360px_1fr]">
        <aside className="border-b border-dark-border lg:border-b-0 lg:border-r">
          <div className="flex items-center gap-2 border-b border-dark-border p-4">
            <MessageSquare size={20} className="text-primary-300" />
            <h2 className="font-semibold text-neutral-text">Conversaciones</h2>
            <span className="ml-auto text-sm text-neutral-muted">{inbox.data?.totalElements ?? 0}</span>
          </div>
          <div className="max-h-[560px] overflow-y-auto">
            {inbox.isLoading && <p className="p-4 text-neutral-muted">Cargando conversaciones…</p>}
            {!inbox.isLoading && inbox.data?.content?.length === 0 && (
              <p className="p-6 text-center text-neutral-muted">Todavía no hay consultas de clientes.</p>
            )}
            {inbox.data?.content?.map((item) => (
              <ConversationItem key={item.id} conversation={item} selected={item.id === selectedId} onSelect={() => setSelectedId(item.id)} />
            ))}
          </div>
        </aside>

        <section className="flex min-w-0 flex-col">
          {!selectedId ? (
            <div className="m-auto p-8 text-center text-neutral-muted">Seleccioná una conversación.</div>
          ) : (
            <>
              <header className="flex flex-wrap items-center gap-3 border-b border-dark-border p-4">
                <div className="min-w-0 flex-1">
                  <h2 className="truncate font-semibold text-neutral-text">{conversation.data?.clienteNombre ?? 'Cliente'}</h2>
                  <p className="truncate text-sm text-neutral-muted">
                    {conversation.data?.pedidoNumero ? `Pedido N.º ${conversation.data.pedidoNumero}` : 'Consulta general'}
                    {' · '}{conversation.data?.clienteEmail}
                  </p>
                </div>
                {conversation.data?.estado === 'ABIERTA' && (
                  <button type="button" className="btn btn-danger inline-flex items-center gap-2 text-sm" onClick={() => close.mutate()} disabled={close.isPending}>
                    <XCircle size={16} /> Cerrar
                  </button>
                )}
              </header>
              <div className="flex-1 space-y-3 overflow-y-auto bg-dark-bg/50 p-4 md:p-6">
                {history.isLoading && <p className="text-neutral-muted">Recuperando historial…</p>}
                {history.isError && <p className="alerta-error">No se pudo recuperar el historial.</p>}
                {messages.map((message) => {
                  const outgoing = message.remitenteId === usuario?.id
                  return (
                    <div key={message.id} className={`flex ${outgoing ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[82%] rounded-2xl px-4 py-3 ${outgoing ? 'rounded-br-sm bg-primary-500 text-white' : 'rounded-bl-sm border border-dark-border bg-dark-card text-neutral-text'}`}>
                        {!outgoing && <p className="mb-1 text-xs font-semibold text-primary-300">{message.remitenteNombre}</p>}
                        <p className="whitespace-pre-wrap break-words text-sm">{message.contenido}</p>
                        <p className={`mt-1 text-right text-[11px] ${outgoing ? 'text-white/70' : 'text-neutral-muted'}`}>
                          {fecha(message.enviadoEn)}{outgoing && message.leidoEn ? ' · leído' : ''}
                        </p>
                      </div>
                    </div>
                  )
                })}
              </div>
              {send.isError && <p className="alerta-error m-4 mb-0" role="alert">No se pudo enviar el mensaje. Reintentá sin cambiar el texto.</p>}
              {conversation.data?.estado === 'CERRADA' ? (
                <p className="border-t border-dark-border p-4 text-center text-sm text-neutral-muted">La conversación está cerrada.</p>
              ) : (
                <form onSubmit={submit} className="flex items-end gap-3 border-t border-dark-border p-4">
                  <label className="flex-1">
                    <span className="sr-only">Respuesta</span>
                    <textarea
                      value={contenido}
                      onChange={(event) => setContenido(event.target.value)}
                      maxLength={2000}
                      rows={2}
                      placeholder="Escribí una respuesta…"
                      className="w-full resize-none rounded-md border border-dark-border bg-dark-bg px-4 py-3 text-neutral-text outline-none focus:border-primary-400"
                    />
                  </label>
                  <button type="submit" className="btn btn-primary inline-flex items-center gap-2" disabled={!contenido.trim() || send.isPending}>
                    <Send size={17} /> {send.isPending ? 'Enviando…' : 'Enviar'}
                  </button>
                </form>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  )
}

export default AdminMensajesPage
