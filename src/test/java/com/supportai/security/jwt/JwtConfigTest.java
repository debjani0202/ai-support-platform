package com.supportai.security.jwt;

import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    private static final String ISSUER = "ai-support-platform";

    private JwtEncoder encoder;
    private JwtConfig jwtConfig;
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();

        // Use a dedicated test secret, never the application's production secret.
        byte[] secretBytes = "01234567890123456789012345678901"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        encoder = jwtConfig.jwtEncoder(secretKey);
        decoder = jwtConfig.jwtDecoder(secretKey);
    }

    @Test
    void acceptsValidToken() {
        Instant now = Instant.now();

        String token = createToken(
                ISSUER,
                "CUSTOMER",
                now,
                now.plusSeconds(300)
        );

        assertDoesNotThrow(() -> decoder.decode(token));
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();

        String token = createToken(
                ISSUER,
                "CUSTOMER",
                now.minusSeconds(120),
                now.minusSeconds(60)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        Instant now = Instant.now();

        String token = createToken(
                "unexpected-issuer",
                "CUSTOMER",
                now,
                now.plusSeconds(300)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    @Test
    void rejectsTokenWithoutRole() {
        Instant now = Instant.now();

        String token = createToken(
                ISSUER,
                null,
                now,
                now.plusSeconds(300)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    @Test
    void rejectsTokenWithUnsupportedRole() {
        Instant now = Instant.now();

        String token = createToken(
                ISSUER,
                "SUPERUSER",
                now,
                now.plusSeconds(300)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    private String createToken(
            String issuer,
            String role,
            Instant issuedAt,
            Instant expiration
    ) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiration)
                .claim("email", "jwt-test@example.com");

        if (role != null) {
            claims.claim("role", role);
        }

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims.build())
        ).getTokenValue();
    }
}