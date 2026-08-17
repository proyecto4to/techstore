package com.techstore.notificaciones;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "push")
public record PushProperties(String provider, URI expoUrl, String accessToken) { }
