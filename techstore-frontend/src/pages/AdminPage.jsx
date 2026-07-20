import { Fragment, useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { pedidoService } from '../services/pedidoService'
import { notaCreditoService } from '../services/facturaService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'
import PedidoDetallePanel from '../components/admin/PedidoDetallePanel'
import CobroPanel from '../components/admin/CobroPanel'

function AdminPage() {
  const [page, setPage] = useState(0)
  const [cobroPedidoId, setCobroPedidoId] = useState(null)
  const [detalleAbierto, setDetalleAbierto] = useState(null)
  const [ncPedidoId, setNcPedidoId] = useState(null)
  const [ncMotivo, setNcMotivo] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-pedidos', page],
    queryFn: () => pedidoService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const refrescar = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-pedidos'] })
    queryClient.invalidateQueries({ queryKey: ['productos'] })
  }

  const onError = (err) =>
    setError(err.response?.data?.detail ?? 'La operación falló. Intente de nuevo.')

  const confirmarMutation = useMutation({
    mutationFn: (id) => pedidoService.confirmar(id),
    onSuccess: (pedido) => {
      setError(null)
      setMensaje(`Pedido N.º ${pedido.numero} confirmado: stock descontado.`)
      refrescar()
    },
    onError,
  })

  const cobrarMutation = useMutation({
    mutationFn: ({ id, lineas }) => pedidoService.registrarPago(id, lineas),
    onSuccess: (pago) => {
      setError(null)
      const metodos = pago.lineas
        .map((l) => `${l.tipoPago} ${formatPrecio(l.monto)}`)
        .join(' + ')
      setMensaje(
        `Cobro registrado (${metodos}). ` +
          `Factura ${pago.factura.numero} emitida: total ${formatPrecio(pago.factura.total)} ` +
          `(IVA ${formatPrecio(pago.factura.iva)}).`,
      )
      setCobroPedidoId(null)
      refrescar()
    },
    onError,
  })

  const anularMutation = useMutation({
    mutationFn: (id) => pedidoService.cancelar(id),
    onSuccess: (pedido) => {
      setError(null)
      setMensaje(`Pedido N.º ${pedido.numero} anulado: stock liberado.`)
      refrescar()
    },
    onError,
  })

  const devolverMutation = useMutation({
    mutationFn: ({ id, motivo }) => notaCreditoService.emitir(id, motivo),
    onSuccess: (nc) => {
      setError(null)
      setMensaje(
        `Nota de crédito ${nc.numero} emitida por ${formatPrecio(nc.total)}: ` +
          `factura ${nc.facturaNumero} anulada, stock devuelto y egreso registrado en caja.`,
      )
      setNcPedidoId(null)
      setNcMotivo('')
      refrescar()
    },
    onError,
  })

  const ocupado =
    confirmarMutation.isPending || cobrarMutation.isPending || anularMutation.isPending ||
    devolverMutation.isPending

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Pedidos</span>
      </h1>
      <AdminTabs />

      {mensaje && (
        <p className="alerta-ok" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}

      {isLoading && <p className="text-neutral-muted">Cargando pedidos…</p>}

      {data && data.content.length === 0 && <p className="text-neutral-muted">No hay pedidos aún.</p>}

      {data && data.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Fecha</th>
                <th>Cliente</th>
                <th>Estado</th>
                <th>Total</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((pedido) => {
                const estado = estadoPedido(pedido.estado)
                const abierto = detalleAbierto === pedido.id
                return (
                  <Fragment key={pedido.id}>
                  <tr>
                    <td className="font-semibold text-neutral-text">
                      <button
                        onClick={() => setDetalleAbierto(abierto ? null : pedido.id)}
                        className="inline-flex items-center gap-1 text-neutral-text transition-colors hover:text-primary-300"
                        aria-expanded={abierto}
                        aria-label={`Ver detalle del pedido ${pedido.numero}`}
                      >
                        {abierto ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                        #{pedido.numero}
                      </button>
                    </td>
                    <td>{new Date(pedido.fecha).toLocaleString('es-PY')}</td>
                    <td className="text-neutral-secondary">{pedido.usuarioEmail}</td>
                    <td>
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${estado.clase}`}>
                        {estado.label}
                      </span>
                    </td>
                    <td className="font-semibold text-primary-300">{formatPrecio(pedido.total)}</td>
                    <td>
                      {pedido.estado === 'P' && (
                        <div className="flex gap-2">
                          <button
                            onClick={() => confirmarMutation.mutate(pedido.id)}
                            disabled={ocupado}
                            className="btn btn-primary text-sm"
                          >
                            Confirmar
                          </button>
                          <button
                            onClick={() => anularMutation.mutate(pedido.id)}
                            disabled={ocupado}
                            className="btn btn-danger text-sm"
                          >
                            Anular
                          </button>
                        </div>
                      )}
                      {pedido.estado === 'C' && (
                        <button
                          onClick={() =>
                            setCobroPedidoId(cobroPedidoId === pedido.id ? null : pedido.id)
                          }
                          disabled={ocupado}
                          className="btn btn-primary text-sm"
                        >
                          {cobroPedidoId === pedido.id ? 'Cerrar cobro' : '💵 Cobrar'}
                        </button>
                      )}
                      {pedido.estado === 'G' && (
                        ncPedidoId === pedido.id ? (
                          <span className="inline-flex flex-wrap items-center gap-2">
                            <input
                              placeholder="Motivo de la devolución"
                              maxLength={255}
                              value={ncMotivo}
                              onChange={(e) => setNcMotivo(e.target.value)}
                              className="input w-56 py-1 text-sm"
                            />
                            <button
                              onClick={() => devolverMutation.mutate({ id: pedido.id, motivo: ncMotivo.trim() })}
                              disabled={!ncMotivo.trim() || ocupado}
                              className="btn btn-danger text-sm"
                            >
                              Emitir NC
                            </button>
                            <button
                              onClick={() => { setNcPedidoId(null); setNcMotivo('') }}
                              className="btn btn-secondary text-sm"
                            >
                              ✕
                            </button>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-3">
                            <span className="text-sm text-emerald-400">Facturado ✓</span>
                            <button
                              onClick={() => { setNcPedidoId(pedido.id); setNcMotivo('') }}
                              className="btn btn-secondary text-sm"
                            >
                              Devolver
                            </button>
                          </span>
                        )
                      )}
                      {pedido.estado === 'R' && (
                        <span className="text-sm text-orange-400">Devuelto (NC emitida)</span>
                      )}
                    </td>
                  </tr>
                  {abierto && (
                    <tr>
                      <td colSpan={6} className="bg-dark-bg/30 p-3">
                        <PedidoDetallePanel pedidoId={pedido.id} />
                      </td>
                    </tr>
                  )}
                  {cobroPedidoId === pedido.id && pedido.estado === 'C' && (
                    <tr>
                      <td colSpan={6} className="bg-dark-bg/30 p-3">
                        <CobroPanel
                          total={pedido.total}
                          ocupado={ocupado}
                          onCobrar={(lineas) => cobrarMutation.mutate({ id: pedido.id, lineas })}
                          onCancelar={() => setCobroPedidoId(null)}
                        />
                      </td>
                    </tr>
                  )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="btn btn-secondary"
          >
            ← Anterior
          </button>
          <span className="text-neutral-secondary">
            Página {data.page + 1} de {data.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="btn btn-secondary"
          >
            Siguiente →
          </button>
        </div>
      )}

      <p className="mt-6 text-sm text-neutral-muted">
        Flujo: <strong className="text-neutral-secondary">Pendiente</strong> (stock reservado) →{' '}
        <strong className="text-neutral-secondary">Confirmar</strong> (descuenta stock real) →{' '}
        <strong className="text-neutral-secondary">Cobrar</strong> (registra el pago y emite la
        factura con IVA desglosado).
      </p>
    </div>
  )
}

export default AdminPage
