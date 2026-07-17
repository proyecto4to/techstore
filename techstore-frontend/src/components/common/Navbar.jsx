import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { useCartStore, selectTotalItems } from '../../store/cartStore'

function Navbar() {
  const { usuario, logout } = useAuthStore()
  const totalItems = useCartStore(selectTotalItems)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <nav className="bg-navy text-white shadow-lg">
      <div className="container mx-auto flex flex-wrap items-center justify-between gap-3 p-4">
        <Link to="/" className="text-3xl font-bold text-gold">
          TechStore
        </Link>
        <div className="flex flex-wrap items-center gap-4">
          <Link to="/" className="hover:text-gold-light">
            Catálogo
          </Link>
          <Link to="/carrito" className="relative hover:text-gold-light">
            🛒 Carrito
            {totalItems > 0 && (
              <span className="absolute -right-3 -top-2 rounded-full bg-gold px-1.5 text-xs font-bold text-navy">
                {totalItems}
              </span>
            )}
          </Link>
          {usuario ? (
            <>
              <Link to="/pedidos" className="hover:text-gold-light">
                Mis pedidos
              </Link>
              {usuario.rol === 'Admin' && (
                <Link to="/admin" className="hover:text-gold-light">
                  Admin
                </Link>
              )}
              <span className="text-gold-light">Hola, {usuario.nombre}</span>
              <button
                onClick={handleLogout}
                className="rounded border border-gold px-3 py-1 text-gold hover:bg-gold hover:text-navy"
              >
                Salir
              </button>
            </>
          ) : (
            <Link
              to="/login"
              className="rounded bg-gold px-4 py-2 font-semibold text-navy hover:bg-gold-light"
            >
              Ingresar
            </Link>
          )}
        </div>
      </div>
    </nav>
  )
}

export default Navbar
