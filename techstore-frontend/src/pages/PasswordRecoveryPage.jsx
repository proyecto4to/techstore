import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { authService } from '../services/authService'
import { passwordValida, passwordsCoinciden } from '../utils/password'

export default function PasswordRecoveryPage() {
  const [email, setEmail] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)
  const [error, setError] = useState(null)

  const enviar = async (event) => {
    event.preventDefault()
    setEnviando(true)
    setError(null)
    try {
      await authService.solicitarRecuperacion(email)
      setEnviado(true)
    } catch {
      // Antes el fallo se mostraba con el mismo estilo verde que el éxito, así
      // que «no pudimos procesar la solicitud» parecía una confirmación y la
      // persona se quedaba esperando un correo que nunca salió.
      setError('No pudimos procesar la solicitud. Revisá tu conexión e intentá de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  if (enviado) {
    return (
      <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
        <div className="card w-full space-y-5">
          <h1 className="text-2xl font-bold text-neutral-text">Revisá tu correo</h1>
          <p className="text-neutral-secondary">
            Si <strong className="text-neutral-text">{email}</strong> tiene una cuenta, le enviamos
            un enlace para crear una contraseña nueva. Puede tardar un par de minutos y a veces cae
            en el correo no deseado.
          </p>
          <p className="text-sm text-neutral-muted">
            El enlace vence en 15 minutos y funciona una sola vez.
          </p>
          <Link to="/login" className="btn btn-primary w-full text-center">
            Volver al ingreso
          </Link>
          <button
            type="button"
            onClick={() => setEnviado(false)}
            className="w-full text-sm text-primary-300 hover:text-accent-400"
          >
            Usar otro correo
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
      <form onSubmit={enviar} className="card w-full space-y-5">
        <h1 className="text-2xl font-bold text-neutral-text">Recuperar contraseña</h1>
        <p className="text-sm text-neutral-secondary">
          Poné tu correo y te mandamos un enlace para crear una contraseña nueva.
        </p>
        {error && (
          <p className="alerta-error mb-0 text-sm" role="alert">
            {error}
          </p>
        )}
        <div>
          <label
            className="mb-2 block text-sm font-medium text-neutral-text"
            htmlFor="recovery-email"
          >
            Correo electrónico
          </label>
          <input
            id="recovery-email"
            type="email"
            required
            autoFocus
            autoComplete="email"
            className="input w-full"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              setError(null)
            }}
          />
        </div>
        <button type="submit" disabled={enviando} className="btn btn-primary w-full">
          {enviando ? 'Enviando…' : 'Enviar enlace'}
        </button>
        <Link to="/login" className="block text-center text-sm text-primary-300">
          Volver al ingreso
        </Link>
      </form>
    </div>
  )
}

/** Los requisitos, dichos antes de escribir y no después de fallar. */
const REQUISITOS = 'Al menos 8 caracteres, con una mayúscula, una minúscula y un número.'

export function PasswordResetPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [form, setForm] = useState({ password: '', confirmar: '' })
  const [verClave, setVerClave] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [completado, setCompletado] = useState(false)

  // Se valida mientras escribe y el mensaje va pegado al campo que falla, en
  // lugar de aparecer todo junto arriba recién al apretar el botón.
  const claveCorta = form.password.length > 0 && !passwordValida(form.password)
  const noCoinciden =
    form.confirmar.length > 0 && !passwordsCoinciden(form.password, form.confirmar)
  const puedeGuardar =
    Boolean(token) &&
    passwordValida(form.password) &&
    passwordsCoinciden(form.password, form.confirmar)

  const guardar = async (event) => {
    event.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await authService.completarRecuperacion(token, form.password)
      setCompletado(true)
      setForm({ password: '', confirmar: '' })
    } catch (err) {
      setError(err.response?.data?.detail ?? 'El enlace es inválido, venció o ya fue utilizado.')
    } finally {
      setEnviando(false)
    }
  }

  if (completado) {
    return (
      <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
        <div className="card w-full space-y-5">
          <h1 className="text-2xl font-bold text-neutral-text">Contraseña actualizada</h1>
          <p className="text-neutral-secondary">
            Ya podés entrar con tu contraseña nueva. Por seguridad cerramos las sesiones que
            estaban abiertas en otros dispositivos.
          </p>
          <Link to="/login" className="btn btn-primary w-full text-center">
            Iniciar sesión
          </Link>
        </div>
      </div>
    )
  }

  // Sin token la pantalla no sirve para nada: se dice qué pasó y cómo salir,
  // en vez de mostrar un formulario con el botón siempre deshabilitado.
  if (!token) {
    return (
      <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
        <div className="card w-full space-y-5">
          <h1 className="text-2xl font-bold text-neutral-text">Este enlace no es válido</h1>
          <p className="text-neutral-secondary">
            Puede que se haya cortado al copiarlo del correo. Pedí uno nuevo y abrilo directamente
            desde ahí.
          </p>
          <Link to="/password/forgot" className="btn btn-primary w-full text-center">
            Pedir un enlace nuevo
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
      <form onSubmit={guardar} className="card w-full space-y-5">
        <h1 className="text-2xl font-bold text-neutral-text">Nueva contraseña</h1>
        <p className="text-sm text-neutral-secondary">{REQUISITOS}</p>

        {error && (
          <p className="alerta-error mb-0 text-sm" role="alert">
            {error}
          </p>
        )}

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="reset-pass">
            Nueva contraseña
          </label>
          <input
            id="reset-pass"
            type={verClave ? 'text' : 'password'}
            required
            autoFocus
            autoComplete="new-password"
            aria-describedby={claveCorta ? 'reset-pass-error' : undefined}
            aria-invalid={claveCorta}
            className="input w-full"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
          />
          {claveCorta && (
            <p id="reset-pass-error" className="mt-1 text-sm text-red-400">
              {REQUISITOS}
            </p>
          )}
        </div>

        <div>
          <label
            className="mb-2 block text-sm font-medium text-neutral-text"
            htmlFor="reset-confirm"
          >
            Repetila para confirmar
          </label>
          <input
            id="reset-confirm"
            type={verClave ? 'text' : 'password'}
            required
            autoComplete="new-password"
            aria-describedby={noCoinciden ? 'reset-confirm-error' : undefined}
            aria-invalid={noCoinciden}
            className="input w-full"
            value={form.confirmar}
            onChange={(event) => setForm({ ...form, confirmar: event.target.value })}
          />
          {noCoinciden && (
            <p id="reset-confirm-error" className="mt-1 text-sm text-red-400">
              Las dos contraseñas tienen que ser iguales.
            </p>
          )}
        </div>

        <label className="flex items-center gap-2 text-sm text-neutral-secondary">
          <input
            type="checkbox"
            checked={verClave}
            onChange={(event) => setVerClave(event.target.checked)}
          />
          Ver lo que escribo
        </label>

        <button
          type="submit"
          disabled={enviando || !puedeGuardar}
          className="btn btn-primary w-full disabled:cursor-not-allowed"
        >
          {enviando ? 'Guardando…' : 'Guardar contraseña nueva'}
        </button>
      </form>
    </div>
  )
}
