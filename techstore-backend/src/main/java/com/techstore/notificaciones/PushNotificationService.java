package com.techstore.notificaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DispositivoMovilRepository dispositivoRepository;
    private final PushProvider pushProvider;

    @Transactional
    public PushDispatchSummary notificarUsuario(
            Long usuarioId, String titulo, String cuerpo, String deepLink, Map<String, Object> datos) {
        int dispositivos = 0;
        int aceptados = 0;
        int omitidos = 0;
        int fallidos = 0;
        Set<String> tokensProcesados = new HashSet<>();
        for (DispositivoMovil dispositivo
                : dispositivoRepository.findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(usuarioId)) {
            if (!tokensProcesados.add(dispositivo.getPushToken())) continue;
            dispositivos++;
            PushResult resultado;
            try {
                resultado = pushProvider.enviar(new PushMessage(
                        dispositivo.getPushToken(), titulo, cuerpo, deepLink, Map.copyOf(datos)));
            } catch (RuntimeException exception) {
                resultado = PushResult.error("PROVIDER_UNAVAILABLE", false);
            }
            if (resultado.aceptado()) {
                aceptados++;
            } else if ("PROVIDER_DISABLED".equals(resultado.codigo())) {
                omitidos++;
            } else {
                fallidos++;
            }
            if (resultado.permanente()) {
                dispositivo.setActivo(false);
                dispositivoRepository.save(dispositivo);
            }
        }
        return new PushDispatchSummary(dispositivos, aceptados, omitidos, fallidos);
    }
}
