package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.AdminSecurityProperties;
import com.eventprocessor.reliable_event_processor.config.RetryProperties;
import com.eventprocessor.reliable_event_processor.dto.DeadLetterResponse;
import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.InvalidStateTransitionException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.exception.UnauthorizedException;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final RetryExecutionService retryExecutionService;
    private final AdminSecurityProperties adminSecurityProperties;
    private final RetryProperties retryProperties;
    private final OperationalAuditService auditService;

    public DeadLetterService(ProcessedEventRepository processedEventRepository,
                             RetryExecutionService retryExecutionService,
                             AdminSecurityProperties adminSecurityProperties,
                             RetryProperties retryProperties,
                             OperationalAuditService auditService) {
        this.processedEventRepository = processedEventRepository;
        this.retryExecutionService = retryExecutionService;
        this.adminSecurityProperties = adminSecurityProperties;
        this.retryProperties = retryProperties;
        this.auditService = auditService;
    }

    public void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(adminSecurityProperties.getApiKey())) {
            throw new UnauthorizedException("Invalid or missing X-Admin-API-Key header");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DeadLetterResponse> listDeadLetterEvents(int page, int size) {
        Page<ProcessedEvent> events = processedEventRepository.findByStatus(
                EventStatus.DEAD_LETTER.name(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        return PageResponse.from(events.map(this::toDeadLetterResponse));
    }

    @Transactional(readOnly = true)
    public DeadLetterResponse getDeadLetterEvent(String idempotencyKey) {
        ProcessedEvent event = processedEventRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + idempotencyKey));

        if (!EventStatus.DEAD_LETTER.name().equals(event.getStatus())) {
            throw new InvalidStateTransitionException(
                    "Event '" + idempotencyKey + "' is not in DEAD_LETTER state (current: " + event.getStatus() + ")");
        }

        return toDeadLetterResponse(event);
    }

    /**
     * Schedules a DEAD_LETTER event for immediate reprocessing by resetting it to RETRY_PENDING.
     * Does NOT directly re-run business logic — that is the worker's responsibility.
     * Does NOT undo or reset any previously successful inventory deduction.
     */
    @Transactional
    public DeadLetterResponse requestReprocessing(String idempotencyKey) {
        return requestReprocessing(idempotencyKey, "SYSTEM");
    }

    /**
     * Schedules a DEAD_LETTER event for immediate reprocessing by resetting it to RETRY_PENDING.
     * Does NOT directly re-run business logic — that is the worker's responsibility.
     * Does NOT undo or reset any previously successful inventory deduction.
     * Records an operational audit log for administrative tracing.
     */
    @Transactional
    public DeadLetterResponse requestReprocessing(String idempotencyKey, String principal) {
        try {
            ProcessedEvent event = processedEventRepository
                    .findByIdempotencyKeyForUpdate(idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + idempotencyKey));

            if (!EventStatus.DEAD_LETTER.name().equals(event.getStatus())) {
                throw new InvalidStateTransitionException(
                        "Only DEAD_LETTER events can be reprocessed. Current status: " + event.getStatus());
            }

            // Reset for one more attempt cycle — preserve original idempotency key and payload
            event.setStatus(EventStatus.RETRY_PENDING.name());
            event.setRetryCount(0);
            event.setMaxRetries(retryProperties.getMaxAttempts());
            event.setNextRetryAt(OffsetDateTime.now()); // immediately eligible
            event.setLastFailureReason("Manually queued for reprocessing by operator");
            processedEventRepository.save(event);

            auditService.recordAuditLog(
                    "DEAD_LETTER_REPROCESS",
                    idempotencyKey,
                    "SUCCESS",
                    null,
                    principal,
                    "Event reset to RETRY_PENDING with immediate schedule"
            );

            log.info("Event {} manually queued for reprocessing from DEAD_LETTER by principal '{}'", idempotencyKey, principal);
            return toDeadLetterResponse(event);
        } catch (Exception ex) {
            auditService.recordAuditLog(
                    "DEAD_LETTER_REPROCESS",
                    idempotencyKey,
                    "FAILED",
                    ex.getMessage(),
                    principal,
                    "Reprocessing request rejected"
            );
            throw ex;
        }
    }

    private DeadLetterResponse toDeadLetterResponse(ProcessedEvent event) {
        return new DeadLetterResponse(
                event.getIdempotencyKey(),
                event.getEventType(),
                event.getResourceId(),
                event.getStatus(),
                event.getRetryCount() != null ? event.getRetryCount() : 0,
                event.getMaxRetries() != null ? event.getMaxRetries() : 3,
                event.getLastFailureReason(),
                event.getNextRetryAt(),
                event.getLastAttemptAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
