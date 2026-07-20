import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

function ProtectedRoute({ children, rol = null, roles = null }) {
  const { usuario } = useAuthStore()

  if (!usuario) {
    return <Navigate to="/login" replace />
  }
  const permitidos = roles ?? (rol ? [rol] : null)
  if (permitidos && !permitidos.includes(usuario.rol)) {
    return <Navigate to="/" replace />
  }
  return children
}

export default ProtectedRoute
