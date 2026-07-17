import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api/v1'

const api = axios.create({ baseURL: API_BASE_URL })

api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// Ante un 401, intenta renovar el par de tokens una sola vez y reintenta la petición.
// Si el refresh también falla, cierra la sesión.
let refreshPromise = null

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const { refreshToken, setAuth, logout } = useAuthStore.getState()

    if (error.response?.status === 401 && refreshToken && original && !original._retry) {
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
