import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api/v1'

const api = axios.create({ baseURL: API_BASE_URL })

// Endpoints públicos de autenticación: nunca deben llevar un token viejo
// ni participar del ciclo de renovación (un 401 ahí es "credenciales malas").
const esEndpointDeAuth = (url = '') =>
  ['/auth/login', '/auth/registro', '/auth/refresh'].some((ruta) => url.includes(ruta))

api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState()
  if (accessToken && !esEndpointDeAuth(config.url)) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  // Desde dónde se hizo la operación, para la auditoría. El backend lo deduce
  // igual del User-Agent; declararlo evita que un navegador raro quede como
  // "Api" en un registro que después alguien tiene que leer.
  config.headers['X-Plataforma'] = 'Web'
  return config
})

// Ante un 401 en la API, intenta renovar el par de tokens una sola vez y
// reintenta la petición. Si el refresh también falla, cierra la sesión.
let refreshPromise = null

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const { refreshToken, setAuth, logout } = useAuthStore.getState()

    if (
      error.response?.status === 401 &&
      refreshToken &&
      original &&
      !original._retry &&
      !esEndpointDeAuth(original.url)
    ) {
      original._retry = true
      try {
        refreshPromise =
          refreshPromise ?? axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken })
        const { data } = await refreshPromise
        refreshPromise = null
        setAuth({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
          usuario: data.usuario,
        })
        original.headers.Authorization = `Bearer ${data.accessToken}`
        return api(original)
      } catch (refreshError) {
        refreshPromise = null
        logout()
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  },
)

export default api
