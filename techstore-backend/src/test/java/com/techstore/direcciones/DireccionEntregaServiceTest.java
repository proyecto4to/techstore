package com.techstore.direcciones;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DireccionEntregaServiceTest {

    @Mock
    private DireccionEntregaRepository direccionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private DireccionEntregaService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new DireccionEntregaService(direccionRepository, usuarioRepository);
        usuario = new Usuario();
        usuario.setId(8L);
        usuario.setTenantId(3L);
        usuario.setEmail("cliente@empresa.py");
        when(usuarioRepository.findByEmail("cliente@empresa.py")).thenReturn(Optional.of(usuario));
    }

    @Test
    void primeraDireccionEsPrincipalYNormalizaTelefono() {
        when(direccionRepository.existsByUsuarioId(8L)).thenReturn(false);
        when(direccionRepository.save(any(DireccionEntrega.class))).thenAnswer(inv -> {
            DireccionEntrega direccion = inv.getArgument(0);
            direccion.setId(21L);
            return direccion;
        });

        DireccionEntregaResponse response = service.crear("cliente@empresa.py", request(false));

        assertEquals(21L, response.id());
        assertEquals("+595981123456", response.telefono());
        assertTrue(response.principal());
        verify(direccionRepository).quitarPrincipalExcepto(8L, 21L);
    }

    @Test
    void siempreConsultaDireccionesPorElUsuarioAutenticado() {
        DireccionEntrega direccion = new DireccionEntrega();
        direccion.setId(1L);
        direccion.setUsuario(usuario);
        direccion.setNombreDestinatario("Cliente");
        direccion.setTelefono("+595981123456");
        direccion.setDepartamento("Central");
        direccion.setCiudad("Luque");
        direccion.setDireccionLinea1("Ruta General Aquino");
        direccion.setPrincipal(true);
        when(direccionRepository.findByUsuarioIdOrderByPrincipalDescUpdatedAtDesc(8L))
                .thenReturn(List.of(direccion));

        List<DireccionEntregaResponse> response = service.listar("cliente@empresa.py");

        assertEquals(1, response.size());
        verify(direccionRepository).findByUsuarioIdOrderByPrincipalDescUpdatedAtDesc(8L);
    }

    @Test
    void noPermiteObtenerUnaDireccionAjena() {
        when(direccionRepository.findByIdAndUsuarioId(99L, 8L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.obtener("cliente@empresa.py", 99L));
    }

    private DireccionEntregaRequest request(boolean principal) {
        return new DireccionEntregaRequest(
                "Casa", "Cliente Uno", "0981 123 456", "Central", "Luque", "Centro",
                "Av. Humaitá", null, null, "Portón negro", null,
                new BigDecimal("-25.2637"), new BigDecimal("-57.5759"), principal);
    }
}
