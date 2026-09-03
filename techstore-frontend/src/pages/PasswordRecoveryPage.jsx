import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { authService } from '../services/authService'
import { passwordValida, passwordsCoinciden } from '../utils/password'

export default function PasswordRecoveryPage() {
  const [email, setEmail] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [mensaje, setMensaje] = useState(null)

  const enviar = async (event) => {
    event.preventDefault()
    setEnviando(true)
    setMensaje(null)
    try {
      await authService.solicitarRecuperacion(email)
      setMensaje('Si la cuenta existe, enviamos un enlace de recuperación. Revisá también el correo no deseado.')
    } catch {
      setMensaje('No pudimos procesar la solicitud. Intentá nuevamente.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
      <form onSubmit={enviar} className="card w-full space-y-5">
        <h1 className="text-2xl font-bold text-neutral-text">Recuperar contraseña</h1>
        <p className="text-sm text-neutral-secondary">
          El enlace vence en 15 minutos, funciona una sola vez y no revela si el correo está registrado.
        </p>
        {mensaje && <p className="alerta-ok mb-0 text-sm" role="status">{mensaje}</p>}
        <label className="block text-sm font-medium text-neutral-text" htmlFor="recovery-email">
          Correo electrónico
        </label>
        <input id="recovery-email" type="email" required autoComplete="email" className="input"
          value={email} onChange={(event) => setEmail(event.target.value)} />
        <button type="submit" disabled={enviando} className="btn btn-primary w-full">
          {enviando ? 'Enviando…' : 'Enviar enlace'}
        </button>
        <Link to="/login" className="text-center text-sm text-primary-300">Volver al ingreso</Link>
      </form>
    </div>
  )
}

export function PasswordResetPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [form, setForm] = useState({ password: '', confirmar: '' })
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(token ? null : 'El enlace no contiene un token válido.')
  const [completado, setCompletado] = useState(false)

  const guardar = async (event) => {
    event.preventDefault()
    setError(null)
    if (!passwordValida(form.password)) {
      setError('Usá al menos 8 caracteres, con mayúscula, minúscula y número.')
      return
    }
    if (!passwordsCoinciden(form.password, form.confirmar)) {
      setError('Las contraseñas no coinciden.')
      return
    }
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

  return (
    <div className="container mx-auto flex min-h-[75vh] max-w-lg items-center px-6 py-8">
      <form onSubmit={guardar} className="card w-full space-y-5">
        <h1 className="text-2xl font-bold text-neutral-text">Nueva contraseña</h1>
        {completado ? (
          <>
            <p className="alerta-ok mb-0" role="status">Contraseña actualizada y sesiones anteriores revocadas.</p>
            <Link to="/login" className="btn btn-primary w-full text-center">Iniciar sesión</Link>
          </>
        ) : (
          <>
            {error && <p className="alerta-error mb-0 text-sm" role="alert">{error}</p>}
            <input type="password" required autoComplete="new-password" className="input"
              placeholder="Nueva contraseña" value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })} />
            <input type="password" required autoComplete="new-password" className="input"
              placeholder="Confirmar contraseña" value={form.confirmar}
              onChange={(event) => setForm({ ...form, confirmar: event.target.value })} />
            <button type="submit" disabled={enviando || !token} className="btn btn-primary w-full">
              {enviando ? 'Guardando…' : 'Cambiar contraseña'}
            </button>
          </>
        )}
      </form>
    </div>
  )
}
