import { useQuery } from '@tanstack/react-query'
import { pedidoService } from '../../services/pedidoService'
import { formatPrecio } from '../../utils/format'
import AsignacionRepartoPanel from './AsignacionRepartoPanel'

/**
 * Panel expandible con los items de un pedido (para las tablas del admin).
 */
function PedidoDetallePanel({ pedidoId }) {
  const { data: pedido, isLoading, isError } = useQuery({
    queryKey: ['admin-pedido', pedidoId],
    queryFn: () => pedidoService.obtener(pedidoId),
  })

  if (isLoading) return <p className="p-4 text-sm text-neutral-muted">Cargando detalle…</p>
  if (isError) return <p className="p-4 text-sm text-red-400">No se pudo cargar el detalle.</p>

  return (
    <div className="rounded-md border border-dark-border bg-dark-bg/50 p-4">
      <div className="mb-3 flex flex-wrap items-center gap-4 text-sm text-neutral-secondary">
        <span>
          Pedido <strong className="text-neutral-text">N.º {pedido.numero}</strong>
        </span>
        <span>Cliente: {pedido.usuarioEmail}</span>
        {pedido.notas && <span>Notas: “{pedido.notas}”</span>}
      </div>
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-dark-border text-neutral-muted">
            <th className="p-2 font-medium">Producto</th>
            <th className="p-2 font-medium">Cantidad</th>
            <th className="p-2 font-medium">Precio unit.</th>
            <th className="p-2 font-medium">IVA</th>
            <th className="p-2 text-right font-medium">Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {pedido.detalles.map((detalle) => (
            <tr key={detalle.productoId} className="border-b border-dark-border/50 last:border-0">
              <td className="p-2 text-neutral-text">{detalle.productoNombre}</td>
              <td className="p-2">{detalle.cantidad}</td>
              <td className="p-2">{formatPrecio(detalle.precioUnitario)}</td>
              <td className="p-2">{detalle.ivaAplicado}%</td>
              <td className="p-2 text-right font-semibold text-primary-300">
                {formatPrecio(detalle.subtotal)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={4} className="p-2 text-right font-semibold text-neutral-text">
              Total
            </td>
            <td className="p-2 text-right text-base font-bold text-primary-300">
              {formatPrecio(pedido.total)}
            </td>
          </tr>
        </tfoot>
      </table>
      <AsignacionRepartoPanel pedido={pedido} />
    </div>
  )
}

export default PedidoDetallePanel
