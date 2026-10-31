import { NavLink } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { usePermisos } from '../../hooks/usePermisos'

/**
 * Navegación del panel, agrupada por tipo de trabajo.
 *
 * Antes eran dieciséis destinos en una fila plana, donde «Auditoría» pesaba lo
 * mismo que «Venta directa». Los grupos no esconden nada —todo sigue a un solo
 * clic— pero ponen primero lo que se usa todos los días y dejan atrás lo que se
 * abre de vez en cuando.
 *
 * El orden dentro de «Mostrador» sigue la jornada real: se miran los pedidos
 * que entraron, se atiende al que vino en persona, se factura y se cierra caja.
 * No se le quitó ningún destino al cajero: el backend lo habilita en compras,
 * reportes, caja y facturación, así que ocultárselos le sacaría capacidad real.
 */
/**
 * Cada destino declara sobre qué módulo trabaja. Con eso, quitarle un permiso
 * a un rol desde Seguridad le saca también la entrada del menú, sin tocar este
 * archivo. El `soloAdmin` / `soloCajero` sigue estando y se aplica primero: es
 * el piso que ya tenía el panel.
 */
const grupos = [
  {
    nombre: 'Mostrador',
    tabs: [
      { to: '/admin', label: 'Pedidos', end: true, modulo: 'PEDIDOS' },
      { to: '/admin/pagos', label: 'Verificar pagos', modulo: 'PEDIDOS' },
      { to: '/admin/venta', label: 'Venta directa', modulo: 'FACTURAS' },
      { to: '/admin/facturas', label: 'Facturas', modulo: 'FACTURAS' },
      { to: '/admin/caja', label: 'Caja', modulo: 'CAJA' },
      { to: '/admin/solicitudes', label: 'Solicitudes', soloCajero: true, modulo: 'REPARTOS' },
    ],
  },
  {
    nombre: 'Catálogo',
    tabs: [
      { to: '/admin/productos', label: 'Productos', soloAdmin: true, modulo: 'PRODUCTOS' },
      { to: '/admin/compras', label: 'Compras', modulo: 'COMPRAS' },
      { to: '/admin/vehiculos', label: 'Vehículos', soloAdmin: true, modulo: 'VEHICULOS' },
    ],
  },
  {
    nombre: 'Contacto',
    tabs: [
      { to: '/admin/mensajes', label: 'Mensajes', modulo: 'CONVERSACIONES' },
      { to: '/admin/correos', label: 'Correos', modulo: 'CONFIGURACION' },
    ],
  },
  {
    nombre: 'Administración',
    tabs: [
      { to: '/admin/reportes', label: 'Reportes', modulo: 'REPORTES' },
      { to: '/admin/contabilidad', label: 'Contabilidad', soloAdmin: true, modulo: 'CONTABILIDAD' },
      { to: '/admin/usuarios', label: 'Usuarios', soloAdmin: true, modulo: 'USUARIOS' },
      { to: '/admin/empresa', label: 'Empresa', soloAdmin: true, modulo: 'EMPRESA' },
      { to: '/admin/auditoria', label: 'Auditoría', soloAdmin: true, modulo: 'AUDITORIA' },
      { to: '/admin/seguridad', label: 'Seguridad', soloAdmin: true, modulo: 'SEGURIDAD' },
    ],
  },
]

const claseTab = ({ isActive }) =>
  `rounded-md px-4 py-2 text-sm font-semibold transition-all ${
    isActive
      ? 'bg-gradient-to-r from-primary-400 to-secondary-400 text-white'
      : 'text-neutral-secondary hover:bg-primary-400/10 hover:text-primary-300'
  }`

function AdminTabs() {
  const { usuario } = useAuthStore()
  const { puede } = usePermisos()

  const porRol = (tab) =>
    (!tab.soloAdmin || usuario?.rol === 'Admin') &&
    (!tab.soloCajero || usuario?.rol === 'Cajero')

  // Mientras los permisos no llegaron, `puede` responde que no y la entrada no
  // se dibuja. Es preferible a mostrarla y sacarla medio segundo después, o a
  // ofrecer una pantalla que el backend va a rechazar.
  const visible = (tab) => porRol(tab) && (!tab.modulo || puede(tab.modulo, 'VER'))

  const visibles = grupos
    .map((grupo) => ({ ...grupo, tabs: grupo.tabs.filter(visible) }))
    .filter((grupo) => grupo.tabs.length > 0)

  return (
    <nav aria-label="Secciones del panel" className="mb-6 border-b border-dark-border pb-3">
      <div className="mb-3">
        <NavLink to="/admin/inicio" className={claseTab}>
          Inicio
        </NavLink>
      </div>
      <div className="flex flex-wrap gap-x-6 gap-y-3">
        {visibles.map((grupo) => (
          <div key={grupo.nombre}>
            <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-neutral-muted">
              {grupo.nombre}
            </p>
            <div className="flex flex-wrap gap-2">
              {grupo.tabs.map((tab) => (
                <NavLink key={tab.to} to={tab.to} end={tab.end} className={claseTab}>
                  {tab.label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </div>
    </nav>
  )
}

export default AdminTabs
