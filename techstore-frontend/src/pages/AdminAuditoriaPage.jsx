import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { auditoriaService } from '../services/auditoriaService'
import AdminTabs from '../components/admin/AdminTabs'

const ESTILO_ACCION = {
  Crear: 'bg-emerald-400/20 text-emerald-400',
  Editar: 'bg-primary-400/20 text-primary-300',
  Eliminar: 'bg-red-400/20 text-red-400',
  Anular: 'bg-red-400/20 text-red-400',
  Devolver: 'bg-amber-400/20 text-amber-300',
  Abrir: 'bg-accent-400/20 text-accent-400',
  Cerrar: 'bg-accent-400/20 text-accent-400',
  Cobrar: 'bg-emerald-400/20 text-emerald-400',
  Pagar: 'bg-secondary-400/20 text-secondary-400',
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-PY', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

/**
 * Muestra el JSON de cambios como "campo: antes → después". Los valores sueltos
 * (altas, donde no hay un "antes") se muestran tal cual.
 */
function Cambios({ cambios }) {
  if (!cambios || Object.keys(cambios).length === 0) {
    return <span className="text-neutral-muted">—</span>
  }
  return (
    <ul className="space-y-0.5">
      {Object.entries(cambios).map(([campo, valor]) => (
        <li key={campo} className="text-xs">
          <span className="text-neutral-muted">{campo}:</span>{' '}
          {valor && typeof valor === 'object' ? (
            <>
              <span className="text-neutral-secondary line-through">{valor.antes ?? '(vacío)'}</span>
              <span className="mx-1 text-neutral-muted">→</span>
              <span className="font-medium text-neutral-text">{valor.despues ?? '(vacío)'}</span>
            </>
          ) : (
            <span className="text-neutral-text">{String(valor)}</span>
          )}
        </li>
      ))}
    </ul>
  )
}

function AdminAuditoriaPage() {
  const [page, setPage] = useState(0)
  const [filtros, setFiltros] = useState({ entidad: '', desde: '', hasta: '' })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['auditoria', page, filtros],
    queryFn: () => auditoriaService.listar({ ...filtros, page }),
    placeholderData: keepPreviousData,
  })

  const { data: entidades = [] } = useQuery({
    queryKey: ['auditoria-entidades'],
    queryFn: () => auditoriaService.entidades(),
  })

  const cambiarFiltro = (campo, valor) => {
    setPage(0)
    setFiltros((f) => ({ ...f, [campo]: valor }))
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-2 flex items-center gap-3 text-4xl font-bold text-neutral-text">
        <ShieldCheck size={32} className="text-primary-300" />
        Auditoría
      </h1>
      <p className="mb-6 text-neutral-secondary">
        Quién hizo qué y cuándo. Es solo lectura: estos registros no se pueden editar ni borrar
        desde el sistema.
      </p>
      <AdminTabs />

      <div className="card mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="f-entidad">
            Qué
          </label>
          <select
            id="f-entidad"
            className="input"
            value={filtros.entidad}
            onChange={(e) => cambiarFiltro('entidad', e.target.value)}
          >
            <option value="">Todo</option>
            {entidades.map((e) => (
              <option key={e} value={e}>{e}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="f-desde">
            Desde
          </label>
          <input
            id="f-desde"
            type="date"
            className="input"
            value={filtros.desde}
            onChange={(e) => cambiarFiltro('desde', e.target.value)}
          />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="f-hasta">
            Hasta
          </label>
          <input
            id="f-hasta"
            type="date"
            className="input"
            value={filtros.hasta}
            onChange={(e) => cambiarFiltro('hasta', e.target.value)}
          />
        </div>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando…</p>}
      {isError && <p className="alerta-error">No se pudo cargar la auditoría.</p>}

      {data && (
        <div className="card">
          {data.content.length === 0 ? (
            <p className="text-neutral-secondary">
              No hay acciones registradas con esos filtros.
            </p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="tabla-admin tabla-apilable">
                  <thead>
                    <tr>
                      <th>Cuándo</th>
                      <th>Quién</th>
                      <th>Acción</th>
                      <th>Qué pasó</th>
                      <th>Cambios</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((registro) => (
                      <tr key={registro.id}>
                        <td data-label="Cuándo" className="whitespace-nowrap text-neutral-secondary">
                          {formatFecha(registro.fecha)}
                        </td>
                        <td data-label="Quién" className="text-neutral-text">{registro.usuarioEmail}</td>
                        <td data-label="Acción">
                          <span
                            className={`rounded-full px-3 py-1 text-xs font-semibold ${
                              ESTILO_ACCION[registro.accion] ?? 'bg-neutral-muted/20 text-neutral-muted'
                            }`}
                          >
                            {registro.accion}
                          </span>
                        </td>
                        <td data-label="Qué pasó" className="text-neutral-text">
                          {registro.detalle ?? '—'}
                          <span className="ml-1 text-xs text-neutral-muted">
                            ({registro.entidad}
                            {registro.entidadId ? ` #${registro.entidadId}` : ''})
                          </span>
                        </td>
                        <td data-label="Cambios">
                          <Cambios cambios={registro.cambios} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="mt-4 flex items-center justify-between">
                <p className="text-sm text-neutral-secondary">
                  Página {data.page + 1} de {Math.max(1, data.totalPages)} · {data.totalElements} acciones
                </p>
                <div className="flex gap-2">
                  <button
                    className="btn btn-secondary text-sm"
                    disabled={data.page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Anterior
                  </button>
                  <button
                    className="btn btn-secondary text-sm"
                    disabled={data.page + 1 >= data.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Siguiente
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

export default AdminAuditoriaPage
