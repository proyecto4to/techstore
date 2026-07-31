package com.techstore.service;

import com.techstore.dto.request.CrearUsuarioRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.UsuarioAdminResponse;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoCorreo;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.RolRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public PageResponse<UsuarioAdminResponse> listar(Pageable pageable) {
        return PageResponse.from(usuarioRepository.findAll(pageable).map(UsuarioAdminResponse::from));
    }

    @Transactional
    public UsuarioAdminResponse crear(CrearUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Ya existe una cuenta con el email " + request.email());
        }
        Rol rol = rolRepository.findByNombre(request.rol())
                .orElseThrow(() -> new BusinessException("El rol '%s' no existe".formatted(request.rol())));

        Usuario usuario = new Usuario();
        usuario.setEmail(request.email());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setRol(rol);
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.save(usuario);

        auditoriaService.registrar("Usuario", guardado.getId(), AuditoriaService.Accion.Crear,
                "Alta de %s con rol %s".formatted(guardado.getEmail(), rol.getNombre()),
                AuditoriaService.Cambios.nuevo().valor("rol", rol.getNombre()));

        return UsuarioAdminResponse.from(guardado);
    }

    @Transactional
    public UsuarioAdminResponse cambiarActivo(Long id, boolean activo, String emailSolicitante) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (!activo && usuario.getEmail().equals(emailSolicitante)) {
            throw new BusinessException("No podés desactivar tu propia cuenta");
        }
        boolean activoAnterior = Boolean.TRUE.equals(usuario.getActivo());
        usuario.setActivo(activo);
        UsuarioAdminResponse respuesta = UsuarioAdminResponse.from(usuarioRepository.save(usuario));

        auditoriaService.registrar("Usuario", id, AuditoriaService.Accion.Editar,
                "%s la cuenta %s".formatted(activo ? "Reactivó" : "Desactivó", usuario.getEmail()),
                AuditoriaService.Cambios.nuevo().de("activo", activoAnterior, activo));

        if (!activo) {
            correoService.enviar(
                    TipoCorreo.CUENTA_DESACTIVADA,
                    usuario.getEmail(),
                    usuario.getNombre() + " " + usuario.getApellido(),
                    "Tu cuenta de TechStore fue desactivada",
                    emailTemplateService.construir(
                            TipoCorreo.CUENTA_DESACTIVADA,
                            "Cuenta desactivada",
                            usuario.getNombre(),
                            "Te informamos que tu cuenta fue desactivada. No podrás iniciar sesión hasta "
                                    + "que un administrador la reactive.",
                            null, null, null),
                    usuario.getEmail());
        }
        return respuesta;
    }

    @Transactional
    public UsuarioAdminResponse resetearPassword(Long id, String passwordNueva) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        UsuarioAdminResponse respuesta = UsuarioAdminResponse.from(usuarioRepository.save(usuario));

        // Nunca la contraseña, ni la vieja ni la nueva: alcanza con dejar
        // constancia de que alguien la cambió y de quién fue.
        auditoriaService.registrar("Usuario", id, AuditoriaService.Accion.Editar,
                "Restableció la contraseña de " + usuario.getEmail());

        correoService.enviar(
                TipoCorreo.RESETEO_PASSWORD,
                usuario.getEmail(),
                usuario.getNombre() + " " + usuario.getApellido(),
                "Un administrador restableció tu contraseña",
                emailTemplateService.construir(
                        TipoCorreo.RESETEO_PASSWORD,
                        "Contraseña restablecida",
                        usuario.getNombre(),
                        "Un administrador restableció tu contraseña de TechStore.",
                        "Iniciá sesión con la nueva contraseña y cambiala cuanto antes desde Mi cuenta.",
                        "INICIAR SESIÓN",
                        emailTemplateService.url("/login")),
                usuario.getEmail());
        return respuesta;
    }
}
