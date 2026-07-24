import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Trash2, PartyPopper } from 'lucide-react'
import { useCartStore, selectTotalMonto } from '../store/cartStore'
import { useAuthStore } from '../store/authStore'
import { pedidoService } from '../services/pedidoService'
import { formatPrecio } from '../utils/format'

function CartPage() {
  const { items, setCantidad, quitar, vaciar } = useCartStore()
  const total = useCartStore(selectTotalMonto)
  const { usuario } = useAuthStore()
  const navigate = useNavigate()

  const [notas, setNotas] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [pedidoCreado, setPedidoCreado] = useState(null)

  const handleCheckout = async () => {
    if (!usuario) {
      navigate('/login')
      return
    }
    setError(null)
    setEnviando(true)
    try {
      const pedido = await pedidoService.checkout(items, notas)
      setPedidoCreado(pedido)
      vaciar()
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo completar la compra. Intente de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  if (pedidoCreado) {
    return (
      <div className="container mx-auto px-6 py-8 text-center">
        <div className="card mx-auto max-w-lg p-8">
          <PartyPopper className="mx-auto mb-4 text-accent-400" size={40} />
          <h2 className="mb-4 text-3xl font-bold text-neutral-text">¡Pedido registrado!</h2>
          <p className="mb-2 text-neutral-secondary">
            Tu pedido <span className="font-bold text-primary-300">N.º {pedidoCreado.numero}</span>{' '}
            fue creado por un total de{' '}
            <span className="font-bold text-neutral-text">{formatPrecio(pedidoCreado.total)}</span>.
          </p>
          <p className="mb-6 text-sm text-neutral-muted">
            Estado: Pendiente — el stock quedó reservado a tu nombre.
          </p>
          <div className="flex justify-center gap-4">
            <Link to="/pedidos" className="btn btn-primary">
              Ver mis pedidos
            </Link>
            <Link to="/" className="btn btn-secondary">
              Seguir comprando
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="container mx-auto px-6 py-16 text-center">
        <h2 className="mb-4 text-3xl font-bold text-neutral-text">Tu carrito está vacío</h2>
        <Link to="/" className="text-primary-300 underline hover:text-accent-400">
          Ir al catálogo
        </Link>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">Carrito</h1>

      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}

      <div className="card overflow-x-auto p-0">
        <table className="tabla-admin">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Precio</th>
              <th>Cantidad</th>
              <th>Subtotal</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map(({ producto, cantidad }) => (
              <tr key={producto.id}>
                <td className="font-medium text-neutral-text">{producto.nombre}</td>
                <td>{formatPrecio(producto.precio)}</td>
                <td>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setCantidad(producto.id, cantidad - 1)}
                      disabled={cantidad <= 1}
                      title={cantidad <= 1 ? 'Mínimo 1 — usá el tacho para quitarlo' : undefined}
                      className="h-7 w-7 rounded border border-dark-border text-neutral-text hover:border-primary-400 hover:bg-primary-400/10 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label={`Restar uno de ${producto.nombre}`}
                    >
                      −
                    </button>
                    <span className="w-8 text-center">{cantidad}</span>
                    <button
                      onClick={() => setCantidad(producto.id, cantidad + 1)}
                      disabled={cantidad >= producto.stockDisponible}
                      title={
                        cantidad >= producto.stockDisponible
                          ? `No hay más stock: solo quedan ${producto.stockDisponible}`
                          : undefined
                      }
                      className="h-7 w-7 rounded border border-dark-border text-neutral-text hover:border-primary-400 hover:bg-primary-400/10 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label={`Sumar uno de ${producto.nombre}`}
                    >
                      +
                    </button>
                  </div>
                  <p className={`mt-1 text-xs ${cantidad >= producto.stockDisponible ? 'text-orange-400' : 'text-neutral-muted'}`}>
                    {cantidad >= producto.stockDisponible
                      ? `Máximo: ${producto.stockDisponible} en stock`
                      : `${producto.stockDisponible} disponibles`}
                  </p>
                </td>
                <td className="font-semibold text-primary-300">
                  {formatPrecio(producto.precio * cantidad)}
                </td>
                <td>
                  <button
                    onClick={() => quitar(producto.id)}
                    className="text-red-400 hover:text-red-300"
                    aria-label={`Quitar ${producto.nombre}`}
                  >
                    <Trash2 size={18} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-6 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <textarea
          value={notas}
          onChange={(e) => setNotas(e.target.value)}
          placeholder="Notas para el pedido (opcional)"
          rows={3}
          className="input md:max-w-md"
        />
        <div className="text-right">
          <p className="mb-3 text-2xl font-bold text-neutral-text">
            Total: <span className="gradient-text">{formatPrecio(total)}</span>
          </p>
          <button
            onClick={handleCheckout}
            disabled={enviando}
            title={enviando ? 'Procesando tu pedido…' : undefined}
            className="btn btn-primary px-8 py-3 disabled:cursor-not-allowed"
          >
            {enviando ? 'Procesando…' : usuario ? 'Finalizar compra' : 'Ingresar para comprar'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default CartPage
