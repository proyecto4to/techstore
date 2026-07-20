import api from './api'

export const dashboardService = {
  async resumen() {
    const { data } = await api.get('/dashboard')
    return data
  },
}
