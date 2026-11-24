package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import com.eventprocessor.reliable_event_processor.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantService tenantService,
                               UserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (tenantService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantHeader = request.getHeader(TENANT_HEADER);
            if (tenantHeader != null) {
                tenantHeader = tenantHeader.trim();
            }

            String apiKeyTenant = (String) request.getAttribute(JwtAndApiKeyAuthenticationFilter.AUTHENTICATED_TENANT_ATTR);
            if (apiKeyTenant != null && !apiKeyTenant.isBlank()) {
                if (tenantHeader != null && !tenantHeader.isBlank() && !tenantHeader.equalsIgnoreCase(apiKeyTenant)) {
                    sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: API key belongs to tenant " + apiKeyTenant + ", not " + tenantHeader);
                    return;
                }
                TenantContext.setTenantId(apiKeyTenant);
                filterChain.doFilter(request, response);
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                String username = auth.getName();
                Optional<AppUser> userOpt = userRepository.findByUsername(username);

                if (isAdmin) {
                    if (tenantHeader != null && !tenantHeader.isBlank()) {
                        if (!tenantService.isTenantActive(tenantHeader)) {
                            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid or inactive tenant: " + tenantHeader);
                            return;
                        }
                        TenantContext.setTenantId(tenantHeader);
                    } else {
                        // Admin without explicit tenant header operates with platform bypass
                        TenantContext.setPlatformAdminBypass(true);
                        TenantContext.setTenantId(TenantService.DEFAULT_TENANT_ID);
                    }
                } else if (userOpt.isPresent()) {
                    AppUser user = userOpt.get();
                    String userTenant = user.getTenantId();

                    if (tenantHeader != null && !tenantHeader.isBlank() && !tenantHeader.equalsIgnoreCase(userTenant)) {
                        sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: User does not belong to tenant " + tenantHeader);
                        return;
                    }

                    TenantContext.setTenantId(userTenant);
                } else {
                    if (tenantHeader != null && !tenantHeader.isBlank()) {
                        TenantContext.setTenantId(tenantHeader);
                    } else {
                        TenantContext.setTenantId(TenantService.DEFAULT_TENANT_ID);
                    }
                }
            } else {
                // Unauthenticated (e.g. event ingestion or public endpoints)
                if (tenantHeader != null && !tenantHeader.isBlank()) {
                    if (!tenantService.isTenantActive(tenantHeader)) {
                        sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid or inactive tenant: " + tenantHeader);
                        return;
                    }
                    TenantContext.setTenantId(tenantHeader);
                } else {
                    TenantContext.setTenantId(TenantService.DEFAULT_TENANT_ID);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
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
