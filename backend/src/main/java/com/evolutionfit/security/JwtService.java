package com.evolutionfit.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import com.evolutionfit.config.AppProperties;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    public String generateToken(AuthUserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRoleName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Devuelve el nombre de usuario del token si la firma es válida y no ha expirado.
     */
    public Optional<String> extractUsername(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
