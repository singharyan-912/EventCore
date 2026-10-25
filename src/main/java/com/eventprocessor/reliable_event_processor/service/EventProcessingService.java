package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EventProcessingService {

    private final ReliabilityEngine reliabilityEngine;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final TransientFailureSimulator failureSimulator;

    public EventProcessingService(ReliabilityEngine reliabilityEngine,
                                  InventoryService inventoryService,
                                  ObjectMapper objectMapper,
                                  TransientFailureSimulator failureSimulator) {
        this.reliabilityEngine = reliabilityEngine;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.failureSimulator = failureSimulator;
    }

    public EventResponse processEvent(EventRequest request) {
        String canonicalPayload = "eventType=%s|resourceId=%s|quantity=%d"
                .formatted(request.eventType(), request.resourceId(), request.quantity());

        String rawPayloadJson;
        try {
            rawPayloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize event request", e);
        }

        return reliabilityEngine.executeIdempotent(
                request.idempotencyKey(),
                request.eventType(),
                request.resourceId(),
                canonicalPayload,
                rawPayloadJson,
                EventResponse.class,
                () -> {
                    // Check if isolated test failure simulation is active
                    failureSimulator.maybeSimulateTransientFailure(request.idempotencyKey(), request.resourceId(), 2);

                    // Business action: deduct inventory with pessimistic row lock
                    Inventory updated = inventoryService.deductInventory(request.resourceId(), request.quantity());
                    return new EventResponse(
                            request.idempotencyKey(),
                            request.eventType(),
                            request.resourceId(),
                            request.quantity(),
                            updated.getAvailableQuantity(),
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
