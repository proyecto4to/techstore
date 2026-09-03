import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ShoppingCart, Check, Minus, Plus } from 'lucide-react'
import { productoService } from '../services/productoService'
import {
  maximoComprable,
  muestraUnidadesRestantes,
  productoDisponible,
  selectCantidadDe,
  useCartStore,
} from '../store/cartStore'
import { formatPrecio } from '../utils/format'

const IMAGEN_DEFAULT = '/img/productos/default.svg'

function ProductoDetallePage() {
  const { id } = useParams()
  const agregar = useCartStore((state) => state.agregar)
  const enCarrito = useCartStore(selectCantidadDe(Number(id)))
  const [cantidad, setCantidad] = useState(1)
  const [agregado, setAgregado] = useState(false)
  const [aviso, setAviso] = useState(null)
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
  // Lo que ya está en el carrito descuenta: sin esto se podría elegir 5,
  // tener 7 adentro y terminar pidiendo 12 de un producto que tiene 10.
  const margen = Math.max(0, maximo - enCarrito)
  const escasez = muestraUnidadesRestantes(producto)
  const src = !imgError && producto.imagenUrl ? producto.imagenUrl : IMAGEN_DEFAULT

  const handleAgregar = () => {
    const resultado = agregar(producto, cantidad)
    if (resultado.agregadas > 0) {
      setAgregado(true)
      setCantidad(1)
      setTimeout(() => setAgregado(false), 1500)
    }
    // Solo se avisa cuando entró menos de lo pedido: repetir el mensaje feliz
    // en el caso normal sería ruido, porque el botón ya lo confirma.
    setAviso(resultado.resultado === 'agregado' ? null : resultado.mensaje)
  }

  const ajustarCantidad = (delta) =>
    setCantidad((c) => Math.min(Math.max(1, c + delta), Math.max(1, margen)))

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
            {/* El número solo se muestra cuando de verdad dice cuánto queda:
                con stock alto el tope es un límite por pedido, no escasez. */}
            {disponible && escasez && (
              <p className="mt-2 text-sm text-amber-300">
                {maximo === 1 ? 'Queda 1 unidad' : `Quedan ${maximo} unidades`}
                {enCarrito > 0 && ` · ya tenés ${enCarrito} en tu carrito`}
              </p>
            )}
          </div>

          {disponible && margen > 0 && (
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
                  disabled={cantidad >= margen}
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

          {disponible && margen === 0 && (
            <p className="rounded-md border border-amber-400/40 bg-amber-400/10 px-4 py-3 text-sm text-amber-300">
              Ya tenés en el carrito todas las unidades disponibles de este producto.
            </p>
          )}

          {aviso && (
            <p role="status" className="mt-4 text-sm text-amber-300">
              {aviso}
            </p>
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
