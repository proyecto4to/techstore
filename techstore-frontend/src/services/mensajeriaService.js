import api from './api'

export const mensajeriaService = {
  listar: async ({ page = 0, size = 50 } = {}) =>
    (await api.get('/conversaciones', { params: { page, size, sort: 'ultimoMensajeEn,desc' } })).data,
  obtener: async (id) => (await api.get(`/conversaciones/${id}`)).data,
  historial: async (id, { page = 0, size = 100 } = {}) =>
    (await api.get(`/conversaciones/${id}/mensajes`, { params: { page, size } })).data,
  enviar: async (id, contenido, clientMessageId = crypto.randomUUID()) =>
    (await api.post(`/conversaciones/${id}/mensajes`, { clientMessageId, contenido })).data,
  marcarLeido: async (id) => (await api.put(`/conversaciones/${id}/leido`)).data,
  cerrar: async (id) => (await api.put(`/conversaciones/${id}/cerrar`)).data,
}
