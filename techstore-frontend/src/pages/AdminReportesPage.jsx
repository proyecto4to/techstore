import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { AlertTriangle, FileText } from 'lucide-react'
import { reporteService } from '../services/reporteService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'
import StatCard from '../components/common/StatCard'

const hoy = () => new Date().toISOString().slice(0, 10)
const haceDias = (dias) => {
  const fecha = new Date()
  fecha.setDate(fecha.getDate() - dias)
  return fecha.toISOString().slice(0, 10)
}

function BarraMagnitud({ valor, maximo }) {
  return (
    <div
      className="h-3 rounded bg-gradient-to-r from-primary-400 to-accent-400"
      style={{ width: `${maximo ? (valor / maximo) * 100 : 0}%`, minWidth: '4px' }}
      aria-hidden="true"
    />
  )
}

function AdminReportesPage() {
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [umbral, setUmbral] = useState(5)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reporte-ventas', desde, hasta],
    queryFn: () => reporteService.ventas({ desde: desde || null, hasta: hasta || null }),
    placeholderData: keepPreviousData,
  })

  const { data: stockBajo } = useQuery({
    queryKey: ['stock-bajo', umbral],
    queryFn: () => reporteService.stockBajo(umbral),
    placeholderData: keepPreviousData,
  })

  const rapido = (dias) => {
    setDesde(dias === null ? '' : haceDias(dias))
    setHasta(dias === null ? '' : hoy())
  }

  const maxDia = data?.ventasPorDia?.length
    ? Math.max(...data.ventasPorDia.map((d) => d.total))
    : 0
  const maxUnidades = data?.topProductos?.length
    ? Math.max(...data.topProductos.map((p) => p.unidadesVendidas))
    : 0
  const maxCliente = data?.topClientes?.length
    ? Math.max(...data.topClientes.map((c) => c.monto))
    : 0

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Reportes</span>
      </h1>
      <AdminTabs />

      <div className="card mb-8 flex flex-wrap items-end gap-4">
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="desde">
            Desde
          </label>
          <input id="desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="hasta">
            Hasta
          </label>
          <input id="hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} className="input" />
        </div>
        <div className="flex gap-2">
          <button onClick={() => rapido(0)} className="btn btn-secondary text-sm">Hoy</button>
          <button onClick={() => rapido(7)} className="btn btn-secondary text-sm">7 días</button>
          <button onClick={() => rapido(30)} className="btn btn-secondary text-sm">30 días</button>
          <button onClick={() => rapido(null)} className="btn btn-secondary text-sm">Todo</button>
        </div>
        <button
          onClick={() => reporteService.ventasPdf({ desde: desde || null, hasta: hasta || null })}
          className="btn btn-primary inline-flex items-center gap-2 text-sm"
        >
          <FileText size={16} /> Exportar PDF
        </button>
        {data && (
          <p className="ml-auto text-sm text-neutral-muted">
            Mostrando {data.desde === '2000-01-01' ? 'todo el historial' : `${data.desde} → ${data.hasta}`}
          </p>
        )}
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando reporte…</p>}
      {isError && <p className="alerta-error">No se pudo cargar el reporte de ventas.</p>}

      {data && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2">
            <StatCard
              etiqueta="Total facturado"
              valor={formatPrecio(data.totalFacturado)}
              detalle="Suma de facturas pagadas en el período"
              icono="💰"
            />
            <StatCard
              etiqueta="Facturas emitidas"
              valor={data.cantidadFacturas}
              detalle="Facturas en estado Pagada en el período"
              icono="🧾"
            />
          </div>

          <div className="card mb-8">
            <h3 className="mb-4 text-lg font-semibold text-neutral-text">Ventas por día</h3>
            {data.ventasPorDia.length === 0 ? (
              <p className="text-neutral-muted">Sin facturación en el período elegido.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="tabla-admin">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Facturas</th>
                      <th className="w-1/2"></th>
                      <th className="text-right">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.ventasPorDia.map((dia) => (
                      <tr key={dia.fecha}>
                        <td className="text-neutral-text">
                          {new Date(`${dia.fecha}T00:00:00`).toLocaleDateString('es-PY')}
                        </td>
                        <td>{dia.cantidadFacturas}</td>
                        <td>
                          <BarraMagnitud valor={dia.total} maximo={maxDia} />
                        </td>
                        <td className="text-right font-semibold text-primary-300">
                          {formatPrecio(dia.total)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="card mb-8">
            <h3 className="mb-4 text-lg font-semibold text-neutral-text">Pedidos por estado</h3>
            {data.pedidosPorEstado.length === 0 ? (
              <p className="text-neutral-muted">Sin pedidos en el período.</p>
            ) : (
              <div className="flex flex-wrap gap-3">
                {data.pedidosPorEstado.map((fila) => {
                  const estado = estadoPedido(fila.estado)
                  return (
                    <span
                      key={fila.estado}
                      className={`rounded-full px-4 py-2 text-sm font-semibold ${estado.clase}`}
                    >
                      {estado.label}: {fila.cantidad}
                    </span>
                  )
                })}
              </div>
            )}
          </div>

          <div className="mb-8 grid grid-cols-1 gap-8 xl:grid-cols-2">
            <div className="card">
              <h3 className="mb-4 text-lg font-semibold text-neutral-text">Productos más vendidos</h3>
              {data.topProductos.length === 0 ? (
                <p className="text-neutral-muted">Sin ventas en el período.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="tabla-admin">
                    <thead>
                      <tr>
                        <th>Producto</th>
                        <th>Unid.</th>
                        <th className="w-1/3"></th>
                        <th className="text-right">Monto</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.topProductos.map((producto) => (
                        <tr key={producto.productoId}>
                          <td className="font-medium text-neutral-text">{producto.nombre}</td>
                          <td>{producto.unidadesVendidas}</td>
                          <td>
                            <BarraMagnitud valor={producto.unidadesVendidas} maximo={maxUnidades} />
                          </td>
                          <td className="text-right font-semibold text-primary-300">
                            {formatPrecio(producto.montoVendido)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="card">
              <h3 className="mb-4 text-lg font-semibold text-neutral-text">Mejores clientes</h3>
              {data.topClientes.length === 0 ? (
                <p className="text-neutral-muted">Sin compras en el período.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="tabla-admin">
                    <thead>
                      <tr>
                        <th>Cliente</th>
                        <th>Pedidos</th>
                        <th className="w-1/3"></th>
                        <th className="text-right">Monto</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.topClientes.map((cliente) => (
                        <tr key={cliente.email}>
                          <td>
                            <p className="font-medium text-neutral-text">{cliente.nombre}</p>
                            <p className="text-xs text-neutral-muted">{cliente.email}</p>
                          </td>
                          <td>{cliente.pedidos}</td>
                          <td>
                            <BarraMagnitud valor={cliente.monto} maximo={maxCliente} />
                          </td>
                          <td className="text-right font-semibold text-primary-300">
                            {formatPrecio(cliente.monto)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      <div className="card">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
          <h3 className="inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
            <AlertTriangle size={18} className="text-yellow-400" /> Stock bajo
          </h3>
          <div className="flex flex-wrap items-center gap-3">
            <label className="inline-flex items-center gap-2 text-sm text-neutral-secondary">
              Umbral:
              <input
                type="number"
                min="0"
                max="1000"
                value={umbral}
                onChange={(e) => setUmbral(Math.max(0, Number(e.target.value)))}
                className="input w-24 py-1"
              />
            </label>
            <button
              onClick={() => reporteService.stockBajoPdf(umbral)}
              className="btn btn-secondary inline-flex items-center gap-2 text-sm"
            >
              <FileText size={16} /> PDF
            </button>
          </div>
        </div>
        {!stockBajo || stockBajo.length === 0 ? (
          <p className="text-neutral-muted">
            Ningún producto activo con stock disponible ≤ {umbral}. 👌
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Marca</th>
                  <th>Stock real</th>
                  <th>Comprometido</th>
                  <th>Disponible</th>
                </tr>
              </thead>
              <tbody>
                {stockBajo.map((producto) => (
                  <tr key={producto.productoId}>
                    <td className="font-medium text-neutral-text">{producto.nombre}</td>
                    <td className="text-neutral-secondary">{producto.marca ?? '—'}</td>
                    <td>{producto.stockReal}</td>
                    <td>{producto.stockComprometido}</td>
                    <td>
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-semibold ${
                          producto.stockDisponible === 0
                            ? 'bg-red-400/20 text-red-400'
                            : 'bg-yellow-400/20 text-yellow-400'
                        }`}
                      >
                        {producto.stockDisponible} {producto.stockDisponible === 0 ? '— agotado' : ''}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

export default AdminReportesPage
