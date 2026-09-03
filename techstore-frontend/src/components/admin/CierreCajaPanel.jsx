import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Lock, LockOpen } from 'lucide-react'
import { cajaService } from '../../services/cajaService'
import { formatPrecio } from '../../utils/format'

/**
 * Apertura y cierre de caja por turno. Al abrir se declara el efectivo con el
 * que arranca el cajón; al cerrar se compara el efectivo contado contra el
 * teórico (saldo inicial + ingresos - egresos). Lista los cierres anteriores.
 */
function CierreCajaPanel() {
  const [saldoInicial, setSaldoInicial] = useState('')
  const [notasApertura, setNotasApertura] = useState('')
  const [contado, setContado] = useState('')
  const [notas, setNotas] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: apertura } = useQuery({
    queryKey: ['caja-apertura'],
    queryFn: () => cajaService.aperturaActual(),
  })

  const { data: preview } = useQuery({
    queryKey: ['caja-cierre-preview'],
    queryFn: () => cajaService.previewCierre(),
    enabled: apertura?.abierta === true,
  })

  const { data: cierres } = useQuery({
    queryKey: ['caja-cierres'],
    queryFn: () => cajaService.listarCierres(),
    placeholderData: keepPreviousData,
  })

  const refrescar = () => {
    queryClient.invalidateQueries({ queryKey: ['caja-apertura'] })
    queryClient.invalidateQueries({ queryKey: ['caja-cierre-preview'] })
    queryClient.invalidateQueries({ queryKey: ['caja-cierres'] })
    queryClient.invalidateQueries({ queryKey: ['caja-resumen'] })
    queryClient.invalidateQueries({ queryKey: ['caja-movimientos'] })
  }

  const abrirMutation = useMutation({
    mutationFn: () => cajaService.abrir(Number(saldoInicial), notasApertura.trim()),
    onSuccess: (a) => {
      setError(null)
      setMensaje(`Caja abierta (turno N.º ${a.numero}) con ${formatPrecio(a.saldoInicial)} en el cajón.`)
      setSaldoInicial('')
      setNotasApertura('')
      refrescar()
    },
    onError: (err) => setError(err.response?.data?.detail ?? 'No se pudo abrir la caja.'),
  })

  /**
   * Antes de cerrar revalida el teórico: si entraron cobros mientras el cajero
   * contaba la plata, avisa con el valor nuevo en vez de registrar una
   * diferencia que en realidad no existe.
   */
  const confirmarYCerrar = async () => {
    const actual = await queryClient.fetchQuery({
      queryKey: ['caja-cierre-preview'],
      queryFn: () => cajaService.previewCierre(),
    })
    if (actual.efectivoTeorico !== teorico) {
      setMensaje(null)
      setError(
        `Entraron movimientos nuevos mientras contabas: el efectivo teórico pasó de ` +
          `${formatPrecio(teorico)} a ${formatPrecio(actual.efectivoTeorico)}. ` +
          `Revisá el conteo y volvé a presionar "Registrar cierre".`,
      )
      return
    }
    cerrarMutation.mutate()
  }

  const cerrarMutation = useMutation({
    mutationFn: () => cajaService.cerrar(Number(contado), notas.trim()),
    onSuccess: (c) => {
      setError(null)
      const signo = c.diferencia > 0 ? 'sobrante' : c.diferencia < 0 ? 'faltante' : 'sin diferencia'
      setMensaje(
        `Cierre N.º ${c.numero} registrado. Teórico ${formatPrecio(c.efectivoTeorico)}, ` +
          `contado ${formatPrecio(c.efectivoContado)} — ${signo} ${formatPrecio(Math.abs(c.diferencia))}.`,
      )
      setContado('')
      setNotas('')
      refrescar()
    },
    onError: (err) => setError(err.response?.data?.detail ?? 'No se pudo cerrar la caja.'),
  })

  const abierta = apertura?.abierta === true
  const teorico = preview?.efectivoTeorico ?? 0
  const diferencia = contado === '' ? null : Number(contado) - teorico

  return (
    <div className="card mt-8">
      <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
        {abierta ? (
          <>
            <LockOpen size={18} className="text-emerald-400" /> Caja abierta
          </>
        ) : (
          <>
            <Lock size={18} className="text-accent-400" /> Caja cerrada
          </>
        )}
      </h3>

      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {error && <p className="alerta-error" role="alert">{error}</p>}

      {!abierta && (
        <>
          <p className="mb-3 text-sm text-neutral-muted">
            Para arquear el turno, abrí la caja indicando cuánto efectivo hay en el cajón al empezar.
          </p>
          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="saldo-inicial">
                Efectivo inicial en caja *
              </label>
              <input
                id="saldo-inicial"
                type="number"
                min="0"
                value={saldoInicial}
                onChange={(e) => setSaldoInicial(e.target.value)}
                className="input w-48"
                placeholder="0"
              />
            </div>
            <input
              value={notasApertura}
              onChange={(e) => setNotasApertura(e.target.value)}
              maxLength={1000}
              placeholder="Notas de la apertura (opcional)"
              className="input max-w-xs flex-1"
             aria-label="Efectivo inicial en caja"/>
            <button
              onClick={() => abrirMutation.mutate()}
              disabled={saldoInicial === '' || abrirMutation.isPending}
              className="btn btn-primary text-sm"
            >
              {abrirMutation.isPending ? 'Abriendo…' : 'Abrir caja'}
            </button>
          </div>
        </>
      )}

      {abierta && preview && (
        <>
          <p className="mb-3 text-sm text-neutral-muted">
            Turno N.º {apertura.numero} abierto el {new Date(apertura.fechaApertura).toLocaleString('es-PY')} por{' '}
            {apertura.usuario}. Movimientos contados desde {new Date(preview.desde).toLocaleString('es-PY')}.
          </p>
          <div className="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Saldo inicial</p>
              <p className="font-semibold text-neutral-secondary">{formatPrecio(preview.saldoInicial)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Ingresos efectivo</p>
              <p className="font-semibold text-emerald-400">{formatPrecio(preview.ingresosEfectivo)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Egresos efectivo</p>
              <p className="font-semibold text-red-400">{formatPrecio(preview.egresosEfectivo)}</p>
            </div>
            <div className="rounded-md border border-primary-400/40 bg-primary-400/5 p-3">
              <p className="text-xs text-neutral-muted">Efectivo teórico (en el cajón)</p>
              <p className="font-bold text-neutral-text">{formatPrecio(preview.efectivoTeorico)}</p>
            </div>
          </div>

          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-neutral-muted">
            Transferencias del turno (van al banco, no al cajón)
          </p>
          <div className="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Ingresos por transferencia</p>
              <p className="font-semibold text-accent-400">{formatPrecio(preview.ingresosTransferencia)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Egresos por transferencia</p>
              <p className="font-semibold text-red-400">{formatPrecio(preview.egresosTransferencia)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Neto bancario</p>
              <p className="font-semibold text-neutral-text">
                {formatPrecio(preview.ingresosTransferencia - preview.egresosTransferencia)}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-neutral-text" htmlFor="contado">
                Efectivo contado en caja *
              </label>
              <input
                id="contado"
                type="number"
                min="0"
                value={contado}
                onChange={(e) => setContado(e.target.value)}
                className="input w-48"
                placeholder="0"
              />
            </div>
            <input
              value={notas}
              onChange={(e) => setNotas(e.target.value)}
              maxLength={1000}
              placeholder="Notas del cierre (opcional)"
              className="input max-w-xs flex-1"
             aria-label="Efectivo contado en caja"/>
            {diferencia !== null && (
              <p
                className={`text-sm font-semibold ${
                  diferencia === 0 ? 'text-emerald-400' : diferencia > 0 ? 'text-accent-400' : 'text-red-400'
                }`}
              >
                {diferencia === 0
                  ? '✓ Cuadra exacto'
                  : diferencia > 0
                    ? `Sobrante: ${formatPrecio(diferencia)}`
                    : `Faltante: ${formatPrecio(-diferencia)}`}
              </p>
            )}
            <button
              onClick={confirmarYCerrar}
              disabled={contado === '' || cerrarMutation.isPending}
              className="btn btn-primary text-sm"
            >
              {cerrarMutation.isPending ? 'Cerrando…' : 'Registrar cierre'}
            </button>
          </div>
        </>
      )}

      {cierres && cierres.content.length > 0 && (
        <div className="mt-6 overflow-x-auto">
          <p className="mb-2 text-sm font-semibold text-neutral-secondary">Cierres anteriores</p>
          <table className="tabla-admin tabla-apilable">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Cierre</th>
                <th>Cajero</th>
                <th>Saldo inicial</th>
                <th>Teórico</th>
                <th>Contado</th>
                <th>Diferencia</th>
                <th>Neto banco</th>
              </tr>
            </thead>
            <tbody>
              {cierres.content.map((c) => (
                <tr key={c.id}>
                  <td data-label="N.º" className="font-semibold text-neutral-text">#{c.numero}</td>
                  <td data-label="Cierre">{new Date(c.fechaCierre).toLocaleString('es-PY')}</td>
                  <td data-label="Cajero" className="text-neutral-secondary">{c.usuarioEmail}</td>
                  <td data-label="Saldo inicial" className="text-neutral-secondary">{formatPrecio(c.saldoInicial)}</td>
                  <td data-label="Teórico">{formatPrecio(c.efectivoTeorico)}</td>
                  <td data-label="Contado">{formatPrecio(c.efectivoContado)}</td>
                  <td data-label="Diferencia"
                    className={`font-semibold ${
                      c.diferencia === 0 ? 'text-emerald-400' : c.diferencia > 0 ? 'text-accent-400' : 'text-red-400'
                    }`}
                  >
                    {c.diferencia > 0 ? '+' : ''}{formatPrecio(c.diferencia)}
                  </td>
                  <td data-label="Neto banco" className="text-neutral-secondary">
                    {formatPrecio(c.ingresosTransferencia - c.egresosTransferencia)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default CierreCajaPanel
