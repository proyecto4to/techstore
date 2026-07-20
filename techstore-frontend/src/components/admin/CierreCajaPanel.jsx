import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Lock } from 'lucide-react'
import { cajaService } from '../../services/cajaService'
import { formatPrecio } from '../../utils/format'

/**
 * Cierre de caja por turno: muestra el efectivo teórico calculado desde
 * el último cierre y deja registrar el efectivo contado a mano, con la
 * diferencia (sobrante/faltante). Lista los cierres anteriores.
 */
function CierreCajaPanel() {
  const [contado, setContado] = useState('')
  const [notas, setNotas] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: preview } = useQuery({
    queryKey: ['caja-cierre-preview'],
    queryFn: () => cajaService.previewCierre(),
  })

  const { data: cierres } = useQuery({
    queryKey: ['caja-cierres'],
    queryFn: () => cajaService.listarCierres(),
    placeholderData: keepPreviousData,
  })

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
      queryClient.invalidateQueries({ queryKey: ['caja-cierre-preview'] })
      queryClient.invalidateQueries({ queryKey: ['caja-cierres'] })
      queryClient.invalidateQueries({ queryKey: ['caja-resumen'] })
      queryClient.invalidateQueries({ queryKey: ['caja-movimientos'] })
    },
    onError: (err) => setError(err.response?.data?.detail ?? 'No se pudo cerrar la caja.'),
  })

  const teorico = preview?.efectivoTeorico ?? 0
  const diferencia = contado === '' ? null : Number(contado) - teorico

  return (
    <div className="card mt-8">
      <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
        <Lock size={18} className="text-accent-400" /> Cierre de caja
      </h3>

      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {error && <p className="alerta-error" role="alert">{error}</p>}

      {preview && (
        <>
          <p className="mb-3 text-sm text-neutral-muted">
            Turno abierto desde {new Date(preview.desde).toLocaleString('es-PY')}
          </p>
          <div className="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Ingresos efectivo</p>
              <p className="font-semibold text-emerald-400">{formatPrecio(preview.ingresosEfectivo)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Egresos efectivo</p>
              <p className="font-semibold text-red-400">{formatPrecio(preview.egresosEfectivo)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Efectivo teórico</p>
              <p className="font-bold text-neutral-text">{formatPrecio(preview.efectivoTeorico)}</p>
            </div>
            <div className="rounded-md border border-dark-border p-3">
              <p className="text-xs text-neutral-muted">Transferencias (al banco)</p>
              <p className="font-semibold text-accent-400">{formatPrecio(preview.ingresosTransferencia)}</p>
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
            />
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
              onClick={() => cerrarMutation.mutate()}
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
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Cierre</th>
                <th>Cajero</th>
                <th>Teórico</th>
                <th>Contado</th>
                <th>Diferencia</th>
              </tr>
            </thead>
            <tbody>
              {cierres.content.map((c) => (
                <tr key={c.id}>
                  <td className="font-semibold text-neutral-text">#{c.numero}</td>
                  <td>{new Date(c.fechaCierre).toLocaleString('es-PY')}</td>
                  <td className="text-neutral-secondary">{c.usuarioEmail}</td>
                  <td>{formatPrecio(c.efectivoTeorico)}</td>
                  <td>{formatPrecio(c.efectivoContado)}</td>
                  <td
                    className={`font-semibold ${
                      c.diferencia === 0 ? 'text-emerald-400' : c.diferencia > 0 ? 'text-accent-400' : 'text-red-400'
                    }`}
                  >
                    {c.diferencia > 0 ? '+' : ''}{formatPrecio(c.diferencia)}
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
