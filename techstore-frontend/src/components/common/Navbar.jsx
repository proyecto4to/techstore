import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { Menu, X, ShoppingCart, Zap } from 'lucide-react'
import { useAuthStore } from '../../store/authStore'
import { useCartStore, selectTotalItems } from '../../store/cartStore'

function Navbar() {
  const { usuario, logout } = useAuthStore()
  const totalItems = useCartStore(selectTotalItems)
  const [abierto, setAbierto] = useState(false)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    setAbierto(false)
    navigate('/')
  }

  const linkClase = ({ isActive }) =>
    `text-sm transition-colors ${isActive ? 'font-semibold text-primary-300' : 'text-neutral-secondary hover:text-primary-300'}`

  const iniciales = usuario
    ? `${usuario.nombre?.[0] ?? ''}${usuario.apellido?.[0] ?? ''}`.toUpperCase()
    : null

  const enlaces = (
    <>
      <NavLink to="/" end className={linkClase} onClick={() => setAbierto(false)}>
        Catálogo
      </NavLink>
      <NavLink to="/carrito" className={linkClase} onClick={() => setAbierto(false)}>
        <span className="relative inline-flex items-center gap-1">
          <ShoppingCart size={16} />
          Carrito
          {totalItems > 0 && (
            <span className="absolute -right-4 -top-2 rounded-full bg-accent-400 px-1.5 text-xs font-bold text-dark-bg">
              {totalItems}
            </span>
          )}
        </span>
      </NavLink>
      {usuario && (
        <NavLink to="/pedidos" className={linkClase} onClick={() => setAbierto(false)}>
          Mis pedidos
        </NavLink>
      )}
      {usuario?.rol === 'Admin' && (
        <NavLink to="/admin" className={linkClase} onClick={() => setAbierto(false)}>
          Admin
        </NavLink>
      )}
    </>
  )

  return (
    <header className="sticky top-0 z-50 border-b border-dark-border bg-dark-bg/70 backdrop-blur-xl">
      <div className="container mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3 hover:opacity-80">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-secondary-400 text-white">
              <Zap size={20} />
            </div>
            <h1 className="text-2xl font-bold gradient-text">TechStore</h1>
          </Link>

          <nav className="hidden items-center gap-8 md:flex">{enlaces}</nav>

          <div className="flex items-center gap-4">
            {usuario ? (
              <>
                <div
                  title={`${usuario.nombre} ${usuario.apellido} (${usuario.rol})`}
                  className="hidden h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-accent-400 text-sm font-semibold text-white md:flex"
                >
                  {iniciales}
                </div>
                <button onClick={handleLogout} className="btn btn-secondary hidden text-sm md:block">
                  Salir
                </button>
              </>
            ) : (
              <Link to="/login" className="btn btn-primary hidden text-sm md:block">
                Ingresar
              </Link>
            )}
            <button
              className="text-neutral-text md:hidden"
              onClick={() => setAbierto(!abierto)}
              aria-label="Abrir menú"
            >
              {abierto ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>

        {abierto && (
          <nav className="mt-4 flex flex-col gap-4 pb-4 md:hidden">
            {enlaces}
            {usuario ? (
              <button onClick={handleLogout} className="btn btn-secondary w-full text-sm">
                Salir ({usuario.nombre})
              </button>
            ) : (
              <Link to="/login" onClick={() => setAbierto(false)} className="btn btn-primary w-full text-center text-sm">
                Ingresar
              </Link>
            )}
          </nav>
        )}
      </div>
    </header>
  )
}

export default Navbar
