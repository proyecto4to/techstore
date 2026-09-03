import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { UserPlus, KeyRound } from 'lucide-react'
import { usuarioService } from '../services/usuarioService'
import { empresaService } from '../services/empresaService'
import { useAuthStore } from '../store/authStore'
import AdminTabs from '../components/admin/AdminTabs'

/** Quiénes emiten documentos. Un Cliente nunca factura, así que no lleva punto. */
const ROLES_QUE_EMITEN = ['Admin', 'Cajero']

const FORM_VACIO = { nombre: '', apellido: '', email: '', password: '', rol: 'Cajero' }

const ESTILO_ROL = {
  Admin: 'bg-secondary-400/20 text-secondary-400',
  Cajero: 'bg-accent-400/20 text-accent-400',
  Cliente: 'bg-primary-400/20 text-primary-300',
  Repartidor: 'bg-emerald-400/20 text-emerald-300',
}

function AdminUsuariosPage() {
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(FORM_VACIO)
  const [resetId, setResetId] = useState(null)
  const [resetPassword, setResetPassword] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const { usuario: usuarioActual } = useAuthStore()
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-usuarios', page],
    queryFn: () => usuarioService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const { data: puntos = [] } = useQuery({
    queryKey: ['puntos-expedicion'],
    queryFn: () => empresaService.puntosExpedicion(),
  })

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['admin-usuarios'] })

  const onError = (err) => {
    const errores = err.response?.data?.errores
    setError(
      errores
        ? Object.entries(errores).map(([campo, msj]) => `${campo}: ${msj}`).join(' · ')
        : err.response?.data?.detail ?? 'La operación falló.',
    )
  }

  const crearMutation = useMutation({
    mutationFn: (f) => usuarioService.crear(f),
    onSuccess: (nuevo) => {
      setError(null)
      setMensaje(`Usuario ${nuevo.email} creado con rol ${nuevo.rol}.`)
      setForm(FORM_VACIO)
      refrescar()
    },
    onError,
  })

  const activoMutation = useMutation({
    mutationFn: ({ id, activo }) => usuarioService.cambiarActivo(id, activo),
    onSuccess: (u) => {
      setError(null)
      setMensaje(`Usuario ${u.email} ${u.activo ? 'activado' : 'desactivado'}.`)
      refrescar()
    },
    onError,
  })

  const puntoMutation = useMutation({
    mutationFn: ({ id, puntoExpedicionId }) =>
      usuarioService.asignarPuntoExpedicion(id, puntoExpedicionId),
    onSuccess: (u) => {
      setError(null)
      setMensaje(
        u.puntoExpedicion
          ? `${u.email} emite desde ${u.puntoExpedicion}.`
          : `${u.email} vuelve a emitir desde el punto por defecto.`,
      )
      refrescar()
    },
    onError,
  })

  const resetMutation = useMutation({
    mutationFn: ({ id, password }) => usuarioService.resetearPassword(id, password),
    onSuccess: (u) => {
      setError(null)
      setMensaje(`Contraseña de ${u.email} restablecida.`)
      setResetId(null)
      setResetPassword('')
    },
    onError,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Usuarios</span>
      </h1>
      <AdminTabs />

      {mensaje && (
        <p className="alerta-ok" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          crearMutation.mutate({
            nombre: form.nombre.trim(),
            apellido: form.apellido.trim(),
            email: form.email.trim(),
            password: form.password,
            rol: form.rol,
          })
        }}
        className="card mb-8 grid grid-cols-1 gap-4 md:grid-cols-5"
      >
        <h3 className="col-span-full inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
          <UserPlus size={18} className="text-accent-400" /> Nuevo usuario
        </h3>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="unombre">
            Nombre *
          </label>
          <input id="unombre" required maxLength={100} value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="uapellido">
            Apellido *
          </label>
          <input id="uapellido" required maxLength={100} value={form.apellido}
            onChange={(e) => setForm({ ...form, apellido: e.target.value })} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="uemail">
            Email *
          </label>
          <input id="uemail" type="email" required value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="upassword">
            Contraseña *
          </label>
          <input id="upassword" type="password" required minLength={8} value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="urol">
            Rol
          </label>
          <select id="urol" value={form.rol}
            onChange={(e) => setForm({ ...form, rol: e.target.value })} className="input">
            <option value="Cajero">Cajero</option>
            <option value="Admin">Admin</option>
            <option value="Cliente">Cliente</option>
            <option value="Repartidor">Repartidor</option>
          </select>
        </div>

        <div className="col-span-full">
          <button type="submit" disabled={crearMutation.isPending} className="btn btn-primary">
            {crearMutation.isPending ? 'Creando…' : 'Crear usuario'}
          </button>
        </div>
      </form>

      {isLoading && <p className="text-neutral-muted">Cargando usuarios…</p>}

      {data && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Emite desde</th>
                <th>Estado</th>
                <th>Último acceso</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => {
                const esYo = u.email === usuarioActual?.email
                return (
                  <tr key={u.id} className={u.activo ? '' : 'opacity-50'}>
                    <td>
                      <p className="font-medium text-neutral-text">
                        {u.nombre} {u.apellido} {esYo && <span className="text-xs text-accent-400">(vos)</span>}
                      </p>
                      <p className="text-xs text-neutral-muted">{u.email}</p>
                    </td>
                    <td>
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${ESTILO_ROL[u.rol] ?? 'bg-dark-lighter text-neutral-secondary'}`}>
                        {u.rol}
                      </span>
                    </td>
                    <td>
                      {ROLES_QUE_EMITEN.includes(u.rol) ? (
                        <select
                          aria-label={`Punto de expedición de ${u.email}`}
                          value={u.puntoExpedicionId ?? ''}
                          disabled={puntoMutation.isPending}
                          onChange={(e) =>
                            puntoMutation.mutate({
                              id: u.id,
                              puntoExpedicionId: e.target.value ? Number(e.target.value) : null,
                            })
                          }
                          className="input w-52 py-1 text-sm"
                        >
                          <option value="">Punto por defecto</option>
                          {puntos
                            // Uno desactivado no se puede asignar, pero si alguien
                            // ya lo tiene hay que mostrarlo o el select mentiría.
                            .filter((p) => p.activo || p.id === u.puntoExpedicionId)
                            .map((p) => (
                              <option key={p.id} value={p.id}>
                                {p.prefijo} · {p.nombre}
                                {p.activo ? '' : ' (inactivo)'}
                              </option>
                            ))}
                        </select>
                      ) : (
                        <span className="text-sm text-neutral-muted">—</span>
                      )}
                    </td>
                    <td>
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${u.activo ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'}`}>
                        {u.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td className="text-neutral-secondary">
                      {u.lastLogin ? new Date(u.lastLogin).toLocaleString('es-PY') : 'Nunca'}
                    </td>
                    <td>
                      <div className="flex flex-wrap items-center gap-2">
                        {!esYo && (
                          <button
                            onClick={() => activoMutation.mutate({ id: u.id, activo: !u.activo })}
                            disabled={activoMutation.isPending}
                            className={`btn text-sm ${u.activo ? 'btn-danger' : 'btn-primary'}`}
                          >
                            {u.activo ? 'Desactivar' : 'Activar'}
                          </button>
                        )}
                        {resetId === u.id ? (
                          <span className="inline-flex items-center gap-2">
                            <input
                              type="password"
                              placeholder="Nueva contraseña"
                              minLength={8}
                              value={resetPassword}
                              onChange={(e) => setResetPassword(e.target.value)}
                              className="input w-44 py-1 text-sm"
                            />
                            <button
                              onClick={() => resetMutation.mutate({ id: u.id, password: resetPassword })}
                              disabled={resetPassword.length < 8 || resetMutation.isPending}
                              className="btn btn-primary text-sm"
                            >
                              Guardar
                            </button>
                            <button
                              onClick={() => { setResetId(null); setResetPassword('') }}
                              className="btn btn-secondary text-sm"
                            >
                              ✕
                            </button>
                          </span>
                        ) : (
                          <button
                            onClick={() => { setResetId(u.id); setResetPassword('') }}
                            className="btn btn-secondary inline-flex items-center gap-1 text-sm"
                          >
                            <KeyRound size={14} /> Resetear clave
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
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
          <button onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))} disabled={page >= data.totalPages - 1} className="btn btn-secondary">
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}

export default AdminUsuariosPage
