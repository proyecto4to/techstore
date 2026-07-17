import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserPlus } from 'lucide-react'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'

function RegisterPage() {
  const [form, setForm] = useState({
    nombre: '',
    apellido: '',
    email: '',
    password: '',
    confirmar: '',
  })
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)
  const setAuth = useAuthStore((state) => state.setAuth)
  const navigate = useNavigate()

  const campo = (clave) => (e) => setForm({ ...form, [clave]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    if (form.password !== form.confirmar) {
      setError('Las contraseñas no coinciden')
      return
    }
    setCargando(true)
    try {
      const { data } = await api.post('/auth/registro', {
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      setAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        usuario: data.usuario,
      })
      navigate('/')
    } catch (err) {
      const errores = err.response?.data?.errores
      setError(
        errores
          ? Object.values(errores).join(' · ')
          : err.response?.data?.detail ?? 'No se pudo crear la cuenta. Intente de nuevo.',
      )
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center px-6 py-8">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-secondary-400 text-white">
            <UserPlus size={26} />
          </div>
          <h1 className="text-3xl font-bold gradient-text">Crear cuenta</h1>
          <p className="mt-2 text-neutral-secondary">Registrate para comprar en TechStore</p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-5">
          {error && (
            <p className="alerta-error mb-0 text-sm" role="alert">
              {error}
            </p>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="nombre">
                Nombre
              </label>
              <input id="nombre" required maxLength={100} value={form.nombre} onChange={campo('nombre')} className="input" />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="apellido">
                Apellido
              </label>
              <input id="apellido" required maxLength={100} value={form.apellido} onChange={campo('apellido')} className="input" />
            </div>
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              autoComplete="email"
              value={form.email}
              onChange={campo('email')}
              className="input"
              placeholder="tu@email.com"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="password">
              Contraseña (mínimo 8 caracteres)
            </label>
            <input
              id="password"
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={form.password}
              onChange={campo('password')}
              className="input"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="confirmar">
              Confirmar contraseña
            </label>
            <input
              id="confirmar"
              type="password"
              required
              autoComplete="new-password"
              value={form.confirmar}
              onChange={campo('confirmar')}
              className="input"
            />
          </div>

          <button type="submit" disabled={cargando} className="btn btn-primary w-full">
            {cargando ? 'Creando cuenta…' : 'Crear cuenta'}
          </button>

          <p className="text-center text-sm text-neutral-secondary">
            ¿Ya tenés cuenta?{' '}
            <Link to="/login" className="text-primary-300 hover:text-accent-400">
              Iniciá sesión
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}

export default RegisterPage
