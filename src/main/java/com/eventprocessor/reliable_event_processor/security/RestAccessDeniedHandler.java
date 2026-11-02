package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.dto.ErrorResponse;
import com.eventprocessor.reliable_event_processor.service.OperationalAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;
    private final OperationalAuditService auditService;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, OperationalAuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = (auth != null && auth.getName() != null) ? auth.getName() : "ANONYMOUS";

        log.warn("Access denied for principal '{}' on path '{}'", principal, request.getRequestURI());

        try {
            auditService.recordAuditLog(
                    "ACCESS_DENIED",
                    request.getRequestURI(),
                    "FAILED",
                    "Access Denied: insufficient permissions",
                    principal,
                    "Forbidden access attempt to " + request.getMethod() + " " + request.getRequestURI()
            );
        } catch (Exception ex) {
            log.error("Failed to record access denied audit entry", ex);
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Access Denied: You do not have permission to access this resource",
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

