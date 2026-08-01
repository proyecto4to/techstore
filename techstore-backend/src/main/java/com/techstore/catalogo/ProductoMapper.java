package com.techstore.catalogo;

import com.techstore.comun.DineroMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ProductoMapper extends DineroMapper {

    @Mapping(target = "stockDisponible", expression = "java(producto.getStockDisponible())")
    @Mapping(target = "necesitaReposicion", expression = "java(producto.necesitaReposicion())")
    ProductoResponse toResponse(Producto producto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "modelo", ignore = true)
    @Mapping(target = "stockComprometido", ignore = true)
    // Sin stockMinimo el producto no avisa, que es como venían los existentes.
    @Mapping(target = "stockMinimo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Producto toEntity(ProductoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "modelo", ignore = true)
    @Mapping(target = "stockComprometido", ignore = true)
    // Sin stockMinimo el producto no avisa, que es como venían los existentes.
    @Mapping(target = "stockMinimo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "activo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ProductoRequest request, @MappingTarget Producto producto);
}
