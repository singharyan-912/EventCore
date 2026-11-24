package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.exception.UnauthorizedException;
import com.eventprocessor.reliable_event_processor.handler.InventoryDeductHandler;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EventProcessingService {

    private final ReliabilityEngine reliabilityEngine;
    private final InventoryDeductHandler inventoryDeductHandler;
    private final ObjectMapper objectMapper;
    private final TenantService tenantService;

    public EventProcessingService(ReliabilityEngine reliabilityEngine,
                                  InventoryDeductHandler inventoryDeductHandler,
                                  ObjectMapper objectMapper,
                                  TenantService tenantService) {
        this.reliabilityEngine = reliabilityEngine;
        this.inventoryDeductHandler = inventoryDeductHandler;
        this.objectMapper = objectMapper;
        this.tenantService = tenantService;
    }

    public EventResponse processEvent(EventRequest request) {
        String effectiveTenant;
        if (TenantContext.isPlatformAdminBypass()) {
            effectiveTenant = (request.tenantId() != null && !request.tenantId().isBlank())
                    ? request.tenantId().trim()
                    : TenantContext.getTenantId();
        } else if (TenantContext.getExplicitTenantId() != null) {
            effectiveTenant = TenantContext.getTenantId();
            if (request.tenantId() != null && !request.tenantId().isBlank()
                    && !request.tenantId().trim().equalsIgnoreCase(effectiveTenant)) {
                throw new UnauthorizedException(
                        "Client-supplied tenantId '%s' conflicts with authenticated/verified server-side tenant '%s'"
                                .formatted(request.tenantId(), effectiveTenant));
            }
        } else {
            if (request.tenantId() != null && !request.tenantId().isBlank()) {
                String candidate = request.tenantId().trim();
                if (!tenantService.isTenantActive(candidate)) {
                    throw new IllegalArgumentException("Invalid or inactive tenant: " + candidate);
                }
                effectiveTenant = candidate;
            } else {
                effectiveTenant = TenantContext.getTenantId();
            }
        }

        String canonicalPayload = "eventType=%s|resourceId=%s|quantity=%d"
                .formatted(request.eventType(), request.resourceId(), request.quantity());

        String rawPayloadJson;
        try {
            rawPayloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize event request", e);
        }

        return reliabilityEngine.executeIdempotent(
                effectiveTenant,
                request.idempotencyKey(),
                request.eventType(),
                request.resourceId(),
                canonicalPayload,
                rawPayloadJson,
                EventResponse.class,
                () -> {
                    // Business action: executed via InventoryDeductHandler inside transaction
                    InventoryDeductHandler.InventoryDeductResult result = inventoryDeductHandler.execute(
                            effectiveTenant, request.idempotencyKey(), request.resourceId(), objectMapper.valueToTree(request));
                    return new EventResponse(
                            request.idempotencyKey(),
                            request.eventType(),
                            request.resourceId(),
                            result.deductedQuantity(),
                            result.remainingQuantity(),
                            "PROCESSED",
                            false,
                            Instant.now()
                    );
                },
                cached -> new EventResponse(
                        cached.idempotencyKey(),
                        cached.eventType(),
                        cached.resourceId(),
                        cached.deductedQuantity(),
                        cached.remainingQuantity(),
                        cached.status(),
                        true, // Idempotent replay flag
                        cached.processedAt()
                )
        );
    }
}
