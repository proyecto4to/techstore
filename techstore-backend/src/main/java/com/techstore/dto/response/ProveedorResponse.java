package com.techstore.dto.response;

import com.techstore.entity.Proveedor;

public record ProveedorResponse(
        Long id,
        String razonSocial,
        String nombreFantasia,
        String email,
        String telefono,
        String direccion,
        String ciudad,
        String pais,
        String ruc,
        Boolean activo) {

    public static ProveedorResponse from(Proveedor proveedor) {
        return new ProveedorResponse(
                proveedor.getId(),
                proveedor.getRazonSocial(),
                proveedor.getNombreFantasia(),
                proveedor.getEmail(),
                proveedor.getTelefono(),
                proveedor.getDireccion(),
                proveedor.getCiudad(),
                proveedor.getPais(),
                proveedor.getRuc(),
                proveedor.getActivo());
    }
}
