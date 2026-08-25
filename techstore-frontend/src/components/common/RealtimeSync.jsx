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
    })
    return () => { void client?.deactivate() }
  }, [accessToken, queryClient])

  return null
}
