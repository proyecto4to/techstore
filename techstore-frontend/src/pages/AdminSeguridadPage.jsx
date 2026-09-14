import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Shield, Users, KeyRound, Boxes, ScrollText } from 'lucide-react'
import { seguridadService } from '../services/seguridadService'
import { usuarioService } from '../services/usuarioService'
import { auditoriaService } from '../services/auditoriaService'
import { useAuthStore } from '../store/authStore'
import { usePermisos } from '../hooks/usePermisos'
import AdminTabs from '../components/admin/AdminTabs'
import ErrorDeCarga from '../components/common/ErrorDeCarga'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

/**
 * Seguridad: quiénes son, qué rol tienen y qué puede hacer cada uno.
 *
 * Todo lo que se ve acá lo decide el backend. La pantalla no conoce ninguna
 * regla: pide el catálogo de módulos y acciones, pide los permisos y los
 * dibuja. Por eso agregar un módulo en el servidor aparece solo, y por eso
 * destildar una casilla cierra de verdad el endpoint y no solo el botón.
 */

const SECCIONES = [
  { id: 'usuarios', label: 'Usuarios', icono: Users },
  { id: 'roles', label: 'Roles', icono: Shield },
  { id: 'permisos', label: 'Permisos', icono: KeyRound },
  { id: 'modulos', label: 'Módulos', icono: Boxes },
  { id: 'auditoria', label: 'Auditoría', icono: ScrollText },
]

const ESTILO_ORIGEN = {
  ROL: 'text-neutral-muted',
  CONCEDIDO: 'text-emerald-400',
  RESTRINGIDO: 'text-red-400',
  PREDETERMINADO: 'text-amber-300',
}

function mensajeDeError(err) {
  const errores = err?.response?.data?.errores
  if (errores) {
    return Object.entries(errores).map(([campo, msj]) => `${campo}: ${msj}`).join(' · ')
  }
  return err?.response?.data?.detail ?? 'La operación falló.'
}

function fecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-PY', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function AdminSeguridadPage() {
  const [seccion, setSeccion] = useState('usuarios')
  const [aviso, setAviso] = useState(null)
  const [error, setError] = useState(null)

  const avisar = (texto) => {
    setError(null)
    setAviso(texto)
  }
  const fallar = (err) => {
    setAviso(null)
    setError(mensajeDeError(err))
  }

  return (
    <div className="container-app py-8">
      <AdminTabs />

      <header className="mb-6">
        <h1 className="flex items-center gap-2 text-2xl font-bold text-neutral-text">
          <Shield className="h-6 w-6 text-secondary-400" />
          Seguridad
        </h1>
        <p className="mt-1 text-sm text-neutral-muted">
          Quién entra, con qué rol y qué puede hacer. Lo que se configura acá lo aplica el
          servidor en cada operación, en la Web y en la aplicación móvil.
        </p>
      </header>

      <nav aria-label="Secciones de seguridad" className="mb-6 flex flex-wrap gap-2">
        {SECCIONES.map(({ id, label, icono: Icono }) => (
          <button
            key={id}
            type="button"
            onClick={() => { setSeccion(id); setAviso(null); setError(null) }}
            aria-current={seccion === id ? 'page' : undefined}
            className={`flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition-all ${
              seccion === id
                ? 'bg-gradient-to-r from-primary-400 to-secondary-400 text-white'
                : 'text-neutral-secondary hover:bg-primary-400/10 hover:text-primary-300'
            }`}
          >
            <Icono className="h-4 w-4" />
            {label}
          </button>
        ))}
      </nav>

      {aviso && (
        <p role="status" className="mb-4 rounded-md bg-emerald-400/10 px-4 py-2 text-sm text-emerald-300">
          {aviso}
        </p>
      )}
      {error && (
        <p role="alert" className="mb-4 rounded-md bg-red-400/10 px-4 py-2 text-sm text-red-300">
          {error}
        </p>
      )}

      {seccion === 'usuarios' && <SeccionUsuarios onAviso={avisar} onError={fallar} />}
      {seccion === 'roles' && <SeccionRoles onAviso={avisar} onError={fallar} />}
      {seccion === 'permisos' && <SeccionPermisos onAviso={avisar} onError={fallar} />}
      {seccion === 'modulos' && <SeccionModulos />}
      {seccion === 'auditoria' && <SeccionAuditoria />}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Usuarios
// ---------------------------------------------------------------------------

function SeccionUsuarios({ onAviso, onError }) {
  const [texto, setTexto] = useState('')
  const [rol, setRol] = useState('')
  const [activo, setActivo] = useState('')
  const [page, setPage] = useState(0)
  const [elegido, setElegido] = useState(null)
  const busqueda = useDebouncedValue(texto, 300)
  const { usuario: yo } = useAuthStore()
  const queryClient = useQueryClient()

  const { data: roles = [] } = useQuery({
    queryKey: ['seguridad-roles'],
    queryFn: () => seguridadService.rolesDetallados(),
  })

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['seguridad-usuarios', busqueda, rol, activo, page],
    queryFn: () => usuarioService.listar({
      q: busqueda,
      rol,
      activo: activo === '' ? null : activo === 'activos',
      page,
    }),
    placeholderData: keepPreviousData,
  })

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['seguridad-usuarios'] })

  const rolMutation = useMutation({
    mutationFn: ({ id, nuevoRol }) => usuarioService.asignarRol(id, nuevoRol),
    onSuccess: (u) => { onAviso(`${u.email} ahora es ${u.rol}.`); refrescar() },
    onError,
  })

  const activoMutation = useMutation({
    mutationFn: ({ id, valor }) => usuarioService.cambiarActivo(id, valor),
    onSuccess: (u) => {
      onAviso(`${u.email} quedó ${u.activo ? 'activo' : 'inactivo'}.`)
      refrescar()
    },
    onError,
  })

  return (
    <section>
      <div className="card mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <div>
          <label htmlFor="buscar" className="mb-1 block text-sm text-neutral-secondary">Buscar</label>
          <input
            id="buscar"
            value={texto}
            onChange={(e) => { setTexto(e.target.value); setPage(0) }}
            placeholder="Nombre, apellido o correo"
            className="input"
          />
        </div>
        <div>
          <label htmlFor="frol" className="mb-1 block text-sm text-neutral-secondary">Rol</label>
          <select id="frol" value={rol} onChange={(e) => { setRol(e.target.value); setPage(0) }} className="input">
            <option value="">Todos</option>
            {roles.map((r) => <option key={r.nombre} value={r.nombre}>{r.nombre}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="festado" className="mb-1 block text-sm text-neutral-secondary">Estado</label>
          <select id="festado" value={activo} onChange={(e) => { setActivo(e.target.value); setPage(0) }} className="input">
            <option value="">Todos</option>
            <option value="activos">Activos</option>
            <option value="inactivos">Inactivos</option>
          </select>
        </div>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando usuarios…</p>}
      {isError && <ErrorDeCarga que="los usuarios" onReintentar={refetch} />}

      {data && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin tabla-apilable">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Último acceso</th>
                <th>Permisos</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => {
                const esYo = u.email === yo?.email
                return (
                  <tr key={u.id} className={u.activo ? '' : 'opacity-50'}>
                    <td data-label="Usuario">
                      <p className="font-medium text-neutral-text">
                        {u.nombre} {u.apellido}{' '}
                        {esYo && <span className="text-xs text-accent-400">(vos)</span>}
                      </p>
                      <p className="text-xs text-neutral-muted">{u.email}</p>
                    </td>
                    <td data-label="Rol">
                      <select
                        aria-label={`Rol de ${u.email}`}
                        value={u.rol}
                        disabled={esYo || rolMutation.isPending}
                        onChange={(e) => rolMutation.mutate({ id: u.id, nuevoRol: e.target.value })}
                        className="input w-40 py-1 text-sm"
                      >
                        {/* Uno desactivado no se puede asignar, pero si alguien
                            ya lo tiene hay que mostrarlo o el select mentiría. */}
                        {roles
                          .filter((r) => r.activo || r.nombre === u.rol)
                          .map((r) => (
                            <option key={r.nombre} value={r.nombre}>
                              {r.nombre}{r.activo ? '' : ' (inactivo)'}
                            </option>
                          ))}
                      </select>
                    </td>
                    <td data-label="Estado">
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        u.activo ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
                      }`}>
                        {u.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td data-label="Último acceso" className="text-sm text-neutral-secondary">
                      {u.lastLogin ? fecha(u.lastLogin) : 'Nunca'}
                    </td>
                    <td data-label="Permisos">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => setElegido(elegido === u.id ? null : u.id)}
                          className="btn btn-secondary px-3 py-1 text-xs"
                        >
                          {elegido === u.id ? 'Ocultar' : 'Ver permisos'}
                        </button>
                        {!esYo && (
                          <button
                            type="button"
                            disabled={activoMutation.isPending}
                            onClick={() => activoMutation.mutate({ id: u.id, valor: !u.activo })}
                            className="btn btn-outline px-3 py-1 text-xs"
                          >
                            {u.activo ? 'Desactivar' : 'Activar'}
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
        <div className="mt-4 flex items-center gap-3">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="btn btn-outline px-3 py-1 text-sm">
            Anterior
          </button>
          <span className="text-sm text-neutral-muted">Página {page + 1} de {data.totalPages}</span>
          <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="btn btn-outline px-3 py-1 text-sm">
            Siguiente
          </button>
        </div>
      )}

      {elegido && <PermisosDelUsuario usuarioId={elegido} onAviso={onAviso} onError={onError} />}
    </section>
  )
}

/**
 * Los permisos efectivos de una persona, con el origen de cada uno y la
 * posibilidad de hacerle una excepción.
 */
function PermisosDelUsuario({ usuarioId, onAviso, onError }) {
  const [motivo, setMotivo] = useState('')
  const queryClient = useQueryClient()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['permisos-usuario', usuarioId],
    queryFn: () => seguridadService.permisosDeUsuario(usuarioId),
  })

  const guardar = useMutation({
    mutationFn: ({ modulo, accion, permitido }) =>
      seguridadService.guardarPermisoUsuario(usuarioId, { modulo, accion, permitido, motivo }),
    onSuccess: (nuevos) => {
      queryClient.setQueryData(['permisos-usuario', usuarioId], nuevos)
      queryClient.invalidateQueries({ queryKey: ['mis-permisos'] })
      onAviso('Permiso actualizado.')
    },
    onError,
  })

  const heredar = useMutation({
    mutationFn: ({ modulo, accion }) =>
      seguridadService.quitarPermisoUsuario(usuarioId, modulo, accion),
    onSuccess: (nuevos) => {
      queryClient.setQueryData(['permisos-usuario', usuarioId], nuevos)
      queryClient.invalidateQueries({ queryKey: ['mis-permisos'] })
      onAviso('La excepción se quitó: vuelve a valer lo que diga el rol.')
    },
    onError,
  })

  if (isLoading) return <p className="mt-6 text-neutral-muted">Cargando permisos…</p>
  if (isError) return <div className="mt-6"><ErrorDeCarga que="los permisos" onReintentar={refetch} /></div>

  return (
    <section className="card mt-6">
      <header className="mb-4">
        <h2 className="text-lg font-semibold text-neutral-text">
          Permisos efectivos de {data.nombre}
        </h2>
        <p className="text-sm text-neutral-muted">
          Rol {data.rol}. Lo que está en verde se le dio a esta persona; lo rojo se le quitó
          aunque el rol lo permita. El resto lo hereda del rol.
        </p>
      </header>

      <div className="mb-4 max-w-lg">
        <label htmlFor="motivo" className="mb-1 block text-sm text-neutral-secondary">
          Motivo de la próxima excepción (opcional)
        </label>
        <input
          id="motivo"
          value={motivo}
          maxLength={200}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="Por qué se le concede o se le quita"
          className="input"
        />
      </div>

      <div className="overflow-x-auto">
        <table className="tabla-admin">
          <thead>
            <tr>
              <th>Módulo</th>
              {data.modulos[0]?.acciones.map((a) => <th key={a.accion}>{a.etiqueta}</th>)}
            </tr>
          </thead>
          <tbody>
            {data.modulos.map((m) => (
              <tr key={m.modulo}>
                <td className="font-medium text-neutral-text">{m.etiqueta}</td>
                {m.acciones.map((a) => (
                  <td key={a.accion}>
                    <div className="flex flex-col gap-1">
                      <span className={`text-sm font-semibold ${a.permitido ? 'text-emerald-400' : 'text-red-400'}`}>
                        {a.permitido ? 'Sí' : 'No'}
                      </span>
                      <span
                        className={`text-[11px] ${ESTILO_ORIGEN[a.origen] ?? 'text-neutral-muted'}`}
                        title={a.motivo ?? undefined}
                      >
                        {a.etiquetaOrigen}
                      </span>
                      <div className="flex gap-1">
                        <button
                          type="button"
                          disabled={guardar.isPending}
                          onClick={() => guardar.mutate({ modulo: m.modulo, accion: a.accion, permitido: true })}
                          className="rounded border border-emerald-400/40 px-2 py-0.5 text-[11px] text-emerald-300 hover:bg-emerald-400/10"
                        >
                          Conceder
                        </button>
                        <button
                          type="button"
                          disabled={guardar.isPending}
                          onClick={() => guardar.mutate({ modulo: m.modulo, accion: a.accion, permitido: false })}
                          className="rounded border border-red-400/40 px-2 py-0.5 text-[11px] text-red-300 hover:bg-red-400/10"
                        >
                          Restringir
                        </button>
                        {a.origen !== 'ROL' && a.origen !== 'PREDETERMINADO' && (
                          <button
                            type="button"
                            disabled={heredar.isPending}
                            onClick={() => heredar.mutate({ modulo: m.modulo, accion: a.accion })}
                            className="rounded border border-dark-border px-2 py-0.5 text-[11px] text-neutral-secondary hover:bg-primary-400/10"
                          >
                            Heredar
                          </button>
                        )}
                      </div>
                    </div>
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Roles
// ---------------------------------------------------------------------------

function SeccionRoles({ onAviso, onError }) {
  const [form, setForm] = useState({ nombre: '', descripcion: '' })
  const [verUsuariosDe, setVerUsuariosDe] = useState(null)
  const queryClient = useQueryClient()
  // Un administrador puede tener restringido «crear» dentro de Seguridad: en
  // ese caso no se le ofrece el alta. El backend lo rechaza igual.
  const { puede } = usePermisos()
  const puedeCrear = puede('SEGURIDAD', 'CREAR')
  const puedeEditar = puede('SEGURIDAD', 'EDITAR')

  const { data: roles, isLoading, isError, refetch } = useQuery({
    queryKey: ['seguridad-roles'],
    queryFn: () => seguridadService.rolesDetallados(),
  })

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['seguridad-roles'] })

  const crear = useMutation({
    mutationFn: () => seguridadService.crearRol(form),
    onSuccess: (rol) => {
      onAviso(`Rol ${rol.nombre} creado. Nace sin permisos: habilitale lo que necesite.`)
      setForm({ nombre: '', descripcion: '' })
      refrescar()
    },
    onError,
  })

  const editar = useMutation({
    mutationFn: ({ nombre, cambios }) => seguridadService.editarRol(nombre, cambios),
    onSuccess: (rol) => {
      onAviso(`Rol ${rol.nombre} actualizado.`)
      refrescar()
    },
    onError,
  })

  const { data: usuariosDelRol } = useQuery({
    queryKey: ['usuarios-del-rol', verUsuariosDe],
    queryFn: () => seguridadService.usuariosDelRol(verUsuariosDe),
    enabled: Boolean(verUsuariosDe),
  })

  return (
    <section>
      {puedeCrear && (
      <form
        onSubmit={(e) => { e.preventDefault(); crear.mutate() }}
        className="card mb-6 grid grid-cols-1 gap-4 md:grid-cols-3"
      >
        <div>
          <label htmlFor="rnombre" className="mb-1 block text-sm text-neutral-secondary">Nombre</label>
          <input
            id="rnombre" required maxLength={50} value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            className="input" placeholder="Supervisor"
          />
        </div>
        <div>
          <label htmlFor="rdesc" className="mb-1 block text-sm text-neutral-secondary">Descripción</label>
          <input
            id="rdesc" maxLength={500} value={form.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
            className="input" placeholder="Para qué sirve este rol"
          />
        </div>
        <div className="flex items-end">
          <button type="submit" disabled={crear.isPending} className="btn btn-primary">
            Crear rol
          </button>
        </div>
      </form>
      )}

      {isLoading && <p className="text-neutral-muted">Cargando roles…</p>}
      {isError && <ErrorDeCarga que="los roles" onReintentar={refetch} />}

      {roles && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin tabla-apilable">
            <thead>
              <tr>
                <th>Rol</th>
                <th>Descripción</th>
                <th>Personas</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((r) => (
                <tr key={r.nombre} className={r.activo ? '' : 'opacity-50'}>
                  <td data-label="Rol" className="font-medium text-neutral-text">
                    {r.nombre}
                    {r.administrador && (
                      <span className="ml-2 rounded-full bg-secondary-400/20 px-2 py-0.5 text-[11px] text-secondary-400">
                        administra Seguridad
                      </span>
                    )}
                  </td>
                  <td data-label="Descripción" className="text-sm text-neutral-secondary">
                    {r.descripcion ?? '—'}
                  </td>
                  <td data-label="Personas">{r.usuarios}</td>
                  <td data-label="Estado">
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
                      r.activo ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
                    }`}>
                      {r.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td data-label="Acciones">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => setVerUsuariosDe(verUsuariosDe === r.nombre ? null : r.nombre)}
                        className="btn btn-secondary px-3 py-1 text-xs"
                      >
                        Ver personas
                      </button>
                      {!r.administrador && puedeEditar && (
                        <button
                          type="button"
                          disabled={editar.isPending}
                          onClick={() => editar.mutate({ nombre: r.nombre, cambios: { activo: !r.activo } })}
                          className="btn btn-outline px-3 py-1 text-xs"
                        >
                          {r.activo ? 'Desactivar' : 'Activar'}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {verUsuariosDe && usuariosDelRol && (
        <div className="card mt-6">
          <h2 className="mb-2 text-lg font-semibold text-neutral-text">
            Personas con el rol {verUsuariosDe}
          </h2>
          {usuariosDelRol.length === 0 ? (
            <p className="text-sm text-neutral-muted">Todavía no lo tiene nadie.</p>
          ) : (
            <ul className="space-y-1 text-sm text-neutral-secondary">
              {usuariosDelRol.map((u) => (
                <li key={u.id}>
                  {u.nombre} {u.apellido} · {u.email}
                  {!u.activo && <span className="ml-2 text-red-400">inactivo</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------
// Permisos por rol
// ---------------------------------------------------------------------------

function SeccionPermisos({ onAviso, onError }) {
  const [rol, setRol] = useState(null)
  const queryClient = useQueryClient()

  const { data: roles = [] } = useQuery({
    queryKey: ['seguridad-roles'],
    queryFn: () => seguridadService.rolesDetallados(),
  })

  const elegido = rol ?? roles[0]?.nombre ?? null

  const { data: matriz, isLoading, isError, refetch } = useQuery({
    queryKey: ['matriz-permisos', elegido],
    queryFn: () => seguridadService.matriz(elegido),
    enabled: Boolean(elegido),
  })

  const guardar = useMutation({
    mutationFn: ({ modulo, accion, permitido }) =>
      seguridadService.guardarPermisoRol(elegido, { modulo, accion, permitido }),
    onSuccess: (nueva) => {
      queryClient.setQueryData(['matriz-permisos', elegido], nueva)
      queryClient.invalidateQueries({ queryKey: ['mis-permisos'] })
      onAviso('Permiso guardado.')
    },
    onError,
  })

  const pantalla = useMutation({
    mutationFn: ({ nombre, permitido }) =>
      seguridadService.guardarPantalla(elegido, { pantalla: nombre, permitido }),
    onSuccess: (nueva) => {
      queryClient.setQueryData(['matriz-permisos', elegido], nueva)
      queryClient.invalidateQueries({ queryKey: ['mis-permisos'] })
      onAviso('Pantalla actualizada.')
    },
    onError,
  })

  return (
    <section>
      <div className="mb-6 flex flex-wrap gap-2">
        {roles.map((r) => (
          <button
            key={r.nombre}
            type="button"
            onClick={() => setRol(r.nombre)}
            className={`rounded-md px-4 py-2 text-sm font-semibold ${
              elegido === r.nombre
                ? 'bg-primary-400 text-white'
                : 'bg-dark-lighter text-neutral-secondary hover:bg-primary-400/10'
            }`}
          >
            {r.nombre}
          </button>
        ))}
      </div>

      <p className="mb-4 text-sm text-neutral-muted">
        Destildar una casilla cierra ese endpoint para el rol. Tildarla no abre lo que el backend
        ya niega por código: los permisos pueden restringir, nunca ampliar.
      </p>

      {isLoading && <p className="text-neutral-muted">Cargando la matriz…</p>}
      {isError && <ErrorDeCarga que="la matriz de permisos" onReintentar={refetch} />}

      {matriz && (
        <>
          <div className="card overflow-x-auto p-0">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th>Módulo</th>
                  {matriz.modulos[0]?.acciones.map((a) => <th key={a.accion}>{a.etiqueta}</th>)}
                </tr>
              </thead>
              <tbody>
                {matriz.modulos.map((m) => (
                  <tr key={m.modulo}>
                    <td className="font-medium text-neutral-text">{m.etiqueta}</td>
                    {m.acciones.map((a) => (
                      <td key={a.accion}>
                        <input
                          type="checkbox"
                          checked={a.permitido}
                          disabled={guardar.isPending}
                          aria-label={`${a.etiqueta} en ${m.etiqueta}`}
                          onChange={(e) => guardar.mutate({
                            modulo: m.modulo, accion: a.accion, permitido: e.target.checked,
                          })}
                          className="h-4 w-4 accent-primary-400"
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {matriz.pantallas.length > 0 && (
            <div className="card mt-6">
              <h2 className="mb-1 text-lg font-semibold text-neutral-text">Pantallas de la app móvil</h2>
              <p className="mb-3 text-sm text-neutral-muted">
                Qué ve este rol al abrir la aplicación. Esconder una pantalla es comodidad;
                lo que restringe de verdad es el permiso del módulo.
              </p>
              <div className="flex flex-wrap gap-3">
                {matriz.pantallas.map((p) => (
                  <label key={p.pantalla} className="flex items-center gap-2 text-sm text-neutral-secondary">
                    <input
                      type="checkbox"
                      checked={p.permitido}
                      disabled={pantalla.isPending}
                      onChange={(e) => pantalla.mutate({ nombre: p.pantalla, permitido: e.target.checked })}
                      className="h-4 w-4 accent-primary-400"
                    />
                    {p.pantalla}
                  </label>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------
// Módulos y acciones
// ---------------------------------------------------------------------------

function SeccionModulos() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['seguridad-catalogo'],
    queryFn: () => seguridadService.catalogo(),
  })

  if (isLoading) return <p className="text-neutral-muted">Cargando el catálogo…</p>
  if (isError) return <ErrorDeCarga que="el catálogo de módulos" onReintentar={refetch} />

  const rutasDe = (modulo) => data.rutas.filter((r) => r.modulo === modulo).map((r) => r.prefijo)

  return (
    <section>
      <p className="mb-4 text-sm text-neutral-muted">
        Los módulos y las acciones los define el backend. Esta pantalla los muestra tal como
        llegan: cuando se agrega uno nuevo aparece acá solo, sin publicar una versión de la Web.
      </p>

      <div className="card mb-6">
        <h2 className="mb-2 text-lg font-semibold text-neutral-text">Acciones</h2>
        <div className="flex flex-wrap gap-2">
          {data.acciones.map((a) => (
            <span key={a.accion} className="rounded-full bg-primary-400/15 px-3 py-1 text-sm text-primary-300">
              {a.etiqueta}
            </span>
          ))}
        </div>
      </div>

      <div className="card overflow-x-auto p-0">
        <table className="tabla-admin tabla-apilable">
          <thead>
            <tr>
              <th>Módulo</th>
              <th>Qué gobierna en la API</th>
            </tr>
          </thead>
          <tbody>
            {data.modulos.map((m) => {
              const rutas = rutasDe(m.modulo)
              return (
                <tr key={m.modulo}>
                  <td data-label="Módulo" className="font-medium text-neutral-text">{m.etiqueta}</td>
                  <td data-label="Qué gobierna" className="text-sm text-neutral-secondary">
                    {rutas.length > 0 ? rutas.join(' · ') : 'Sin rutas asociadas'}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {data.pantallas.length > 0 && (
        <div className="card mt-6">
          <h2 className="mb-2 text-lg font-semibold text-neutral-text">Pantallas configurables</h2>
          <p className="text-sm text-neutral-secondary">{data.pantallas.join(' · ')}</p>
        </div>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------
// Auditoría de seguridad
// ---------------------------------------------------------------------------

const ENTIDADES_DE_SEGURIDAD = ['Acceso', 'PermisoRol', 'PermisoUsuario', 'AccesoPantalla', 'Rol', 'Usuario']

function SeccionAuditoria() {
  const [entidad, setEntidad] = useState('Acceso')
  const [resultado, setResultado] = useState('Rechazado')
  const [plataforma, setPlataforma] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['auditoria-seguridad', entidad, resultado, plataforma, page],
    queryFn: () => auditoriaService.listar({ entidad, resultado, plataforma, page }),
    placeholderData: keepPreviousData,
  })

  return (
    <section>
      <div className="card mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <div>
          <label htmlFor="aentidad" className="mb-1 block text-sm text-neutral-secondary">Qué se tocó</label>
          <select id="aentidad" value={entidad} onChange={(e) => { setEntidad(e.target.value); setPage(0) }} className="input">
            <option value="">Todo</option>
            {ENTIDADES_DE_SEGURIDAD.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="aresultado" className="mb-1 block text-sm text-neutral-secondary">Resultado</label>
          <select id="aresultado" value={resultado} onChange={(e) => { setResultado(e.target.value); setPage(0) }} className="input">
            <option value="">Todos</option>
            <option value="Rechazado">Rechazado</option>
            <option value="Permitido">Permitido</option>
          </select>
        </div>
        <div>
          <label htmlFor="aplataforma" className="mb-1 block text-sm text-neutral-secondary">Plataforma</label>
          <select id="aplataforma" value={plataforma} onChange={(e) => { setPlataforma(e.target.value); setPage(0) }} className="input">
            <option value="">Todas</option>
            <option value="Web">Web</option>
            <option value="Movil">Móvil</option>
            <option value="Api">Api</option>
          </select>
        </div>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando la auditoría…</p>}
      {isError && <ErrorDeCarga que="la auditoría" onReintentar={refetch} />}

      {data && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin tabla-apilable">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Quién</th>
                <th>Acción</th>
                <th>Detalle</th>
                <th>Resultado</th>
                <th>Plataforma</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id}>
                  <td data-label="Fecha" className="whitespace-nowrap text-sm">{fecha(r.fecha)}</td>
                  <td data-label="Quién">
                    <p className="text-sm text-neutral-text">{r.usuarioEmail}</p>
                    <p className="text-xs text-neutral-muted">{r.usuarioRol ?? '—'}</p>
                  </td>
                  <td data-label="Acción" className="text-sm">{r.accion}</td>
                  <td data-label="Detalle" className="text-sm text-neutral-secondary">{r.detalle ?? '—'}</td>
                  <td data-label="Resultado">
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
                      r.resultado === 'Rechazado'
                        ? 'bg-red-400/20 text-red-400'
                        : 'bg-emerald-400/20 text-emerald-400'
                    }`}>
                      {r.resultado ?? '—'}
                    </span>
                  </td>
                  <td data-label="Plataforma" className="text-sm text-neutral-secondary">
                    {r.plataforma ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center gap-3">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="btn btn-outline px-3 py-1 text-sm">
            Anterior
          </button>
          <span className="text-sm text-neutral-muted">Página {page + 1} de {data.totalPages}</span>
          <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="btn btn-outline px-3 py-1 text-sm">
            Siguiente
          </button>
        </div>
      )}
    </section>
  )
}

export default AdminSeguridadPage
