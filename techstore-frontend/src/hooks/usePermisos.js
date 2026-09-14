import { useCallback, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { seguridadService } from '../services/seguridadService'

/**
 * Qué puede hacer el usuario de esta sesión.
 *
 * Los permisos se piden una vez al backend y se reutilizan en toda la
 * aplicación: el menú, las rutas y los botones preguntan lo mismo y responden
 * lo mismo. No hay una segunda tabla de reglas escrita acá.
 *
 * Mientras no se sabe, `puede()` devuelve false. Es a propósito: ante la duda
 * no se ofrece una acción que después va a rebotar. Quien necesite distinguir
 * «todavía no sé» de «no» tiene `cargando`, y con eso muestra un cartel de
 * espera en vez de una pantalla vacía.
 *
 * Esconder un botón no protege nada: es comodidad, no seguridad. La
 * autorización de verdad la aplica el backend en cada pedido.
 */
export function usePermisos() {
  const usuario = useAuthStore((estado) => estado.usuario)
  const accessToken = useAuthStore((estado) => estado.accessToken)
  const conSesion = Boolean(usuario && accessToken)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['mis-permisos', usuario?.id ?? null],
    queryFn: () => seguridadService.misPermisos(),
    enabled: conSesion,
    // Los permisos cambian cuando un administrador los cambia, no solo. Cinco
    // minutos evitan pedirlos en cada navegación sin dejarlos viejos por horas.
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })

  const permitidos = useMemo(() => new Set(data?.permitidos ?? []), [data])
  const pantallas = useMemo(() => new Set(data?.pantallas ?? []), [data])

  const puede = useCallback(
    (modulo, accion) => {
      if (!conSesion || !modulo || !accion) return false
      return permitidos.has(`${modulo}.${accion}`.toLowerCase())
    },
    [conSesion, permitidos],
  )

  const vePantalla = useCallback(
    (pantalla) => (pantalla ? pantallas.has(pantalla) : false),
    [pantallas],
  )

  return {
    puede,
    vePantalla,
    permisos: data ?? null,
    rol: data?.rol ?? usuario?.rol ?? null,
    cargando: conSesion && isLoading,
    error: isError,
    recargar: refetch,
  }
}

export default usePermisos
