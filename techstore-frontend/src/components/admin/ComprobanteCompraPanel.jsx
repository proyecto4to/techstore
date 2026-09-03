import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { contabilidadService } from '../../services/contabilidadService'
import { formatPrecio } from '../../utils/format'

/**
 * Los tipos de comprobante que puede recibir de un proveedor un negocio que
 * compra mercadería. La tabla de la DNIT tiene doce, pero ofrecer boletos de
 * lotería o entradas a espectáculos sería ruido: si algún día hacen falta,
 * están en el backend.
 */
const TIPOS = [
  { codigo: 109, etiqueta: 'Factura' },
  { codigo: 110, etiqueta: 'Nota de crédito' },
  { codigo: 111, etiqueta: 'Nota de débito' },
  { codigo: 101, etiqueta: 'Autofactura' },
  { codigo: 107, etiqueta: 'Despacho de importación' },
  { codigo: 112, etiqueta: 'Ticket de máquina registradora' },
]

/** Los que se declaran solo con el total, sin abrir por tasa de IVA. */
const SIN_DESGLOSE = [101, 104, 105, 112]
/** Los que tienen que decir qué documento corrigen. */
const CORRECTIVOS = [110, 111]

const hoy = () => new Date().toISOString().slice(0, 10)

const VACIO = {
  tipoComprobante: 109,
  timbrado: '',
  numero: '',
  fechaEmision: hoy(),
  condicion: 'Contado',
  gravado10: '',
  gravado5: '',
  exento: '',
  total: '',
  asociadoNumero: '',
  asociadoTimbrado: '',
}

/**
 * Carga del comprobante fiscal que mandó el proveedor. Sin esto la compra no
 * entra en el libro de IVA: lo que se declara es el documento del proveedor,
 * no el pedido interno.
 */
function ComprobanteCompraPanel({ compra, onListo }) {
  const [form, setForm] = useState(VACIO)
  const [editando, setEditando] = useState(false)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: cargado, isLoading } = useQuery({
    queryKey: ['comprobante-compra', compra.id],
    queryFn: () => contabilidadService.comprobanteDeCompra(compra.id),
    // Todavía no cargado es lo normal, no un error que valga la pena reintentar.
    retry: false,
  })

  const guardar = useMutation({
    mutationFn: () =>
      contabilidadService.guardarComprobante(compra.id, {
        tipoComprobante: Number(form.tipoComprobante),
        timbrado: form.timbrado.trim() || null,
        numero: form.numero.trim() || null,
        fechaEmision: form.fechaEmision,
        condicion: form.condicion,
        // Vacío significa "lo que dice la compra": el backend lo deriva de los
        // ítems en vez de obligar a retipear lo mismo.
        gravado10: form.gravado10 === '' ? null : Number(form.gravado10),
        gravado5: form.gravado5 === '' ? null : Number(form.gravado5),
        exento: form.exento === '' ? null : Number(form.exento),
        total: form.total === '' ? null : Number(form.total),
        asociadoNumero: form.asociadoNumero.trim() || null,
        asociadoTimbrado: form.asociadoTimbrado.trim() || null,
      }),
    onSuccess: () => {
      setError(null)
      setEditando(false)
      queryClient.invalidateQueries({ queryKey: ['comprobante-compra', compra.id] })
      onListo?.()
    },
    onError: (err) => {
      const errores = err.response?.data?.errores
      setError(
        errores
          ? Object.values(errores).join(' · ')
          : err.response?.data?.detail ?? 'No se pudo guardar el comprobante.',
      )
    },
  })

  const subirArchivo = useMutation({
    mutationFn: (archivo) => contabilidadService.subirArchivoComprobante(compra.id, archivo),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['comprobante-compra', compra.id] })
    },
    onError: (err) => setError(err.response?.data?.detail ?? 'No se pudo adjuntar el archivo.'),
  })

  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })
  const tipo = Number(form.tipoComprobante)
  const llevaDesglose = !SIN_DESGLOSE.includes(tipo)
  const esCorrectivo = CORRECTIVOS.includes(tipo)

  if (isLoading) return <p className="text-sm text-neutral-muted">Buscando el comprobante…</p>

  if (cargado && !editando) {
    return (
      <div className="flex flex-wrap items-center gap-x-8 gap-y-2 text-sm">
        <div>
          <span className="block text-xs text-neutral-muted">Comprobante</span>
          <span className="font-mono text-neutral-text">
            {cargado.tipoComprobanteEtiqueta} {cargado.numero ?? '—'}
          </span>
        </div>
        <div>
          <span className="block text-xs text-neutral-muted">Timbrado</span>
          <span className="font-mono text-neutral-text">{cargado.timbrado ?? '—'}</span>
        </div>
        <div>
          <span className="block text-xs text-neutral-muted">Emitido</span>
          <span className="text-neutral-text">{cargado.fechaEmision}</span>
        </div>
        <div>
          <span className="block text-xs text-neutral-muted">Total</span>
          <span className="text-neutral-text">{formatPrecio(cargado.total, cargado.moneda)}</span>
        </div>
        <div>
          <span className="block text-xs text-neutral-muted">Crédito fiscal</span>
          <span className="font-semibold text-emerald-400">
            {formatPrecio(cargado.creditoFiscal, cargado.moneda)}
          </span>
        </div>
        <button
          onClick={() => {
            setForm({
              tipoComprobante: cargado.tipoComprobante,
              timbrado: cargado.timbrado ?? '',
              numero: cargado.numero ?? '',
              fechaEmision: cargado.fechaEmision,
              condicion: cargado.condicion === 'Crédito' ? 'Credito' : 'Contado',
              gravado10: String(cargado.gravado10),
              gravado5: String(cargado.gravado5),
              exento: String(cargado.exento),
              total: String(cargado.total),
              asociadoNumero: cargado.asociadoNumero ?? '',
              asociadoTimbrado: cargado.asociadoTimbrado ?? '',
            })
            setEditando(true)
          }}
          className="btn btn-secondary text-sm"
        >
          Corregir
        </button>
        <label className="btn btn-secondary cursor-pointer text-sm">
          {subirArchivo.isPending ? 'Adjuntando…' : cargado.archivoDisponible ? 'Reemplazar archivo' : 'Adjuntar foto/PDF'}
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            capture="environment"
            className="sr-only"
            disabled={subirArchivo.isPending}
            onChange={(event) => {
              const archivo = event.target.files?.[0]
              if (archivo) subirArchivo.mutate(archivo)
              event.target.value = ''
            }}
          />
        </label>
        {cargado.archivoDisponible && (
          <button
            onClick={() => contabilidadService.abrirArchivoComprobante(compra.id)}
            className="btn btn-secondary text-sm"
          >
            Ver archivo
          </button>
        )}
        {error && <p className="alerta-error basis-full">{error}</p>}
      </div>
    )
  }

  return (
    <div>
      <p className="mb-3 text-sm text-neutral-secondary">
        Cargá la factura que mandó el proveedor. Es lo que entra en el libro de compras y lo que da
        el crédito fiscal. <strong>Si dejás los importes vacíos</strong> se toman los de la compra
        ({formatPrecio(compra.total)}).
      </p>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`tipo-${compra.id}`}>
            Tipo
          </label>
          <select id={`tipo-${compra.id}`} value={form.tipoComprobante} onChange={set('tipoComprobante')} className="input" aria-label="Tipo">
            {TIPOS.map((t) => (
              <option key={t.codigo} value={t.codigo}>{t.etiqueta}</option>
            ))}
          </select>
        </div>
        {tipo !== 107 && (
          <div>
            <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`timbrado-${compra.id}`}>
              Timbrado *
            </label>
            <input id={`timbrado-${compra.id}`} value={form.timbrado} onChange={set('timbrado')}
              maxLength={8} placeholder="12345678" className="input w-32 font-mono"  aria-label="Timbrado"/>
          </div>
        )}
        {tipo !== 107 && tipo !== 112 && (
          <div>
            <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`numero-${compra.id}`}>
              Número *
            </label>
            <input id={`numero-${compra.id}`} value={form.numero} onChange={set('numero')}
              placeholder="001-001-0000250" className="input w-44 font-mono"  aria-label="Número"/>
          </div>
        )}
        <div>
          <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`fecha-${compra.id}`}>
            Fecha de emisión *
          </label>
          <input id={`fecha-${compra.id}`} type="date" value={form.fechaEmision}
            onChange={set('fechaEmision')} className="input"  aria-label="Fecha de emisión"/>
        </div>
        <div>
          <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`condicion-${compra.id}`}>
            Condición
          </label>
          <select id={`condicion-${compra.id}`} value={form.condicion} onChange={set('condicion')} className="input" aria-label="Condición">
            <option value="Contado">Contado</option>
            <option value="Credito">Crédito</option>
          </select>
        </div>
      </div>

      {esCorrectivo && (
        <div className="mb-3 flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`asoc-num-${compra.id}`}>
              Corrige al comprobante *
            </label>
            <input id={`asoc-num-${compra.id}`} value={form.asociadoNumero} onChange={set('asociadoNumero')}
              placeholder="001-001-0000240" className="input w-44 font-mono"  aria-label="Corrige al comprobante"/>
          </div>
          <div>
            <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`asoc-tim-${compra.id}`}>
              Timbrado del corregido *
            </label>
            <input id={`asoc-tim-${compra.id}`} value={form.asociadoTimbrado} onChange={set('asociadoTimbrado')}
              maxLength={8} className="input w-32 font-mono"  aria-label="Timbrado del corregido"/>
          </div>
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-end gap-3">
        {llevaDesglose && (
          <>
            <div>
              <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`g10-${compra.id}`}>
                Gravado 10% (IVA incl.)
              </label>
              <input id={`g10-${compra.id}`} type="number" min="0" value={form.gravado10}
                onChange={set('gravado10')} className="input w-36"  aria-label="Gravado 10% (IVA incl.)"/>
            </div>
            <div>
              <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`g5-${compra.id}`}>
                Gravado 5% (IVA incl.)
              </label>
              <input id={`g5-${compra.id}`} type="number" min="0" value={form.gravado5}
                onChange={set('gravado5')} className="input w-36"  aria-label="Gravado 5% (IVA incl.)"/>
            </div>
            <div>
              <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`ex-${compra.id}`}>
                Exentas
              </label>
              <input id={`ex-${compra.id}`} type="number" min="0" value={form.exento}
                onChange={set('exento')} className="input w-36"  aria-label="Exentas"/>
            </div>
          </>
        )}
        <div>
          <label className="mb-1 block text-xs text-neutral-muted" htmlFor={`tot-${compra.id}`}>
            Total
          </label>
          <input id={`tot-${compra.id}`} type="number" min="0" value={form.total}
            onChange={set('total')} className="input w-36"  aria-label="Total"/>
        </div>
      </div>

      {error && <p className="alerta-error mb-3">{error}</p>}

      <div className="flex gap-2">
        <button onClick={() => guardar.mutate()} disabled={guardar.isPending} className="btn btn-primary text-sm">
          {guardar.isPending ? 'Guardando…' : 'Guardar comprobante'}
        </button>
        {cargado && (
          <button onClick={() => { setEditando(false); setError(null) }} className="btn btn-secondary text-sm">
            Cancelar
          </button>
        )}
      </div>
    </div>
  )
}

export default ComprobanteCompraPanel
