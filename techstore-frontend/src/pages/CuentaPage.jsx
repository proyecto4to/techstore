import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { usuarioService } from '../services/usuarioService'
import { useAuthStore } from '../store/authStore'

function CuentaPage() {
  const { usuario } = useAuthStore()
  const [form, setForm] = useState({ actual: '', nueva: '', confirmar: '' })
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setMensaje(null)
    setError(null)
    if (form.nueva !== form.confirmar) {
      setError('Las contraseñas nuevas no coinciden')
      return
    }
    setGuardando(true)
    try {
      await usuarioService.cambiarMiPassword(form.actual, form.nueva)
      setMensaje('Contraseña actualizada correctamente.')
      setForm({ actual: '', nueva: '', confirmar: '' })
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo cambiar la contraseña.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="container mx-auto max-w-lg px-6 py-8">
      <h1 className="mb-2 text-4xl font-bold text-neutral-text">Mi cuenta</h1>
      <p className="mb-8 text-neutral-secondary">
        {usuario?.nombre} {usuario?.apellido} · {usuario?.email} · rol {usuario?.rol}
      </p>

      <form onSubmit={handleSubmit} className="card space-y-5">
        <h3 className="inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
          <KeyRound size={18} className="text-accent-400" /> Cambiar contraseña
        </h3>

        {mensaje && <p className="alerta-ok mb-0 text-sm" role="status">{mensaje}</p>}
        {error && <p className="alerta-error mb-0 text-sm" role="alert">{error}</p>}

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="actual">
            Contraseña actual
          </label>
          <input id="actual" type="password" required autoComplete="current-password"
            value={form.actual} onChange={(e) => setForm({ ...form, actual: e.target.value })} className="input" />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="nueva">
            Contraseña nueva (mínimo 8 caracteres)
          </label>
          <input id="nueva" type="password" required minLength={8} autoComplete="new-password"
            value={form.nueva} onChange={(e) => setForm({ ...form, nueva: e.target.value })} className="input" />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="confirmar">
            Confirmar contraseña nueva
          </label>
          <input id="confirmar" type="password" required autoComplete="new-password"
            value={form.confirmar} onChange={(e) => setForm({ ...form, confirmar: e.target.value })} className="input" />
        </div>

        <button type="submit" disabled={guardando} className="btn btn-primary w-full">
          {guardando ? 'Guardando…' : 'Cambiar contraseña'}
        </button>
      </form>
    </div>
  )
}

export default CuentaPage
