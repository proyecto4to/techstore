package com.techstore.notificaciones;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DispositivoMovilService {

    private final DispositivoMovilRepository dispositivoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public DispositivoMovilResponse registrar(String email, RegistroDispositivoRequest request) {
        Usuario usuario = usuario(email);
        String identifier = request.deviceIdentifier().trim();
        DispositivoMovil dispositivo = dispositivoRepository.findByDeviceIdentifier(identifier)
                .orElseGet(DispositivoMovil::new);
        dispositivo.setTenantId(usuario.getTenantId());
        dispositivo.setUsuario(usuario);
        dispositivo.setPlataforma(request.plataforma());
        dispositivo.setPushToken(request.pushToken().trim());
        dispositivo.setDeviceIdentifier(identifier);
        dispositivo.setActivo(true);
        dispositivo.setUltimoUso(LocalDateTime.now());
        return respuesta(dispositivoRepository.save(dispositivo));
    }

    @Transactional(readOnly = true)
    public List<DispositivoMovilResponse> listar(String email) {
        Usuario usuario = usuario(email);
        return dispositivoRepository.findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(usuario.getId()).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional
    public void desactivar(String email, String deviceIdentifier) {
        Usuario usuario = usuario(email);
        DispositivoMovil dispositivo = dispositivoRepository
                .findByDeviceIdentifierAndUsuarioId(deviceIdentifier, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "dispositivos.no.encontrado", deviceIdentifier));
        dispositivo.setActivo(false);
        dispositivo.setUltimoUso(LocalDateTime.now());
        dispositivoRepository.save(dispositivo);
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", email));
    }

    private DispositivoMovilResponse respuesta(DispositivoMovil dispositivo) {
        return new DispositivoMovilResponse(
                dispositivo.getId(), dispositivo.getPlataforma(), dispositivo.getDeviceIdentifier(),
                Boolean.TRUE.equals(dispositivo.getActivo()), dispositivo.getUltimoUso(),
                dispositivo.getCreatedAt(), dispositivo.getUpdatedAt());
    }
}
