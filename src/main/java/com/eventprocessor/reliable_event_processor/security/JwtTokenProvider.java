package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties, Environment environment) {
        this.jwtProperties = jwtProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        String configuredSecret = jwtProperties.getSecret();
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (configuredSecret != null && configuredSecret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES) {
            this.signingKey = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        } else if (isProduction) {
            throw new IllegalStateException(
                    "Production configuration failure: JWT secret is required and must be at least 32 characters. " +
                    "Set the JWT_SECRET environment variable."
            );
        } else {
            // Generate a secure ephemeral signing key for non-production environments
            this.signingKey = Jwts.SIG.HS256.key().build();
            log.warn("JWT secret is not configured or too short. Generated an ephemeral in-memory signing key for the current runtime session.");
        }
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token.trim());
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
        }
        return false;
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
        return claims.get("role", String.class);
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationMs();
    }
}

