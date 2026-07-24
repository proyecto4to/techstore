import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Mail, Paperclip } from 'lucide-react'
import { correoService } from '../services/correoService'
import AdminTabs from '../components/admin/AdminTabs'

const ESTILO_ESTADO = {
  ENVIADO: 'bg-emerald-400/20 text-emerald-400',
  SIMULADO: 'bg-primary-400/20 text-primary-300',
  PENDIENTE: 'bg-yellow-400/20 text-yellow-400',
  ERROR: 'bg-red-400/20 text-red-400',
}

const ETIQUETA_ESTADO = {
  ENVIADO: 'Enviado',
  SIMULADO: 'Simulado',
  PENDIENTE: 'Pendiente',
  ERROR: 'Error',
}

function AdminCorreosPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-correos', page],
    queryFn: () => correoService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Correos</span>
      </h1>
      <AdminTabs />

      <div className="mb-6 rounded-md border border-primary-400/30 bg-primary-400/5 p-4 text-sm text-neutral-secondary">
        <p className="inline-flex items-center gap-2 font-semibold text-neutral-text">
          <Mail size={16} className="text-accent-400" /> Bandeja de salida
        </p>
        <p className="mt-1">
          Cada factura, alta y cambio de contraseña genera una notificación por correo.
          Estado <span className="font-semibold text-primary-300">Simulado</span> = registrado localmente
          sin envío real. Para enviar de verdad, completá tus credenciales SMTP en{' '}
          <code className="mx-1 rounded bg-dark-bg px-1">email.local.ps1</code> y reiniciá TechStore
          (pasan a <span className="font-semibold text-emerald-400">Enviado</span>).
        </p>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando correos…</p>}

      {data && data.content.length === 0 && (
        <p className="text-neutral-muted">Todavía no se generó ningún correo.</p>
      )}

      {data && data.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Tipo</th>
                <th>Destinatario</th>
                <th>Asunto</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((c) => (
                <tr key={c.id}>
                  <td className="whitespace-nowrap text-neutral-secondary">
                    {new Date(c.fecha).toLocaleString('es-PY')}
                  </td>
                  <td className="text-neutral-text">{c.tipoDescripcion}</td>
                  <td>
                    <p className="text-neutral-text">{c.destinatarioNombre ?? '—'}</p>
                    <p className="text-xs text-neutral-muted">{c.destinatario}</p>
                  </td>
                  <td className="text-neutral-secondary">
                    <span className="inline-flex items-center gap-1">
                      {c.tieneAdjunto && <Paperclip size={13} className="text-accent-400" />}
                      {c.asunto}
                    </span>
                    {c.estado === 'ERROR' && c.error && (
                      <p className="text-xs text-red-400">{c.error}</p>
                    )}
                  </td>
                  <td>
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${ESTILO_ESTADO[c.estado] ?? 'bg-dark-lighter text-neutral-secondary'}`}>
                      {ETIQUETA_ESTADO[c.estado] ?? c.estado}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="btn btn-secondary">
            ← Anterior
          </button>
          <span className="text-neutral-secondary">Página {data.page + 1} de {data.totalPages}</span>
          <button
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="btn btn-secondary"
          >
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}

export default AdminCorreosPage
