import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Menu, X, ShoppingCart, Bell } from 'lucide-react'
import { useAuthStore } from '../../store/authStore'
import { useCartStore, selectTotalItems } from '../../store/cartStore'
import { notificacionService } from '../../services/notificacionService'

function Navbar() {
  const { usuario, logout } = useAuthStore()
  const totalItems = useCartStore(selectTotalItems)
  const [abierto, setAbierto] = useState(false)
  const navigate = useNavigate()

  // El contador se refresca solo cada minuto: los avisos que importan —una
  // avería, una transferencia esperando al cajero— no pueden quedar esperando
  // a que alguien recargue la página.
  const { data: pendientes } = useQuery({
    queryKey: ['notificaciones-no-leidas'],
    queryFn: notificacionService.noLeidas,
    enabled: Boolean(usuario),
    refetchInterval: 60_000,
  })
  const sinLeer = pendientes?.noLeidas ?? 0

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
      {usuario && (
        <NavLink to="/notificaciones" className={linkClase} onClick={() => setAbierto(false)}>
          <span className="relative inline-flex items-center gap-1">
            <Bell size={16} />
            Avisos
            {sinLeer > 0 && (
              <span
                className="absolute -right-4 -top-2 rounded-full bg-accent-400 px-1.5 text-xs font-bold text-dark-bg"
                aria-label={`${sinLeer} sin leer`}
              >
                {sinLeer > 99 ? '99+' : sinLeer}
              </span>
            )}
          </span>
        </NavLink>
      )}
      {(usuario?.rol === 'Admin' || usuario?.rol === 'Cajero') && (
        <NavLink to="/admin/inicio" className={linkClase} onClick={() => setAbierto(false)}>
          Panel
        </NavLink>
      )}
    </>
  )

  return (
    <header className="sticky top-0 z-50 border-b border-dark-border bg-dark-bg/70 backdrop-blur-xl">
      <div className="container mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3 hover:opacity-80">
            {/* El mismo isotipo que va en la factura y en el favicon. */}
            <img src="/isotipo.svg" alt="" width="40" height="40" className="h-10 w-10" />
            <h1 className="text-2xl font-bold gradient-text">TechStore</h1>
          </Link>

          <nav className="hidden items-center gap-8 md:flex">{enlaces}</nav>

          <div className="flex items-center gap-4">
            {usuario ? (
              <>
                <Link
                  to="/cuenta"
                  title={`Mi cuenta — ${usuario.nombre} ${usuario.apellido} (${usuario.rol})`}
                  className="hidden h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-accent-400 text-sm font-semibold text-white transition-opacity hover:opacity-80 md:flex"
                >
                  {iniciales}
                </Link>
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
            {usuario && (
              <NavLink to="/cuenta" className={linkClase} onClick={() => setAbierto(false)}>
                Mi cuenta
              </NavLink>
            )}
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
