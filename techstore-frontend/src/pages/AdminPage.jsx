import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { pedidoService } from '../services/pedidoService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'

const TIPOS_PAGO = ['Efectivo', 'Tarjeta', 'Transferencia', 'Cheque']

function AdminPage() {
  const [page, setPage] = useState(0)
  const [tipoPagoPorPedido, setTipoPagoPorPedido] = useState({})
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
    mutationFn: ({ id, tipoPago }) => pedidoService.registrarPago(id, tipoPago),
    onSuccess: (pago) => {
      setError(null)
      setMensaje(
        `Pago N.º ${pago.numero} registrado (${pago.tipoPago}). ` +
          `Factura ${pago.factura.numero} emitida: total ${formatPrecio(pago.factura.total)} ` +
          `(IVA ${formatPrecio(pago.factura.iva)}).`,
      )
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

  const ocupado =
    confirmarMutation.isPending || cobrarMutation.isPending || anularMutation.isPending

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
                return (
                  <tr key={pedido.id}>
                    <td className="font-semibold text-neutral-text">#{pedido.numero}</td>
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
                        <div className="flex gap-2">
                          <select
                            value={tipoPagoPorPedido[pedido.id] ?? 'Efectivo'}
                            onChange={(e) =>
                              setTipoPagoPorPedido((prev) => ({
                                ...prev,
                                [pedido.id]: e.target.value,
                              }))
                            }
                            className="input w-auto py-1 text-sm"
                          >
                            {TIPOS_PAGO.map((tipo) => (
                              <option key={tipo} value={tipo}>
                                {tipo}
                              </option>
                            ))}
                          </select>
                          <button
                            onClick={() =>
                              cobrarMutation.mutate({
                                id: pedido.id,
                                tipoPago: tipoPagoPorPedido[pedido.id] ?? 'Efectivo',
                              })
                            }
                            disabled={ocupado}
                            className="btn btn-primary text-sm"
                          >
                            💵 Cobrar
                          </button>
                        </div>
                      )}
                      {pedido.estado === 'G' && (
                        <span className="text-sm text-emerald-400">Facturado ✓</span>
                      )}
                    </td>
                  </tr>
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
