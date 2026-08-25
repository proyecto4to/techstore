package com.techstore.comun.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/** Sirve las fotos de producto subidas por el Admin desde el directorio externo de uploads. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadsDir, "productos").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/productos/**").addResourceLocations(location);
    }
}
