package com.stampx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final CorsConfig corsConfig;

    public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter, CorsConfig corsConfig) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.corsConfig = corsConfig;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    // Public auth endpoints
                    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                    // Public shop info and pass download
                    .requestMatchers("/api/shops/**", "/api/pass/download/**").permitAll()
                    // Apple Wallet web service — authentication handled per-call or skipped for simplicity
                    .requestMatchers("/v1/**").permitAll()
                    // Admin and stamp — auth enforced by dedicated filters/controllers
                    .requestMatchers("/api/admin/**", "/api/stamp/**").permitAll()
                    // Actuator health
                    .requestMatchers("/actuator/health").permitAll()
                    // Everything else requires an authenticated session
                    .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Expose the default SecurityContextRepository so controllers can call saveContext()
     * explicitly after setting authentication manually (required in Spring Boot 3.2).
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        );
    }
}
