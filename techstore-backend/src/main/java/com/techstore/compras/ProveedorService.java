package com.techstore.compras;

import com.techstore.comun.BusinessException;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProveedorResponse> listar(Pageable pageable) {
        return PageResponse.from(proveedorRepository.findAll(pageable).map(ProveedorResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> activos() {
        return proveedorRepository.findByActivoTrueOrderByRazonSocialAsc().stream()
                .map(ProveedorResponse::from)
                .toList();
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        if (request.ruc() != null && !request.ruc().isBlank() && proveedorRepository.existsByRuc(request.ruc())) {
            throw new BusinessException("Ya existe un proveedor con el RUC " + request.ruc());
        }
        Proveedor proveedor = new Proveedor();
        aplicar(proveedor, request);
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = buscar(id);
        if (request.ruc() != null && !request.ruc().isBlank()
                && !Objects.equals(request.ruc(), proveedor.getRuc())
                && proveedorRepository.existsByRuc(request.ruc())) {
            throw new BusinessException("Ya existe un proveedor con el RUC " + request.ruc());
        }
        aplicar(proveedor, request);
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse cambiarActivo(Long id, boolean activo) {
        Proveedor proveedor = buscar(id);
        proveedor.setActivo(activo);
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    private Proveedor buscar(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
    }

    private static void aplicar(Proveedor proveedor, ProveedorRequest request) {
        proveedor.setRazonSocial(request.razonSocial().trim());
        proveedor.setNombreFantasia(vacioANull(request.nombreFantasia()));
        proveedor.setEmail(vacioANull(request.email()));
        proveedor.setTelefono(vacioANull(request.telefono()));
        proveedor.setDireccion(vacioANull(request.direccion()));
        proveedor.setCiudad(vacioANull(request.ciudad()));
        proveedor.setPais(request.pais() == null || request.pais().isBlank() ? "Paraguay" : request.pais().trim());
        proveedor.setRuc(vacioANull(request.ruc()));
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
