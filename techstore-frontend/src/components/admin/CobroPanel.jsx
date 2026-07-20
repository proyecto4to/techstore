import { useState } from 'react'
import { formatPrecio } from '../../utils/format'

/**
 * Panel de cobro de un pedido con pago mixto: efectivo y/o transferencia.
 * La suma debe igualar el total antes de habilitar el registro.
 */
function CobroPanel({ total, onCobrar, onCancelar, ocupado }) {
  const [efectivo, setEfectivo] = useState('')
  const [transferencia, setTransferencia] = useState('')
  const [comprobante, setComprobante] = useState('')

  const nEfectivo = Number(efectivo) || 0
  const nTransferencia = Number(transferencia) || 0
  const suma = nEfectivo + nTransferencia
  const restante = Math.round((total - suma) * 100) / 100
  const valido = Math.abs(restante) < 0.005 && suma > 0

  const registrar = () => {
    const lineas = []
    if (nEfectivo > 0) {
      lineas.push({ tipoPago: 'Efectivo', monto: nEfectivo, numeroComprobante: null })
    }
    if (nTransferencia > 0) {
      lineas.push({
        tipoPago: 'Transferencia',
        monto: nTransferencia,
        numeroComprobante: comprobante.trim() || null,
      })
    }
    onCobrar(lineas)
  }

  return (
    <div className="rounded-md border border-dark-border bg-dark-bg/50 p-4">
      <p className="mb-3 text-sm text-neutral-secondary">
        Total a cobrar: <span className="font-bold text-neutral-text">{formatPrecio(total)}</span>
      </p>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-neutral-text">💵 Efectivo</label>
          <input
            type="number"
            min="0"
            value={efectivo}
            onChange={(e) => setEfectivo(e.target.value)}
            className="input w-40 py-1 text-sm"
            placeholder="0"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-neutral-text">🏦 Transferencia</label>
          <input
            type="number"
            min="0"
            value={transferencia}
            onChange={(e) => setTransferencia(e.target.value)}
            className="input w-40 py-1 text-sm"
            placeholder="0"
          />
        </div>
        {nTransferencia > 0 && (
          <div>
            <label className="mb-1 block text-xs font-medium text-neutral-text">Nº comprobante</label>
            <input
              value={comprobante}
              onChange={(e) => setComprobante(e.target.value)}
              maxLength={50}
              className="input w-40 py-1 text-sm"
              placeholder="Opcional"
            />
          </div>
        )}
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => { setEfectivo(String(total)); setTransferencia('') }}
          className="btn btn-secondary text-xs"
        >
          Todo efectivo
        </button>
        <button
          type="button"
          onClick={() => { setTransferencia(String(total)); setEfectivo('') }}
          className="btn btn-secondary text-xs"
        >
          Todo transferencia
        </button>
        {nEfectivo > 0 && restante > 0 && (
          <button
            type="button"
            onClick={() => setTransferencia(String(restante))}
            className="btn btn-secondary text-xs"
          >
            Resto en transferencia
          </button>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <p className={`text-sm font-semibold ${valido ? 'text-emerald-400' : 'text-yellow-400'}`}>
          {valido
            ? '✓ Montos completos'
            : restante > 0
              ? `Falta: ${formatPrecio(restante)}`
              : `Excede por: ${formatPrecio(-restante)}`}
        </p>
        <button
          onClick={registrar}
          disabled={!valido || ocupado}
          className="btn btn-primary text-sm"
        >
          💵 Registrar cobro
        </button>
        <button onClick={onCancelar} className="btn btn-secondary text-sm">
          Cancelar
        </button>
      </div>
    </div>
  )
}

export default CobroPanel
