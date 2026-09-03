package com.techstore.usuarios;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/registro",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/app/version",
                                // El APK se descarga sin sesión: una actualización
                                // obligatoria debe poder aplicarse antes de iniciarla.
                                "/api/v1/app/download",
                                "/ws",
                                "/ws/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api/v1/api-docs/**",
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        // El catálogo es público: es la vidriera del e-commerce.
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/marcas/**")
                        .permitAll()
                        // Un visitante puede armar carrito; validar no reserva ni modifica stock.
                        .requestMatchers(HttpMethod.POST, "/api/v1/carrito/validar")
                        .permitAll()
                        // Fotos del catálogo, tanto empaquetadas como subidas por Admin:
                        // deben verse en la tienda y en la app móvil sin iniciar sesión.
                        .requestMatchers(HttpMethod.GET, "/img/productos/**", "/uploads/productos/**")
                        .permitAll()
                        // El resto de actuator (metrics, info) expone datos internos:
                        // cualquier usuario autenticado no debería verlos, solo Admin.
                        .requestMatchers("/actuator/**")
                        .hasRole("Admin")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
