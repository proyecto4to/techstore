package com.techstore.catalogo;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final String PREFIJO_SUBIDA = "/uploads/productos/";
    private static final Map<String, String> EXTENSIONES_PERMITIDAS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final ProductoRepository productoRepository;
    private final MarcaRepository marcaRepository;
    private final ModeloRepository modeloRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Transactional(readOnly = true)
    public PageResponse<ProductoPublicResponse> listarPublico(
            String search, Long marcaId, Boolean disponible, Pageable pageable) {
        return PageResponse.from(
                productoRepository.findAll(ProductoSpecs.catalogoPublico(search, marcaId, disponible), pageable)
                        .map(productoMapper::toPublicResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> listarAdmin(
            String search, Long marcaId, boolean incluirInactivos, Pageable pageable) {
        return PageResponse.from(
                productoRepository.findAll(ProductoSpecs.conFiltros(search, marcaId, incluirInactivos), pageable)
                        .map(productoMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductoPublicResponse obtenerPublico(Long id) {
        return productoMapper.toPublicResponse(buscarProductoPublico(id));
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtenerAdmin(Long id) {
        return productoMapper.toResponse(buscarProducto(id));
    }

    /** Los productos que llegaron a su punto de reposición, del más urgente al menos. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> reposicion() {
        return productoRepository.necesitanReposicion(Producto.UMBRAL_POR_DEFECTO).stream()
                .map(productoMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        Producto producto = productoMapper.toEntity(request);
        if (producto.getActivo() == null) {
            producto.setActivo(true);
        }
        aplicarRelaciones(producto, request);
        Producto guardado = productoRepository.save(producto);

        auditoriaService.registrar("Producto", guardado.getId(), AuditoriaService.Accion.Crear,
                "Alta de " + guardado.getNombre(),
                AuditoriaService.Cambios.nuevo()
                        .valor("precio", guardado.getPrecio())
                        .valor("stock", guardado.getStockReal())
                        .valor("stockMinimo", guardado.getStockMinimo()));

        return productoMapper.toResponse(guardado);
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarProducto(id);

        // Los valores anteriores se copian ANTES de aplicar el request: después
        // el objeto ya viene modificado y no habría con qué comparar.
        Dinero precioAnterior = producto.getPrecio();
        Integer stockAnterior = producto.getStockReal();
        Integer minimoAnterior = producto.getStockMinimo();
        String nombreAnterior = producto.getNombre();

        productoMapper.updateEntity(request, producto);
        aplicarRelaciones(producto, request);
        Producto guardado = productoRepository.save(producto);

        auditoriaService.registrar("Producto", guardado.getId(), AuditoriaService.Accion.Editar,
                "Edición de " + guardado.getNombre(),
                AuditoriaService.Cambios.nuevo()
                        .de("nombre", nombreAnterior, guardado.getNombre())
                        .de("precio", precioAnterior, guardado.getPrecio())
                        .de("stock", stockAnterior, guardado.getStockReal())
                        .de("stockMinimo", minimoAnterior, guardado.getStockMinimo()));

        return productoMapper.toResponse(guardado);
    }

    /**
     * Baja lógica: el producto deja de aparecer en el catálogo pero se conserva
     * para mantener la integridad de pedidos y facturas históricas.
     */
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarProducto(id);
        producto.setActivo(false);
        productoRepository.save(producto);
        auditoriaService.registrar("Producto", id, AuditoriaService.Accion.Eliminar,
                "Baja de " + producto.getNombre());
    }

    /** Reemplaza la foto del producto por un archivo subido desde el equipo del Admin. */
    @Transactional
    public ProductoResponse actualizarImagen(Long id, MultipartFile archivo) {
        Producto producto = buscarProducto(id);
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("catalogo.imagen.vacia");
        }
        String extension = EXTENSIONES_PERMITIDAS.get(archivo.getContentType());
        if (extension == null) {
            throw new BusinessException("catalogo.imagen.formato.invalido");
        }

        try {
            byte[] contenido = archivo.getBytes();
            if (!firmaCoincide(archivo.getContentType(), contenido)) {
                throw new BusinessException("catalogo.imagen.formato.invalido");
            }
            Path carpeta = Paths.get(uploadsDir, "productos").toAbsolutePath().normalize();
            Files.createDirectories(carpeta);

            String nombreArchivo = "producto-" + id + "-" + UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);

            borrarImagenSubidaAnterior(producto.getImagenUrl(), carpeta);
            producto.setImagenUrl(PREFIJO_SUBIDA + nombreArchivo);
            return productoMapper.toResponse(productoRepository.save(producto));
        } catch (IOException ex) {
            throw new BusinessException("catalogo.imagen.no.guardada", ex.getMessage());
        }
    }

    /** Impide aceptar HTML/scripts renombrados confiando únicamente en el MIME del request. */
    private boolean firmaCoincide(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/gif" -> bytes.length >= 6
                    && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    /** Borra el archivo anterior solo si fue subido por este mismo mecanismo (no una URL externa). */
    private void borrarImagenSubidaAnterior(String imagenUrlAnterior, Path carpeta) {
        if (imagenUrlAnterior == null || !imagenUrlAnterior.startsWith(PREFIJO_SUBIDA)) {
            return;
        }
        Path candidato = carpeta.resolve(imagenUrlAnterior.substring(PREFIJO_SUBIDA.length())).normalize();
        if (!candidato.startsWith(carpeta)) {
            return; // fuera de la carpeta de subidas: no tocar
        }
        try {
            Files.deleteIfExists(candidato);
        } catch (IOException ignored) {
            // No pasa nada si no se pudo borrar el archivo viejo; queda huerfano.
        }
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("catalogo.producto.no.encontrado", id));
    }

    Producto buscarProductoPublico(Long id) {
        Producto producto = buscarProducto(id);
        if (!producto.visibleEnTienda()) {
            throw new ResourceNotFoundException("catalogo.producto.no.encontrado", id);
        }
        return producto;
    }

    private void aplicarRelaciones(Producto producto, ProductoRequest request) {
        Marca marca = null;
        if (request.marcaId() != null) {
            marca = marcaRepository.findById(request.marcaId())
                    .orElseThrow(() -> new ResourceNotFoundException("catalogo.marca.no.encontrada", request.marcaId()));
        }
        producto.setMarca(marca);

        Modelo modelo = null;
        if (request.modeloId() != null) {
            modelo = modeloRepository.findById(request.modeloId())
                    .orElseThrow(() -> new ResourceNotFoundException("catalogo.modelo.no.encontrado", request.modeloId()));
            if (marca != null && !modelo.getMarca().getId().equals(marca.getId())) {
                throw new BusinessException("catalogo.modelo.ajeno.a.marca");
            }
        }
        producto.setModelo(modelo);
    }
}
