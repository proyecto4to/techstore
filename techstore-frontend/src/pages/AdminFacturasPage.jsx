import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { facturaService } from '../services/facturaService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'

const ESTILOS_FACTURA = {
  Emitida: 'bg-yellow-400/20 text-yellow-400',
  Pagada: 'bg-emerald-400/20 text-emerald-400',
  Cancelada: 'bg-neutral-muted/20 text-neutral-muted',
  Anulada: 'bg-red-400/20 text-red-400',
}

function AdminFacturasPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-facturas', page],
    queryFn: () => facturaService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Facturas</span>
      </h1>
      <AdminTabs />

      {isLoading && <p className="text-neutral-muted">Cargando facturas…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar las facturas.</p>}

      {data && data.content.length === 0 && (
        <p className="text-neutral-muted">Todavía no se emitió ninguna factura.</p>
      )}

      {data && data.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>Factura</th>
                <th>Tipo</th>
                <th>Pedido</th>
                <th>Fecha</th>
                <th>Subtotal</th>
                <th>IVA</th>
                <th>Total</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((factura) => (
                <tr key={factura.id}>
                  <td className="font-mono font-semibold text-neutral-text">{factura.numero}</td>
                  <td className="text-neutral-secondary">{factura.tipo}</td>
                  <td>N.º {factura.pedidoNumero}</td>
                  <td>{new Date(factura.fecha).toLocaleString('es-PY')}</td>
                  <td>{formatPrecio(factura.subtotal)}</td>
                  <td>{formatPrecio(factura.iva)}</td>
                  <td className="font-semibold text-primary-300">{formatPrecio(factura.total)}</td>
                  <td>
                    <span
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        ESTILOS_FACTURA[factura.estado] ?? 'bg-dark-lighter text-neutral-secondary'
                      }`}
                    >
                      {factura.estado}
                    </span>
                  </td>
                </tr>
              ))}
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
        Los montos muestran el IVA incluido desglosado por tasa de cada producto al momento del cobro.
      </p>
    </div>
  )
}

export default AdminFacturasPage
