package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.ReliabilityEnhancementProperties;
import com.eventprocessor.reliable_event_processor.dto.CleanupPreviewResponse;
import com.eventprocessor.reliable_event_processor.dto.CleanupResponse;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.entity.WebhookDelivery;
import com.eventprocessor.reliable_event_processor.repository.OperationalAuditLogRepository;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.repository.WebhookDeliveryRepository;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class DataRetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionCleanupService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final OperationalAuditLogRepository auditLogRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OperationalAuditService auditService;
    private final ReliabilityEnhancementProperties properties;
    private final TransactionTemplate transactionTemplate;

    public DataRetentionCleanupService(OutboxEventRepository outboxEventRepository,
                                       WebhookDeliveryRepository webhookDeliveryRepository,
                                       OperationalAuditLogRepository auditLogRepository,
                                       ProcessedEventRepository processedEventRepository,
                                       OperationalAuditService auditService,
                                       ReliabilityEnhancementProperties properties,
                                       PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.auditLogRepository = auditLogRepository;
        this.processedEventRepository = processedEventRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Nightly scheduled safe cleanup job (runs at 03:00 AM server time).
     */
    @Scheduled(cron = "${event-processor.cleanup.cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.getCleanup().isEnabled()) {
            return;
        }
        log.info("Starting scheduled data retention cleanup...");
        try {
            CleanupResponse result = executeCleanup(false);
            log.info("Scheduled cleanup completed: outboxDeleted={}, webhooksDeleted={}, eventsArchived={}",
                    result.outboxRecordsDeleted(), result.webhookDeliveriesDeleted(), result.eventsArchived());
        } catch (Exception e) {
            log.error("Scheduled data retention cleanup failed: {}", e.getMessage(), e);
        }
    }

    public CleanupPreviewResponse previewCleanup() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime outboxCutoff = now.minusDays(properties.getCleanup().getOutboxRetentionDays());
        OffsetDateTime webhookCutoff = now.minusDays(properties.getCleanup().getWebhookDeliveryRetentionDays());
        OffsetDateTime auditCutoff = now.minusDays(properties.getCleanup().getAuditRetentionDays());
        OffsetDateTime archivalCutoff = now.minusDays(properties.getCleanup().getArchivedEventsRetentionDays());

        int outboxEligible = outboxEventRepository.findEligibleForCleanupWithLock(outboxCutoff, 1000).size();
        int webhookEligible = webhookDeliveryRepository.findEligibleForCleanupWithLock(webhookCutoff, 1000).size();
        int eventsEligible = processedEventRepository.findEligibleForArchivalWithLock(archivalCutoff, 1000).size();

        return new CleanupPreviewResponse(
                outboxEligible,
                webhookEligible,
                0, // Audit logs previewed via size check
                eventsEligible
        );
    }

    /**
     * Executes safe, bounded batch cleanup in short transactions.
     * Retains idempotency tombstones to permanently protect against duplicate business execution.
     */
    public CleanupResponse executeCleanup(boolean dryRun) {
        OffsetDateTime now = OffsetDateTime.now();
        if (dryRun) {
            CleanupPreviewResponse preview = previewCleanup();
            return new CleanupResponse(true, preview.outboxRecordsEligible(), preview.webhookDeliveriesEligible(),
                    preview.auditLogsEligible(), preview.eventsEligibleForArchival(), now);
        }

        int batchSize = properties.getCleanup().getBatchSize();
        OffsetDateTime outboxCutoff = now.minusDays(properties.getCleanup().getOutboxRetentionDays());
        OffsetDateTime webhookCutoff = now.minusDays(properties.getCleanup().getWebhookDeliveryRetentionDays());
        OffsetDateTime archivalCutoff = now.minusDays(properties.getCleanup().getArchivedEventsRetentionDays());

        // 1. Clean up published outbox events in short transactions
        int outboxDeleted = transactionTemplate.execute(status -> {
            List<OutboxEvent> eligible = outboxEventRepository.findEligibleForCleanupWithLock(outboxCutoff, batchSize);
            outboxEventRepository.deleteAll(eligible);
            return eligible.size();
        });

        // 2. Clean up completed webhook deliveries in short transactions
        int webhooksDeleted = transactionTemplate.execute(status -> {
            List<WebhookDelivery> eligible = webhookDeliveryRepository.findEligibleForCleanupWithLock(webhookCutoff, batchSize);
            webhookDeliveryRepository.deleteAll(eligible);
            return eligible.size();
        });

        // 3. Tombstone old PROCESSED events (clear payload text but preserve key, hash, status, tenant)
        int eventsArchived = transactionTemplate.execute(status -> {
            List<ProcessedEvent> eligible = processedEventRepository.findEligibleForArchivalWithLock(archivalCutoff, batchSize);
            for (ProcessedEvent event : eligible) {
                event.setArchived(true);
                event.setArchivedAt(now);
                event.setRequestPayload(null);
                event.setResponsePayload(null);
            }
            processedEventRepository.saveAll(eligible);
            return eligible.size();
        });

        String tenantId = TenantContext.getTenantId();
        auditService.recordAuditLog(
                tenantId != null ? tenantId : "SYSTEM",
                "DATA_RETENTION_CLEANUP",
                "cleanup-" + now.toEpochSecond(),
                "SUCCESS",
                null,
                "OPERATOR",
                "Cleanup executed: %d outbox deleted, %d webhooks deleted, %d events archived tombstones"
                        .formatted(outboxDeleted, webhooksDeleted, eventsArchived)
        );

        return new CleanupResponse(false, outboxDeleted, webhooksDeleted, 0, eventsArchived, now);
    }
}
