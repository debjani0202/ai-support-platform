
package com.supportai.security;

import java.util.List;

import com.supportai.user.entity.Role;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/*
 * 1. Basically, this file is the security guard before entering the backend.
 *
 * 2. When a request arrives, Spring Security checks whether the request
 * is permitted, whether authentication is required, and whether a
 * supplied JWT is valid.
 *
 * My configuration has two main responsibilities:
 * - Define which requests are public and which require authentication.
 * - Convert the role in a JWT into a Spring Security authority.
 */

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                /*
                 * This API uses bearer tokens rather than session cookies.
                 * CSRF protection is disabled for this stateless API.
                 */
                .csrf(csrf -> csrf.disable())

                // Do not store authentication in an HTTP session.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                /*
                 * Define which requests are public and which require
                 * authentication.
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/health",
                                "/actuator/health")
                        .permitAll()

                        .requestMatchers("/api/tickets/**")
                        .authenticated()

                        .anyRequest()
                        .authenticated())

                /*
                 * Spring Security validates the JWT and uses our converter
                 * to map its role claim to a granted authority.
                 */
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            // Read the role stored in the JWT.
            String role = jwt.getClaimAsString("role");

            /*
             * If the role is missing or blank, the converter returns
             * an empty list of authorities.
             */
            if (role == null || role.isBlank()) {
                return List.of();
            }

            try {
                /*
                 * Validate the role against the Role enum.
                 * Valid values are CUSTOMER, AGENT, and ADMIN.
                 */
                Role validatedRole = Role.valueOf(role);

                return List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + validatedRole.name()));

            } catch (IllegalArgumentException exception) {

                /*
                 * An unknown role must not grant any authority.
                 */
                return List.of();
            }
        });

        return converter;
    }
}
