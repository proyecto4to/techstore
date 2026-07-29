import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Wallet, FileText } from 'lucide-react'
import { cajaService } from '../services/cajaService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'
import StatCard from '../components/common/StatCard'
import CierreCajaPanel from '../components/admin/CierreCajaPanel'

const FORM_VACIO = { tipo: 'Ingreso', monto: '', concepto: '', referencia: '' }

function AdminCajaPage() {
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(FORM_VACIO)
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const filtros = { desde: desde || null, hasta: hasta || null }

  const { data: resumen } = useQuery({
    queryKey: ['caja-resumen', desde, hasta],
    queryFn: () => cajaService.resumen(filtros),
    placeholderData: keepPreviousData,
  })

  const { data: movimientos, isLoading } = useQuery({
    queryKey: ['caja-movimientos', desde, hasta, page],
    queryFn: () => cajaService.listar({ ...filtros, page }),
    placeholderData: keepPreviousData,
  })

  const registrarMutation = useMutation({
    mutationFn: (f) =>
      cajaService.registrar({
        tipo: f.tipo,
        monto: Number(f.monto),
        concepto: f.concepto.trim(),
        referencia: f.referencia.trim() || null,
      }),
    onSuccess: (movimiento) => {
      setError(null)
      setMensaje(`Movimiento N.º ${movimiento.numero} registrado (${movimiento.tipo}).`)
      setForm(FORM_VACIO)
      queryClient.invalidateQueries({ queryKey: ['caja-resumen'] })
      queryClient.invalidateQueries({ queryKey: ['caja-movimientos'] })
    },
    onError: (err) => {
      const errores = err.response?.data?.errores
      setError(
        errores
          ? Object.values(errores).join(' · ')
          : err.response?.data?.detail ?? 'No se pudo registrar el movimiento.',
      )
    },
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Caja</span>
      </h1>
      <AdminTabs />

      <div className="card mb-8 flex flex-wrap items-end gap-4">
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="cdesde">
            Desde
          </label>
          <input id="cdesde" type="date" value={desde} onChange={(e) => { setPage(0); setDesde(e.target.value) }} className="input" />
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="chasta">
            Hasta
          </label>
          <input id="chasta" type="date" value={hasta} onChange={(e) => { setPage(0); setHasta(e.target.value) }} className="input" />
        </div>
        <button onClick={() => { setDesde(''); setHasta(''); setPage(0) }} className="btn btn-secondary text-sm">
          Todo el historial
        </button>
        <button
          onClick={() => cajaService.resumenPdf({ desde: desde || null, hasta: hasta || null })}
          className="btn btn-primary ml-auto inline-flex items-center gap-2 text-sm"
        >
          <FileText size={16} /> Exportar arqueo PDF
        </button>
      </div>

      {resumen && (
        <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-3">
          <StatCard
            etiqueta="Efectivo (cajón)"
            valor={formatPrecio(resumen.netoEfectivo)}
            detalle={`Ingresos ${formatPrecio(resumen.ingresosEfectivo)} · Egresos ${formatPrecio(
              resumen.egresosEfectivo,
            )}`}
            icono="💵"
          />
          <StatCard
            etiqueta="Transferencias (banco)"
            valor={formatPrecio(resumen.netoTransferencia)}
            detalle={`Ingresos ${formatPrecio(resumen.ingresosTransferencia)} · Egresos ${formatPrecio(
              resumen.egresosTransferencia,
            )}`}
            icono="🏦"
          />
          <StatCard
            etiqueta="Total del período"
            valor={formatPrecio(resumen.saldo)}
            detalle={
              resumen.saldo < 0 ? 'Negativo: salió más de lo que entró' : 'Efectivo + transferencias'
            }
            icono="📊"
          />
        </div>
      )}

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
          registrarMutation.mutate(form)
        }}
        className="card mb-8 grid grid-cols-1 gap-4 md:grid-cols-4"
      >
        <h3 className="col-span-full inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
          <Wallet size={18} className="text-accent-400" /> Registrar movimiento manual
        </h3>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="tipo">
            Tipo
          </label>
          <select
            id="tipo"
            value={form.tipo}
            onChange={(e) => setForm({ ...form, tipo: e.target.value })}
            className="input"
          >
            <option value="Ingreso">Ingreso</option>
            <option value="Egreso">Egreso</option>
          </select>
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="monto">
            Monto (₲) *
          </label>
          <input
            id="monto"
            type="number"
            required
            min="1"
            value={form.monto}
            onChange={(e) => setForm({ ...form, monto: e.target.value })}
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="concepto">
            Concepto *
          </label>
          <input
            id="concepto"
            required
            maxLength={255}
            placeholder="Ej.: Pago de servicios"
            value={form.concepto}
            onChange={(e) => setForm({ ...form, concepto: e.target.value })}
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="referencia">
            Referencia
          </label>
          <input
            id="referencia"
            maxLength={50}
            placeholder="Nro. recibo (opcional)"
            value={form.referencia}
            onChange={(e) => setForm({ ...form, referencia: e.target.value })}
            className="input"
          />
        </div>

        <div className="col-span-full">
          <button type="submit" disabled={registrarMutation.isPending} className="btn btn-primary">
            {registrarMutation.isPending ? 'Registrando…' : 'Registrar movimiento'}
          </button>
        </div>
      </form>

      {isLoading && <p className="text-neutral-muted">Cargando movimientos…</p>}

      {movimientos && movimientos.content.length === 0 && (
        <p className="text-neutral-muted">Sin movimientos en el período.</p>
      )}

      {movimientos && movimientos.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Fecha</th>
                <th>Tipo</th>
                <th>Concepto</th>
                <th>Referencia</th>
                <th>Usuario</th>
                <th className="text-right">Monto</th>
              </tr>
            </thead>
            <tbody>
              {movimientos.content.map((movimiento) => {
                const esIngreso = movimiento.tipo === 'Ingreso'
                return (
                  <tr key={movimiento.id}>
                    <td className="font-semibold text-neutral-text">#{movimiento.numero}</td>
                    <td>{new Date(movimiento.fecha).toLocaleString('es-PY')}</td>
                    <td>
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-semibold ${
                          esIngreso ? 'bg-emerald-400/20 text-emerald-400' : 'bg-red-400/20 text-red-400'
                        }`}
                      >
                        {movimiento.tipo}
                      </span>
                    </td>
                    <td className="text-neutral-text">{movimiento.concepto}</td>
                    <td className="text-neutral-secondary">{movimiento.referencia ?? '—'}</td>
                    <td className="text-neutral-secondary">{movimiento.usuarioEmail}</td>
                    <td
                      className={`text-right font-semibold ${
                        esIngreso ? 'text-emerald-400' : 'text-red-400'
                      }`}
                    >
                      {esIngreso ? '+' : '−'} {formatPrecio(movimiento.monto)}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {movimientos && movimientos.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="btn btn-secondary"
          >
            ← Anterior
          </button>
          <span className="text-neutral-secondary">
            Página {movimientos.page + 1} de {movimientos.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(movimientos.totalPages - 1, p + 1))}
            disabled={page >= movimientos.totalPages - 1}
            className="btn btn-secondary"
          >
            Siguiente →
          </button>
        </div>
      )}

      <p className="mt-6 text-sm text-neutral-muted">
        Al cobrar un pedido, el ingreso se registra automáticamente con la factura como referencia.
      </p>

      <CierreCajaPanel />
    </div>
  )
}

export default AdminCajaPage
