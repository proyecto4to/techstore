import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { pagoService } from '../services/pagoService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'

const MOTIVOS = [
  'El monto no coincide con el total del pedido',
  'La transferencia todavía no se acreditó en la cuenta',
  'El comprobante no se lee bien',
  'La transferencia fue a otra cuenta',
]

/** Un comprobante: la imagen se pide con la sesión, por eso va como blob. */
function Comprobante({ pedidoId, comprobante }) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    let activo = true
    let creada = null
    pagoService.urlDelComprobante(pedidoId, comprobante.id).then((u) => {
      creada = u
      if (activo) setUrl(u)
    }).catch(() => {})
    return () => {
      activo = false
      if (creada) URL.revokeObjectURL(creada)
    }
  }, [pedidoId, comprobante.id])

  const esPdf = comprobante.tipo === 'application/pdf'
  return (
    <div className="rounded-md border border-dark-border p-2">
      {!url ? (
        <p className="text-sm text-neutral-muted">Cargando…</p>
      ) : esPdf ? (
        <a href={url} target="_blank" rel="noopener" className="text-primary-300 underline">Abrir el PDF</a>
      ) : (
        <a href={url} target="_blank" rel="noopener">
          <img src={url} alt="Comprobante de la transferencia" className="max-h-72 rounded" />
        </a>
      )}
      <p className="mt-1 text-xs text-neutral-muted">
        {comprobante.estado === 'RECHAZADO' ? `Rechazado: ${comprobante.motivoRechazo ?? ''}` : comprobante.estado === 'APROBADO' ? 'Aprobado' : 'En revisión'}
      </p>
    </div>
  )
}

function PagoPendiente({ pago }) {
  const queryClient = useQueryClient()
  const [abierto, setAbierto] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [referencia, setReferencia] = useState('')
  const comprobantes = useQuery({
    queryKey: ['comprobantes', pago.pedidoId],
    queryFn: () => pagoService.comprobantesDelPedido(pago.pedidoId),
    enabled: abierto,
  })
  const alTerminar = () => {
    queryClient.invalidateQueries({ queryKey: ['pagos-pendientes'] })
    queryClient.invalidateQueries({ queryKey: ['pedidos'] })
  }
  const aprobar = useMutation({ mutationFn: () => pagoService.aprobar(pago.pedidoId, referencia.trim()), onSuccess: alTerminar })
  const rechazar = useMutation({ mutationFn: () => pagoService.rechazar(pago.pedidoId, motivo.trim()), onSuccess: alTerminar })
  const error = aprobar.error ?? rechazar.error

  return (
    <li className="card">
      <button type="button" className="flex w-full flex-wrap items-center justify-between gap-2 text-left" onClick={() => setAbierto((v) => !v)}>
        <span>
          <span className="font-semibold text-neutral-text">Pedido N.º {pago.numero}</span>
          <span className="ml-2 text-sm text-neutral-muted">{pago.clienteNombre || pago.clienteEmail}</span>
        </span>
        <span className="font-bold text-neutral-text">{formatPrecio(pago.total)}</span>
      </button>
      {abierto && (
        <div className="mt-4 space-y-4">
          <p className="text-sm text-neutral-secondary">
            Tiene que haber llegado <strong>{formatPrecio(pago.total)}</strong>.
            {pago.metodoEntrega === 'ENVIO_DOMICILIO' ? ' Es un envío: sale apenas lo apruebes.' : ' Es para retirar en el local.'}
          </p>
          {comprobantes.isLoading && <p className="text-sm text-neutral-muted">Cargando comprobantes…</p>}
          <div className="grid gap-3 md:grid-cols-2">
            {(comprobantes.data ?? []).map((c) => (
              <Comprobante key={c.id} pedidoId={pago.pedidoId} comprobante={c} />
            ))}
          </div>
          {error && (
            <p className="alerta-error" role="alert">{error.response?.data?.detail ?? 'No se pudo completar la operación.'}</p>
          )}
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <input className="input" placeholder="N.º de operación del banco (opcional)" maxLength={50}
                value={referencia} onChange={(e) => setReferencia(e.target.value)} />
              <button type="button" className="btn btn-primary w-full" disabled={aprobar.isPending || rechazar.isPending}
                onClick={() => aprobar.mutate()}>
                {aprobar.isPending ? 'Aprobando…' : 'Aprobar pago'}
              </button>
              <p className="text-xs text-neutral-muted">Confirma el pedido, registra el cobro, emite la factura y lo pasa a preparación.</p>
            </div>
            <div className="space-y-2">
              <select className="input" value={MOTIVOS.includes(motivo) ? motivo : ''} onChange={(e) => setMotivo(e.target.value)}>
                <option value="">Elegí un motivo o escribilo abajo</option>
                {MOTIVOS.map((m) => <option key={m} value={m}>{m}</option>)}
              </select>
              <textarea className="input" rows={2} maxLength={300} placeholder="Motivo para el cliente"
                value={motivo} onChange={(e) => setMotivo(e.target.value)} />
              <button type="button" className="btn btn-secondary w-full" disabled={!motivo.trim() || aprobar.isPending || rechazar.isPending}
                onClick={() => rechazar.mutate()}>
                {rechazar.isPending ? 'Rechazando…' : 'Rechazar y avisar al cliente'}
              </button>
            </div>
          </div>
        </div>
      )}
    </li>
  )
}

/**
 * Las transferencias que esperan una decisión de la tienda.
 *
 * Es la misma bandeja que «Verificar pagos» en la app: un pedido a domicilio
 * no se prepara hasta que alguien apruebe acá su comprobante.
 */
function AdminPagosPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['pagos-pendientes'],
    queryFn: () => pagoService.pendientes(false),
    refetchInterval: 60_000,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-2 text-4xl font-bold text-neutral-text">Verificar pagos</h1>
      <p className="mb-6 text-neutral-secondary">Del que más espera al más nuevo.</p>
      <AdminTabs />
      {isLoading && <p className="text-neutral-muted">Cargando…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar los pagos para verificar.</p>}
      {data && data.length === 0 && <p className="text-neutral-muted">No hay transferencias para revisar.</p>}
      <ul className="space-y-4">
        {(data ?? []).map((pago) => <PagoPendiente key={pago.pedidoId} pago={pago} />)}
      </ul>
    </div>
  )
}

export default AdminPagosPage
