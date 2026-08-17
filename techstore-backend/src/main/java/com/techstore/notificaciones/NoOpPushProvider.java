package com.techstore.notificaciones;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "push", name = "provider", havingValue = "none", matchIfMissing = true)
public class NoOpPushProvider implements PushProvider {
    @Override
    public PushResult enviar(PushMessage mensaje) {
        return PushResult.omitido("PROVIDER_DISABLED");
    }
}
