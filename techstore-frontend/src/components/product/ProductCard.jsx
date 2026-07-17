import { useState } from 'react'
import { ShoppingCart, Check } from 'lucide-react'
import { formatPrecio } from '../../utils/format'
import { useCartStore } from '../../store/cartStore'

const ICONOS_MARCA = {
  Intel: '💻',
  NVIDIA: '🎮',
  Samsung: '💾',
  Kingston: '🔧',
  Corsair: '⚡',
}

function ProductCard({ producto }) {
  const disponible = producto.stockDisponible > 0
  const agregar = useCartStore((state) => state.agregar)
  const [agregado, setAgregado] = useState(false)

  const handleAgregar = () => {
    agregar(producto)
    setAgregado(true)
    setTimeout(() => setAgregado(false), 1200)
  }

  return (
    <div className="card card-hover group flex flex-col overflow-hidden">
      <div className="mb-4 flex h-32 w-full items-center justify-center rounded-md bg-gradient-to-br from-primary-400/20 to-secondary-400/20 transition-all group-hover:from-primary-400/30 group-hover:to-secondary-400/30">
        <span className="text-5xl">{ICONOS_MARCA[producto.marca?.nombre] ?? '📦'}</span>
      </div>

      <div className="mb-2 flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-neutral-text transition-colors group-hover:text-primary-300">
          {producto.nombre}
        </h3>
        {producto.marca && (
          <span className="whitespace-nowrap rounded-full bg-primary-400/20 px-2 py-1 text-xs font-semibold text-primary-300">
            {producto.marca.nombre}
          </span>
        )}
      </div>

      {producto.descripcion && (
        <p className="mb-4 flex-1 text-sm text-neutral-secondary">{producto.descripcion}</p>
      )}

      <div className="mt-auto">
        <div className="mb-4 flex items-end justify-between">
          <div>
            <p className="text-xs text-neutral-muted">Precio</p>
            <p className="text-2xl font-bold text-primary-300">{formatPrecio(producto.precio)}</p>
          </div>
          <span
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              disponible ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
            }`}
          >
            {disponible ? `${producto.stockDisponible} en stock` : 'Agotado'}
          </span>
        </div>
        <button onClick={handleAgregar} disabled={!disponible} className="btn btn-primary w-full text-sm">
          {agregado ? (
            <span className="inline-flex items-center gap-2">
              <Check size={16} /> Agregado
            </span>
          ) : (
            <span className="inline-flex items-center gap-2">
              <ShoppingCart size={16} /> Agregar al carrito
            </span>
          )}
        </button>
      </div>
    </div>
  )
}

export default ProductCard
