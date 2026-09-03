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
    @Mapping(target = "margen", expression = "java(aMonto(producto.getMargen()))")
    ProductoResponse toResponse(Producto producto);

    @Mapping(target = "disponible", expression = "java(producto.getStockDisponible() > 0)")
    @Mapping(target = "maxPorPedido", expression = "java(producto.getMaxPorPedido())")
    ProductoPublicResponse toPublicResponse(Producto producto);

    @Mapping(target = "stockDisponible", expression = "java(producto.getStockDisponible())")
    ProductoOperativoResponse toOperativoResponse(Producto producto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "moneda", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "modelo", ignore = true)
    @Mapping(target = "stockComprometido", ignore = true)
    // Sin stockMinimo el producto no avisa, que es como venían los existentes.
    @Mapping(target = "stockMinimo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "pesoKg", source = "pesoKg", defaultValue = "0")
    // El costo no se carga a mano: sale de la última compra recibida.
    @Mapping(target = "costo", ignore = true)
    @Mapping(target = "costoActualizadoEn", ignore = true)
    // Con defaultValue y no ignorándolo: si el request no lo trae, MapStruct
    // escribiría null encima del valor por defecto de la entidad y el INSERT
    // fallaría contra el NOT NULL de la columna.
    @Mapping(target = "ocultarSinStock", source = "ocultarSinStock", defaultValue = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Producto toEntity(ProductoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "moneda", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "modelo", ignore = true)
    @Mapping(target = "stockComprometido", ignore = true)
    // Sin stockMinimo el producto no avisa, que es como venían los existentes.
    @Mapping(target = "stockMinimo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "pesoKg", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "costo", ignore = true)
    @Mapping(target = "costoActualizadoEn", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "activo", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "ocultarSinStock", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ProductoRequest request, @MappingTarget Producto producto);
}
