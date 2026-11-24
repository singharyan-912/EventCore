package com.eventprocessor.reliable_event_processor.handler;

import com.eventprocessor.reliable_event_processor.exception.UnsupportedOperationTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring-managed registry for business operation handlers.
 *
 * Discovers all BusinessOperationHandler beans, prevents duplicate registrations,
 * and resolves handlers by operation type with fast O(1) lookup.
 */
@Component
public class OperationHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(OperationHandlerRegistry.class);

    private final Map<String, BusinessOperationHandler<?>> handlers = new ConcurrentHashMap<>();

    public OperationHandlerRegistry(List<BusinessOperationHandler<?>> handlerList) {
        if (handlerList != null) {
            for (BusinessOperationHandler<?> handler : handlerList) {
                registerHandler(handler);
            }
        }
    }

    public synchronized void registerHandler(BusinessOperationHandler<?> handler) {
        Objects.requireNonNull(handler, "Handler must not be null");
        String type = handler.getOperationType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Handler operation type must not be null or blank");
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (handlers.containsKey(normalizedType)) {
            throw new IllegalStateException("Duplicate handler registration for operation type: " + type);
        }
        handlers.put(normalizedType, handler);
        log.info("Registered business operation handler: [{}] -> {}", normalizedType, handler.getClass().getSimpleName());
    }

    public Optional<BusinessOperationHandler<?>> getHandler(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(operationType.trim().toUpperCase(Locale.ROOT)));
    }

    public BusinessOperationHandler<?> getRequiredHandler(String operationType) {
        return getHandler(operationType)
                .orElseThrow(() -> new UnsupportedOperationTypeException("Unsupported operation type: " + operationType));
    }

    public boolean supports(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return false;
        }
        return handlers.containsKey(operationType.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> getSupportedOperationTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
