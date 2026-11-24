package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.ConnectionTestResponse;
import com.eventprocessor.reliable_event_processor.dto.CreateIntegrationRequest;
import com.eventprocessor.reliable_event_processor.dto.IntegrationResponse;
import com.eventprocessor.reliable_event_processor.dto.UpdateIntegrationRequest;
import com.eventprocessor.reliable_event_processor.entity.ExternalIntegration;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.integration.HttpIntegrationAdapter;
import com.eventprocessor.reliable_event_processor.repository.ExternalIntegrationRepository;
import com.eventprocessor.reliable_event_processor.security.DestinationValidationService;
import com.eventprocessor.reliable_event_processor.security.IntegrationSecretCryptoService;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final ExternalIntegrationRepository integrationRepository;
    private final IntegrationSecretCryptoService cryptoService;
    private final DestinationValidationService destinationValidationService;
    private final HttpIntegrationAdapter httpIntegrationAdapter;
    private final OperationalAuditService auditService;

    public IntegrationService(ExternalIntegrationRepository integrationRepository,
                              IntegrationSecretCryptoService cryptoService,
                              DestinationValidationService destinationValidationService,
                              HttpIntegrationAdapter httpIntegrationAdapter,
                              OperationalAuditService auditService) {
        this.integrationRepository = integrationRepository;
        this.cryptoService = cryptoService;
        this.destinationValidationService = destinationValidationService;
        this.httpIntegrationAdapter = httpIntegrationAdapter;
        this.auditService = auditService;
    }

    @Transactional
    public IntegrationResponse createIntegration(CreateIntegrationRequest request) {
        String tenantId = TenantContext.getTenantId();

        // Validate destination URL against SSRF policy
        destinationValidationService.validateDestinationUrl(request.baseUrl());

        String encryptedSecret = null;
        String prefixHint = null;
        if (request.secret() != null && !request.secret().isBlank()) {
            encryptedSecret = cryptoService.encrypt(request.secret().trim());
            prefixHint = cryptoService.computePrefixHint(request.secret().trim());
        }

        ExternalIntegration integration = new ExternalIntegration(
                tenantId,
                request.name().trim(),
                request.integrationType() != null ? request.integrationType().trim() : "HTTP_REST",
                request.baseUrl().trim(),
                request.supportedOperationTypes().trim().toUpperCase(),
                request.authType() != null ? request.authType().trim().toUpperCase() : "NONE",
                request.authHeaderName() != null ? request.authHeaderName().trim() : null,
                encryptedSecret,
                prefixHint,
                request.timeoutMs() != null ? request.timeoutMs() : 5000
        );

        ExternalIntegration saved = integrationRepository.save(integration);
        log.info("Created integration [id={}, tenant={}, name={}]", saved.getId(), tenantId, saved.getName());

        auditService.recordAuditLog(
                tenantId,
                "INTEGRATION_CREATE",
                "integration-" + saved.getId(),
                "SUCCESS",
                null,
                "OPERATOR",
                "Created integration: " + saved.getName()
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IntegrationResponse> listIntegrations() {
        String tenantId = TenantContext.getTenantId();
        return integrationRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationResponse getIntegration(Long id) {
        String tenantId = TenantContext.getTenantId();
        ExternalIntegration integration = integrationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found: " + id));
        return toResponse(integration);
    }

    @Transactional
    public IntegrationResponse updateIntegration(Long id, UpdateIntegrationRequest request) {
        String tenantId = TenantContext.getTenantId();
        ExternalIntegration integration = integrationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found: " + id));

        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            destinationValidationService.validateDestinationUrl(request.baseUrl());
            integration.setBaseUrl(request.baseUrl().trim());
        }

        if (request.name() != null && !request.name().isBlank()) {
            integration.setName(request.name().trim());
        }

        if (request.supportedOperationTypes() != null && !request.supportedOperationTypes().isBlank()) {
            integration.setSupportedOperationTypes(request.supportedOperationTypes().trim().toUpperCase());
        }

        if (request.authType() != null && !request.authType().isBlank()) {
            integration.setAuthType(request.authType().trim().toUpperCase());
        }

        if (request.authHeaderName() != null) {
            integration.setAuthHeaderName(request.authHeaderName().trim());
        }

        if (request.timeoutMs() != null && request.timeoutMs() > 0) {
            integration.setTimeoutMs(request.timeoutMs());
        }

        if (request.status() != null && !request.status().isBlank()) {
            integration.setStatus(request.status().trim().toUpperCase());
        }

        if (request.secret() != null && !request.secret().isBlank()) {
            integration.setEncryptedSecret(cryptoService.encrypt(request.secret().trim()));
            integration.setSecretPrefix(cryptoService.computePrefixHint(request.secret().trim()));
        }

        integration.setUpdatedAt(OffsetDateTime.now());
        ExternalIntegration updated = integrationRepository.save(integration);
        log.info("Updated integration [id={}, tenant={}]", updated.getId(), tenantId);

        auditService.recordAuditLog(
                tenantId,
                "INTEGRATION_UPDATE",
                "integration-" + updated.getId(),
                "SUCCESS",
                null,
                "OPERATOR",
                "Updated integration configuration"
        );

        return toResponse(updated);
    }

    @Transactional
    public void deleteIntegration(Long id) {
        String tenantId = TenantContext.getTenantId();
        ExternalIntegration integration = integrationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found: " + id));

        integrationRepository.delete(integration);
        log.info("Deleted integration [id={}, tenant={}]", id, tenantId);

        auditService.recordAuditLog(
                tenantId,
                "INTEGRATION_DELETE",
                "integration-" + id,
                "SUCCESS",
                null,
                "OPERATOR",
                "Deleted integration"
        );
    }

    @Transactional
    public ConnectionTestResponse testConnection(Long id) {
        String tenantId = TenantContext.getTenantId();
        ExternalIntegration integration = integrationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found: " + id));

        boolean ok = httpIntegrationAdapter.testConnection(integration);
        OffsetDateTime now = OffsetDateTime.now();

        integration.setLastConnectionAt(now);
        integration.setLastConnectionStatus(ok ? "CONNECTED" : "FAILED");
        integration.setLastConnectionError(ok ? null : "Target endpoint unresponsive or unreachable");
        integrationRepository.save(integration);

        return new ConnectionTestResponse(
                id,
                ok,
                ok ? "Connection test successful" : "Connection test failed: target unreachable or returned 5xx",
                now
        );
    }

    public IntegrationResponse toResponse(ExternalIntegration entity) {
        return new IntegrationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getIntegrationType(),
                entity.getBaseUrl(),
                entity.getStatus(),
                entity.getSupportedOperationTypes(),
                entity.getAuthType(),
                entity.getAuthHeaderName(),
                entity.getSecretPrefix(),
                entity.getTimeoutMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastConnectionAt(),
                entity.getLastConnectionStatus(),
                entity.getLastConnectionError()
        );
    }
}
