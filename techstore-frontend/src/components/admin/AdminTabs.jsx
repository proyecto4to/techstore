import { NavLink } from 'react-router-dom'

const tabs = [
  { to: '/admin', label: 'Pedidos', end: true },
  { to: '/admin/productos', label: 'Productos' },
  { to: '/admin/facturas', label: 'Facturas' },
  { to: '/admin/reportes', label: 'Reportes' },
]

function AdminTabs() {
  return (
    <div className="mb-6 flex gap-2 border-b border-dark-border pb-2">
      {tabs.map((tab) => (
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
