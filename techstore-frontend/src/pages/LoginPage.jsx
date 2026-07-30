import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)
  const setAuth = useAuthStore((state) => state.setAuth)
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const data = await authService.login(email, password)
      setAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        usuario: data.usuario,
      })
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo iniciar sesión. Verifique sus datos.')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center px-6">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <img src="/isotipo.svg" alt="" width="56" height="56" className="mb-4 inline-block h-14 w-14" />
          <h1 className="text-3xl font-bold gradient-text">TechStore</h1>
          <p className="mt-2 text-neutral-secondary">Sistema de e-commerce profesional</p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-6">
          <h2 className="text-2xl font-bold text-neutral-text">Bienvenido</h2>

          {error && (
            <p className="alerta-error mb-0 text-sm" role="alert">
              {error}
            </p>
          )}

          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="input"
              placeholder="tu@email.com"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="password">
              Contraseña
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="input"
              placeholder="••••••••"
            />
          </div>

          <button type="submit" disabled={cargando} className="btn btn-primary w-full">
            {cargando ? 'Ingresando…' : 'Ingresar'}
          </button>

          <p className="text-center text-sm text-neutral-secondary">
            ¿No tenés cuenta?{' '}
            <Link to="/registro" className="text-primary-300 hover:text-accent-400">
              Registrate
            </Link>
          </p>

          <div className="text-center text-sm text-neutral-secondary">
            <p>Credenciales de prueba:</p>
            <p className="text-primary-300">admin@techstore.py / admin123</p>
          </div>
        </form>
      </div>
    </div>
  )
}

export default LoginPage
