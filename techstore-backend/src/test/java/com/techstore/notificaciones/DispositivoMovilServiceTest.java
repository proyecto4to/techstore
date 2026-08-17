package com.techstore.notificaciones;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispositivoMovilServiceTest {

    @Mock private DispositivoMovilRepository dispositivoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private DispositivoMovilService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new DispositivoMovilService(dispositivoRepository, usuarioRepository);
        usuario = usuario(8L, 3L, "cliente@techstore.py");
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    @Test
    void registraUnaInstalacionSinExponerElPushToken() {
        when(dispositivoRepository.findByDeviceIdentifier("android-install-123456"))
                .thenReturn(Optional.empty());
        when(dispositivoRepository.save(any(DispositivoMovil.class))).thenAnswer(invocation -> {
            DispositivoMovil dispositivo = invocation.getArgument(0);
            dispositivo.setId(21L);
            return dispositivo;
        });

        DispositivoMovilResponse response = service.registrar(usuario.getEmail(), request("token-nuevo"));

        assertEquals(21L, response.id());
        assertEquals("android-install-123456", response.deviceIdentifier());
        assertTrue(response.activo());
        verify(dispositivoRepository).save(any(DispositivoMovil.class));
    }

    @Test
    void rotarTokenTransfiereLaInstalacionAlUsuarioAutenticado() {
        Usuario anterior = usuario(4L, 3L, "anterior@techstore.py");
        DispositivoMovil existente = dispositivo(anterior, "token-anterior");
        existente.setActivo(false);
        when(dispositivoRepository.findByDeviceIdentifier("android-install-123456"))
                .thenReturn(Optional.of(existente));
        when(dispositivoRepository.save(existente)).thenReturn(existente);

        DispositivoMovilResponse response = service.registrar(usuario.getEmail(), request("token-rotado"));

        assertEquals(usuario, existente.getUsuario());
        assertEquals("ExponentPushToken[token-rotado]", existente.getPushToken());
        assertTrue(response.activo());
    }

    @Test
    void logoutSoloDesactivaUnaInstalacionPropia() {
        when(dispositivoRepository.findByDeviceIdentifierAndUsuarioId("android-install-123456", 8L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.desactivar(usuario.getEmail(), "android-install-123456"));
    }

    private RegistroDispositivoRequest request(String token) {
        return new RegistroDispositivoRequest(
                PlataformaMovil.ANDROID, "ExponentPushToken[" + token + "]", "android-install-123456");
    }

    private DispositivoMovil dispositivo(Usuario owner, String token) {
        DispositivoMovil dispositivo = new DispositivoMovil();
        dispositivo.setId(21L);
        dispositivo.setTenantId(owner.getTenantId());
        dispositivo.setUsuario(owner);
        dispositivo.setPlataforma(PlataformaMovil.ANDROID);
        dispositivo.setPushToken("ExponentPushToken[" + token + "]");
        dispositivo.setDeviceIdentifier("android-install-123456");
        return dispositivo;
    }

    private Usuario usuario(Long id, Long tenantId, String email) {
        Usuario value = new Usuario();
        value.setId(id);
        value.setTenantId(tenantId);
        value.setEmail(email);
        return value;
    }
}
