import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Mail, Paperclip, Settings, Send } from 'lucide-react'
import { correoService } from '../services/correoService'
import { useAuthStore } from '../store/authStore'
import AdminTabs from '../components/admin/AdminTabs'
import ErrorDeCarga from '../components/common/ErrorDeCarga'

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

const FORM_VACIO = { host: 'smtp.gmail.com', port: 587, username: '', password: '', from: '' }

function AdminCorreosPage() {
  const { usuario } = useAuthStore()
  const esAdmin = usuario?.rol === 'Admin'
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(FORM_VACIO)
  const [prueba, setPrueba] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: config } = useQuery({
    queryKey: ['correos-config'],
    queryFn: correoService.getConfig,
    enabled: esAdmin,
  })

  useEffect(() => {
    if (config) {
      setForm({
        host: config.host || 'smtp.gmail.com',
        port: config.port || 587,
        username: config.username || '',
        password: '',
        from: config.from || '',
      })
    }
  }, [config])

  const onError = (err) =>
    setError(err.response?.data?.detail ?? 'La operación falló. Intente de nuevo.')

  const guardarMutation = useMutation({
    mutationFn: () => correoService.guardarConfig({ ...form, password: form.password || null }),
    onSuccess: () => {
      setError(null)
      setMensaje('Configuración de correo guardada.')
      queryClient.invalidateQueries({ queryKey: ['correos-config'] })
    },
    onError,
  })

  const pruebaMutation = useMutation({
    mutationFn: () => correoService.enviarPrueba(prueba.trim()),
    onSuccess: (r) => {
      if (r.exito) {
        setError(null)
        setMensaje(r.mensaje)
      } else {
        setMensaje(null)
        setError(r.mensaje)
      }
      queryClient.invalidateQueries({ queryKey: ['admin-correos'] })
    },
    onError,
  })

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-correos', page],
    queryFn: () => correoService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const activo = config?.configurado

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Correos</span>
      </h1>
      <AdminTabs />

      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {error && <p className="alerta-error" role="alert">{error}</p>}

      {esAdmin && (
        <div className="card mb-8">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h3 className="inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
              <Settings size={18} className="text-accent-400" /> Configuración de envío (SMTP)
            </h3>
            <span
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                activo ? 'bg-emerald-400/20 text-emerald-400' : 'bg-primary-400/20 text-primary-300'
              }`}
            >
              {activo ? 'Envío real activo' : 'Modo simulado (sin credenciales)'}
            </span>
          </div>

          <p className="mb-4 text-sm text-neutral-secondary">
            Para enviar correos de verdad con Gmail necesitás una{' '}
            <a
              href="https://myaccount.google.com/apppasswords"
              target="_blank"
              rel="noreferrer"
              className="text-primary-300 underline hover:text-accent-400"
            >
              contraseña de aplicación
            </a>{' '}
            (con la verificación en 2 pasos activada). Si dejás esto vacío, los correos quedan en{' '}
            <span className="font-semibold text-primary-300">Simulado</span>.
          </p>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="host">Servidor SMTP</label>
              <input id="host" value={form.host}
                onChange={(e) => setForm({ ...form, host: e.target.value })} className="input" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="port">Puerto</label>
              <input id="port" type="number" value={form.port}
                onChange={(e) => setForm({ ...form, port: Number(e.target.value) })} className="input" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="username">Usuario (tu casilla)</label>
              <input id="username" type="email" placeholder="tucorreo@gmail.com" value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })} className="input" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="password">Contraseña de aplicación</label>
              <input id="password" type="password"
                placeholder={config?.tienePassword ? '•••••••• (guardada — dejar vacío para conservar)' : '16 caracteres'}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} className="input" />
            </div>
            <div className="md:col-span-2">
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="from">Remitente (opcional)</label>
              <input id="from" type="email" placeholder="por defecto: tu casilla" value={form.from}
                onChange={(e) => setForm({ ...form, from: e.target.value })} className="input" />
            </div>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              onClick={() => guardarMutation.mutate()}
              disabled={guardarMutation.isPending || !form.host || !form.username}
              title={!form.host || !form.username ? 'Completá al menos el servidor y el usuario' : undefined}
              className="btn btn-primary"
            >
              {guardarMutation.isPending ? 'Guardando…' : 'Guardar configuración'}
            </button>

            <span className="mx-1 h-6 w-px bg-dark-border" />

            <input
              type="email"
              placeholder="Enviar prueba a…"
              value={prueba}
              onChange={(e) => setPrueba(e.target.value)}
              className="input max-w-xs py-2 text-sm"
             aria-label="Enviar prueba a"/>
            <button
              onClick={() => pruebaMutation.mutate()}
              disabled={pruebaMutation.isPending || !prueba.trim()}
              title={!prueba.trim() ? 'Escribí un correo de destino para la prueba' : undefined}
              className="btn btn-secondary inline-flex items-center gap-2"
            >
              <Send size={16} /> {pruebaMutation.isPending ? 'Enviando…' : 'Enviar prueba'}
            </button>
          </div>
        </div>
      )}

      <div className="mb-6 rounded-md border border-primary-400/30 bg-primary-400/5 p-4 text-sm text-neutral-secondary">
        <p className="inline-flex items-center gap-2 font-semibold text-neutral-text">
          <Mail size={16} className="text-accent-400" /> Bandeja de salida
        </p>
        <p className="mt-1">
          Cada factura, alta y cambio de contraseña genera una notificación por correo.
          Estado <span className="font-semibold text-primary-300">Simulado</span> = registrado localmente
          sin envío real; <span className="font-semibold text-emerald-400">Enviado</span> = salió por SMTP.
        </p>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando correos…</p>}
      {isError && <ErrorDeCarga que="los correos" onReintentar={refetch} />}

      {data && data.content.length === 0 && (
        <p className="text-neutral-muted">Todavía no se generó ningún correo.</p>
      )}

      {data && data.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin tabla-apilable">
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
                  <td data-label="Fecha" className="whitespace-nowrap text-neutral-secondary">
                    {new Date(c.fecha).toLocaleString('es-PY')}
                  </td>
                  <td data-label="Tipo" className="text-neutral-text">{c.tipoDescripcion}</td>
                  <td data-label="Destinatario">
                    <p className="text-neutral-text">{c.destinatarioNombre ?? '—'}</p>
                    <p className="text-xs text-neutral-muted">{c.destinatario}</p>
                  </td>
                  <td data-label="Asunto" className="text-neutral-secondary">
                    <span className="inline-flex items-center gap-1">
                      {c.tieneAdjunto && <Paperclip size={13} className="text-accent-400" />}
                      {c.asunto}
                    </span>
                    {c.estado === 'ERROR' && c.error && (
                      <p className="text-xs text-red-400">{c.error}</p>
                    )}
                  </td>
                  <td data-label="Estado">
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
