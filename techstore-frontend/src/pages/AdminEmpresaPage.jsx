import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import { empresaService } from '../services/empresaService'
import AdminTabs from '../components/admin/AdminTabs'

const VACIO = {
  nombre: '', ruc: '', direccion: '', ciudad: '', telefono: '', email: '', actividad: '',
  timbrado: '', timbradoVigencia: '', timbradoInicioVigencia: '',
  tipoContribuyente: 2, establecimiento: '', puntoExpedicion: '', actividadCodigo: '',
  departamentoCodigo: '', departamentoNombre: '', distritoCodigo: '', distritoNombre: '',
  ciudadCodigo: '', ciudadNombre: '',
}

/** Un campo de texto del formulario, para no repetir el markup 19 veces. */
function Campo({ id, etiqueta, valor, onChange, ayuda, ...props }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor={id}>
        {etiqueta}
      </label>
      <input id={id} value={valor ?? ''} onChange={onChange} className="input" {...props} />
      {ayuda && <p className="mt-1 text-xs text-neutral-muted">{ayuda}</p>}
    </div>
  )
}

/**
 * Datos del emisor: lo que sale impreso en la factura y viaja en el Documento
 * Electrónico. Antes solo se podían cambiar editando application.properties.
 */
function AdminEmpresaPage() {
  const [form, setForm] = useState(VACIO)
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)

  const { data, isLoading } = useQuery({
    queryKey: ['empresa'],
    queryFn: empresaService.actual,
  })

  useEffect(() => {
    if (data) setForm({ ...VACIO, ...data })
  }, [data])

  const guardarMutation = useMutation({
    mutationFn: () => empresaService.guardar(form),
    onSuccess: (guardado) => {
      setError(null)
      setForm({ ...VACIO, ...guardado })
      setMensaje('Datos guardados. Las facturas que emitas de ahora en más salen con estos datos.')
    },
    onError: (err) => {
      setMensaje(null)
      const errores = err.response?.data?.errores
      setError(
        errores
          ? Object.entries(errores).map(([campo, msj]) => `${campo}: ${msj}`).join(' · ')
          : err.response?.data?.detail ?? 'No se pudieron guardar los datos.',
      )
    },
  })

  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        🏢 Administración — <span className="gradient-text">Datos de la empresa</span>
      </h1>
      <AdminTabs />

      <p className="mb-4 text-sm text-neutral-secondary">
        Estos datos son los del <strong>emisor</strong>: salen impresos en la factura y viajan dentro
        del Documento Electrónico. Cambiarlos afecta a las facturas que emitas de acá en adelante;
        las ya emitidas no se tocan.
      </p>

      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {error && <p className="alerta-error" role="alert">{error}</p>}

      {isLoading ? (
        <p className="text-neutral-muted">Cargando…</p>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            guardarMutation.mutate()
          }}
          className="space-y-6"
        >
          <div className="card">
            <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
              <Building2 size={18} className="text-primary-300" /> Datos generales
            </h3>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Campo id="nombre" etiqueta="Nombre o razón social *" valor={form.nombre}
                onChange={set('nombre')} required maxLength={255} />
              <Campo id="ruc" etiqueta="RUC *" valor={form.ruc} onChange={set('ruc')}
                required maxLength={20} placeholder="80012345-0" />
              <Campo id="direccion" etiqueta="Dirección" valor={form.direccion}
                onChange={set('direccion')} maxLength={255} />
              <Campo id="ciudad" etiqueta="Ciudad" valor={form.ciudad} onChange={set('ciudad')}
                maxLength={100} />
              <Campo id="telefono" etiqueta="Teléfono" valor={form.telefono}
                onChange={set('telefono')} maxLength={50} />
              <Campo id="email" etiqueta="Correo electrónico" valor={form.email}
                onChange={set('email')} maxLength={255} />
              <Campo id="actividad" etiqueta="Actividad económica" valor={form.actividad}
                onChange={set('actividad')} maxLength={255} />
            </div>
          </div>

          <div className="card">
            <h3 className="mb-4 text-lg font-semibold text-neutral-text">Timbrado</h3>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Campo id="timbrado" etiqueta="Número de timbrado" valor={form.timbrado}
                onChange={set('timbrado')} maxLength={20} />
              <Campo id="timbradoVigencia" etiqueta="Vigencia (texto impreso)"
                valor={form.timbradoVigencia} onChange={set('timbradoVigencia')} maxLength={100}
                placeholder="01/01/2026 al 31/12/2026" />
              <Campo id="timbradoInicioVigencia" etiqueta="Inicio de vigencia"
                valor={form.timbradoInicioVigencia} onChange={set('timbradoInicioVigencia')}
                placeholder="2026-01-01" ayuda="Formato AAAA-MM-DD; va en el XML" />
            </div>
          </div>

          <div className="card">
            <h3 className="mb-1 text-lg font-semibold text-neutral-text">
              Datos fiscales del Documento Electrónico
            </h3>
            <p className="mb-4 text-xs text-neutral-muted">
              Códigos que exige el SIFEN. Si no estás seguro, dejalos como están.
            </p>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="tipoContribuyente">
                  Tipo de contribuyente
                </label>
                <select
                  id="tipoContribuyente"
                  value={form.tipoContribuyente ?? 2}
                  onChange={(e) => setForm({ ...form, tipoContribuyente: Number(e.target.value) })}
                  className="input"
                >
                  <option value={1}>1 — Persona física</option>
                  <option value={2}>2 — Persona jurídica</option>
                </select>
              </div>
              <Campo id="establecimiento" etiqueta="Establecimiento" valor={form.establecimiento}
                onChange={set('establecimiento')} maxLength={3} placeholder="001" />
              <Campo id="puntoExpedicion" etiqueta="Punto de expedición" valor={form.puntoExpedicion}
                onChange={set('puntoExpedicion')} maxLength={3} placeholder="001" />
              <Campo id="actividadCodigo" etiqueta="Código de actividad" valor={form.actividadCodigo}
                onChange={set('actividadCodigo')} maxLength={10} placeholder="47411" />
              <Campo id="departamentoCodigo" etiqueta="Código de departamento"
                valor={form.departamentoCodigo} onChange={set('departamentoCodigo')} maxLength={5} />
              <Campo id="departamentoNombre" etiqueta="Departamento" valor={form.departamentoNombre}
                onChange={set('departamentoNombre')} maxLength={100} />
              <Campo id="distritoCodigo" etiqueta="Código de distrito" valor={form.distritoCodigo}
                onChange={set('distritoCodigo')} maxLength={5} />
              <Campo id="distritoNombre" etiqueta="Distrito" valor={form.distritoNombre}
                onChange={set('distritoNombre')} maxLength={100} />
              <Campo id="ciudadCodigo" etiqueta="Código de ciudad" valor={form.ciudadCodigo}
                onChange={set('ciudadCodigo')} maxLength={5} />
              <Campo id="ciudadNombre" etiqueta="Ciudad (SIFEN)" valor={form.ciudadNombre}
                onChange={set('ciudadNombre')} maxLength={100} />
            </div>
          </div>

          <button type="submit" disabled={guardarMutation.isPending} className="btn btn-primary">
            {guardarMutation.isPending ? 'Guardando…' : 'Guardar datos de la empresa'}
          </button>
        </form>
      )}
    </div>
  )
}

export default AdminEmpresaPage
