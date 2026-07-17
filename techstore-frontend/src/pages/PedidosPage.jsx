import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { pedidoService } from '../services/pedidoService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'

function PedidosPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pedidos', page],
    queryFn: () => pedidoService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const cancelarMutation = useMutation({
    mutationFn: (id) => pedidoService.cancelar(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pedidos'] }),
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">Mis pedidos</h1>

      {isLoading && <p className="text-neutral-muted">Cargando pedidos…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar los pedidos.</p>}
      {cancelarMutation.isError && (
        <p className="alerta-error" role="alert">
          {cancelarMutation.error.response?.data?.detail ?? 'No se pudo anular el pedido.'}
        </p>
      )}

      {data && data.content.length === 0 && (
        <p className="text-neutral-muted">Todavía no hiciste ningún pedido.</p>
      )}

      {data && data.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Fecha</th>
                <th>Estado</th>
                <th>Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((pedido) => {
                const estado = estadoPedido(pedido.estado)
                return (
                  <tr key={pedido.id}>
                    <td className="font-semibold text-neutral-text">#{pedido.numero}</td>
                    <td>{new Date(pedido.fecha).toLocaleString('es-PY')}</td>
                    <td>
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${estado.clase}`}>
                        {estado.label}
                      </span>
                    </td>
                    <td className="font-semibold text-primary-300">{formatPrecio(pedido.total)}</td>
                    <td>
                      {pedido.estado === 'P' && (
                        <button
                          onClick={() => cancelarMutation.mutate(pedido.id)}
                          disabled={cancelarMutation.isPending}
                          className="btn btn-danger text-sm"
                        >
                          Anular
                        </button>
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
    </div>
  )
}

export default PedidosPage
