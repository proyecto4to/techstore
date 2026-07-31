package com.techstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
// Para la limpieza diaria de los intentos de login ya vencidos.
@EnableScheduling
@ConfigurationPropertiesScan
public class TechStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TechStoreApplication.class, args);
    }
}
