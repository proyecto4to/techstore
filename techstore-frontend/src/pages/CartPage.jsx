import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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
      <div className="container mx-auto p-6 text-center">
        <div className="mx-auto max-w-lg rounded-lg border-2 border-gold bg-white p-8 shadow-lg">
          <h2 className="mb-4 text-3xl font-bold text-navy">¡Pedido confirmado! 🎉</h2>
          <p className="mb-2 text-gray-700">
            Tu pedido <span className="font-bold text-gold-dark">N.º {pedidoCreado.numero}</span> fue
            registrado por un total de{' '}
            <span className="font-bold">{formatPrecio(pedidoCreado.total)}</span>.
          </p>
          <p className="mb-6 text-sm text-gray-500">
            Estado: Pendiente — el stock quedó reservado a tu nombre.
          </p>
          <div className="flex justify-center gap-4">
            <Link to="/pedidos" className="rounded bg-navy px-4 py-2 font-semibold text-gold hover:bg-navy-light">
              Ver mis pedidos
            </Link>
            <Link to="/" className="rounded border border-navy px-4 py-2 text-navy hover:bg-gray-50">
              Seguir comprando
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="container mx-auto p-6 text-center">
        <h2 className="mb-4 text-3xl font-bold text-navy">Tu carrito está vacío</h2>
        <Link to="/" className="text-navy underline hover:text-gold-dark">
          Ir al catálogo
        </Link>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-3xl font-bold text-navy">Carrito</h2>

      {error && (
        <p className="mb-4 rounded bg-red-50 p-3 text-red-600" role="alert">
          {error}
        </p>
      )}

      <div className="overflow-x-auto rounded-lg border-2 border-gold bg-white shadow-md">
        <table className="w-full text-left">
          <thead className="bg-navy text-gold">
            <tr>
              <th className="p-3">Producto</th>
              <th className="p-3">Precio</th>
              <th className="p-3">Cantidad</th>
              <th className="p-3">Subtotal</th>
              <th className="p-3"></th>
            </tr>
          </thead>
          <tbody>
            {items.map(({ producto, cantidad }) => (
              <tr key={producto.id} className="border-t">
                <td className="p-3 font-medium text-navy">{producto.nombre}</td>
                <td className="p-3">{formatPrecio(producto.precio)}</td>
                <td className="p-3">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setCantidad(producto.id, cantidad - 1)}
                      className="h-7 w-7 rounded border border-navy text-navy hover:bg-gray-100"
                      aria-label={`Restar uno de ${producto.nombre}`}
                    >
                      −
                    </button>
                    <span className="w-8 text-center">{cantidad}</span>
                    <button
                      onClick={() => setCantidad(producto.id, cantidad + 1)}
                      className="h-7 w-7 rounded border border-navy text-navy hover:bg-gray-100"
                      aria-label={`Sumar uno de ${producto.nombre}`}
                    >
                      +
                    </button>
                  </div>
                </td>
                <td className="p-3 font-semibold">{formatPrecio(producto.precio * cantidad)}</td>
                <td className="p-3">
                  <button
                    onClick={() => quitar(producto.id)}
                    className="text-red-500 hover:text-red-700"
                    aria-label={`Quitar ${producto.nombre}`}
                  >
                    Quitar
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
          className="w-full rounded border border-gray-300 p-2 focus:border-gold focus:outline-none md:max-w-md"
        />
        <div className="text-right">
          <p className="mb-3 text-2xl font-bold text-navy">
            Total: <span className="text-gold-dark">{formatPrecio(total)}</span>
          </p>
          <button
            onClick={handleCheckout}
            disabled={enviando}
            className="rounded bg-navy px-8 py-3 font-semibold text-gold hover:bg-navy-light disabled:opacity-50"
          >
            {enviando ? 'Procesando…' : usuario ? 'Finalizar compra' : 'Ingresar para comprar'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default CartPage
