import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShoppingCart, Check } from 'lucide-react'
import { formatPrecio } from '../../utils/format'
import { useCartStore } from '../../store/cartStore'

const IMAGEN_DEFAULT = '/img/productos/default.svg'

function ProductCard({ producto }) {
  const disponible = producto.stockDisponible > 0
  const agregar = useCartStore((state) => state.agregar)
  const [agregado, setAgregado] = useState(false)
  const [imgError, setImgError] = useState(false)
  const navigate = useNavigate()

  const handleAgregar = (e) => {
    e.stopPropagation()
    agregar(producto)
    setAgregado(true)
    setTimeout(() => setAgregado(false), 1200)
  }

  const irAlDetalle = () => navigate(`/productos/${producto.id}`)

  const src = !imgError && producto.imagenUrl ? producto.imagenUrl : IMAGEN_DEFAULT

  return (
    <div
      onClick={irAlDetalle}
      onKeyDown={(e) => e.key === 'Enter' && irAlDetalle()}
      role="link"
      tabIndex={0}
      aria-label={`Ver detalle de ${producto.nombre}`}
      className="card card-hover group flex cursor-pointer flex-col overflow-hidden focus:outline-none focus:ring-1 focus:ring-primary-400"
    >
      <div className="mb-4 h-40 w-full overflow-hidden rounded-md bg-gradient-to-br from-primary-400/10 to-secondary-400/10">
        <img
          src={src}
          alt={producto.nombre}
          onError={() => setImgError(true)}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
        />
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
        <button
          onClick={handleAgregar}
          disabled={!disponible}
          title={!disponible ? 'Sin stock disponible por el momento' : undefined}
          className="btn btn-primary w-full text-sm disabled:cursor-not-allowed"
        >
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
