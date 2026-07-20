import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { FileText, QrCode, FileCode } from 'lucide-react'
import { facturaService, notaCreditoService } from '../services/facturaService'
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
  const [errorPdf, setErrorPdf] = useState(null)
  const [abriendo, setAbriendo] = useState(null)

  const verPdf = async (factura) => {
    setErrorPdf(null)
    setAbriendo(factura.id)
    try {
      await facturaService.abrirPdf(factura.id)
    } catch {
      setErrorPdf(`No se pudo generar el PDF de la factura ${factura.numero}.`)
    } finally {
      setAbriendo(null)
    }
  }

  const verKude = async (factura) => {
    setErrorPdf(null)
    try {
      await facturaService.abrirKude(factura.id)
    } catch {
      setErrorPdf(`No se pudo generar el KuDE de la factura ${factura.numero}.`)
    }
  }

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-facturas', page],
    queryFn: () => facturaService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const { data: notasCredito } = useQuery({
    queryKey: ['admin-notas-credito'],
    queryFn: () => notaCreditoService.listar({ size: 20 }),
  })

  const verNcPdf = async (nc) => {
    setErrorPdf(null)
    try {
      await notaCreditoService.abrirPdf(nc.id)
    } catch {
      setErrorPdf(`No se pudo generar el PDF de la nota de crédito ${nc.numero}.`)
    }
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Facturas</span>
      </h1>
      <AdminTabs />

      {isLoading && <p className="text-neutral-muted">Cargando facturas…</p>}
      {isError && <p className="alerta-error">No se pudieron cargar las facturas.</p>}
      {errorPdf && (
        <p className="alerta-error" role="alert">
          {errorPdf}
        </p>
      )}

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
                <th></th>
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
                  <td>
                    <div className="flex flex-wrap gap-2">
                      <button
                        onClick={() => verPdf(factura)}
                        disabled={abriendo === factura.id}
                        className="btn btn-secondary inline-flex items-center gap-2 text-sm"
                        aria-label={`Ver PDF de la factura ${factura.numero}`}
                      >
                        <FileText size={16} />
                        {abriendo === factura.id ? 'Generando…' : 'PDF'}
                      </button>
                      <button
                        onClick={() => verKude(factura)}
                        className="btn btn-secondary inline-flex items-center gap-2 text-sm"
                        aria-label={`KuDE electrónico de la factura ${factura.numero}`}
                      >
                        <QrCode size={16} /> KuDE
                      </button>
                      <button
                        onClick={() => facturaService.descargarXml(factura.id, factura.numero)}
                        className="btn btn-secondary inline-flex items-center gap-2 text-sm"
                        aria-label={`XML del documento electrónico de la factura ${factura.numero}`}
                      >
                        <FileCode size={16} /> XML
                      </button>
                    </div>
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

      <div className="card mt-8">
        <h3 className="mb-4 text-lg font-semibold text-neutral-text">Notas de crédito</h3>
        {!notasCredito || notasCredito.content.length === 0 ? (
          <p className="text-neutral-muted">
            No hay notas de crédito. Se emiten desde la pestaña Pedidos con el botón «Devolver».
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th>N.º NC</th>
                  <th>Factura afectada</th>
                  <th>Pedido</th>
                  <th>Motivo</th>
                  <th>Fecha</th>
                  <th className="text-right">Total</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {notasCredito.content.map((nc) => (
                  <tr key={nc.id}>
                    <td className="font-mono font-semibold text-neutral-text">{nc.numero}</td>
                    <td className="font-mono text-neutral-secondary">{nc.facturaNumero}</td>
                    <td>N.º {nc.pedidoNumero}</td>
                    <td className="text-neutral-secondary">{nc.motivo}</td>
                    <td>{new Date(nc.fecha).toLocaleString('es-PY')}</td>
                    <td className="text-right font-semibold text-orange-400">
                      − {formatPrecio(nc.total)}
                    </td>
                    <td>
                      <button
                        onClick={() => verNcPdf(nc)}
                        className="btn btn-secondary inline-flex items-center gap-2 text-sm"
                        aria-label={`Ver PDF de la nota de crédito ${nc.numero}`}
                      >
                        <FileText size={16} /> PDF
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <p className="mt-6 text-sm text-neutral-muted">
        Los montos muestran el IVA incluido desglosado por tasa de cada producto al momento del cobro.
      </p>
    </div>
  )
}

export default AdminFacturasPage
