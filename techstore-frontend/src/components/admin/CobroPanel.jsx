import { useState } from 'react'
import { formatPrecio } from '../../utils/format'

/**
 * Pago mixto: efectivo y/o transferencia, cuya suma debe igualar el total.
 * Se usa para cobrar una venta y también para pagarle a un proveedor; en ese
 * caso no hay factura que emitir, así que se oculta la parte del RUC.
 */
function CobroPanel({
  total,
  onCobrar,
  onCancelar,
  ocupado,
  clienteRuc,
  clienteRazonSocial,
  pedirRuc = true,
  razonSocialObligatoria = false,
  permitirGuardarEnCliente = true,
  textoBoton = '💵 Registrar cobro',
}) {
  const [efectivo, setEfectivo] = useState('')
  const [transferencia, setTransferencia] = useState('')
  const [comprobante, setComprobante] = useState('')
  // Si el cliente ya tiene RUC cargado, se factura a su nombre por defecto.
  const [conRuc, setConRuc] = useState(Boolean(clienteRuc))
  const [ruc, setRuc] = useState(clienteRuc ?? '')
  const [razonSocial, setRazonSocial] = useState(clienteRazonSocial ?? '')
  const [direccion, setDireccion] = useState('')
  const [telefono, setTelefono] = useState('')
  const [guardarEnCliente, setGuardarEnCliente] = useState(false)

  const nEfectivo = Number(efectivo) || 0
  const nTransferencia = Number(transferencia) || 0
  const suma = nEfectivo + nTransferencia
  const restante = Math.round((total - suma) * 100) / 100
  const montosOk = Math.abs(restante) < 0.005 && suma > 0
  const rucOk = !pedirRuc || !conRuc || ruc.trim() !== ''
  const razonSocialOk = !pedirRuc || !conRuc || !razonSocialObligatoria || razonSocial.trim() !== ''
  const valido = montosOk && rucOk && razonSocialOk

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
    // receptor null = factura a consumidor final (o pago a proveedor, que no factura)
    const receptor =
      pedirRuc && conRuc
        ? {
            ruc: ruc.trim(),
            razonSocial: razonSocial.trim() || null,
            direccion: direccion.trim() || null,
            telefono: telefono.trim() || null,
            guardarEnCliente: permitirGuardarEnCliente && guardarEnCliente,
          }
        : null
    onCobrar(lineas, receptor)
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

      {pedirRuc && (
      <div className="mb-3 rounded-md border border-dark-border p-3">
        <label className="flex items-center gap-2 text-sm font-medium text-neutral-text">
          <input
            type="checkbox"
            checked={conRuc}
            onChange={(e) => setConRuc(e.target.checked)}
            className="h-4 w-4"
          />
          🧾 Facturar con RUC
        </label>
        {!conRuc ? (
          <p className="mt-1 text-xs text-neutral-muted">
            Sin marcar, la factura sale a consumidor final.
          </p>
        ) : (
          <div className="mt-3 flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-text" htmlFor="cobro-ruc">
                RUC *
              </label>
              <input
                id="cobro-ruc"
                value={ruc}
                onChange={(e) => setRuc(e.target.value)}
                maxLength={20}
                className="input w-40 py-1 text-sm"
                placeholder="80012345-0"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-text" htmlFor="cobro-razon">
                Razón social{razonSocialObligatoria ? ' *' : ''}
              </label>
              <input
                id="cobro-razon"
                value={razonSocial}
                onChange={(e) => setRazonSocial(e.target.value)}
                maxLength={255}
                className="input w-64 py-1 text-sm"
                placeholder={razonSocialObligatoria ? 'Nombre fiscal del cliente' : 'Nombre del cliente si se deja vacío'}
              />
            </div>
            {/* Opcionales: el manual del SIFEN no los exige para una venta
                local. Si se cargan, salen impresos en la factura. */}
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-text" htmlFor="cobro-direccion">
                Dirección
              </label>
              <input
                id="cobro-direccion"
                value={direccion}
                onChange={(e) => setDireccion(e.target.value)}
                maxLength={255}
                className="input w-64 py-1 text-sm"
                placeholder="Opcional"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-text" htmlFor="cobro-telefono">
                Teléfono
              </label>
              <input
                id="cobro-telefono"
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                maxLength={20}
                className="input w-40 py-1 text-sm"
                placeholder="Opcional"
              />
            </div>
            {permitirGuardarEnCliente && (
              <label className="flex items-center gap-2 pb-1 text-xs text-neutral-secondary">
                <input
                  type="checkbox"
                  checked={guardarEnCliente}
                  onChange={(e) => setGuardarEnCliente(e.target.checked)}
                  className="h-4 w-4"
                />
                Guardar en la cuenta del cliente
              </label>
            )}
          </div>
        )}
      </div>
      )}

      <div className="flex flex-wrap items-center gap-4">
        <p className={`text-sm font-semibold ${valido ? 'text-emerald-400' : 'text-yellow-400'}`}>
          {!montosOk
            ? restante > 0
              ? `Falta: ${formatPrecio(restante)}`
              : `Excede por: ${formatPrecio(-restante)}`
            : !rucOk
              ? 'Ingresá el RUC o destildá "Facturar con RUC"'
              : !razonSocialOk
                ? 'Ingresá la razón social del receptor'
              : '✓ Montos completos'}
        </p>
        <button
          onClick={registrar}
          disabled={!valido || ocupado}
          title={
            valido
              ? undefined
              : !rucOk
                ? 'Ingresá el RUC del cliente'
                : !razonSocialOk
                  ? 'Ingresá la razón social del cliente'
                : suma <= 0
                  ? 'Ingresá el monto en efectivo y/o transferencia'
                  : restante > 0
                    ? `Falta ${formatPrecio(restante)} para llegar al total`
                    : `Te excedés por ${formatPrecio(-restante)}`
          }
          className="btn btn-primary text-sm disabled:cursor-not-allowed"
        >
          {textoBoton}
        </button>
        <button onClick={onCancelar} className="btn btn-secondary text-sm">
          Cancelar
        </button>
      </div>
    </div>
  )
}

export default CobroPanel
