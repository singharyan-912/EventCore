package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.SimulationProperties;
import com.eventprocessor.reliable_event_processor.exception.TransientProcessingException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolated development/test-only failure simulator.
 * Strictly disabled by default in production.
 */
@Component
public class TransientFailureSimulator {

    private final SimulationProperties simulationProperties;
    private final ConcurrentHashMap<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final java.util.Set<String> clearedKeys = ConcurrentHashMap.newKeySet();

    public TransientFailureSimulator(SimulationProperties simulationProperties) {
        this.simulationProperties = simulationProperties;
    }

    /**
     * If simulation is enabled and the resource ID starts with 'transient-fail',
     * throws a TransientProcessingException for a configured number of attempts.
     */
    public void maybeSimulateTransientFailure(String idempotencyKey, String resourceId, int failureCount) {
        if (!simulationProperties.isEnabled()) {
            return;
        }

        if (clearedKeys.contains(idempotencyKey)) {
            return;
        }

        if (resourceId != null && resourceId.startsWith("transient-fail")) {
            AtomicInteger attempts = failureCounters.computeIfAbsent(idempotencyKey, k -> new AtomicInteger(0));
            if (attempts.incrementAndGet() <= failureCount) {
                throw new TransientProcessingException(
                        "Simulated transient error on attempt " + attempts.get() + " for resource: " + resourceId
                );
            }
        }
    }

    public void clear() {
        clearedKeys.addAll(failureCounters.keySet());
        failureCounters.clear();
    }
}
