package com.techstore.comun.web;

import com.techstore.seguridad.PermisoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * Recursos estaticos y los interceptores del dominio.
 *
 * <p>Sirve las fotos de producto subidas por el Admin desde el directorio
 * externo de uploads, y engancha la comprobacion de permisos por rol.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /**
     * Una foto subida lleva un nombre unico: si el Admin la cambia, la nueva
     * tiene otra direccion. Se puede guardar un mes sin volver a preguntar.
     *
     * <p>Sin una cabecera propia, Spring Security manda "no-cache, no-store" en
     * toda respuesta, y la tienda web y la app volvian a bajar cada foto entera
     * en cada visita.
     */
    static final CacheControl FOTOS_SUBIDAS = CacheControl.maxAge(Duration.ofDays(30)).cachePublic();

    /** Las fotos empaquetadas cambian solo con una version nueva del backend. */
    static final CacheControl FOTOS_EMPAQUETADAS = CacheControl.maxAge(Duration.ofDays(7)).cachePublic();

    private final PermisoInterceptor permisoInterceptor;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Solo la API: los recursos estaticos no tienen modulo ni permiso.
        registry.addInterceptor(permisoInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadsDir, "productos").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/productos/**")
                .addResourceLocations(location)
                .setCacheControl(FOTOS_SUBIDAS);
        registry.addResourceHandler("/img/productos/**")
                .addResourceLocations("classpath:/static/img/productos/")
                .setCacheControl(FOTOS_EMPAQUETADAS);
    }
}
