import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
    <div className="flex min-h-[70vh] items-center justify-center px-4">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-lg border-2 border-gold bg-white p-8 shadow-lg"
      >
        <h2 className="mb-6 text-center text-2xl font-bold text-navy">Iniciar sesión</h2>

        {error && (
          <p className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600" role="alert">
            {error}
          </p>
        )}

        <label className="mb-1 block text-sm font-medium text-navy" htmlFor="email">
          Email
        </label>
        <input
          id="email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mb-4 w-full rounded border border-gray-300 p-2 focus:border-gold focus:outline-none"
        />

        <label className="mb-1 block text-sm font-medium text-navy" htmlFor="password">
          Contraseña
        </label>
        <input
          id="password"
          type="password"
          required
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-6 w-full rounded border border-gray-300 p-2 focus:border-gold focus:outline-none"
        />

        <button
          type="submit"
          disabled={cargando}
          className="w-full rounded bg-navy py-2 font-semibold text-gold hover:bg-navy-light disabled:opacity-50"
        >
          {cargando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </div>
  )
}

export default LoginPage
