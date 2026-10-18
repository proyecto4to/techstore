import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShoppingCart, Check } from 'lucide-react'
import { formatPrecio } from '../../utils/format'
import {
  maximoComprable,
  muestraUnidadesRestantes,
  productoDisponible,
  tienePromocion,
  selectCantidadDe,
  useCartStore,
} from '../../store/cartStore'

const IMAGEN_DEFAULT = '/img/productos/default.svg'

function ProductCard({ producto }) {
  const disponible = productoDisponible(producto)
  const agregar = useCartStore((state) => state.agregar)
  const enCarrito = useCartStore(selectCantidadDe(producto.id))
  const [agregado, setAgregado] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [imgError, setImgError] = useState(false)
  const navigate = useNavigate()

  const maximo = maximoComprable(producto)
  // Lo que ya está en el carrito descuenta del margen: sin esto el botón sigue
  // habilitado y cada clic recorta en silencio.
  const margen = Math.max(0, maximo - enCarrito)
  const escasez = muestraUnidadesRestantes(producto)
  const hayPromocion = tienePromocion(producto)

  const handleAgregar = (e) => {
    e.stopPropagation()
    const resultado = agregar(producto)
    if (resultado.agregadas > 0) {
      setAgregado(true)
      setTimeout(() => setAgregado(false), 1200)
    }
    // Solo se avisa cuando no entró lo pedido: en el caso normal el propio
    // botón ya confirma y repetirlo sería ruido.
    setAviso(resultado.resultado === 'agregado' ? null : resultado.mensaje)
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

      {/* Recortada a dos líneas: la tarjeta es para decidir si abrir el
          producto, y una descripción larga empuja el precio y el botón. */}
      {producto.descripcion && (
        <p className="mb-4 line-clamp-2 flex-1 text-sm text-neutral-secondary">
          {producto.descripcion}
        </p>
      )}

      <div className="mt-auto">
        <div className="mb-4 flex items-end justify-between">
          <div>
            <p className="text-xs text-neutral-muted">Precio</p>
            <p className="text-2xl font-bold text-primary-300">{formatPrecio(producto.precio)}</p>
            {/* `precio` ya viene con la promoción aplicada; el de lista solo
                se muestra tachado al lado, nunca al revés. */}
            {hayPromocion && (
              <p className="text-xs text-neutral-muted">
                <span className="line-through">{formatPrecio(producto.precioLista)}</span>{' '}
                <span className="font-semibold text-emerald-400">
                  −{Math.round(producto.promocion.porcentaje)}%
                </span>
              </p>
            )}
          </div>
          <span
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              disponible ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
            }`}
          >
            {!disponible ? 'Agotado' : escasez ? `Quedan ${maximo}` : 'Disponible'}
          </span>
        </div>
        <button
          onClick={handleAgregar}
          disabled={!disponible || margen === 0}
          title={
            !disponible
              ? 'Sin stock disponible por el momento'
              : margen === 0
                ? 'Ya tenés todo el stock disponible en el carrito'
                : undefined
          }
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
        {aviso && (
          <p role="status" className="mt-2 text-xs text-amber-300">
            {aviso}
          </p>
        )}
      </div>
    </div>
  )
}

export default ProductCard
