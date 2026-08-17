package com.techstore.direcciones;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.comun.TelefonoParaguayo;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DireccionEntregaService {

    private final DireccionEntregaRepository direccionRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<DireccionEntregaResponse> listar(String email) {
        Usuario usuario = usuario(email);
        return direccionRepository.findByUsuarioIdOrderByPrincipalDescUpdatedAtDesc(usuario.getId()).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public DireccionEntregaResponse obtener(String email, Long id) {
        Usuario usuario = usuario(email);
        return respuesta(direccionPropia(usuario, id));
    }

    @Transactional
    public DireccionEntregaResponse crear(String email, DireccionEntregaRequest request) {
        Usuario usuario = usuario(email);
        DireccionEntrega direccion = new DireccionEntrega();
        direccion.setTenantId(usuario.getTenantId());
        direccion.setUsuario(usuario);
        boolean primera = !direccionRepository.existsByUsuarioId(usuario.getId());
        aplicar(direccion, request, primera);
        DireccionEntrega guardada = direccionRepository.save(direccion);
        if (Boolean.TRUE.equals(guardada.getPrincipal())) {
            direccionRepository.quitarPrincipalExcepto(usuario.getId(), guardada.getId());
        }
        return respuesta(guardada);
    }

    @Transactional
    public DireccionEntregaResponse actualizar(String email, Long id, DireccionEntregaRequest request) {
        Usuario usuario = usuario(email);
        DireccionEntrega direccion = direccionPropia(usuario, id);
        aplicar(direccion, request, false);
        if (Boolean.TRUE.equals(direccion.getPrincipal())) {
            direccionRepository.quitarPrincipalExcepto(usuario.getId(), direccion.getId());
        }
        return respuesta(direccionRepository.save(direccion));
    }

    @Transactional
    public void eliminar(String email, Long id) {
        Usuario usuario = usuario(email);
        DireccionEntrega direccion = direccionPropia(usuario, id);
        boolean eraPrincipal = Boolean.TRUE.equals(direccion.getPrincipal());
        direccionRepository.delete(direccion);
        direccionRepository.flush();
        if (eraPrincipal) {
            direccionRepository.findByUsuarioIdOrderByPrincipalDescUpdatedAtDesc(usuario.getId()).stream()
                    .findFirst()
                    .ifPresent(siguiente -> {
                        siguiente.setPrincipal(true);
                        direccionRepository.save(siguiente);
                    });
        }
    }

    public DireccionEntrega buscarPropia(Usuario usuario, Long id) {
        return direccionPropia(usuario, id);
    }

    private void aplicar(DireccionEntrega direccion, DireccionEntregaRequest request, boolean forzarPrincipal) {
        String telefono = TelefonoParaguayo.normalizar(request.telefono());
        if (telefono == null) {
            throw new BusinessException("direcciones.telefono.invalido");
        }
        direccion.setNombreDestinatario(limpiar(request.nombreDestinatario()));
        direccion.setTelefono(telefono);
        direccion.setDepartamento(limpiar(request.departamento()));
        direccion.setCiudad(limpiar(request.ciudad()));
        direccion.setBarrio(vacioANull(request.barrio()));
        direccion.setDireccionLinea1(limpiar(request.direccionLinea1()));
        direccion.setDireccionLinea2(vacioANull(request.direccionLinea2()));
        direccion.setNumeroCasa(vacioANull(request.numeroCasa()));
        direccion.setReferencia(vacioANull(request.referencia()));
        direccion.setCodigoPostal(vacioANull(request.codigoPostal()));
        direccion.setLatitud(request.latitud());
        direccion.setLongitud(request.longitud());
        direccion.setPrincipal(forzarPrincipal || Boolean.TRUE.equals(request.principal()));
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", email));
    }

    private DireccionEntrega direccionPropia(Usuario usuario, Long id) {
        return direccionRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("direcciones.no.encontrada", id));
    }

    private DireccionEntregaResponse respuesta(DireccionEntrega direccion) {
        return new DireccionEntregaResponse(
                direccion.getId(), direccion.getNombreDestinatario(), direccion.getTelefono(),
                direccion.getDepartamento(), direccion.getCiudad(), direccion.getBarrio(),
                direccion.getDireccionLinea1(), direccion.getDireccionLinea2(), direccion.getNumeroCasa(),
                direccion.getReferencia(), direccion.getCodigoPostal(), direccion.getLatitud(),
                direccion.getLongitud(), direccion.getPrincipal(), direccion.getCreatedAt(), direccion.getUpdatedAt());
    }

    private static String limpiar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private static String vacioANull(String valor) {
        String limpio = limpiar(valor);
        return limpio == null || limpio.isEmpty() ? null : limpio;
    }
}

