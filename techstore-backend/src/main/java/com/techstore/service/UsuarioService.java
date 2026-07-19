package com.techstore.service;

import com.techstore.dto.request.CrearUsuarioRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.UsuarioAdminResponse;
import com.techstore.entity.Rol;
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
        return UsuarioAdminResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioAdminResponse cambiarActivo(Long id, boolean activo, String emailSolicitante) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (!activo && usuario.getEmail().equals(emailSolicitante)) {
            throw new BusinessException("No podés desactivar tu propia cuenta");
        }
        usuario.setActivo(activo);
        return UsuarioAdminResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioAdminResponse resetearPassword(Long id, String passwordNueva) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        return UsuarioAdminResponse.from(usuarioRepository.save(usuario));
    }
}
