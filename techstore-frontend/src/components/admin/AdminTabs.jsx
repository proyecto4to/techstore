import { NavLink } from 'react-router-dom'

const tabs = [
  { to: '/admin', label: 'Pedidos', end: true },
  { to: '/admin/productos', label: 'Productos' },
  { to: '/admin/reportes', label: 'Reportes' },
]

function AdminTabs() {
  return (
    <div className="mb-6 flex gap-2 border-b-2 border-gold pb-2">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) =>
            `rounded-t px-4 py-2 font-semibold ${
              isActive ? 'bg-navy text-gold' : 'text-navy hover:bg-gray-100'
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
