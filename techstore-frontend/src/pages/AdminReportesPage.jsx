import { useQuery } from '@tanstack/react-query'
import { reporteService } from '../services/reporteService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'

function StatTile({ etiqueta, valor, detalle }) {
  return (
    <div className="rounded-lg border-2 border-gold bg-white p-6 shadow-md">
      <p className="text-sm font-medium text-gray-500">{etiqueta}</p>
      <p className="mt-1 text-3xl font-bold text-navy">{valor}</p>
      {detalle && <p className="mt-1 text-sm text-gray-500">{detalle}</p>}
    </div>
  )
}

function AdminReportesPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['reporte-ventas'],
    queryFn: () => reporteService.ventas(5),
  })

  const maxUnidades = data?.topProductos?.length
    ? Math.max(...data.topProductos.map((p) => p.unidadesVendidas))
    : 0

  return (
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-3xl font-bold text-navy">Panel de administración — Reportes</h2>
      <AdminTabs />

      {isLoading && <p className="text-gray-500">Cargando reporte…</p>}
      {isError && <p className="text-red-500">No se pudo cargar el reporte de ventas.</p>}

      {data && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2">
            <StatTile
              etiqueta="Total facturado"
              valor={formatPrecio(data.totalFacturado)}
              detalle="Suma de facturas pagadas"
            />
            <StatTile
              etiqueta="Facturas emitidas"
              valor={data.cantidadFacturas}
              detalle="Facturas en estado Pagada"
            />
          </div>

          <div className="mb-8 rounded-lg border-2 border-gold bg-white p-6 shadow-md">
            <h3 className="mb-4 text-lg font-bold text-navy">Pedidos por estado</h3>
            {data.pedidosPorEstado.length === 0 ? (
              <p className="text-gray-500">Aún no hay pedidos.</p>
            ) : (
              <div className="flex flex-wrap gap-3">
                {data.pedidosPorEstado.map((fila) => {
                  const estado = estadoPedido(fila.estado)
                  return (
                    <span
                      key={fila.estado}
                      className={`rounded px-3 py-2 text-sm font-semibold ${estado.clase}`}
                    >
                      {estado.label}: {fila.cantidad}
                    </span>
                  )
                })}
              </div>
            )}
          </div>

          <div className="rounded-lg border-2 border-gold bg-white p-6 shadow-md">
            <h3 className="mb-4 text-lg font-bold text-navy">Productos más vendidos</h3>
            {data.topProductos.length === 0 ? (
              <p className="text-gray-500">Todavía no hay ventas registradas.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead>
                    <tr className="border-b text-sm text-gray-500">
                      <th className="p-2 font-medium">Producto</th>
                      <th className="p-2 font-medium">Unidades</th>
                      <th className="w-1/3 p-2"></th>
                      <th className="p-2 text-right font-medium">Monto</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.topProductos.map((producto) => (
                      <tr key={producto.productoId} className="border-b last:border-0">
                        <td className="p-2 font-medium text-navy">{producto.nombre}</td>
                        <td className="p-2">{producto.unidadesVendidas}</td>
                        <td className="p-2">
                          <div
                            className="h-3 rounded bg-gold"
                            style={{
                              width: `${maxUnidades ? (producto.unidadesVendidas / maxUnidades) * 100 : 0}%`,
                              minWidth: '4px',
                            }}
                            aria-hidden="true"
                          />
                        </td>
                        <td className="p-2 text-right font-semibold">
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
