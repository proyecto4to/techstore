import api from './api'

/**
 * Todo lo que la Web sabe sobre permisos sale de acá, y acá sale del backend.
 *
 * No hay ninguna regla de autorización escrita en el front: lo que se muestra
 * se decide con `misPermisos()`, y lo que de verdad se puede hacer lo decide el
 * servidor en cada llamada. Si las dos cosas se contradicen, gana el servidor y
 * el usuario ve un 403; nunca al revés.
 */
export const seguridadService = {
  /** Los permisos de quien tiene la sesión abierta. */
  async misPermisos() {
    const { data } = await api.get('/auth/mis-permisos')
    return data
  },

  /** Módulos, acciones y pantallas que existen para configurar. */
  async catalogo() {
    const { data } = await api.get('/seguridad/catalogo')
    return data
  },

  async roles() {
    const { data } = await api.get('/seguridad/roles')
    return data
  },

  async rolesDetallados() {
    const { data } = await api.get('/seguridad/roles/detalle')
    return data
  },

  async crearRol({ nombre, descripcion }) {
    const { data } = await api.post('/seguridad/roles', { nombre, descripcion })
    return data
  },

  async editarRol(rol, { descripcion, activo }) {
    const { data } = await api.put(`/seguridad/roles/${encodeURIComponent(rol)}`, {
      descripcion,
      activo,
    })
    return data
  },

  async usuariosDelRol(rol) {
    const { data } = await api.get(`/seguridad/roles/${encodeURIComponent(rol)}/usuarios`)
    return data
  },

  async matriz(rol) {
    const { data } = await api.get(`/seguridad/roles/${encodeURIComponent(rol)}/permisos`)
    return data
  },

  async guardarPermisoRol(rol, { modulo, accion, permitido }) {
    const { data } = await api.put(`/seguridad/roles/${encodeURIComponent(rol)}/permisos`, {
      modulo,
      accion,
      permitido,
    })
    return data
  },

  async guardarPantalla(rol, { pantalla, permitido }) {
    const { data } = await api.put(`/seguridad/roles/${encodeURIComponent(rol)}/pantallas`, {
      pantalla,
      permitido,
    })
    return data
  },

  /** Permisos efectivos de una persona, con el origen de cada uno. */
  async permisosDeUsuario(id) {
    const { data } = await api.get(`/seguridad/usuarios/${id}/permisos`)
    return data
  },

  async guardarPermisoUsuario(id, { modulo, accion, permitido, motivo }) {
    const { data } = await api.put(`/seguridad/usuarios/${id}/permisos`, {
      modulo,
      accion,
      permitido,
      motivo: motivo || null,
    })
    return data
  },

  /** Le saca la excepción: vuelve a valer lo que diga el rol. */
  async quitarPermisoUsuario(id, modulo, accion) {
    const { data } = await api.delete(`/seguridad/usuarios/${id}/permisos/${modulo}/${accion}`)
    return data
  },
}
