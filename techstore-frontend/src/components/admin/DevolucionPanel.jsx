import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { pedidoService } from '../../services/pedidoService'
import { formatPrecio } from '../../utils/format'

/**
 * Panel de devolución de un pedido: permite elegir qué productos y
 * cuántas unidades devolver (total o parcial) antes de emitir la NC.
 */
function DevolucionPanel({ pedidoId, onEmitir, onCancelar, ocupado }) {
  const [motivo, setMotivo] = useState('')
  const [cantidades, setCantidades] = useState({})

  const { data: pedido, isLoading, isError } = useQuery({
    queryKey: ['admin-pedido', pedidoId],
    queryFn: () => pedidoService.obtener(pedidoId),
  })

  if (isLoading) return <p className="p-4 text-sm text-neutral-muted">Cargando productos del pedido…</p>
  if (isError) return <p className="p-4 text-sm text-red-400">No se pudo cargar el pedido.</p>

  const setCantidad = (productoId, max, valor) => {
    const n = Math.max(0, Math.min(max, Number(valor) || 0))
    setCantidades((prev) => ({ ...prev, [productoId]: n }))
  }

  const items = pedido.detalles
    .map((d) => ({ productoId: d.productoId, cantidad: cantidades[d.productoId] ?? 0 }))
    .filter((i) => i.cantidad > 0)

  const totalDevolver = pedido.detalles.reduce((acc, d) => {
    const cant = cantidades[d.productoId] ?? 0
    return acc + d.precioUnitario * cant
  }, 0)

  const emitir = () => onEmitir({ motivo: motivo.trim(), items })

  return (
    <div className="rounded-md border border-dark-border bg-dark-bg/50 p-4">
      <p className="mb-3 text-sm text-neutral-secondary">
        Elegí qué productos y cuántas unidades devolver (dejá en 0 lo que se queda el cliente):
      </p>

      <div className="mb-3 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-dark-border text-neutral-muted">
              <th className="p-2 font-medium">Producto</th>
              <th className="p-2 font-medium">Comprado</th>
              <th className="p-2 font-medium">Precio</th>
              <th className="p-2 font-medium">Devolver</th>
            </tr>
          </thead>
          <tbody>
            {pedido.detalles.map((d) => (
              <tr key={d.productoId} className="border-b border-dark-border/50 last:border-0">
                <td className="p-2 text-neutral-text">{d.productoNombre}</td>
                <td className="p-2">{d.cantidad}</td>
                <td className="p-2">{formatPrecio(d.precioUnitario)}</td>
                <td className="p-2">
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min="0"
                      max={d.cantidad}
                      value={cantidades[d.productoId] ?? 0}
                      onChange={(e) => setCantidad(d.productoId, d.cantidad, e.target.value)}
                      className="input w-20 py-1 text-sm"
                    />
                    <button
                      type="button"
                      onClick={() => setCantidad(d.productoId, d.cantidad, d.cantidad)}
                      className="text-xs text-primary-300 hover:text-accent-400"
                    >
                      todo
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div className="flex-1">
          <label className="mb-1 block text-xs font-medium text-neutral-text">Motivo *</label>
          <input
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            maxLength={255}
            placeholder="Ej.: producto con falla de fábrica"
            className="input w-full py-1 text-sm"
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <p className="text-sm font-semibold text-orange-400">
          A devolver: {formatPrecio(totalDevolver)}
        </p>
        <button
          onClick={emitir}
          disabled={items.length === 0 || !motivo.trim() || ocupado}
          className="btn btn-danger text-sm"
        >
          Emitir nota de crédito
        </button>
        <button onClick={onCancelar} className="btn btn-secondary text-sm">
          Cancelar
        </button>
      </div>
    </div>
  )
}

export default DevolucionPanel
