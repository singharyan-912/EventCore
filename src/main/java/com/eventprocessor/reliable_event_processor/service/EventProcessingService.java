package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EventProcessingService {

    private final ReliabilityEngine reliabilityEngine;
    private final InventoryService inventoryService;

    public EventProcessingService(ReliabilityEngine reliabilityEngine,
                                  InventoryService inventoryService) {
        this.reliabilityEngine = reliabilityEngine;
        this.inventoryService = inventoryService;
    }

    public EventResponse processEvent(EventRequest request) {
        // Construct a deterministic canonical payload string to detect modifications
        String canonicalPayload = "eventType=%s|resourceId=%s|quantity=%d"
                .formatted(request.eventType(), request.resourceId(), request.quantity());

        return reliabilityEngine.executeIdempotent(
                request.idempotencyKey(),
                request.eventType(),
                request.resourceId(),
                canonicalPayload,
                EventResponse.class,
                () -> {
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
