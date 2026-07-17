import { useQuery } from '@tanstack/react-query'
import { reporteService } from '../services/reporteService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'
import StatCard from '../components/common/StatCard'

function AdminReportesPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['reporte-ventas'],
    queryFn: () => reporteService.ventas(5),
  })

  const maxUnidades = data?.topProductos?.length
    ? Math.max(...data.topProductos.map((p) => p.unidadesVendidas))
    : 0

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Reportes</span>
      </h1>
      <AdminTabs />

      {isLoading && <p className="text-neutral-muted">Cargando reporte…</p>}
      {isError && <p className="alerta-error">No se pudo cargar el reporte de ventas.</p>}

      {data && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2">
            <StatCard
              etiqueta="Total facturado"
              valor={formatPrecio(data.totalFacturado)}
              detalle="Suma de facturas pagadas"
              icono="💰"
            />
            <StatCard
              etiqueta="Facturas emitidas"
              valor={data.cantidadFacturas}
              detalle="Facturas en estado Pagada"
              icono="🧾"
            />
          </div>

          <div className="card mb-8">
            <h3 className="mb-4 text-lg font-semibold text-neutral-text">Pedidos por estado</h3>
            {data.pedidosPorEstado.length === 0 ? (
              <p className="text-neutral-muted">Aún no hay pedidos.</p>
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

          <div className="card">
            <h3 className="mb-4 text-lg font-semibold text-neutral-text">
              Productos más vendidos
            </h3>
            {data.topProductos.length === 0 ? (
              <p className="text-neutral-muted">Todavía no hay ventas registradas.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="tabla-admin">
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Unidades</th>
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
                          <div
                            className="h-3 rounded bg-gradient-to-r from-primary-400 to-accent-400"
                            style={{
                              width: `${maxUnidades ? (producto.unidadesVendidas / maxUnidades) * 100 : 0}%`,
                              minWidth: '4px',
                            }}
                            aria-hidden="true"
                          />
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
        </>
      )}
    </div>
  )
}

export default AdminReportesPage
