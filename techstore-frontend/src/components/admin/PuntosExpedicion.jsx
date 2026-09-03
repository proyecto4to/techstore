import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Store } from 'lucide-react'
import { empresaService } from '../../services/empresaService'

const VACIO = { establecimiento: '001', punto: '', nombre: '' }

/**
 * Las cajas o sucursales que emiten documentos.
 *
 * Cada una lleva su propia serie de numeración: 001-001-0000001 y
 * 001-002-0000001 son dos facturas distintas y las dos válidas. Quién emite
 * desde dónde se define por usuario, en Admin → Usuarios.
 */
function PuntosExpedicion() {
  const [form, setForm] = useState(VACIO)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: puntos = [], isLoading } = useQuery({
    queryKey: ['puntos-expedicion'],
    queryFn: () => empresaService.puntosExpedicion(),
  })

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['puntos-expedicion'] })

  const onError = (err) => {
    const errores = err.response?.data?.errores
    setError(
      errores
        ? Object.entries(errores).map(([campo, msj]) => `${campo}: ${msj}`).join(' · ')
        : err.response?.data?.detail ?? 'No se pudo guardar el punto de expedición.',
    )
  }

  const crearMutation = useMutation({
    mutationFn: () => empresaService.crearPuntoExpedicion(form),
    onSuccess: () => {
      setError(null)
      setForm(VACIO)
      refrescar()
    },
    onError,
  })

  const activoMutation = useMutation({
    mutationFn: ({ id, activo }) => empresaService.cambiarActivoPunto(id, activo),
    onSuccess: () => {
      setError(null)
      refrescar()
    },
    onError,
  })

  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })

  return (
    <div className="card">
      <h3 className="mb-1 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
        <Store size={18} className="text-primary-300" /> Puntos de expedición
      </h3>
      <p className="mb-4 text-xs text-neutral-muted">
        Cada caja o sucursal lleva su propia numeración. Quién emite desde cuál se define por
        usuario, en la pestaña <strong>Usuarios</strong>. El punto por defecto es el que se usa
        cuando quien factura no tiene ninguno asignado.
      </p>

      {error && <p className="alerta-error" role="alert">{error}</p>}

      {isLoading ? (
        <p className="text-neutral-muted">Cargando…</p>
      ) : (
        <div className="mb-6 overflow-x-auto">
          <table className="tabla-admin tabla-apilable">
            <thead>
              <tr>
                <th>Prefijo</th>
                <th>Nombre</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {puntos.map((p) => (
                <tr key={p.id} className={p.activo ? '' : 'opacity-50'}>
                  <td data-label="Prefijo" className="font-mono text-neutral-text">{p.prefijo}</td>
                  <td data-label="Nombre" className="text-neutral-text">
                    {p.nombre}
                    {p.porDefecto && (
                      <span className="ml-2 rounded-full bg-accent-400/20 px-2 py-0.5 text-xs font-semibold text-accent-400">
                        por defecto
                      </span>
                    )}
                  </td>
                  <td data-label="Estado">
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${p.activo ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'}`}>
                      {p.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td data-label="Acciones">
                    {/* El por defecto no se puede desactivar: es al que caen los
                        usuarios sin punto asignado. */}
                    {p.porDefecto ? (
                      <span className="text-xs text-neutral-muted">—</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => activoMutation.mutate({ id: p.id, activo: !p.activo })}
                        disabled={activoMutation.isPending}
                        className={`btn text-sm ${p.activo ? 'btn-danger' : 'btn-primary'}`}
                      >
                        {p.activo ? 'Desactivar' : 'Activar'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="peEstablecimiento">
            Establecimiento
          </label>
          <input id="peEstablecimiento" value={form.establecimiento} onChange={set('establecimiento')}
            maxLength={3} placeholder="001" className="input" />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="pePunto">
            Punto
          </label>
          <input id="pePunto" value={form.punto} onChange={set('punto')} maxLength={3}
            placeholder="002" className="input" />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="peNombre">
            Nombre
          </label>
          <input id="peNombre" value={form.nombre} onChange={set('nombre')} maxLength={100}
            placeholder="Caja 2 - Mostrador" className="input" />
        </div>
        <div className="flex items-end">
          {/* type="button": esto va dentro de la página de empresa, y no tiene
              que disparar el submit del formulario de datos del emisor. */}
          <button
            type="button"
            onClick={() => crearMutation.mutate()}
            disabled={crearMutation.isPending || !form.punto || !form.nombre.trim()}
            className="btn btn-primary w-full"
          >
            {crearMutation.isPending ? 'Agregando…' : 'Agregar punto'}
          </button>
        </div>
      </div>
      <p className="mt-2 text-xs text-neutral-muted">
        Tres dígitos cada uno, como salen impresos en la factura. El prefijo no se puede cambiar
        después: las facturas ya emitidas lo llevan impreso.
      </p>
    </div>
  )
}

export default PuntosExpedicion
