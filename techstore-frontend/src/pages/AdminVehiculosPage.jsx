import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Car, History, Pencil, Plus } from 'lucide-react'
import AdminTabs from '../components/admin/AdminTabs'
import { vehiculoService } from '../services/vehiculoService'
// Con alias: `estadoVehiculo` ya se usa acá como nombre de parámetro al
// resolver un evento, y el import lo taparía dentro de esa función.
import {
  estadoVehiculo as etiquetaEstado,
  eventoVehiculo as etiquetaEvento,
} from '../utils/pedidoEstados'

const EMPTY = {
  matricula: '', marca: '', modelo: '', anio: '', tipo: '', color: '', numeroChasis: '',
  kilometrajeActual: 0, tipoCombustible: '', estado: 'DISPONIBLE', observaciones: '',
}

export default function AdminVehiculosPage() {
  const [form, setForm] = useState(EMPTY)
  const [editando, setEditando] = useState(null)
  const [historialId, setHistorialId] = useState(null)
  // El resultado de guardar se muestra junto al botón que lo dispara, no
  // arriba de once campos: ahí es donde está mirando quien acaba de apretar.
  // Las acciones sobre la flota siguen avisando en la cabecera, que es donde
  // vive esa lista.
  const [mensajeForm, setMensajeForm] = useState(null)
  const [mensajeFlota, setMensajeFlota] = useState(null)
  const queryClient = useQueryClient()
  const { data: vehiculos = [], isLoading, isError } = useQuery({
    queryKey: ['vehiculos-admin'],
    queryFn: vehiculoService.listar,
  })
  const { data: historial, isLoading: historialCargando } = useQuery({
    queryKey: ['vehiculo-historial', historialId],
    queryFn: () => vehiculoService.historial(historialId),
    enabled: Boolean(historialId),
  })
  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['vehiculos-admin'] })
  const detalle = (error) => error.response?.data?.detail ?? 'No se pudo completar la operación.'
  // Un mensaje que sobrevive a la corrección miente: se limpia apenas el
  // usuario vuelve a tocar el formulario.
  const cambiar = (parche) => {
    setMensajeForm(null)
    setForm((actual) => ({ ...actual, ...parche }))
  }

  const guardar = useMutation({
    mutationFn: (payload) => editando
      ? vehiculoService.actualizar(editando.id, payload)
      : vehiculoService.crear(payload),
    onSuccess: (vehiculo) => {
      setMensajeForm({ ok: true, texto: `Vehículo ${vehiculo.matricula} guardado.` })
      setEditando(null)
      setForm(EMPTY)
      refrescar()
    },
    onError: (error) => setMensajeForm({ ok: false, texto: detalle(error) }),
  })
  const activo = useMutation({
    mutationFn: ({ id, value }) => vehiculoService.cambiarActivo(id, value),
    onSuccess: (vehiculo) => {
      setMensajeFlota({ ok: true, texto: `${vehiculo.matricula} ${vehiculo.activo ? 'activado' : 'desactivado'}.` })
      refrescar()
    },
    onError: (error) => setMensajeFlota({ ok: false, texto: detalle(error) }),
  })
  const resolver = useMutation({
    mutationFn: ({ id, estadoVehiculo, observacion }) =>
      vehiculoService.resolverEvento(id, estadoVehiculo, observacion),
    onSuccess: () => {
      setMensajeFlota({ ok: true, texto: 'Evento resuelto y estado del vehículo actualizado.' })
      queryClient.invalidateQueries({ queryKey: ['vehiculo-historial', historialId] })
      refrescar()
    },
    onError: (error) => setMensajeFlota({ ok: false, texto: detalle(error) }),
  })

  const payload = () => ({
    ...form,
    anio: form.anio ? Number(form.anio) : null,
    kilometrajeActual: Number(form.kilometrajeActual),
    color: form.color.trim() || null,
    numeroChasis: form.numeroChasis.trim() || null,
    tipoCombustible: form.tipoCombustible.trim() || null,
    observaciones: form.observaciones.trim() || null,
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        Administración — <span className="gradient-text">Vehículos</span>
      </h1>
      <AdminTabs />
      {mensajeFlota && (
        <p className={mensajeFlota.ok ? 'alerta-ok' : 'alerta-error'} role={mensajeFlota.ok ? 'status' : 'alert'}>
          {mensajeFlota.texto}
        </p>
      )}

      <form
        onSubmit={(event) => { event.preventDefault(); guardar.mutate(payload()) }}
        className="card mb-8"
      >
        <h2 className="mb-4 inline-flex items-center gap-2 text-xl font-semibold text-neutral-text">
          {editando ? <Pencil size={19} /> : <Plus size={19} />}
          {editando ? `Editar ${editando.matricula}` : 'Nuevo vehículo'}
        </h2>
        <div className="grid gap-4 md:grid-cols-3 lg:grid-cols-4">
          <Field label="Matrícula *" value={form.matricula} maxLength={20} required onChange={(value) => cambiar({ matricula: value })} />
          <Field label="Marca *" value={form.marca} maxLength={80} required onChange={(value) => cambiar({ marca: value })} />
          <Field label="Modelo *" value={form.modelo} maxLength={80} required onChange={(value) => cambiar({ modelo: value })} />
          <Field label="Año" type="number" min="1900" max="2200" value={form.anio} onChange={(value) => cambiar({ anio: value })} />
          <Field label="Tipo *" value={form.tipo} required placeholder="Moto, utilitario…" onChange={(value) => cambiar({ tipo: value })} />
          <Field label="Color" value={form.color} onChange={(value) => cambiar({ color: value })} />
          <Field label="N.º de chasis" value={form.numeroChasis} onChange={(value) => cambiar({ numeroChasis: value })} />
          <Field label="Kilometraje *" type="number" min="0" step="0.1" required value={form.kilometrajeActual} onChange={(value) => cambiar({ kilometrajeActual: value })} />
          <Field label="Combustible" value={form.tipoCombustible} onChange={(value) => cambiar({ tipoCombustible: value })} />
          <label className="block text-sm font-medium text-neutral-text">
            Estado *
            <select
              value={form.estado}
              disabled={editando?.estado === 'EN_USO'}
              onChange={(event) => cambiar({ estado: event.target.value })}
              className="input mt-2"
            >
              {['DISPONIBLE', 'MANTENIMIENTO', 'FUERA_DE_SERVICIO', 'INACTIVO'].map((estado) => (
                <option key={estado} value={estado}>{etiquetaEstado(estado).label}</option>
              ))}
              {editando?.estado === 'EN_USO' && (
                <option value="EN_USO">{etiquetaEstado('EN_USO').label}</option>
              )}
            </select>
          </label>
          <label className="block text-sm font-medium text-neutral-text md:col-span-2">
            Observaciones
            <textarea value={form.observaciones} maxLength={2000} rows={2}
              onChange={(event) => cambiar({ observaciones: event.target.value })} className="input mt-2" />
          </label>
        </div>
        {mensajeForm && (
          <p className={`mt-4 ${mensajeForm.ok ? 'alerta-ok' : 'alerta-error'}`} role={mensajeForm.ok ? 'status' : 'alert'}>
            {mensajeForm.texto}
          </p>
        )}
        <div className="mt-4 flex gap-3">
          <button type="submit" disabled={guardar.isPending} className="btn btn-primary">
            {guardar.isPending ? 'Guardando…' : 'Guardar vehículo'}
          </button>
          {editando && (
            <button type="button" onClick={() => { setEditando(null); setForm(EMPTY); setMensajeForm(null) }} className="btn btn-secondary">Cancelar edición</button>
          )}
        </div>
      </form>

      {isLoading && <p className="text-neutral-muted">Cargando flota…</p>}
      {isError && <p className="alerta-error">No se pudo cargar la flota.</p>}
      <div className="grid gap-4 lg:grid-cols-2">
        {vehiculos.map((vehiculo) => (
          <article key={vehiculo.id} className={`card ${vehiculo.activo ? '' : 'opacity-60'}`}>
            <div className="flex items-start gap-4">
              <span className="rounded-lg bg-primary-400/10 p-3 text-primary-300"><Car /></span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="text-xl font-semibold text-neutral-text">{vehiculo.matricula}</h2>
                  <span
                    className={`rounded-full px-3 py-1 text-xs font-semibold ${etiquetaEstado(vehiculo.estado).clase}`}
                  >
                    {etiquetaEstado(vehiculo.estado).label}
                  </span>
                </div>
                <p>{vehiculo.marca} {vehiculo.modelo} {vehiculo.anio ? `· ${vehiculo.anio}` : ''}</p>
                <p className="text-sm text-neutral-muted">
                  {vehiculo.tipo} · {Number(vehiculo.kilometrajeActual).toLocaleString('es-PY')} km
                </p>
                {vehiculo.utilizadoPor && <p className="mt-2 text-sm text-accent-400">En uso por {vehiculo.utilizadoPor}</p>}
              </div>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button type="button" disabled={vehiculo.estado === 'EN_USO'} onClick={() => {
                setMensajeForm(null)
                setEditando(vehiculo)
                setForm({
                  matricula: vehiculo.matricula, marca: vehiculo.marca, modelo: vehiculo.modelo,
                  anio: vehiculo.anio ?? '', tipo: vehiculo.tipo, color: vehiculo.color ?? '',
                  numeroChasis: vehiculo.numeroChasis ?? '', kilometrajeActual: vehiculo.kilometrajeActual,
                  tipoCombustible: vehiculo.tipoCombustible ?? '', estado: vehiculo.estado,
                  observaciones: vehiculo.observaciones ?? '',
                })
                window.scrollTo({ top: 0, behavior: 'smooth' })
              }} className="btn btn-secondary inline-flex items-center gap-2 text-sm">
                <Pencil size={15} /> Editar
              </button>
              <button type="button" onClick={() => setHistorialId(vehiculo.id)} className="btn btn-secondary inline-flex items-center gap-2 text-sm">
                <History size={15} /> Historial
              </button>
              <button type="button" disabled={activo.isPending || vehiculo.estado === 'EN_USO'}
                onClick={() => activo.mutate({ id: vehiculo.id, value: !vehiculo.activo })}
                className={`btn text-sm ${vehiculo.activo ? 'btn-danger' : 'btn-primary'}`}>
                {vehiculo.activo ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </article>
        ))}
      </div>

      {historialId && (
        <VehicleHistory
          loading={historialCargando}
          data={historial}
          busy={resolver.isPending}
          onClose={() => setHistorialId(null)}
          onResolve={(eventId, state, note) => resolver.mutate({ id: eventId, estadoVehiculo: state, observacion: note })}
        />
      )}
    </div>
  )
}

function Field({ label, onChange, ...props }) {
  return (
    <label className="block text-sm font-medium text-neutral-text">
      {label}
      <input {...props} onChange={(event) => onChange(event.target.value)} className="input mt-2" />
    </label>
  )
}

function VehicleHistory({ loading, data, busy, onClose, onResolve }) {
  const [resolution, setResolution] = useState({})
  useEffect(() => setResolution({}), [data?.vehiculo?.id])
  return (
    <div className="fixed inset-0 z-[60] overflow-y-auto bg-black/75 p-4" role="dialog" aria-modal="true">
      <div className="card mx-auto my-8 max-w-4xl">
        <div className="mb-5 flex justify-between gap-4">
          <div>
            <h2 className="text-2xl font-semibold text-neutral-text">Historial del vehículo</h2>
            {data && <p>{data.vehiculo.matricula} · {data.vehiculo.marca} {data.vehiculo.modelo}</p>}
          </div>
          <button onClick={onClose} className="btn btn-secondary">Cerrar</button>
        </div>
        {loading && <p>Cargando línea histórica…</p>}
        {data && (
          <div className="space-y-4 border-l-2 border-primary-400/40 pl-5">
            <Timeline title="Alta" detail={new Date(data.vehiculo.fechaAlta).toLocaleString('es-PY')} />
            {[...data.usos, ...data.eventos]
              .map((item) => ({ ...item, timelineDate: item.fecha ?? item.fechaInicio, kind: item.tipo ? 'evento' : 'uso' }))
              .sort((a, b) => new Date(a.timelineDate) - new Date(b.timelineDate))
              .map((item) => item.kind === 'uso' ? (
                <Timeline key={`u-${item.id}`} title={`Uso por ${item.repartidor}`}
                  detail={`${new Date(item.fechaInicio).toLocaleString('es-PY')} · ${item.kmInicio} km → ${item.kmFin ?? 'en curso'} km`} />
              ) : (
                <div key={`e-${item.id}`}>
                  <Timeline title={etiquetaEvento(item.tipo)}
                    detail={`${new Date(item.fecha).toLocaleString('es-PY')} · ${item.kilometraje} km · ${item.descripcion}`} />
                  {item.requiereAtencion && !item.resuelto && (
                    <div className="ml-2 mt-2 rounded-md border border-amber-400/30 p-3">
                      <p className="mb-2 text-sm text-amber-300">Requiere atención administrativa</p>
                      <div className="flex flex-wrap gap-2">
                        <select value={resolution[item.id]?.state ?? 'DISPONIBLE'}
                          onChange={(event) => setResolution({ ...resolution, [item.id]: { ...resolution[item.id], state: event.target.value } })}
                          className="input w-auto py-1 text-sm"
                          aria-label="Estado en que queda el vehículo">
                          <option value="DISPONIBLE">Disponible</option>
                          <option value="MANTENIMIENTO">Mantenimiento</option>
                          <option value="FUERA_DE_SERVICIO">Fuera de servicio</option>
                        </select>
                        <input value={resolution[item.id]?.note ?? ''} placeholder="Resolución / observación"
                          onChange={(event) => setResolution({ ...resolution, [item.id]: { ...resolution[item.id], note: event.target.value } })}
                          className="input min-w-56 flex-1 py-1 text-sm"  aria-label="Resolución / observación"/>
                        <button disabled={busy} onClick={() => onResolve(item.id, resolution[item.id]?.state ?? 'DISPONIBLE', resolution[item.id]?.note)}
                          className="btn btn-primary text-sm">Resolver</button>
                      </div>
                    </div>
                  )}
                </div>
              ))}
          </div>
        )}
      </div>
    </div>
  )
}

function Timeline({ title, detail }) {
  return (
    <div className="relative">
      <span className="absolute -left-[29px] top-1 h-3 w-3 rounded-full bg-primary-400" />
      <h3 className="font-semibold text-neutral-text">{title}</h3>
      <p className="text-sm text-neutral-secondary">{detail}</p>
    </div>
  )
}

