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
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-3xl font-bold text-navy">Panel de administración — Pedidos</h2>
      <AdminTabs />

      {mensaje && (
        <p className="mb-4 rounded bg-green-50 p-3 text-green-700" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="mb-4 rounded bg-red-50 p-3 text-red-600" role="alert">
          {error}
        </p>
      )}

      {isLoading && <p className="text-gray-500">Cargando pedidos…</p>}

      {data && data.content.length === 0 && <p className="text-gray-500">No hay pedidos aún.</p>}

      {data && data.content.length > 0 && (
        <div className="overflow-x-auto rounded-lg border-2 border-gold bg-white shadow-md">
          <table className="w-full text-left">
            <thead className="bg-navy text-gold">
              <tr>
                <th className="p-3">N.º</th>
                <th className="p-3">Fecha</th>
                <th className="p-3">Cliente</th>
                <th className="p-3">Estado</th>
                <th className="p-3">Total</th>
                <th className="p-3">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((pedido) => {
                const estado = estadoPedido(pedido.estado)
                return (
                  <tr key={pedido.id} className="border-t">
                    <td className="p-3 font-semibold text-navy">{pedido.numero}</td>
                    <td className="p-3">{new Date(pedido.fecha).toLocaleString('es-PY')}</td>
                    <td className="p-3">{pedido.usuarioEmail}</td>
                    <td className="p-3">
                      <span className={`rounded px-2 py-1 text-xs font-semibold ${estado.clase}`}>
                        {estado.label}
                      </span>
                    </td>
                    <td className="p-3 font-semibold">{formatPrecio(pedido.total)}</td>
                    <td className="p-3">
                      {pedido.estado === 'P' && (
                        <div className="flex gap-2">
                          <button
                            onClick={() => confirmarMutation.mutate(pedido.id)}
                            disabled={ocupado}
                            className="rounded bg-navy px-3 py-1 text-sm font-semibold text-gold hover:bg-navy-light disabled:opacity-50"
                          >
                            Confirmar
                          </button>
                          <button
                            onClick={() => anularMutation.mutate(pedido.id)}
                            disabled={ocupado}
                            className="rounded border border-red-400 px-3 py-1 text-sm text-red-500 hover:bg-red-50 disabled:opacity-50"
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
                            className="rounded border border-gray-300 p-1 text-sm"
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
                            className="rounded bg-gold px-3 py-1 text-sm font-semibold text-navy hover:bg-gold-light disabled:opacity-50"
                          >
                            💵 Cobrar
                          </button>
                        </div>
                      )}
                      {pedido.estado === 'G' && <span className="text-sm text-gray-500">Facturado ✓</span>}
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
            className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
          >
            ← Anterior
          </button>
          <span className="text-navy">
            Página {data.page + 1} de {data.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
          >
            Siguiente →
          </button>
        </div>
      )}

      <p className="mt-6 text-sm text-gray-500">
        Flujo: <strong>Pendiente</strong> (stock reservado) → <strong>Confirmar</strong> (descuenta
        stock real) → <strong>Cobrar</strong> (registra el pago y emite la factura con IVA
        desglosado). API completa en{' '}
        <a
          href="http://localhost:8080/swagger-ui.html"
          target="_blank"
          rel="noreferrer"
          className="text-navy underline hover:text-gold-dark"
        >
          Swagger UI
        </a>
        .
      </p>
    </div>
  )
}

export default AdminPage
