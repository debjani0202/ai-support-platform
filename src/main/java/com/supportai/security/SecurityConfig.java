package com.supportai.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/*
1.basically this file security guard before entering backend
2.When a request arrives, Spring Security checks whether the request is permitted, whether authentication is required, and whether a supplied JWT is valid.
my configuration has two main responsibilities:
Define which requests are public and which require authentication.
Convert the role in a JWT into a Spring Security authority.
 */

@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                SessionCreationPolicy.STATELESS))
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
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter())));

                return http.build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(jwt -> {

                        String role = jwt.getClaimAsString("role");

                        if (role == null || role.isBlank())  //f the role is missing or blank, the converter returns an empty list of authorities.
                        {
                                return List.of();
                        }

                        return List.of(
                                        new SimpleGrantedAuthority("ROLE_" + role));
                });

                return converter;
        }
}
