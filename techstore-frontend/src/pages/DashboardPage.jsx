import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { TrendingUp, ClipboardList, Wallet, AlertTriangle, ArrowRight } from 'lucide-react'
import { dashboardService } from '../services/dashboardService'
import { useAuthStore } from '../store/authStore'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'

function Tarjeta({ icono, etiqueta, valor, detalle, acento, to, accion }) {
  return (
    <div className="card card-hover relative overflow-hidden">
      <div className={`absolute inset-x-0 top-0 h-[3px] ${acento}`} />
      <div className="flex items-start justify-between">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-neutral-muted">
            {etiqueta}
          </p>
          <p className="text-3xl font-bold text-neutral-text">{valor}</p>
          {detalle && <p className="mt-1 text-sm text-neutral-secondary">{detalle}</p>}
        </div>
        <div className="text-2xl opacity-70">{icono}</div>
      </div>
      {to && (
        <Link to={to} className="mt-3 inline-flex items-center gap-1 text-sm text-primary-300 hover:text-accent-400">
          {accion} <ArrowRight size={14} />
        </Link>
      )}
    </div>
  )
}

function DashboardPage() {
  const { usuario } = useAuthStore()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardService.resumen(),
    refetchInterval: 60_000,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-2 text-4xl font-bold text-neutral-text">
        Hola, <span className="gradient-text">{usuario?.nombre}</span>
      </h1>
      <p className="mb-6 text-neutral-secondary">Resumen del negocio hoy</p>
      <AdminTabs />

      {isLoading && <p className="text-neutral-muted">Cargando indicadores…</p>}
      {isError && <p className="alerta-error">No se pudo cargar el resumen.</p>}

      {data && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
            <Tarjeta
              icono={<TrendingUp size={22} className="text-emerald-400" />}
              etiqueta="Ventas de hoy"
              valor={formatPrecio(data.ventasHoy)}
              detalle={`Este mes: ${formatPrecio(data.ventasMes)} · ${data.facturasMes} factura${data.facturasMes === 1 ? '' : 's'}`}
              acento="bg-emerald-400"
              to="/admin/reportes"
              accion="Ver reportes"
            />
            <Tarjeta
              icono={<ClipboardList size={22} className="text-yellow-400" />}
              etiqueta="Por atender"
              valor={data.pedidosPendientes + data.pedidosPorCobrar}
              detalle={`${data.pedidosPendientes} por confirmar · ${data.pedidosPorCobrar} por cobrar`}
              acento="bg-yellow-400"
              to="/admin"
              accion="Ir a pedidos"
            />
            <Tarjeta
              icono={<Wallet size={22} className="text-accent-400" />}
              etiqueta="Efectivo en caja (turno)"
              valor={formatPrecio(data.efectivoTurno)}
              detalle={`Transferencias: ${formatPrecio(data.transferenciasTurno)}`}
              acento="bg-accent-400"
              to="/admin/caja"
              accion="Ir a caja"
            />
            <Tarjeta
              icono={<AlertTriangle size={22} className="text-red-400" />}
              etiqueta="Stock bajo"
              valor={data.stockBajoCount}
              detalle={
                data.stockBajoCount === 0
                  ? 'Todo con stock'
                  : data.sinCoberturaCount > 0
                    ? `${data.sinCoberturaCount} ya sin cobertura`
                    : 'productos necesitan reposición'
              }
              acento="bg-red-400"
              to="/admin/compras"
              accion="Registrar compra"
            />
          </div>

          {data.stockBajo.length > 0 && (
            <div className="card">
              <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
                <AlertTriangle size={18} className="text-yellow-400" /> Productos a reponer
              </h3>
              <div className="overflow-x-auto">
                <table className="tabla-admin">
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Marca</th>
                      <th>Disponible</th>
                      <th>Alcanza para</th>
                      <th>Reponer en</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.stockBajo.map((p) => (
                      <tr key={p.productoId}>
                        <td className="font-medium text-neutral-text">{p.nombre}</td>
                        <td className="text-neutral-secondary">{p.marca ?? '—'}</td>
                        <td>
                          <span
                            className={`rounded-full px-3 py-1 text-xs font-semibold ${
                              p.stockDisponible === 0
                                ? 'bg-red-400/20 text-red-400'
                                : 'bg-yellow-400/20 text-yellow-400'
                            }`}
                          >
                            {p.stockDisponible} {p.stockDisponible === 0 ? '— agotado' : ''}
                          </span>
                        </td>
                        {/* Los días dicen más que las unidades: "quedan 3 días"
                            se entiende sin saber cuánto se vende. */}
                        <td className="text-neutral-secondary">
                          {p.diasDeCobertura === null || p.diasDeCobertura === undefined ? (
                            <span className="text-neutral-muted">sin datos de venta</span>
                          ) : p.diasDeCobertura <= 0 ? (
                            <span className="font-semibold text-red-400">se agotó</span>
                          ) : (
                            `${p.diasDeCobertura} día${p.diasDeCobertura === 1 ? '' : 's'}`
                          )}
                        </td>
                        <td className="text-neutral-secondary">
                          {p.umbral}
                          {!p.umbralCalculado && (
                            <span className="ml-1 text-xs text-neutral-muted" title="Mínimo cargado a mano: todavía no hay ventas para calcularlo">
                              (manual)
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {data.topProductosMes.length > 0 && (
            <div className="card mt-6">
              <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
                <TrendingUp size={18} className="text-emerald-400" /> Más vendidos del mes
              </h3>
              <div className="overflow-x-auto">
                <table className="tabla-admin">
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Unidades</th>
                      <th>Facturado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.topProductosMes.map((p) => (
                      <tr key={p.productoId}>
                        <td className="font-medium text-neutral-text">{p.nombre}</td>
                        <td className="text-neutral-secondary">{p.unidadesVendidas}</td>
                        <td className="text-primary-300">{formatPrecio(p.montoVendido)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default DashboardPage
