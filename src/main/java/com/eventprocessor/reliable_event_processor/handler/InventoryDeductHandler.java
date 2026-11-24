package com.eventprocessor.reliable_event_processor.handler;

import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.exception.InsufficientInventoryException;
import com.eventprocessor.reliable_event_processor.exception.OperationValidationException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.service.InventoryService;
import com.eventprocessor.reliable_event_processor.service.TransientFailureSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class InventoryDeductHandler implements BusinessOperationHandler<InventoryDeductHandler.InventoryDeductResult> {

    public static final String OPERATION_TYPE = "INVENTORY_DEDUCT";

    public record InventoryDeductResult(int deductedQuantity, int remainingQuantity) {}

    private final InventoryService inventoryService;
    private final TransientFailureSimulator failureSimulator;
    private final ObjectMapper objectMapper;

    public InventoryDeductHandler(InventoryService inventoryService,
                                  TransientFailureSimulator failureSimulator,
                                  ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.failureSimulator = failureSimulator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getOperationType() {
        return OPERATION_TYPE;
    }

    @Override
    public void validate(String tenantId, String resourceId, JsonNode payload) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new OperationValidationException("resourceId must not be blank for INVENTORY_DEDUCT");
        }
        int quantity = extractQuantity(payload);
        if (quantity <= 0) {
            throw new OperationValidationException("quantity must be a positive integer, got: " + quantity);
        }
    }

    @Override
    public InventoryDeductResult execute(String tenantId, String idempotencyKey, String resourceId, JsonNode payload) {
        int quantity = extractQuantity(payload);

        // Transient failure simulation hook
        failureSimulator.maybeSimulateTransientFailure(idempotencyKey, resourceId, 2);

        // Deduct inventory atomically with pessimistic write lock for the given tenant
        Inventory updated = inventoryService.deductInventory(tenantId, resourceId, quantity);
        return new InventoryDeductResult(quantity, updated.getAvailableQuantity());
    }

    @Override
    public String canonicalizePayload(String resourceId, JsonNode payload) {
        int quantity = extractQuantity(payload);
        // Matches exact canonical fingerprint representation used in Phase A and B
        return "eventType=INVENTORY_DEDUCT|resourceId=%s|quantity=%d"
                .formatted(resourceId != null ? resourceId.trim() : "", quantity);
    }

    @Override
    public boolean isRetryable(Exception ex) {
        if (ex instanceof InsufficientInventoryException) {
            return false;
        }
        if (ex instanceof ResourceNotFoundException) {
            return false;
        }
        if (ex instanceof OperationValidationException) {
            return false;
        }
        return !(ex instanceof IllegalArgumentException);
    }

    @Override
    public JsonNode serializeResult(InventoryDeductResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("deductedQuantity", result.deductedQuantity());
        node.put("remainingQuantity", result.remainingQuantity());
        return node;
    }

    public int extractQuantity(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new OperationValidationException("Payload must not be null for INVENTORY_DEDUCT");
        }
        if (payload.isNumber()) {
            return payload.asInt();
        }
        if (payload.has("quantity") && payload.get("quantity").isNumber()) {
            return payload.get("quantity").asInt();
        }
        if (payload.has("deductedQuantity") && payload.get("deductedQuantity").isNumber()) {
            return payload.get("deductedQuantity").asInt();
        }
        if (payload.has("payload") && payload.get("payload").isObject()) {
            JsonNode inner = payload.get("payload");
            if (inner.has("quantity") && inner.get("quantity").isNumber()) {
                return inner.get("quantity").asInt();
            }
        }
        throw new OperationValidationException("Missing required field 'quantity' in payload");
    }
}
