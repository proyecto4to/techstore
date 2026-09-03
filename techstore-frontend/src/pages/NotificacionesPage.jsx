import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Bell, BellOff, CheckCheck } from 'lucide-react'

import { notificacionService } from '../services/notificacionService'
import { useAuthStore } from '../store/authStore'
import { rutaDeEnlace } from '../utils/deepLinks'

const fecha = (iso) => new Date(iso).toLocaleString('es-PY', { dateStyle: 'short', timeStyle: 'short' })

export default function NotificacionesPage() {
  const { usuario } = useAuthStore()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['notificaciones'],
    queryFn: () => notificacionService.listar({ size: 50 }),
  })

  const refrescar = () => {
    void queryClient.invalidateQueries({ queryKey: ['notificaciones'] })
    void queryClient.invalidateQueries({ queryKey: ['notificaciones-no-leidas'] })
  }

  const marcarLeida = useMutation({ mutationFn: notificacionService.marcarLeida, onSuccess: refrescar })
  const marcarTodas = useMutation({ mutationFn: notificacionService.marcarTodasLeidas, onSuccess: refrescar })

  const avisos = data?.content ?? []
  const noLeidas = avisos.filter((aviso) => !aviso.leida).length

  // El aviso se marca leído y, si tiene dónde abrirse, lleva ahí. Un aviso ya
  // leído sigue siendo navegable: es la forma natural de volver a lo que lo
  // originó.
  const abrir = (aviso) => {
    if (!aviso.leida) marcarLeida.mutate(aviso.id)
    const destino = rutaDeEnlace(aviso.deepLink, usuario?.rol)
    if (destino && destino !== '/notificaciones') navigate(destino)
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-4xl font-bold text-neutral-text">
          Mis <span className="gradient-text">notificaciones</span>
        </h1>
        {noLeidas > 0 && (
          <button
            type="button"
            onClick={() => marcarTodas.mutate()}
            disabled={marcarTodas.isPending}
            className="btn btn-secondary inline-flex items-center gap-2 text-sm"
          >
            <CheckCheck size={16} />
            {marcarTodas.isPending ? 'Marcando…' : `Marcar todas (${noLeidas})`}
          </button>
        )}
      </div>

      {(marcarLeida.isError || marcarTodas.isError) && (
        <p className="alerta-error" role="alert">
          No se pudo actualizar la notificación. Probá de nuevo.
        </p>
      )}
      {isLoading && <p className="text-neutral-muted">Cargando notificaciones…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar las notificaciones.</p>}

      {!isLoading && !isError && avisos.length === 0 && (
        <div className="card flex flex-col items-center gap-3 py-10 text-center text-neutral-muted">
          <BellOff size={28} />
          <p>No tenés notificaciones.</p>
        </div>
      )}

      <ul className="flex flex-col gap-3">
        {avisos.map((aviso) => {
          const destino = rutaDeEnlace(aviso.deepLink, usuario?.rol)
          return (
            <li key={aviso.id}>
              <button
                type="button"
                onClick={() => abrir(aviso)}
                aria-label={`${aviso.titulo}${aviso.leida ? '' : ' (sin leer)'}`}
                className={`card flex w-full items-start gap-4 text-left transition-colors hover:border-primary-400 ${
                  aviso.leida ? '' : 'border-primary-400/60'
                }`}
              >
                <Bell size={20} className={aviso.leida ? 'text-neutral-muted' : 'text-primary-300'} />
                <div className="min-w-0 flex-1">
                  <p className={`truncate ${aviso.leida ? 'text-neutral-text' : 'font-semibold text-neutral-text'}`}>
                    {aviso.titulo}
                  </p>
                  <p className="text-sm text-neutral-secondary">{aviso.mensaje}</p>
                  <p className="mt-1 text-xs text-neutral-muted">
                    {fecha(aviso.createdAt)}
                    {destino && destino !== '/notificaciones' && ' · abre la pantalla relacionada'}
                  </p>
                </div>
                {!aviso.leida && (
                  <span className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full bg-accent-400" aria-hidden="true" />
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
