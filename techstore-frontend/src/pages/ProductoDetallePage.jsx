import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ShoppingCart, Check, Minus, Plus } from 'lucide-react'
import { productoService } from '../services/productoService'
import { maximoComprable, productoDisponible, useCartStore } from '../store/cartStore'
import { formatPrecio } from '../utils/format'

const IMAGEN_DEFAULT = '/img/productos/default.svg'

function ProductoDetallePage() {
  const { id } = useParams()
  const agregar = useCartStore((state) => state.agregar)
  const [cantidad, setCantidad] = useState(1)
  const [agregado, setAgregado] = useState(false)
  const [imgError, setImgError] = useState(false)

  const {
    data: producto,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['producto', id],
    queryFn: () => productoService.obtener(id),
  })

  if (isLoading) {
    return (
      <div className="container mx-auto px-6 py-16 text-center text-neutral-muted">
        Cargando producto…
      </div>
    )
  }

  if (isError) {
    const noExiste = error.response?.status === 404
    return (
      <div className="container mx-auto px-6 py-16 text-center">
        <h2 className="mb-4 text-3xl font-bold text-neutral-text">
          {noExiste ? 'Producto no encontrado' : 'No se pudo cargar el producto'}
        </h2>
        <Link to="/" className="text-primary-300 underline hover:text-accent-400">
          Volver al catálogo
        </Link>
      </div>
    )
  }

  const disponible = productoDisponible(producto)
  const maximo = maximoComprable(producto)
  const src = !imgError && producto.imagenUrl ? producto.imagenUrl : IMAGEN_DEFAULT

  const handleAgregar = () => {
    agregar(producto, cantidad)
    setAgregado(true)
    setTimeout(() => setAgregado(false), 1500)
  }

  const ajustarCantidad = (delta) =>
    setCantidad((c) => Math.min(Math.max(1, c + delta), Math.max(1, maximo)))

  return (
    <div className="container mx-auto px-6 py-8">
      <Link
        to="/"
        className="mb-6 inline-flex items-center gap-2 text-sm text-neutral-secondary transition-colors hover:text-primary-300"
      >
        <ArrowLeft size={16} /> Volver al catálogo
      </Link>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
        <div className="card overflow-hidden p-0">
          <img
            src={src}
            alt={producto.nombre}
            onError={() => setImgError(true)}
            className="h-full max-h-[420px] w-full object-cover"
          />
        </div>

        <div className="flex flex-col">
          <div className="mb-2 flex flex-wrap items-center gap-3">
            {producto.marca && (
              <span className="rounded-full bg-primary-400/20 px-3 py-1 text-sm font-semibold text-primary-300">
                {producto.marca.nombre}
              </span>
            )}
            {producto.modelo && (
              <span className="rounded-full bg-secondary-400/20 px-3 py-1 text-sm font-semibold text-neutral-secondary">
                {producto.modelo.nombre}
              </span>
            )}
          </div>

          <h1 className="mb-4 text-4xl font-bold text-neutral-text">{producto.nombre}</h1>

          {producto.descripcion && (
            <p className="mb-6 text-lg text-neutral-secondary">{producto.descripcion}</p>
          )}

          <div className="card mb-6">
            <p className="text-sm text-neutral-muted">Precio (IVA {producto.iva}% incluido)</p>
            <p className="text-4xl font-bold gradient-text">{formatPrecio(producto.precio)}</p>
            <p
              className={`mt-3 inline-block rounded-full px-3 py-1 text-sm font-semibold ${
                disponible ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
              }`}
            >
              {disponible ? 'Disponible para comprar' : 'Sin stock por el momento'}
            </p>
          </div>

          {disponible && (
            <div className="flex flex-wrap items-center gap-4">
              <div className="flex items-center gap-3 rounded-md border border-dark-border px-3 py-2">
                <button
                  onClick={() => ajustarCantidad(-1)}
                  disabled={cantidad <= 1}
                  className="text-neutral-text hover:text-primary-300 disabled:opacity-40"
                  aria-label="Restar cantidad"
                >
                  <Minus size={18} />
                </button>
                <span className="w-8 text-center text-lg font-semibold text-neutral-text">
                  {cantidad}
                </span>
                <button
                  onClick={() => ajustarCantidad(1)}
                  disabled={cantidad >= maximo}
                  className="text-neutral-text hover:text-primary-300 disabled:opacity-40"
                  aria-label="Sumar cantidad"
                >
                  <Plus size={18} />
                </button>
              </div>

              <button onClick={handleAgregar} className="btn btn-primary flex-1 py-3 sm:flex-none sm:px-10">
                {agregado ? (
                  <span className="inline-flex items-center gap-2">
                    <Check size={18} /> Agregado al carrito
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-2">
                    <ShoppingCart size={18} /> Agregar al carrito
                  </span>
                )}
              </button>
            </div>
          )}

          {agregado && (
            <Link to="/carrito" className="mt-4 text-sm text-accent-400 underline hover:text-accent-400/80">
              Ir al carrito →
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}

export default ProductoDetallePage
