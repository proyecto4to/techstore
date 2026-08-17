import { useMutation } from '@tanstack/react-query'
import { Scale, Check, AlertCircle } from 'lucide-react'
import { compraService } from '../../services/compraService'
import { formatPrecio } from '../../utils/format'

/**
 * Compara qué proveedor conviene para los productos que ya se cargaron en la
 * compra. No elige: ordena la información y la decisión queda en quien compra,
 * que suele saber cosas que el sistema no (una deuda, la calidad de una marca).
 *
 * @param items    los que ya están en la compra: [{ producto, cantidad }]
 * @param onElegir se llama con el proveedorId cuando se toca "Usar este"
 */
function ComparadorProveedores({ items, onElegir }) {
  const comparar = useMutation({
    mutationFn: () =>
      compraService.comparar(
        items.map((i) => ({ productoId: i.producto.id, cantidad: i.cantidad })),
      ),
  })

  if (items.length === 0) {
    return (
      <p className="text-sm text-neutral-muted">
        Agregá productos a la compra y después compará qué proveedor conviene.
      </p>
    )
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => comparar.mutate()}
        disabled={comparar.isPending}
        className="btn btn-secondary inline-flex items-center gap-2 text-sm"
      >
        <Scale size={16} />
        {comparar.isPending ? 'Comparando…' : 'Comparar proveedores'}
      </button>

      {comparar.isError && (
        <p className="alerta-error mt-3" role="alert">
          No se pudo comparar. Probá de nuevo.
        </p>
      )}

      {comparar.data && comparar.data.length === 0 && (
        <p className="mt-3 text-sm text-neutral-secondary">
          Ningún proveedor tiene cargados estos productos todavía. Se van cargando solos a
          medida que recibís compras, o podés anotar una cotización desde el proveedor.
        </p>
      )}

      {comparar.data && comparar.data.length > 0 && (
        <div className="mt-4 space-y-3">
          {comparar.data.map((p) => (
            <div
              key={`${p.proveedorId}-${p.moneda}`}
              className={`rounded-md border p-3 ${
                p.coberturaCompleta ? 'border-dark-border' : 'border-yellow-400/40'
              }`}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-neutral-text">{p.razonSocial}</p>
                  <p className="mt-1 text-sm">
                    {p.coberturaCompleta ? (
                      <span className="inline-flex items-center gap-1 text-emerald-400">
                        <Check size={14} /> Tiene todo
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-yellow-400">
                        <AlertCircle size={14} /> No tiene: {p.faltantes.join(', ')}
                      </span>
                    )}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xl font-bold text-primary-300">
                    {formatPrecio(p.total, p.moneda)}
                  </p>
                  <p className="text-xs text-neutral-muted">
                    por {p.cubre.length} producto{p.cubre.length === 1 ? '' : 's'}
                  </p>
                </div>
              </div>

              <div className="mt-2 flex flex-wrap gap-4 text-xs text-neutral-secondary">
                <span>
                  Entrega:{' '}
                  {p.diasEntregaPromedio === null || p.diasEntregaPromedio === undefined ? (
                    <span className="text-neutral-muted">sin datos</span>
                  ) : (
                    `~${Math.round(p.diasEntregaPromedio)} días`
                  )}
                </span>
                {/* La fiabilidad va con cuántas entregas la respaldan: un 100%
                    sobre una compra no dice lo mismo que sobre cuarenta. */}
                <span>
                  Cumplimiento:{' '}
                  {p.fiabilidad === null || p.fiabilidad === undefined ? (
                    <span className="text-neutral-muted">nunca le compramos</span>
                  ) : (
                    <>
                      <span className={p.fiabilidad >= 90 ? 'text-emerald-400' : 'text-yellow-400'}>
                        {Math.round(p.fiabilidad)}%
                      </span>{' '}
                      <span className="text-neutral-muted">
                        ({p.comprasRecibidas} entrega{p.comprasRecibidas === 1 ? '' : 's'})
                      </span>
                    </>
                  )}
                </span>
              </div>

              {onElegir && (
                <button
                  type="button"
                  onClick={() => onElegir(p.proveedorId)}
                  className="btn btn-secondary mt-3 text-xs"
                >
                  Usar este proveedor
                </button>
              )}
            </div>
          ))}
          <p className="text-xs text-neutral-muted">
            Ordenados por cobertura, moneda y precio. Los importes de monedas distintas se muestran
            separados porque no se pueden comparar sin una cotización. La entrega y el cumplimiento
            se muestran pero no ordenan: cuánto pesan lo decidís vos.
          </p>
        </div>
      )}
    </div>
  )
}

export default ComparadorProveedores
