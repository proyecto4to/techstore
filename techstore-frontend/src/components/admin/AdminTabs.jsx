import { NavLink } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

const tabs = [
  { to: '/admin/inicio', label: 'Inicio' },
  { to: '/admin', label: 'Pedidos', end: true },
  { to: '/admin/venta', label: 'Venta directa' },
  { to: '/admin/compras', label: 'Compras' },
  { to: '/admin/productos', label: 'Productos', soloAdmin: true },
  { to: '/admin/facturas', label: 'Facturas' },
  { to: '/admin/caja', label: 'Caja' },
  { to: '/admin/solicitudes', label: 'Solicitudes', soloCajero: true },
  { to: '/admin/vehiculos', label: 'Vehículos', soloAdmin: true },
  { to: '/admin/usuarios', label: 'Usuarios', soloAdmin: true },
  { to: '/admin/empresa', label: 'Empresa', soloAdmin: true },
  { to: '/admin/correos', label: 'Correos' },
  { to: '/admin/mensajes', label: 'Mensajes' },
  { to: '/admin/reportes', label: 'Reportes' },
  { to: '/admin/contabilidad', label: 'Contabilidad', soloAdmin: true },
  { to: '/admin/auditoria', label: 'Auditoría', soloAdmin: true },
]

function AdminTabs() {
  const { usuario } = useAuthStore()
  const visibles = tabs.filter(
    (tab) =>
      (!tab.soloAdmin || usuario?.rol === 'Admin') &&
      (!tab.soloCajero || usuario?.rol === 'Cajero'),
  )
  return (
    <div className="mb-6 flex flex-wrap gap-2 border-b border-dark-border pb-2">
      {visibles.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) =>
            `rounded-md px-4 py-2 text-sm font-semibold transition-all ${
              isActive
                ? 'bg-gradient-to-r from-primary-400 to-secondary-400 text-white'
                : 'text-neutral-secondary hover:bg-primary-400/10 hover:text-primary-300'
            }`
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </div>
  )
}

export default AdminTabs
