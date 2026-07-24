import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { notaCreditoService } from '../../services/facturaService'
import { formatPrecio } from '../../utils/format'

/**
 * Panel de devolución de un pedido: para cada producto muestra cuánto se
 * compró, cuánto ya se devolvió en notas de crédito previas y cuánto queda
 * disponible. Solo permite devolver hasta lo que realmente falta devolver.
 */
function DevolucionPanel({ pedidoId, onEmitir, onCancelar, ocupado }) {
  const [motivo, setMotivo] = useState('')
  const [cantidades, setCantidades] = useState({})

  const { data: lineas, isLoading, isError } = useQuery({
    queryKey: ['devolucion-disponible', pedidoId],
    queryFn: () => notaCreditoService.disponible(pedidoId),
  })

  if (isLoading) return <p className="p-4 text-sm text-neutral-muted">Cargando productos del pedido…</p>
  if (isError) return <p className="p-4 text-sm text-red-400">No se pudo cargar el pedido.</p>

  const setCantidad = (productoId, max, valor) => {
    const n = Math.max(0, Math.min(max, Number(valor) || 0))
    setCantidades((prev) => ({ ...prev, [productoId]: n }))
  }

  const items = lineas
    .map((l) => ({ productoId: l.productoId, cantidad: cantidades[l.productoId] ?? 0 }))
    .filter((i) => i.cantidad > 0)

  const totalDevolver = lineas.reduce((acc, l) => {
    const cant = cantidades[l.productoId] ?? 0
    return acc + l.precioUnitario * cant
  }, 0)

  const nadaPorDevolver = lineas.every((l) => l.disponible === 0)

  const emitir = () => onEmitir({ motivo: motivo.trim(), items })

  return (
    <div className="rounded-md border border-dark-border bg-dark-bg/50 p-4">
      <p className="mb-3 text-sm text-neutral-secondary">
        Elegí cuántas unidades devolver de cada producto (solo se ofrece lo que queda por devolver):
      </p>

      <div className="mb-3 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-dark-border text-neutral-muted">
              <th className="p-2 font-medium">Producto</th>
              <th className="p-2 font-medium">Comprado</th>
              <th className="p-2 font-medium">Ya devuelto</th>
              <th className="p-2 font-medium">Disponible</th>
              <th className="p-2 font-medium">Precio</th>
              <th className="p-2 font-medium">Devolver</th>
            </tr>
          </thead>
          <tbody>
            {lineas.map((l) => {
              const agotado = l.disponible === 0
              return (
                <tr key={l.productoId} className="border-b border-dark-border/50 last:border-0">
                  <td className="p-2 text-neutral-text">{l.productoNombre}</td>
                  <td className="p-2">{l.comprado}</td>
                  <td className="p-2 text-orange-400">{l.devuelto}</td>
                  <td className="p-2 font-semibold text-neutral-text">{l.disponible}</td>
                  <td className="p-2">{formatPrecio(l.precioUnitario)}</td>
                  <td className="p-2">
                    {agotado ? (
                      <span className="text-xs text-emerald-400">Devuelto ✓</span>
                    ) : (
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min="0"
                          max={l.disponible}
                          value={cantidades[l.productoId] ?? 0}
                          onChange={(e) => setCantidad(l.productoId, l.disponible, e.target.value)}
                          className="input w-20 py-1 text-sm"
                        />
                        <button
                          type="button"
                          onClick={() => setCantidad(l.productoId, l.disponible, l.disponible)}
                          className="text-xs text-primary-300 hover:text-accent-400"
                        >
                          todo
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {nadaPorDevolver ? (
        <p className="mb-3 text-sm text-emerald-400">
          Este pedido ya fue devuelto por completo: no queda nada por devolver.
        </p>
      ) : (
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
      )}

      <div className="flex flex-wrap items-center gap-4">
        <p className="text-sm font-semibold text-orange-400">
          A devolver: {formatPrecio(totalDevolver)}
        </p>
        <button
          onClick={emitir}
          disabled={items.length === 0 || !motivo.trim() || ocupado}
          title={
            items.length === 0
              ? 'Elegí al menos una unidad a devolver'
              : !motivo.trim()
                ? 'Escribí el motivo de la devolución'
                : undefined
          }
          className="btn btn-danger text-sm disabled:cursor-not-allowed"
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
