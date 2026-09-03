import api from './api'

export const authService = {
  async login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    return data
  },

  async me() {
    const { data } = await api.get('/auth/me')
    return data
  },

  async solicitarRecuperacion(email) {
    await api.post('/auth/password/forgot', { email: email.trim() })
  },

  async completarRecuperacion(token, password) {
    await api.post('/auth/password/reset', { token, password })
  },
}
