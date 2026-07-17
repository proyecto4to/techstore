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
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-3xl font-bold text-navy">Mis pedidos</h2>

      {isLoading && <p className="text-gray-500">Cargando pedidos…</p>}
      {isError && <p className="text-red-500">No se pudieron cargar los pedidos.</p>}
      {cancelarMutation.isError && (
        <p className="mb-4 rounded bg-red-50 p-3 text-red-600" role="alert">
          {cancelarMutation.error.response?.data?.detail ?? 'No se pudo anular el pedido.'}
        </p>
      )}

      {data && data.content.length === 0 && (
        <p className="text-gray-500">Todavía no hiciste ningún pedido.</p>
      )}

      {data && data.content.length > 0 && (
        <div className="overflow-x-auto rounded-lg border-2 border-gold bg-white shadow-md">
          <table className="w-full text-left">
            <thead className="bg-navy text-gold">
              <tr>
                <th className="p-3">N.º</th>
                <th className="p-3">Fecha</th>
                <th className="p-3">Estado</th>
                <th className="p-3">Total</th>
                <th className="p-3"></th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((pedido) => {
                const estado = estadoPedido(pedido.estado)
                return (
                  <tr key={pedido.id} className="border-t">
                    <td className="p-3 font-semibold text-navy">{pedido.numero}</td>
                    <td className="p-3">{new Date(pedido.fecha).toLocaleString('es-PY')}</td>
                    <td className="p-3">
                      <span className={`rounded px-2 py-1 text-xs font-semibold ${estado.clase}`}>
                        {estado.label}
                      </span>
                    </td>
                    <td className="p-3 font-semibold">{formatPrecio(pedido.total)}</td>
                    <td className="p-3">
                      {pedido.estado === 'P' && (
                        <button
                          onClick={() => cancelarMutation.mutate(pedido.id)}
                          disabled={cancelarMutation.isPending}
                          className="text-red-500 hover:text-red-700 disabled:opacity-50"
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
    </div>
  )
}

export default PedidosPage
