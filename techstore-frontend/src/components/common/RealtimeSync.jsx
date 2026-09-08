import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { connectDomainEvents, queryKeysForEvent } from '../../services/syncSocket'
import { useAuthStore } from '../../store/authStore'

export default function RealtimeSync() {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore((state) => state.accessToken)

  useEffect(() => {
    if (!accessToken) return undefined
    const client = connectDomainEvents({
      onEvent: (event) => {
        for (const queryKey of queryKeysForEvent(event)) {
          void queryClient.invalidateQueries({ queryKey })
        }
      },
      // Sirve tanto para un aviso nuevo como para uno leído desde otro
      // dispositivo: en los dos casos el número de la campana cambió.
      onNotification: () => {
        void queryClient.invalidateQueries({ queryKey: ['notificaciones-no-leidas'] })
        void queryClient.invalidateQueries({ queryKey: ['notificaciones'] })
      },
    })
    return () => { void client?.deactivate() }
  }, [accessToken, queryClient])

  return null
}
