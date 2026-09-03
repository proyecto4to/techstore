import { useEffect, useRef } from 'react'
import { AlertTriangle } from 'lucide-react'

/**
 * Confirmación para una acción que no se puede deshacer.
 *
 * Dice qué va a pasar, no solo pregunta si está seguro, y los botones nombran
 * la acción en vez de decir «Sí» y «No»: leyendo el botón ya se sabe qué hace.
 *
 * El foco arranca en el botón de volver, que es la salida segura: si alguien
 * aprieta Enter por inercia, no destruye nada.
 */
export default function DialogoConfirmacion({
  abierto,
  titulo,
  descripcion,
  etiquetaConfirmar,
  etiquetaCancelar = 'Volver',
  ocupado = false,
  onConfirmar,
  onCancelar,
}) {
  const salida = useRef(null)

  useEffect(() => {
    if (!abierto) return undefined
    salida.current?.focus()
    const alTeclado = (evento) => {
      if (evento.key === 'Escape') onCancelar()
    }
    document.addEventListener('keydown', alTeclado)
    return () => document.removeEventListener('keydown', alTeclado)
  }, [abierto, onCancelar])

  if (!abierto) return null

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirmacion-titulo"
      aria-describedby="confirmacion-descripcion"
    >
      <div className="card w-full max-w-md">
        <div className="mb-3 flex items-start gap-3">
          <AlertTriangle className="mt-0.5 shrink-0 text-amber-400" size={22} aria-hidden="true" />
          <h2 id="confirmacion-titulo" className="text-xl font-bold text-neutral-text">
            {titulo}
          </h2>
        </div>
        <p id="confirmacion-descripcion" className="mb-6 text-neutral-secondary">
          {descripcion}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button
            ref={salida}
            type="button"
            onClick={onCancelar}
            disabled={ocupado}
            className="btn btn-secondary"
          >
            {etiquetaCancelar}
          </button>
          <button
            type="button"
            onClick={onConfirmar}
            disabled={ocupado}
            className="btn btn-danger disabled:cursor-not-allowed"
          >
            {ocupado ? 'Procesando…' : etiquetaConfirmar}
          </button>
        </div>
      </div>
    </div>
  )
}
