package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.*;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OperationsService {

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;

    public OperationsService(ProcessedEventRepository processedEventRepository,
                             OutboxEventRepository outboxEventRepository,
                             MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Search events with pagination and bounded filtering.
     * Prevents unbounded memory consumption through safe pagination caps.
     */
    @Transactional(readOnly = true)
    public PageResponse<EventDetailResponse> searchEvents(String status,
                                                          String eventType,
                                                          String resourceId,
                                                          String idempotencyKey,
                                                          OffsetDateTime from,
                                                          OffsetDateTime to,
                                                          int page,
                                                          int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1: " + size);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Start date ('from') must not be after end date ('to')");
        }

        int boundedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Specification<ProcessedEvent> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (!TenantContext.isPlatformAdminBypass()) {
                String tenantId = TenantContext.getTenantId();
                predicates = cb.and(predicates, cb.equal(root.get("tenantId"), tenantId));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (resourceId != null && !resourceId.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("resourceId"), resourceId.trim()));
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("idempotencyKey"), idempotencyKey.trim()));
            }
            if (from != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return predicates;
        };

        Page<ProcessedEvent> eventPage = processedEventRepository.findAll(spec, pageable);

        // Batch fetch outbox records to avoid N+1 queries
        List<String> keys = eventPage.getContent().stream()
                .map(ProcessedEvent::getIdempotencyKey)
                .toList();

        Map<String, OutboxEvent> outboxMap;
        if (TenantContext.isPlatformAdminBypass()) {
            outboxMap = outboxEventRepository.findByIdempotencyKeyIn(keys).stream()
                    .collect(Collectors.toMap(
                            o -> o.getTenantId() + "::" + o.getIdempotencyKey(),
                            o -> o,
                            (o1, o2) -> o1));
        } else {
            String tenantId = TenantContext.getTenantId();
            outboxMap = outboxEventRepository.findByTenantIdAndIdempotencyKeyIn(tenantId, keys).stream()
                    .collect(Collectors.toMap(
                            OutboxEvent::getIdempotencyKey,
                            o -> o,
                            (o1, o2) -> o1));
        }

        Page<EventDetailResponse> mapped = eventPage.map(event -> {
            OutboxEvent outbox = TenantContext.isPlatformAdminBypass()
                    ? outboxMap.get(event.getTenantId() + "::" + event.getIdempotencyKey())
                    : outboxMap.get(event.getIdempotencyKey());
            return toEventDetailResponse(event, outbox);
        });

        return PageResponse.from(mapped);
    }

    /**
     * Retrieves full detail for a single event including related outbox publication status.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be empty");
        }

        ProcessedEvent event;
        OutboxEvent outbox;
        if (TenantContext.isPlatformAdminBypass()) {
            event = processedEventRepository.findByIdempotencyKey(idempotencyKey.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found with idempotency key: " + idempotencyKey));
            outbox = outboxEventRepository.findByTenantIdAndIdempotencyKey(event.getTenantId(), idempotencyKey.trim()).orElse(null);
        } else {
            String tenantId = TenantContext.getTenantId();
            event = processedEventRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found with idempotency key: " + idempotencyKey));
            outbox = outboxEventRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey.trim()).orElse(null);
        }

        return toEventDetailResponse(event, outbox);
    }

    /**
     * Aggregates event counts by status directly from database queries.
     */
    @Transactional(readOnly = true)
    public EventSummaryResponse getEventSummary() {
        long total;
        List<Object[]> statusCounts;
        if (TenantContext.isPlatformAdminBypass()) {
            total = processedEventRepository.count();
            statusCounts = processedEventRepository.countGroupedByStatus();
        } else {
            String tenantId = TenantContext.getTenantId();
            total = processedEventRepository.countByTenantId(tenantId);
            statusCounts = processedEventRepository.countGroupedByStatusForTenant(tenantId);
        }

        Map<String, Long> map = new HashMap<>();
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            map.put(status, count);
        }

        long processed = map.getOrDefault("PROCESSED", 0L);
        long retryPending = map.getOrDefault("RETRY_PENDING", 0L);
        long deadLetter = map.getOrDefault("DEAD_LETTER", 0L);
        long processing = map.getOrDefault("PROCESSING", 0L);

        return new EventSummaryResponse(total, processed, retryPending, deadLetter, processing, map);
    }

    /**
     * Aggregates outbox counts by publication status directly from database queries.
     */
    @Transactional(readOnly = true)
    public OutboxSummaryResponse getOutboxSummary() {
        long total;
        List<Object[]> statusCounts;
        long retried;
        if (TenantContext.isPlatformAdminBypass()) {
            total = outboxEventRepository.count();
            statusCounts = outboxEventRepository.countGroupedByStatus();
            retried = outboxEventRepository.countRetriedOutboxEvents();
        } else {
            String tenantId = TenantContext.getTenantId();
            total = outboxEventRepository.countByTenantId(tenantId);
            statusCounts = outboxEventRepository.countGroupedByStatusForTenant(tenantId);
            retried = outboxEventRepository.countRetriedOutboxEventsForTenant(tenantId);
        }

        Map<String, Long> map = new HashMap<>();
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            map.put(status, count);
        }

        long pending = map.getOrDefault("PENDING", 0L);
        long claimed = map.getOrDefault("CLAIMED", 0L);
        long published = map.getOrDefault("PUBLISHED", 0L);
        long failed = map.getOrDefault("FAILED", 0L);

        return new OutboxSummaryResponse(total, pending, claimed, published, failed, retried, map);
    }

    /**
     * Provides unified operational snapshot:
     * In-memory Micrometer counters (process lifetime) + durable database counts.
     */
    @Transactional(readOnly = true)
    public OperationsMetricsResponse getOperationsMetrics() {
        long attempts = getCounterSum("events.processing.attempts");
        long successes = getCounterSum("events.processing.success");
        long failures = getCounterSum("events.processing.failure");
        long duplicates = getCounterSum("events.processed.duplicate");
        long retries = getCounterSum("events.retry.attempts");
        long deadLetters = getCounterSum("events.deadletter.total");
        long outboxPublished = getCounterSum("outbox.publication.success");
        long outboxFailed = getCounterSum("outbox.publication.failure");
        long consumerSuccess = getCounterSum("consumer.processing.success");
        long consumerFailure = getCounterSum("consumer.processing.failure");
        long consumerDuplicate = getCounterSum("consumer.duplicate.deliveries");

        EventSummaryResponse dbEvents = getEventSummary();
        OutboxSummaryResponse dbOutbox = getOutboxSummary();

        String notice = "In-memory metrics reflect the current JVM process lifetime and reset upon application restart. Use database summaries for durable historical counts.";

        return new OperationsMetricsResponse(
                notice,
                attempts,
                successes,
                failures,
                duplicates,
                retries,
                deadLetters,
                outboxPublished,
                outboxFailed,
                consumerSuccess,
                consumerFailure,
                consumerDuplicate,
                dbEvents,
                dbOutbox
        );
    }

    private long getCounterSum(String meterName) {
        try {
            Collection<Counter> counters = Search.in(meterRegistry).name(meterName).counters();
            return (long) counters.stream().mapToDouble(Counter::count).sum();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private EventDetailResponse toEventDetailResponse(ProcessedEvent event, OutboxEvent outbox) {
        OutboxDetailResponse outboxDetail = null;
        if (outbox != null) {
            outboxDetail = new OutboxDetailResponse(
                    outbox.getId(),
                    outbox.getStatus(),
                    outbox.getTopic(),
                    outbox.getPartitionKey(),
                    outbox.getRetryCount(),
                    outbox.getMaxRetries(),
                    outbox.getLastError(),
                    outbox.getPublishedAt(),
                    outbox.getCreatedAt(),
                    outbox.getUpdatedAt()
            );
        }

        return new EventDetailResponse(
                event.getIdempotencyKey(),
                event.getEventType(),
                event.getResourceId(),
                event.getStatus(),
                event.getResponseStatusCode(),
                event.getRetryCount(),
                event.getMaxRetries(),
                event.getLastFailureReason(),
                event.getNextRetryAt(),
                event.getLastAttemptAt(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getRequestPayload(),
                event.getResponsePayload(),
                outboxDetail
        );
    }
}
