package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.dto.ReconcileRequest;
import com.eventprocessor.reliable_event_processor.dto.ReconcileResponse;
import com.eventprocessor.reliable_event_processor.dto.ReconciliationDetailResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ExternalIntegration;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.InvalidStateTransitionException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.integration.HttpIntegrationAdapter;
import com.eventprocessor.reliable_event_processor.repository.ExternalIntegrationRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class OperationReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OperationReconciliationService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ExternalIntegrationRepository integrationRepository;
    private final HttpIntegrationAdapter httpIntegrationAdapter;
    private final OperationalAuditService auditService;
    private final WebhookService webhookService;
    private final EventMetricsService metricsService;
    private final TransactionTemplate transactionTemplate;

    public OperationReconciliationService(ProcessedEventRepository processedEventRepository,
                                          ExternalIntegrationRepository integrationRepository,
                                          HttpIntegrationAdapter httpIntegrationAdapter,
                                          OperationalAuditService auditService,
                                          WebhookService webhookService,
                                          EventMetricsService metricsService,
                                          PlatformTransactionManager transactionManager) {
        this.processedEventRepository = processedEventRepository;
        this.integrationRepository = integrationRepository;
        this.httpIntegrationAdapter = httpIntegrationAdapter;
        this.auditService = auditService;
        this.webhookService = webhookService;
        this.metricsService = metricsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PageResponse<ReconciliationDetailResponse> listReconciliations(int page, int size) {
        String tenantId = TenantContext.getTenantId();
        int boundedSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<ProcessedEvent> events;
        if (TenantContext.isPlatformAdminBypass()) {
            events = processedEventRepository.findByStatus(EventStatus.RECONCILIATION_REQUIRED.name(), pageRequest);
        } else {
            events = processedEventRepository.findByTenantIdAndStatus(tenantId, EventStatus.RECONCILIATION_REQUIRED.name(), pageRequest);
        }

        return PageResponse.from(events.map(this::toDetailResponse));
    }

    public ReconciliationDetailResponse getReconciliation(String idempotencyKey) {
        String tenantId = TenantContext.getTenantId();
        ProcessedEvent event;
        if (TenantContext.isPlatformAdminBypass()) {
            event = processedEventRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + idempotencyKey));
        } else {
            event = processedEventRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found for tenant: " + idempotencyKey));
        }

        return toDetailResponse(event);
    }

    /**
     * Executes automatic status verification outside the database transaction.
     * Uses atomic claiming in short transactions to prevent concurrent reconciliation attempts.
     */
    public ReconcileResponse reconcileAutomatically(String idempotencyKey) {
        String tenantId = TenantContext.getTenantId();

        // Step 1: Claim record atomically in a short transaction
        ClaimResult claim = transactionTemplate.execute(status -> {
            ProcessedEvent locked = findAndLockEvent(tenantId, idempotencyKey);
            if (!EventStatus.RECONCILIATION_REQUIRED.name().equals(locked.getStatus())) {
                throw new InvalidStateTransitionException(
                        "Cannot reconcile event '%s' with status '%s'. Only RECONCILIATION_REQUIRED can be claimed."
                                .formatted(idempotencyKey, locked.getStatus())
                );
            }

            locked.setStatus(EventStatus.RECONCILIATION_IN_PROGRESS.name());
            locked.setUpdatedAt(OffsetDateTime.now());
            processedEventRepository.saveAndFlush(locked);

            Optional<ExternalIntegration> matchedIntegration = integrationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                    .stream()
                    .filter(i -> i.supportsOperation(locked.getEventType()))
                    .findFirst();

            return new ClaimResult(locked.getEventType(), matchedIntegration.orElse(null));
        });

        if (claim == null) {
            throw new ResourceNotFoundException("Event not found: " + idempotencyKey);
        }

        // Step 2: External HTTP call is executed strictly OUTSIDE the database transaction
        HttpIntegrationAdapter.ProviderStatusResult checkResult;
        if (claim.integration() != null) {
            checkResult = httpIntegrationAdapter.checkStatus(claim.integration(), idempotencyKey);
        } else {
            checkResult = HttpIntegrationAdapter.ProviderStatusResult.UNRESOLVED;
        }

        // Step 3: Persist verified outcome in a new short transaction
        return transactionTemplate.execute(status -> {
            ProcessedEvent locked = findAndLockEvent(tenantId, idempotencyKey);
            OffsetDateTime now = OffsetDateTime.now();
            String finalStatus;
            String message;

            if (checkResult == HttpIntegrationAdapter.ProviderStatusResult.SUCCESS) {
                finalStatus = EventStatus.RECONCILED_SUCCESS.name();
                locked.setStatus(finalStatus);
                locked.setReconciledAt(now);
                locked.setReconciledBy("AUTOMATED_PROVIDER_CHECK");
                locked.setReconciliationNotes("External provider confirmed operation succeeded");
                message = "Automated reconciliation succeeded: external provider confirmed success";
                metricsService.recordEventSuccess(locked.getEventType(), null);
                webhookService.dispatchWebhookNotification(tenantId, "OPERATION_RECONCILED_SUCCESS", idempotencyKey, locked.getRequestPayload());
            } else if (checkResult == HttpIntegrationAdapter.ProviderStatusResult.FAILED) {
                finalStatus = EventStatus.RECONCILED_FAILURE.name();
                locked.setStatus(finalStatus);
                locked.setReconciledAt(now);
                locked.setReconciledBy("AUTOMATED_PROVIDER_CHECK");
                locked.setReconciliationNotes("External provider confirmed operation was not executed or failed");
                message = "Automated reconciliation resolved to failure: provider confirmed operation failed/absent";
                metricsService.recordEventFailure(locked.getEventType(), "RECONCILED_FAILURE", null);
                webhookService.dispatchWebhookNotification(tenantId, "OPERATION_RECONCILED_FAILED", idempotencyKey, locked.getRequestPayload());
            } else {
                // Provider timed out or does not support status lookup -> revert to RECONCILIATION_REQUIRED
                finalStatus = EventStatus.RECONCILIATION_REQUIRED.name();
                locked.setStatus(finalStatus);
                locked.setReconciliationNotes("Provider status check timed out or unavailable; awaiting manual resolution");
                message = "Provider status verification timed out or unavailable; operation remains RECONCILIATION_REQUIRED";
            }

            locked.setUpdatedAt(now);
            processedEventRepository.saveAndFlush(locked);

            auditService.recordAuditLog(
                    tenantId,
                    "RECONCILIATION_AUTOMATED_CHECK",
                    "event-" + idempotencyKey,
                    finalStatus,
                    null,
                    "SYSTEM",
                    message
            );

            return new ReconcileResponse(
                    idempotencyKey,
                    tenantId,
                    finalStatus,
                    locked.getReconciledAt(),
                    locked.getReconciledBy(),
                    locked.getReconciliationNotes(),
                    message
            );
        });
    }

    /**
     * Resolves an uncertain operation manually with mandatory operator reason and audit record.
     * Does NOT re-execute the original business side effect.
     */
    public ReconcileResponse reconcileManually(String idempotencyKey, ReconcileRequest request) {
        String tenantId = TenantContext.getTenantId();
        String principal = getAuthenticatedPrincipal();

        return transactionTemplate.execute(status -> {
            ProcessedEvent locked = findAndLockEvent(tenantId, idempotencyKey);

            if (!EventStatus.RECONCILIATION_REQUIRED.name().equals(locked.getStatus())
                    && !EventStatus.RECONCILIATION_IN_PROGRESS.name().equals(locked.getStatus())) {
                throw new InvalidStateTransitionException(
                        "Cannot manually reconcile event '%s' with status '%s'. Only unresolved events can be reconciled."
                                .formatted(idempotencyKey, locked.getStatus())
                );
            }

            OffsetDateTime now = OffsetDateTime.now();
            String targetStatus = "SUCCESS".equalsIgnoreCase(request.resolution())
                    ? EventStatus.RECONCILED_SUCCESS.name()
                    : EventStatus.RECONCILED_FAILURE.name();

            locked.setStatus(targetStatus);
            locked.setReconciledAt(now);
            locked.setReconciledBy(principal);
            String fullNotes = request.reason() + (request.notes() != null && !request.notes().isBlank() ? " (" + request.notes() + ")" : "");
            locked.setReconciliationNotes(fullNotes);
            locked.setUpdatedAt(now);
            processedEventRepository.saveAndFlush(locked);

            String auditAction = "SUCCESS".equalsIgnoreCase(request.resolution())
                    ? "RECONCILIATION_MANUAL_SUCCESS"
                    : "RECONCILIATION_MANUAL_FAILURE";

            auditService.recordAuditLog(
                    tenantId,
                    auditAction,
                    "event-" + idempotencyKey,
                    targetStatus,
                    null,
                    principal,
                    "Manual reconciliation by operator: " + fullNotes
            );

            if ("SUCCESS".equalsIgnoreCase(request.resolution())) {
                webhookService.dispatchWebhookNotification(tenantId, "OPERATION_RECONCILED_SUCCESS", idempotencyKey, locked.getRequestPayload());
            } else {
                webhookService.dispatchWebhookNotification(tenantId, "OPERATION_RECONCILED_FAILED", idempotencyKey, locked.getRequestPayload());
            }

            return new ReconcileResponse(
                    idempotencyKey,
                    tenantId,
                    targetStatus,
                    now,
                    principal,
                    fullNotes,
                    "Manual reconciliation successfully recorded as " + targetStatus
            );
        });
    }

    private ProcessedEvent findAndLockEvent(String tenantId, String idempotencyKey) {
        if (TenantContext.isPlatformAdminBypass()) {
            return processedEventRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + idempotencyKey));
        } else {
            return processedEventRepository.findByTenantIdAndIdempotencyKeyForUpdate(tenantId, idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found for tenant: " + idempotencyKey));
        }
    }

    private String getAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "OPERATOR";
    }

    private ReconciliationDetailResponse toDetailResponse(ProcessedEvent event) {
        return new ReconciliationDetailResponse(
                event.getIdempotencyKey(),
                event.getTenantId(),
                event.getEventType(),
                event.getResourceId(),
                event.getStatus(),
                event.getLastFailureReason(),
                event.getLastAttemptAt(),
                event.getReconciledAt(),
                event.getReconciledBy(),
                event.getReconciliationNotes(),
                event.getRequestPayload()
        );
    }

    private record ClaimResult(String eventType, ExternalIntegration integration) {}
}
