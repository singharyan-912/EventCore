package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.ApiKeyResponse;
import com.eventprocessor.reliable_event_processor.dto.CreateApiKeyRequest;
import com.eventprocessor.reliable_event_processor.dto.CreateApiKeyResponse;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import com.eventprocessor.reliable_event_processor.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing API Keys scoped to the authenticated tenant.
 * Provides creation, listing, rotation, and revocation endpoints.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<CreateApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication) {
        String tenantId = TenantContext.getTenantId();
        String principal = authentication != null ? authentication.getName() : "UNKNOWN";
        CreateApiKeyResponse response = apiKeyService.createApiKey(tenantId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        String tenantId = TenantContext.getTenantId();
        List<ApiKeyResponse> response = apiKeyService.listApiKeys(tenantId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiKeyResponse> revokeApiKey(
            @PathVariable Long id,
            Authentication authentication) {
        String tenantId = TenantContext.getTenantId();
        String principal = authentication != null ? authentication.getName() : "UNKNOWN";
        ApiKeyResponse response = apiKeyService.revokeApiKey(tenantId, id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<CreateApiKeyResponse> rotateApiKey(
            @PathVariable Long id,
            Authentication authentication) {
        String tenantId = TenantContext.getTenantId();
        String principal = authentication != null ? authentication.getName() : "UNKNOWN";
        CreateApiKeyResponse response = apiKeyService.rotateApiKey(tenantId, id, principal);
        return ResponseEntity.ok(response);
    }
}
