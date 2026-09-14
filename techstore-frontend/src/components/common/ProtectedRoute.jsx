import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { usePermisos } from '../../hooks/usePermisos'

/**
 * Deja pasar, o no, a una pantalla.
 *
 * Hay dos filtros y se aplican en este orden:
 *
 * 1. `rol` / `roles`, que es como venía funcionando y sigue igual.
 * 2. `modulo` / `accion`, el permiso configurado en Seguridad, que un
 *    administrador puede cambiar sin tocar código.
 *
 * Mientras los permisos se están cargando no redirige: muestra que está
 * comprobando. Sin eso, recargar la página en una ruta protegida rebotaba al
 * inicio antes de que llegara la respuesta, y parecía un permiso denegado
 * cuando era solo una petición en curso.
 *
 * Nada de esto es la seguridad del sistema: es lo que se muestra. Quien escriba
 * la URL a mano igual va a chocar contra el backend, que decide de verdad.
 */
function ProtectedRoute({ children, rol = null, roles = null, modulo = null, accion = 'VER' }) {
  const { usuario } = useAuthStore()
  const { puede, cargando } = usePermisos()

  if (!usuario) {
    return <Navigate to="/login" replace />
  }
  const permitidos = roles ?? (rol ? [rol] : null)
  if (permitidos && !permitidos.includes(usuario.rol)) {
    return <Navigate to="/" replace />
  }
  if (modulo) {
    if (cargando) {
      return <div className="loading" role="status">Comprobando permisos…</div>
    }
    if (!puede(modulo, accion)) {
      return <Navigate to="/" replace />
    }
  }
  return children
}

export default ProtectedRoute
