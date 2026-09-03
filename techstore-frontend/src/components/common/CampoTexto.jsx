import { useId, useState } from 'react'

/**
 * Un campo con etiqueta propia y su error debajo.
 *
 * Existe porque cada formulario resolvía esto a mano y ninguno igual: unos con
 * `placeholder` en vez de etiqueta, otros juntando todos los errores arriba al
 * enviar. Acá el mensaje aparece pegado al campo que falla y recién cuando la
 * persona ya pasó por él, no mientras lo está escribiendo por primera vez.
 */
export default function CampoTexto({
  etiqueta,
  valor,
  onChange,
  /** Devuelve el motivo del rechazo, o `null` si el valor está bien. */
  validar,
  ayuda,
  type = 'text',
  requerido = false,
  autoComplete,
  ...props
}) {
  const id = useId()
  const [tocado, setTocado] = useState(false)
  const error = tocado && validar ? validar(valor) : null
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`

  return (
    <div>
      <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor={id}>
        {etiqueta}
        {requerido && <span className="text-red-400"> *</span>}
      </label>
      <input
        id={id}
        type={type}
        required={requerido}
        autoComplete={autoComplete}
        value={valor}
        onChange={(event) => onChange(event.target.value)}
        // Se marca como tocado al salir del campo: mostrar el error mientras
        // todavía está escribiendo la primera letra sería regañarlo por no
        // haber terminado.
        onBlur={() => setTocado(true)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : ayuda ? idAyuda : undefined}
        className="input w-full"
        {...props}
      />
      {error ? (
        <p id={idError} className="mt-1 text-sm text-red-400" role="alert">
          {error}
        </p>
      ) : (
        ayuda && (
          <p id={idAyuda} className="mt-1 text-xs text-neutral-secondary">
            {ayuda}
          </p>
        )
      )}
    </div>
  )
}
