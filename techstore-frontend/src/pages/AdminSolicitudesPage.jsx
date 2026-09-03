import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import AdminTabs from '../components/admin/AdminTabs'
import { repartoService } from '../services/repartoService'
import { formatPrecio } from '../utils/format'

export default function AdminSolicitudesPage() {
  const [rechazo, setRechazo] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const queryClient = useQueryClient()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['solicitudes-caja-repartidor'],
    queryFn: () => repartoService.solicitudes({ size: 100 }),
  })
  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['solicitudes-caja-repartidor'] })
  const errorMessage = (error) =>
    error.response?.data?.detail ?? 'La solicitud no pudo procesarse. Actualizá la pantalla.'

  const aceptar = useMutation({
    mutationFn: repartoService.aceptar,
    onSuccess: (transferencia) => {
      setMensaje(`Transferencia de ${formatPrecio(transferencia.monto)} aceptada de forma íntegra.`)
      refrescar()
    },
  })
  const rechazar = useMutation({
    mutationFn: ({ id, reason }) => repartoService.rechazar(id, reason),
    onSuccess: () => {
      setMensaje('Solicitud rechazada. El saldo permanece en la caja del repartidor.')
      setRechazo(null)
      setMotivo('')
      refrescar()
    },
  })
  const ocupado = aceptar.isPending || rechazar.isPending

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        Administración — <span className="gradient-text">Solicitudes</span>
      </h1>
      <AdminTabs />
      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {(aceptar.isError || rechazar.isError) && (
        <p className="alerta-error" role="alert">
          {errorMessage(aceptar.error ?? rechazar.error)}
        </p>
      )}
      {isLoading && <p className="text-neutral-muted">Cargando solicitudes…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar las solicitudes.</p>}
      {data?.content.length === 0 && (
        <div className="card text-center text-neutral-muted">No hay solicitudes dirigidas a tu caja.</div>
      )}
      <div className="grid gap-4 lg:grid-cols-2">
        {data?.content.map((item) => (
          <article key={item.id} className="card">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-neutral-text">{item.repartidor}</h2>
                <p className="text-sm text-neutral-muted">
                  {new Date(item.fechaSolicitud).toLocaleString('es-PY')} · cierre #{item.cierreId}
                </p>
              </div>
              <span className={`rounded-full px-3 py-1 text-xs font-semibold ${estadoClase(item.estado)}`}>
                {estadoLabel(item.estado)}
              </span>
            </div>
            <p className="my-5 text-3xl font-bold text-primary-300">{formatPrecio(item.monto)}</p>
            {item.observacion && <p className="mb-4 text-sm text-neutral-secondary">{item.observacion}</p>}
            {item.estado === 'RECHAZADO' && (
              <p className="alerta-error mb-0 text-sm">Motivo: {item.motivoRechazo}</p>
            )}
            {item.estado === 'PENDIENTE_ACEPTACION' && (
              <div className="flex gap-3">
                <button
                  type="button"
                  disabled={ocupado}
                  onClick={() => {
                    if (window.confirm(`¿Confirmás que recibiste ${formatPrecio(item.monto)} de ${item.repartidor}?`)) {
                      aceptar.mutate(item.id)
                    }
                  }}
                  className="btn btn-primary inline-flex items-center gap-2"
                >
                  <CheckCircle2 size={17} /> Aceptar
                </button>
                <button
                  type="button"
                  disabled={ocupado}
                  onClick={() => { setRechazo(item); setMotivo('') }}
                  className="btn btn-danger inline-flex items-center gap-2"
                >
                  <XCircle size={17} /> Rechazar
                </button>
              </div>
            )}
          </article>
        ))}
      </div>

      {rechazo && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
          <form
            onSubmit={(event) => {
              event.preventDefault()
              rechazar.mutate({ id: rechazo.id, reason: motivo.trim() })
            }}
            className="card w-full max-w-lg"
          >
            <h2 className="mb-2 text-xl font-semibold text-neutral-text">Rechazar solicitud</h2>
            <p className="mb-4 text-sm text-neutral-secondary">
              El saldo de {rechazo.repartidor} permanecerá intacto. El motivo será visible para el repartidor.
            </p>
            <label htmlFor="motivo-rechazo" className="mb-2 block text-sm font-medium text-neutral-text">
              Motivo del rechazo *
            </label>
            <textarea
              id="motivo-rechazo"
              required
              maxLength={1000}
              rows={4}
              value={motivo}
              onChange={(event) => setMotivo(event.target.value)}
              className="input"
              autoFocus
            />
            <div className="mt-4 flex justify-end gap-3">
              <button type="button" onClick={() => setRechazo(null)} className="btn btn-secondary">Cancelar</button>
              <button type="submit" disabled={!motivo.trim() || rechazar.isPending} className="btn btn-danger">
                {rechazar.isPending ? 'Guardando…' : 'Confirmar rechazo'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

const estadoLabel = (estado) => ({
  PENDIENTE_ACEPTACION: 'Pendiente',
  ACEPTADO: 'Aceptada',
  RECHAZADO: 'Rechazada',
  CANCELADO: 'Cancelada',
}[estado] ?? estado)

const estadoClase = (estado) => ({
  PENDIENTE_ACEPTACION: 'bg-amber-400/20 text-amber-300',
  ACEPTADO: 'bg-emerald-400/20 text-emerald-300',
  RECHAZADO: 'bg-red-400/20 text-red-300',
}[estado] ?? 'bg-dark-lighter text-neutral-secondary')
