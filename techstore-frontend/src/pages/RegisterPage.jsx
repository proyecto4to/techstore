import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserPlus } from 'lucide-react'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'
import CampoTexto from '../components/common/CampoTexto'
import {
  REQUISITOS_PASSWORD,
  errorDeConfirmacion,
  errorDePassword,
  passwordValida,
  passwordsCoinciden,
} from '../utils/password'

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

  const campo = (clave) => (valor) => {
    setError(null)
    setForm((actual) => ({ ...actual, [clave]: valor }))
  }

  // La misma regla que aplica el backend. Antes la etiqueta decía «mínimo 8
  // caracteres» y el `minLength` lo confirmaba, pero el servidor además exige
  // mayúscula, minúscula y número: quien ponía «12345678» pasaba la validación
  // del navegador y recibía un rechazo genérico recién después de enviar.
  const puedeEnviar =
    form.nombre.trim() &&
    form.apellido.trim() &&
    form.email.trim() &&
    passwordValida(form.password) &&
    passwordsCoinciden(form.password, form.confirmar)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
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
          : err.response?.data?.detail ?? 'No se pudo crear la cuenta. Intentá de nuevo.',
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
            <CampoTexto
              etiqueta="Nombre"
              requerido
              maxLength={100}
              autoComplete="given-name"
              valor={form.nombre}
              onChange={campo('nombre')}
              validar={(v) => (v.trim() ? null : 'Escribí tu nombre.')}
            />
            <CampoTexto
              etiqueta="Apellido"
              requerido
              maxLength={100}
              autoComplete="family-name"
              valor={form.apellido}
              onChange={campo('apellido')}
              validar={(v) => (v.trim() ? null : 'Escribí tu apellido.')}
            />
          </div>

          <CampoTexto
            etiqueta="Correo electrónico"
            type="email"
            requerido
            autoComplete="email"
            valor={form.email}
            onChange={campo('email')}
            validar={(v) =>
              !v.trim()
                ? 'Escribí tu correo.'
                : v.includes('@') && v.length > 4
                  ? null
                  : 'Escribí un correo válido, como nombre@correo.com'
            }
          />

          <CampoTexto
            etiqueta="Contraseña"
            type="password"
            requerido
            autoComplete="new-password"
            ayuda={REQUISITOS_PASSWORD}
            valor={form.password}
            onChange={campo('password')}
            validar={errorDePassword}
          />

          <CampoTexto
            etiqueta="Repetí la contraseña"
            type="password"
            requerido
            autoComplete="new-password"
            valor={form.confirmar}
            onChange={campo('confirmar')}
            validar={(v) => errorDeConfirmacion(form.password, v)}
          />

          <button
            type="submit"
            disabled={cargando || !puedeEnviar}
            className="btn btn-primary w-full disabled:cursor-not-allowed"
          >
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
