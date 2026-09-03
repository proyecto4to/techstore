import { useState } from 'react'
import { KeyRound, Receipt } from 'lucide-react'
import { usuarioService } from '../services/usuarioService'
import { useAuthStore } from '../store/authStore'
import { passwordValida, passwordsCoinciden } from '../utils/password'

function CuentaPage() {
  const { usuario, setUsuario } = useAuthStore()
  const [form, setForm] = useState({ actual: '', nueva: '', confirmar: '' })
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  const [fiscal, setFiscal] = useState({
    ruc: usuario?.ruc ?? '',
    razonSocial: usuario?.razonSocial ?? '',
  })
  const [mensajeFiscal, setMensajeFiscal] = useState(null)
  const [errorFiscal, setErrorFiscal] = useState(null)
  const [guardandoFiscal, setGuardandoFiscal] = useState(false)

  const guardarFiscales = async (e) => {
    e.preventDefault()
    setMensajeFiscal(null)
    setErrorFiscal(null)
    setGuardandoFiscal(true)
    try {
      const actualizado = await usuarioService.guardarMisDatosFiscales(
        fiscal.ruc.trim(),
        fiscal.razonSocial.trim(),
      )
      setUsuario(actualizado)
      setMensajeFiscal(
        actualizado.ruc
          ? `Listo: tus compras se van a facturar al RUC ${actualizado.ruc}.`
          : 'Datos borrados: tus compras se van a facturar a consumidor final.',
      )
    } catch (err) {
      setErrorFiscal(err.response?.data?.detail ?? 'No se pudieron guardar los datos fiscales.')
    } finally {
      setGuardandoFiscal(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setMensaje(null)
    setError(null)
    if (!passwordsCoinciden(form.nueva, form.confirmar)) {
      setError('Las contraseñas nuevas no coinciden')
      return
    }
    if (!passwordValida(form.nueva)) {
      setError('Usá al menos 8 caracteres, con mayúscula, minúscula y número.')
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

      <form onSubmit={guardarFiscales} className="card mb-6 space-y-5">
        <h3 className="inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
          <Receipt size={18} className="text-primary-300" /> Datos para la factura
        </h3>
        <p className="-mt-2 text-sm text-neutral-secondary">
          Si cargás tu RUC, tus compras se facturan a tu nombre en vez de a consumidor final.
          Dejalo vacío para volver al consumidor final.
        </p>

        {mensajeFiscal && <p className="alerta-ok mb-0 text-sm" role="status">{mensajeFiscal}</p>}
        {errorFiscal && <p className="alerta-error mb-0 text-sm" role="alert">{errorFiscal}</p>}

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="ruc">
            RUC
          </label>
          <input
            id="ruc"
            value={fiscal.ruc}
            onChange={(e) => setFiscal({ ...fiscal, ruc: e.target.value })}
            maxLength={20}
            placeholder="80012345-0"
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="razonSocial">
            Razón social
          </label>
          <input
            id="razonSocial"
            value={fiscal.razonSocial}
            onChange={(e) => setFiscal({ ...fiscal, razonSocial: e.target.value })}
            maxLength={255}
            placeholder="Si lo dejás vacío se usa tu nombre y apellido"
            className="input"
          />
        </div>

        <button type="submit" disabled={guardandoFiscal} className="btn btn-primary w-full">
          {guardandoFiscal ? 'Guardando…' : 'Guardar datos fiscales'}
        </button>
      </form>

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
            Contraseña nueva (mayúscula, minúscula y número)
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
