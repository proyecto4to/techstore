import { useState } from 'react'
import { formatPrecio } from '../../utils/format'
import { useCartStore } from '../../store/cartStore'

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
    <div className="flex flex-col rounded-lg border-2 border-gold bg-white p-6 shadow-md transition hover:shadow-xl">
      <div className="mb-2 flex items-start justify-between gap-2">
        <h3 className="text-lg font-bold text-navy">{producto.nombre}</h3>
        {producto.marca && (
          <span className="whitespace-nowrap rounded bg-navy px-2 py-1 text-xs font-semibold text-gold">
            {producto.marca.nombre}
          </span>
        )}
      </div>
      {producto.descripcion && (
        <p className="mb-4 flex-1 text-sm text-gray-600">{producto.descripcion}</p>
      )}
      <div className="mt-auto">
        <p className="text-2xl font-bold text-gold-dark">{formatPrecio(producto.precio)}</p>
        <p className={`mt-1 text-sm font-medium ${disponible ? 'text-green-600' : 'text-red-500'}`}>
          {disponible ? `En stock: ${producto.stockDisponible}` : 'Sin stock'}
        </p>
        <button
          onClick={handleAgregar}
          disabled={!disponible}
          className="mt-3 w-full rounded bg-navy py-2 font-semibold text-gold transition hover:bg-navy-light disabled:cursor-not-allowed disabled:opacity-40"
        >
          {agregado ? '✓ Agregado' : '🛒 Agregar al carrito'}
        </button>
      </div>
    </div>
  )
}

export default ProductCard
