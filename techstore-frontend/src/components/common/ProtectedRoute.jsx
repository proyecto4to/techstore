import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

function ProtectedRoute({ children, rol = null }) {
  const { usuario } = useAuthStore()

  if (!usuario) {
    return <Navigate to="/login" replace />
  }
  if (rol && usuario.rol !== rol) {
    return <Navigate to="/" replace />
  }
  return children
}

export default ProtectedRoute
