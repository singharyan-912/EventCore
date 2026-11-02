package com.eventprocessor.reliable_event_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "event-processor.security.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA signing secret for JWT tokens.
     * Must be at least 32 bytes (256 bits).
     * Must be provided via environment variable (JWT_SECRET) in production.
     */
    private String secret;

    /**
     * Token expiration duration in milliseconds.
     * Defaults to 3600000 ms (1 hour).
     */
    private long expirationMs = 3600000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}

