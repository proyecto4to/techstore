package com.techstore.comun.web;

import com.techstore.seguridad.PermisoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Recursos estaticos y los interceptores del dominio.
 *
 * <p>Sirve las fotos de producto subidas por el Admin desde el directorio
 * externo de uploads, y engancha la comprobacion de permisos por rol.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

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
        registry.addResourceHandler("/uploads/productos/**").addResourceLocations(location);
    }
}
