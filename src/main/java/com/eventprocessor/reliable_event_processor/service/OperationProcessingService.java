package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.OperationRequest;
import com.eventprocessor.reliable_event_processor.dto.OperationResponse;
import com.eventprocessor.reliable_event_processor.entity.ExternalIntegration;
import com.eventprocessor.reliable_event_processor.exception.OperationValidationException;
import com.eventprocessor.reliable_event_processor.exception.UnauthorizedException;
import com.eventprocessor.reliable_event_processor.exception.UnsupportedOperationTypeException;
import com.eventprocessor.reliable_event_processor.handler.BusinessOperationHandler;
import com.eventprocessor.reliable_event_processor.handler.OperationHandlerRegistry;
import com.eventprocessor.reliable_event_processor.integration.HttpIntegrationAdapter;
import com.eventprocessor.reliable_event_processor.repository.ExternalIntegrationRepository;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class OperationProcessingService {

    private final ReliabilityEngine reliabilityEngine;
    private final OperationHandlerRegistry handlerRegistry;
    private final TenantService tenantService;
    private final ExternalIntegrationRepository integrationRepository;
    private final HttpIntegrationAdapter httpIntegrationAdapter;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public OperationProcessingService(ReliabilityEngine reliabilityEngine,
                                      OperationHandlerRegistry handlerRegistry,
                                      TenantService tenantService,
                                      ExternalIntegrationRepository integrationRepository,
                                      HttpIntegrationAdapter httpIntegrationAdapter,
                                      WebhookService webhookService,
                                      ObjectMapper objectMapper) {
        this.reliabilityEngine = reliabilityEngine;
        this.handlerRegistry = handlerRegistry;
        this.tenantService = tenantService;
        this.integrationRepository = integrationRepository;
        this.httpIntegrationAdapter = httpIntegrationAdapter;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    public OperationResponse processOperation(OperationRequest request) {
        String effectiveTenant = resolveTenant(request.tenantId());

        Optional<BusinessOperationHandler<?>> optionalHandler = handlerRegistry.getHandler(request.operationType());
        Optional<ExternalIntegration> matchedIntegration = integrationRepository.findByTenantIdAndStatus(effectiveTenant, "ACTIVE")
                .stream()
                .filter(i -> i.supportsOperation(request.operationType()))
                .findFirst();

        if (optionalHandler.isEmpty() && matchedIntegration.isEmpty()) {
            throw new UnsupportedOperationTypeException("Unsupported operation type: " + request.operationType());
        }

        String canonicalPayload;
        if (optionalHandler.isPresent()) {
            BusinessOperationHandler<?> handler = optionalHandler.get();
            handler.validate(effectiveTenant, request.resourceId(), request.payload());
            canonicalPayload = handler.canonicalizePayload(request.resourceId(), request.payload());
        } else {
            if (request.payload() == null || request.payload().isNull()) {
                throw new OperationValidationException("Payload must not be null for operation: " + request.operationType());
            }
            canonicalPayload = "operationType=" + request.operationType().toUpperCase() +
                    "|resourceId=" + (request.resourceId() != null ? request.resourceId().trim() : "") +
                    "|payload=" + request.payload().toString();
        }

        String rawPayloadJson;
        try {
            rawPayloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize operation request", e);
        }

        OperationResponse response = reliabilityEngine.executeIdempotent(
                effectiveTenant,
                request.idempotencyKey(),
                request.operationType().toUpperCase(),
                request.resourceId(),
                canonicalPayload,
                rawPayloadJson,
                OperationResponse.class,
                () -> {
                    JsonNode resultNode;
                    if (matchedIntegration.isPresent()) {
                        ExternalIntegration integration = matchedIntegration.get();
                        HttpIntegrationAdapter.ExternalExecutionResult extResult = httpIntegrationAdapter.execute(
                                integration,
                                request.idempotencyKey(),
                                request.operationType(),
                                request.resourceId(),
                                request.payload()
                        );
                        resultNode = extResult.body();
                    } else {
                        BusinessOperationHandler<?> handler = optionalHandler.get();
                        Object result = handler.execute(effectiveTenant, request.idempotencyKey(), request.resourceId(), request.payload());
                        resultNode = handler.serializeResultUntyped(result);
                    }
                    return new OperationResponse(
                            request.idempotencyKey(),
                            request.operationType().toUpperCase(),
                            request.resourceId(),
                            effectiveTenant,
                            "PROCESSED",
                            false,
                            resultNode,
                            Instant.now()
                    );
                },
                cached -> new OperationResponse(
                        cached.idempotencyKey(),
                        cached.operationType(),
                        cached.resourceId(),
                        cached.tenantId(),
                        cached.status(),
                        true, // Idempotent replay flag
                        cached.result(),
                        cached.processedAt(),
                        cached.retryCount(),
                        cached.nextRetryAt(),
                        cached.message()
                )
        );

        // Dispatch webhook notifications on non-replayed status transitions
        if (!response.idempotentReplay()) {
            if ("PROCESSED".equals(response.status())) {
                webhookService.dispatchWebhookNotification(effectiveTenant, "OPERATION_SUCCEEDED", response.idempotencyKey(), rawPayloadJson);
            } else if ("RECONCILIATION_REQUIRED".equals(response.status())) {
                webhookService.dispatchWebhookNotification(effectiveTenant, "OPERATION_REQUIRES_RECONCILIATION", response.idempotencyKey(), rawPayloadJson);
            } else if ("RETRY_PENDING".equals(response.status())) {
                webhookService.dispatchWebhookNotification(effectiveTenant, "OPERATION_RETRYING", response.idempotencyKey(), rawPayloadJson);
            }
        }

        return response;
    }

    public String resolveTenant(String requestTenantId) {
        String effectiveTenant;
        if (TenantContext.isPlatformAdminBypass()) {
            effectiveTenant = (requestTenantId != null && !requestTenantId.isBlank())
                    ? requestTenantId.trim()
                    : TenantContext.getTenantId();
        } else if (TenantContext.getExplicitTenantId() != null) {
            effectiveTenant = TenantContext.getTenantId();
            if (requestTenantId != null && !requestTenantId.isBlank()
                    && !requestTenantId.trim().equalsIgnoreCase(effectiveTenant)) {
                throw new UnauthorizedException(
                        "Client-supplied tenantId '%s' conflicts with authenticated/verified server-side tenant '%s'"
                                .formatted(requestTenantId, effectiveTenant));
            }
        } else {
            if (requestTenantId != null && !requestTenantId.isBlank()) {
                String candidate = requestTenantId.trim();
                if (!tenantService.isTenantActive(candidate)) {
                    throw new IllegalArgumentException("Invalid or inactive tenant: " + candidate);
                }
                effectiveTenant = candidate;
            } else {
                effectiveTenant = TenantContext.getTenantId();
            }
        }
        return effectiveTenant;
    }
}
