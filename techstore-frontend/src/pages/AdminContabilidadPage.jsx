import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { AlertTriangle, Download, FileText } from 'lucide-react'
import { contabilidadService } from '../services/contabilidadService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'
import StatCard from '../components/common/StatCard'

const hoy = () => new Date().toISOString().slice(0, 10)

const primerDiaDelMes = () => {
  const fecha = new Date()
  return new Date(fecha.getFullYear(), fecha.getMonth(), 1).toISOString().slice(0, 10)
}

const ultimoDiaDelMes = (anio, mes) => new Date(anio, mes, 0).toISOString().slice(0, 10)

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
]

/**
 * El importe de una línea que resta, con su signo. Un cero se deja en cero:
 * multiplicarlo por -1 daría "-0", que no significa nada.
 */
const firmado = (valor, resta) => (resta && valor !== 0 ? -valor : valor)

function Libro({ libro }) {
  if (!libro) return null
  const { totales } = libro
  return (
    <>
      <div className="card mb-6 overflow-x-auto p-0">
        <table className="tabla-admin">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Comprobante</th>
              <th>Timbrado</th>
              <th>Número</th>
              <th>{libro.libro === 'Ventas' ? 'Comprador' : 'Proveedor'}</th>
              <th className="text-right">Gravado 10%</th>
              <th className="text-right">IVA 10%</th>
              <th className="text-right">Gravado 5%</th>
              <th className="text-right">IVA 5%</th>
              <th className="text-right">Exentas</th>
              <th className="text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {libro.lineas.length === 0 && (
              <tr>
                <td colSpan={11} className="py-6 text-center text-neutral-muted">
                  Sin comprobantes en el período.
                </td>
              </tr>
            )}
            {libro.lineas.map((l, i) => (
              <tr key={`${l.numero ?? 'sin-numero'}-${i}`} className={l.resta ? 'text-red-300' : undefined}>
                <td>{l.fecha}</td>
                <td>
                  {l.tipoComprobanteEtiqueta}
                  {l.asociadoNumero && (
                    <span className="block text-xs text-neutral-muted">corrige {l.asociadoNumero}</span>
                  )}
                </td>
                <td className="font-mono">{l.timbrado ?? '—'}</td>
                <td className="font-mono">{l.numero ?? '—'}</td>
                <td>
                  <span className="font-mono text-neutral-text">{l.identificacion ?? '—'}</span>
                  <span className="block text-xs text-neutral-muted">{l.nombre ?? 'Consumidor final'}</span>
                </td>
                <td className="text-right">{formatPrecio(firmado(l.gravado10, l.resta), libro.moneda)}</td>
                <td className="text-right">{formatPrecio(firmado(l.iva10, l.resta), libro.moneda)}</td>
                <td className="text-right">{formatPrecio(firmado(l.gravado5, l.resta), libro.moneda)}</td>
                <td className="text-right">{formatPrecio(firmado(l.iva5, l.resta), libro.moneda)}</td>
                <td className="text-right">{formatPrecio(firmado(l.exento, l.resta), libro.moneda)}</td>
                <td className="text-right font-semibold">
                  {formatPrecio(firmado(l.total, l.resta), libro.moneda)}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-dark-border font-semibold text-neutral-text">
              <td colSpan={5}>{totales.comprobantes} comprobante(s)</td>
              <td className="text-right">{formatPrecio(totales.gravado10, libro.moneda)}</td>
              <td className="text-right">{formatPrecio(totales.iva10, libro.moneda)}</td>
              <td className="text-right">{formatPrecio(totales.gravado5, libro.moneda)}</td>
              <td className="text-right">{formatPrecio(totales.iva5, libro.moneda)}</td>
              <td className="text-right">{formatPrecio(totales.exento, libro.moneda)}</td>
              <td className="text-right">{formatPrecio(totales.total, libro.moneda)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      <Avisos avisos={libro.avisos} />
    </>
  )
}

function Avisos({ avisos }) {
  if (!avisos?.length) return null
  return (
    <div className="card mb-6 border border-amber-500/40">
      <h3 className="mb-3 flex items-center gap-2 text-lg font-semibold text-amber-300">
        <AlertTriangle size={18} /> Falta completar {avisos.length} dato(s) para poder declarar
      </h3>
      <p className="mb-3 text-sm text-neutral-secondary">
        Marangatu rechaza el archivo entero si una fila tiene un campo obligatorio vacío, así que
        mientras quede algo de esta lista el archivo no se genera.
      </p>
      <ul className="list-disc space-y-1 pl-5 text-sm text-neutral-secondary">
        {avisos.map((aviso, i) => (
          <li key={i}>{aviso}</li>
        ))}
      </ul>
    </div>
  )
}

function AdminContabilidadPage() {
  const [solapa, setSolapa] = useState('resumen')
  const [desde, setDesde] = useState(primerDiaDelMes())
  const [hasta, setHasta] = useState(hoy())
  const [error, setError] = useState('')

  const ahora = new Date()
  const [anio, setAnio] = useState(ahora.getFullYear())
  const [mes, setMes] = useState(ahora.getMonth() + 1)
  const [anual, setAnual] = useState(false)
  const [formato, setFormato] = useState('TXT')
  const [identificador, setIdentificador] = useState('V0001')
  const [renta, setRenta] = useState('IRE')
  const [descargado, setDescargado] = useState('')

  const rango = { desde, hasta }

  const { data: resumen, isLoading, isError } = useQuery({
    queryKey: ['resumen-iva', desde, hasta],
    queryFn: () => contabilidadService.resumenIva(rango),
    placeholderData: keepPreviousData,
  })

  const { data: libroVentas } = useQuery({
    queryKey: ['libro-ventas', desde, hasta],
    queryFn: () => contabilidadService.libroVentas(rango),
    placeholderData: keepPreviousData,
    enabled: solapa === 'ventas',
  })

  const { data: libroCompras } = useQuery({
    queryKey: ['libro-compras', desde, hasta],
    queryFn: () => contabilidadService.libroCompras(rango),
    placeholderData: keepPreviousData,
    enabled: solapa === 'compras',
  })

  const esteMes = () => {
    setDesde(primerDiaDelMes())
    setHasta(hoy())
  }

  const mesPasado = () => {
    const fecha = new Date()
    const anioPrevio = fecha.getMonth() === 0 ? fecha.getFullYear() - 1 : fecha.getFullYear()
    const mesPrevio = fecha.getMonth() === 0 ? 12 : fecha.getMonth()
    setDesde(new Date(anioPrevio, mesPrevio - 1, 1).toISOString().slice(0, 10))
    setHasta(ultimoDiaDelMes(anioPrevio, mesPrevio))
  }

  const cierreDeHoy = () => {
    setDesde(hoy())
    setHasta(hoy())
  }

  const descargar = async () => {
    setError('')
    setDescargado('')
    try {
      const nombre = await contabilidadService.archivoDnit({
        anio,
        mes: anual ? null : mes,
        formato,
        identificador,
        renta,
      })
      setDescargado(nombre)
    } catch (err) {
      // El error viene como blob porque la petición pidió blob: hay que leerlo.
      const cuerpo = err.response?.data
      if (cuerpo instanceof Blob) {
        try {
          const { detail } = JSON.parse(await cuerpo.text())
          setError(detail ?? 'No se pudo generar el archivo.')
          return
        } catch {
          setError('No se pudo generar el archivo.')
          return
        }
      }
      setError(cuerpo?.detail ?? 'No se pudo generar el archivo.')
    }
  }

  const solapas = [
    { id: 'resumen', label: 'Liquidación del IVA' },
    { id: 'ventas', label: 'Libro de ventas' },
    { id: 'compras', label: 'Libro de compras' },
    { id: 'dnit', label: 'Archivo para la DNIT' },
  ]

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Contabilidad</span>
      </h1>
      <AdminTabs />

      <div className="mb-6 flex flex-wrap gap-2">
        {solapas.map((s) => (
          <button
            key={s.id}
            onClick={() => setSolapa(s.id)}
            className={`rounded-md px-4 py-2 text-sm font-semibold transition-all ${
              solapa === s.id
                ? 'bg-gradient-to-r from-primary-400 to-secondary-400 text-white'
                : 'text-neutral-secondary hover:bg-primary-400/10 hover:text-primary-300'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {solapa !== 'dnit' && (
        <div className="card mb-8 flex flex-wrap items-end gap-4">
          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="desde">
              Desde
            </label>
            <input id="desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} className="input" />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="hasta">
              Hasta
            </label>
            <input id="hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} className="input" />
          </div>
          <div className="flex gap-2">
            <button onClick={cierreDeHoy} className="btn btn-secondary text-sm">Cierre de hoy</button>
            <button onClick={esteMes} className="btn btn-secondary text-sm">Este mes</button>
            <button onClick={mesPasado} className="btn btn-secondary text-sm">Mes pasado</button>
          </div>
          <button
            onClick={() => {
              if (solapa === 'ventas') return contabilidadService.libroVentasPdf(rango)
              if (solapa === 'compras') return contabilidadService.libroComprasPdf(rango)
              return contabilidadService.resumenIvaPdf(rango)
            }}
            className="btn btn-primary inline-flex items-center gap-2 text-sm"
          >
            <FileText size={16} /> Exportar PDF
          </button>
        </div>
      )}

      {isLoading && <p className="text-neutral-muted">Cargando…</p>}
      {isError && <p className="alerta-error">No se pudo cargar la contabilidad del período.</p>}

      {solapa === 'resumen' && resumen && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-3">
            <StatCard
              etiqueta="Débito fiscal"
              valor={formatPrecio(resumen.debitoFiscal, resumen.moneda)}
              detalle="IVA cobrado en las ventas del período"
              icono="🧾"
            />
            <StatCard
              etiqueta="Crédito fiscal"
              valor={formatPrecio(resumen.creditoFiscal, resumen.moneda)}
              detalle="IVA pagado a los proveedores"
              icono="📥"
            />
            <StatCard
              etiqueta={resumen.situacion}
              valor={formatPrecio(Math.abs(resumen.saldo), resumen.moneda)}
              detalle="Débito menos crédito del período"
              icono={resumen.saldo > 0 ? '💸' : '💰'}
            />
          </div>

          <div className="card mb-6 overflow-x-auto p-0">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th>Libro</th>
                  <th className="text-right">Comprobantes</th>
                  <th className="text-right">Gravado 10%</th>
                  <th className="text-right">IVA 10%</th>
                  <th className="text-right">Gravado 5%</th>
                  <th className="text-right">IVA 5%</th>
                  <th className="text-right">Exentas</th>
                  <th className="text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {[['Ventas', resumen.ventas], ['Compras', resumen.compras]].map(([nombre, t]) => (
                  <tr key={nombre}>
                    <td className="font-semibold text-neutral-text">{nombre}</td>
                    <td className="text-right">{t.comprobantes}</td>
                    <td className="text-right">{formatPrecio(t.gravado10, resumen.moneda)}</td>
                    <td className="text-right">{formatPrecio(t.iva10, resumen.moneda)}</td>
                    <td className="text-right">{formatPrecio(t.gravado5, resumen.moneda)}</td>
                    <td className="text-right">{formatPrecio(t.iva5, resumen.moneda)}</td>
                    <td className="text-right">{formatPrecio(t.exento, resumen.moneda)}</td>
                    <td className="text-right font-semibold">{formatPrecio(t.total, resumen.moneda)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Avisos avisos={resumen.avisos} />
        </>
      )}

      {solapa === 'ventas' && <Libro libro={libroVentas} />}
      {solapa === 'compras' && <Libro libro={libroCompras} />}

      {solapa === 'dnit' && (
        <div className="card max-w-3xl">
          <h3 className="mb-2 text-lg font-semibold text-neutral-text">
            Registro de Comprobantes — Marangatu
          </h3>
          <p className="mb-6 text-sm text-neutral-secondary">
            Genera el ZIP que se sube a Marangatu (RG 90/2021, el que reemplazó a Hechauka). El
            registro <strong>mensual</strong> es la obligación 955 y el <strong>anual</strong> la
            956. Se presenta hasta el día que le toca al RUC según el calendario perpetuo, del mes
            siguiente al período.
          </p>

          <div className="mb-6 flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="anio">
                Año
              </label>
              <input
                id="anio"
                type="number"
                min="2000"
                max="2100"
                value={anio}
                onChange={(e) => setAnio(Number(e.target.value))}
                className="input w-28"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="mes">
                Mes
              </label>
              <select
                id="mes"
                value={anual ? '' : mes}
                disabled={anual}
                onChange={(e) => setMes(Number(e.target.value))}
                className="input"
              >
                {MESES.map((nombre, i) => (
                  <option key={nombre} value={i + 1}>{nombre}</option>
                ))}
              </select>
            </div>
            <label className="flex items-center gap-2 pb-2 text-sm text-neutral-secondary">
              <input type="checkbox" checked={anual} onChange={(e) => setAnual(e.target.checked)} />
              Registro anual (todo el año)
            </label>
          </div>

          <div className="mb-6 flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="formato">
                Formato
              </label>
              <select id="formato" value={formato} onChange={(e) => setFormato(e.target.value)} className="input">
                <option value="TXT">TXT (tabulaciones)</option>
                <option value="CSV">CSV (punto y coma)</option>
              </select>
              <p className="mt-1 max-w-xs text-xs text-neutral-muted">
                TXT por defecto: el tabulador no aparece dentro de una razón social, así que no hay
                separador que se confunda con el texto.
              </p>
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="renta">
                Impuesto a la renta
              </label>
              <select id="renta" value={renta} onChange={(e) => setRenta(e.target.value)} className="input">
                <option value="IRE">IRE (empresarial)</option>
                <option value="IRP">IRP-RSP (personal)</option>
              </select>
              <p className="mt-1 max-w-xs text-xs text-neutral-muted">
                A cuál se imputan los comprobantes. Depende de las obligaciones inscriptas en el RUC,
                así que lo elegís vos.
              </p>
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="identificador">
                Identificador
              </label>
              <input
                id="identificador"
                value={identificador}
                onChange={(e) => setIdentificador(e.target.value)}
                maxLength={5}
                className="input w-28"
              />
              <p className="mt-1 max-w-xs text-xs text-neutral-muted">
                Hasta 5 letras o números, distinto en cada archivo que subas.
              </p>
            </div>
          </div>

          <button onClick={descargar} className="btn btn-primary inline-flex items-center gap-2">
            <Download size={16} /> Generar y descargar
          </button>

          {error && <p className="alerta-error mt-4">{error}</p>}
          {descargado && (
            <p className="mt-4 text-sm text-green-300">
              Listo: <span className="font-mono">{descargado}</span>. Subilo tal cual a Marangatu,
              sin descomprimirlo.
            </p>
          )}

          <p className="mt-6 border-t border-dark-border pt-4 text-xs text-neutral-muted">
            El archivo <strong>no incluye</strong> comprobantes que hayan pasado por e-Kuatia,
            porque de esos la DNIT ya tiene el dato. Hoy TechStore arma el KuDE pero no transmite al
            SIFEN, así que sus facturas sí van acá. Si algún día se transmite de verdad, esto hay
            que revisarlo.
          </p>
        </div>
      )}
    </div>
  )
}

export default AdminContabilidadPage
