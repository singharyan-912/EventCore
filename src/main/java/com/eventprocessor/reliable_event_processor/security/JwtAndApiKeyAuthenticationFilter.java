package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.config.AdminSecurityProperties;
import com.eventprocessor.reliable_event_processor.entity.ApiKey;
import com.eventprocessor.reliable_event_processor.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JwtAndApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_TENANT_ATTR = "AUTHENTICATED_TENANT_ID";

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminSecurityProperties adminSecurityProperties;
    private final ObjectProvider<ApiKeyService> apiKeyServiceProvider;
    private final ObjectMapper objectMapper;

    public JwtAndApiKeyAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                            AdminSecurityProperties adminSecurityProperties,
                                            ObjectProvider<ApiKeyService> apiKeyServiceProvider,
                                            ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminSecurityProperties = adminSecurityProperties;
        this.apiKeyServiceProvider = apiKeyServiceProvider;
        this.objectMapper = objectMapper;
    }

    public JwtAndApiKeyAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                            AdminSecurityProperties adminSecurityProperties) {
        this(jwtTokenProvider, adminSecurityProperties, null, new ObjectMapper());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String adminKeyHeader = request.getHeader("X-Admin-API-Key");
        String businessApiKeyHeader = request.getHeader("X-API-Key");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

                if (username != null && role != null) {
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } else if (adminKeyHeader != null && !adminKeyHeader.isBlank()) {
            if (adminKeyHeader.equals(adminSecurityProperties.getApiKey())) {
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("admin-api-key", null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } else if (businessApiKeyHeader != null && !businessApiKeyHeader.isBlank()) {
            ApiKeyService apiKeyService = apiKeyServiceProvider != null ? apiKeyServiceProvider.getIfAvailable() : null;
            if (apiKeyService != null) {
                Optional<ApiKey> apiKeyOpt = apiKeyService.authenticateApiKey(businessApiKeyHeader.trim());
                if (apiKeyOpt.isPresent()) {
                    ApiKey apiKey = apiKeyOpt.get();
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_OPERATOR")
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken("apikey:" + apiKey.getKeyPrefix(), null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    request.setAttribute(AUTHENTICATED_TENANT_ATTR, apiKey.getTenantId());
                } else {
                    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid, revoked, or expired API key");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
