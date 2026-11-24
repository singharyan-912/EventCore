package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.OperationRequest;
import com.eventprocessor.reliable_event_processor.dto.ReconcileRequest;
import com.eventprocessor.reliable_event_processor.entity.*;
import com.eventprocessor.reliable_event_processor.repository.*;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.service.DataRetentionCleanupService;
import com.eventprocessor.reliable_event_processor.service.OperationReconciliationService;
import com.eventprocessor.reliable_event_processor.service.RetryExecutionService;
import com.eventprocessor.reliable_event_processor.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "event-processor.outbox.enabled=false",
        "event-processor.consumer.enabled=false",
        "event-processor.worker.enabled=false",
        "event-processor.webhook.enabled=false",
        "event-processor.cleanup.enabled=false",
        "simulation.enabled=false",
        "event-processor.integration.allow-private-ips=true",
        "event-processor.integration.allow-insecure-http=true"
})
class PhaseEReliabilityIntegrationTest {

    private static final String TENANT_A = "tenant-phasee-a";
    private static final String TENANT_B = "tenant-phasee-b";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired ExternalIntegrationRepository integrationRepository;
    @Autowired WebhookRepository webhookRepository;
    @Autowired WebhookDeliveryRepository webhookDeliveryRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired OperationReconciliationService reconciliationService;
    @Autowired DataRetentionCleanupService cleanupService;
    @Autowired RetryExecutionService retryExecutionService;
    @Autowired WebhookService webhookService;

    private String tokenA;
    private String tokenB;
    private String adminToken;

    @BeforeEach
    void setUp() {
        ensureTenant(TENANT_A, "Phase E Tenant A");
        ensureTenant(TENANT_B, "Phase E Tenant B");

        AppUser userA = ensureUser("user-phasee-a", "user-a@phasee.test", Role.OPERATOR, TENANT_A);
        AppUser userB = ensureUser("user-phasee-b", "user-b@phasee.test", Role.OPERATOR, TENANT_B);
        AppUser adminUser = ensureUser("admin-phasee", "admin@phasee.test", Role.ADMIN, TENANT_A);

        tokenA = jwtTokenProvider.generateToken(userA.getUsername(), userA.getRole().name(), TENANT_A);
        tokenB = jwtTokenProvider.generateToken(userB.getUsername(), userB.getRole().name(), TENANT_B);
        adminToken = jwtTokenProvider.generateToken(adminUser.getUsername(), adminUser.getRole().name(), TENANT_A);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Idempotency Lifecycle & Tombstone Rejection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Expired idempotency key reuse is rejected without executing the business operation again")
    void testExpiredIdempotencyKey_rejection_noReexecution() throws Exception {
        String key = "exp-key-" + UUID.randomUUID();
        String item = "ITEM-" + UUID.randomUUID().toString().substring(0, 6);
        inventoryRepository.save(new Inventory(TENANT_A, item, 100));

        // First execution succeeds
        Map<String, Object> body = Map.of(
                "idempotencyKey", key,
                "operationType", "INVENTORY_DEDUCT",
                "resourceId", item,
                "tenantId", TENANT_A,
                "payload", Map.of("quantity", 5)
        );

        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Simulate expiration of the idempotency record
        ProcessedEvent event = processedEventRepository.findByTenantIdAndIdempotencyKey(TENANT_A, key).orElseThrow();
        event.setExpiresAt(OffsetDateTime.now().minusDays(1));
        processedEventRepository.saveAndFlush(event);

        // Resubmit with the same key and payload -> must be rejected with 410 GONE
        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Idempotency Key Expired"));
    }

    @Test
    @DisplayName("2. Expired idempotency key with DIFFERENT payload still throws 409 Conflict")
    void testExpiredIdempotencyKey_differentPayload_conflict() throws Exception {
        String key = "exp-conflict-" + UUID.randomUUID();
        String item = "ITEM-" + UUID.randomUUID().toString().substring(0, 6);
        inventoryRepository.save(new Inventory(TENANT_A, item, 100));

        Map<String, Object> body1 = Map.of(
                "idempotencyKey", key,
                "operationType", "INVENTORY_DEDUCT",
                "resourceId", item,
                "tenantId", TENANT_A,
                "payload", Map.of("quantity", 5)
        );

        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk());

        // Expire the key
        ProcessedEvent event = processedEventRepository.findByTenantIdAndIdempotencyKey(TENANT_A, key).orElseThrow();
        event.setExpiresAt(OffsetDateTime.now().minusDays(1));
        processedEventRepository.saveAndFlush(event);

        // Resubmit with DIFFERENT payload (quantity: 99) -> 409 Conflict
        Map<String, Object> body2 = Map.of(
                "idempotencyKey", key,
                "operationType", "INVENTORY_DEDUCT",
                "resourceId", item,
                "tenantId", TENANT_A,
                "payload", Map.of("quantity", 99)
        );

        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Safe Data Retention & Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3. Cleanup archives old PROCESSED events as tombstones and protects all non-terminal states")
    void testCleanup_preservesAllProtectedStatuses_andTombstones() {
        OffsetDateTime oldTime = OffsetDateTime.now().minusDays(40);

        // Create an old PROCESSED event
        ProcessedEvent oldProcessed = new ProcessedEvent(TENANT_A, "clean-proc-" + UUID.randomUUID(),
                "INVENTORY_DEDUCT", "RES-1", "hash-1", "PROCESSED", "{}", 200);
        oldProcessed.setUpdatedAt(oldTime);
        oldProcessed = processedEventRepository.saveAndFlush(oldProcessed);

        // Create a RETRY_PENDING event (must NEVER be deleted or archived)
        ProcessedEvent retryPending = new ProcessedEvent(TENANT_A, "clean-retry-" + UUID.randomUUID(),
                "INVENTORY_DEDUCT", "RES-2", "hash-2", "RETRY_PENDING", null, null);
        retryPending.setUpdatedAt(oldTime);
        retryPending = processedEventRepository.saveAndFlush(retryPending);

        // Create a RECONCILIATION_REQUIRED event (must NEVER be deleted or archived)
        ProcessedEvent reconcRequired = new ProcessedEvent(TENANT_A, "clean-reconc-" + UUID.randomUUID(),
                "BOOKING_APPOINTMENT", "RES-3", "hash-3", "RECONCILIATION_REQUIRED", null, null);
        reconcRequired.setUpdatedAt(oldTime);
        reconcRequired = processedEventRepository.saveAndFlush(reconcRequired);

        // Execute cleanup
        cleanupService.executeCleanup(false);

        // Verify protected states remain unchanged
        ProcessedEvent checkRetry = processedEventRepository.findById(retryPending.getId()).orElseThrow();
        assertThat(checkRetry.getStatus()).isEqualTo("RETRY_PENDING");
        assertThat(checkRetry.isArchived()).isFalse();

        ProcessedEvent checkReconc = processedEventRepository.findById(reconcRequired.getId()).orElseThrow();
        assertThat(checkReconc.getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(checkReconc.isArchived()).isFalse();
    }

    @Test
    @DisplayName("4. Cleanup execution endpoint: ADMIN allowed, OPERATOR denied (403)")
    void testCleanupEndpoint_roleGating() throws Exception {
        // OPERATOR cannot execute cleanup
        mockMvc.perform(post("/api/v1/operations/cleanup")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        // ADMIN can execute cleanup
        mockMvc.perform(post("/api/v1/operations/cleanup?dryRun=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Stuck PROCESSING Recovery
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5. Stuck PROCESSING recovery: local event recovers safely to RETRY_PENDING")
    void testStuckProcessingRecovery_localEvent_recoversToRetryPending() {
        String key = "stuck-local-" + UUID.randomUUID();
        ProcessedEvent event = new ProcessedEvent(TENANT_A, key, "INVENTORY_DEDUCT", "ITEM-1",
                "dummyhash", "PROCESSING", null, null);
        event.setRetryCount(0);
        event.setMaxRetries(3);
        event.setProcessingLeaseExpiresAt(OffsetDateTime.now().minusMinutes(10));
        event = processedEventRepository.saveAndFlush(event);

        retryExecutionService.recoverStuckEvent(event);

        ProcessedEvent recovered = processedEventRepository.findById(event.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(EventStatus.RETRY_PENDING.name());
        assertThat(recovered.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("6. Stuck PROCESSING recovery: external integration event moves to RECONCILIATION_REQUIRED")
    void testStuckProcessingRecovery_externalIntegrationEvent_movesToReconciliationRequired() {
        String extOpType = "EXTERNAL_ORDER_SYNC_" + UUID.randomUUID().toString().substring(0, 4);

        // Register active external integration for this operation
        ExternalIntegration integration = new ExternalIntegration(
                TENANT_A,
                "ExtOrderSync",
                "HTTP_REST",
                "http://127.0.0.1:9898/orders",
                extOpType,
                "NONE",
                null,
                null,
                null,
                5000
        );
        integrationRepository.saveAndFlush(integration);

        String key = "stuck-ext-" + UUID.randomUUID();
        ProcessedEvent event = new ProcessedEvent(TENANT_A, key, extOpType, "ORD-123",
                "dummyhash", "PROCESSING", null, null);
        event.setProcessingLeaseExpiresAt(OffsetDateTime.now().minusMinutes(10));
        event = processedEventRepository.saveAndFlush(event);

        retryExecutionService.recoverStuckEvent(event);

        ProcessedEvent recovered = processedEventRepository.findById(event.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(EventStatus.RECONCILIATION_REQUIRED.name());
        assertThat(recovered.getNextRetryAt()).isNull(); // Must NOT automatically retry!
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. External Operation Reconciliation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7. Reconciliation listing and detail: tenant isolation enforced")
    void testReconciliation_tenantIsolation() throws Exception {
        String keyA = "reconc-a-" + UUID.randomUUID();
        ProcessedEvent eventA = new ProcessedEvent(TENANT_A, keyA, "BOOKING", "B-1",
                "hash-a", EventStatus.RECONCILIATION_REQUIRED.name(), null, null);
        eventA.setLastFailureReason("External provider gateway timeout");
        processedEventRepository.saveAndFlush(eventA);

        // Tenant A can view the reconciliation
        mockMvc.perform(get("/api/v1/operations/reconciliations/" + keyA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(keyA))
                .andExpect(jsonPath("$.status").value("RECONCILIATION_REQUIRED"));

        // Tenant B cannot view Tenant A's reconciliation (404)
        mockMvc.perform(get("/api/v1/operations/reconciliations/" + keyA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("8. Manual reconciliation: operator records SUCCESS with mandatory reason and audit entry")
    void testManualReconciliation_operatorSuccess() throws Exception {
        String key = "manual-rec-ok-" + UUID.randomUUID();
        ProcessedEvent event = new ProcessedEvent(TENANT_A, key, "BOOKING", "B-2",
                "hash-m", EventStatus.RECONCILIATION_REQUIRED.name(), null, null);
        event.setRequestPayload("{\"bookingId\":\"B-2\"}");
        event.setLastFailureReason("External provider read timeout");
        processedEventRepository.saveAndFlush(event);

        Map<String, Object> body = Map.of(
                "resolution", "SUCCESS",
                "reason", "Verified via partner merchant dashboard that booking was confirmed",
                "notes", "Merchant ticket #98231"
        );

        mockMvc.perform(post("/api/v1/operations/reconciliations/" + key + "/resolve")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED_SUCCESS"))
                .andExpect(jsonPath("$.reconciledBy").exists())
                .andExpect(jsonPath("$.reconciliationNotes").exists());

        ProcessedEvent updated = processedEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("RECONCILED_SUCCESS");
        assertThat(updated.getReconciledAt()).isNotNull();
    }

    @Test
    @DisplayName("9. Automated reconciliation verification: timeout safely reverts to RECONCILIATION_REQUIRED")
    void testAutomatedReconciliation_timeoutSafety() throws Exception {
        String extOpType = "TIMEOUT_OP_" + UUID.randomUUID().toString().substring(0, 4);
        ExternalIntegration integration = new ExternalIntegration(
                TENANT_A, "TimeoutTest", "HTTP_REST", "http://127.0.0.1:9999/timeout",
                extOpType, "NONE", null, null, null, 1000
        );
        integrationRepository.saveAndFlush(integration);

        String key = "rec-timeout-" + UUID.randomUUID();
        ProcessedEvent event = new ProcessedEvent(TENANT_A, key, extOpType, "RES-99",
                "hash-t", EventStatus.RECONCILIATION_REQUIRED.name(), null, null);
        processedEventRepository.saveAndFlush(event);

        // Verification call against unreachable endpoint
        mockMvc.perform(post("/api/v1/operations/reconciliations/" + key + "/verify")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILIATION_REQUIRED"));

        ProcessedEvent checked = processedEventRepository.findById(event.getId()).orElseThrow();
        assertThat(checked.getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Webhook Delivery Reliability & Retries
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("10. Webhook redelivery: manual redelivery resets to RETRY_PENDING with stable eventId")
    void testWebhookRedelivery_preservesStableEventId() throws Exception {
        Webhook webhook = new Webhook(
                TENANT_A, "TestHook", "http://127.0.0.1:9000/hook", "*",
                "encrypted-secret", "sec-"
        );
        webhook = webhookRepository.saveAndFlush(webhook);

        String stableEventId = "evt-stable-" + UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                TENANT_A, webhook.getId(), stableEventId, "OPERATION_SUCCEEDED",
                "key-123", "{\"status\":\"ok\"}"
        );
        delivery.setStatus("FAILED");
        delivery.setAttemptCount(1);
        delivery.setErrorMessage("HTTP 500");
        delivery = webhookDeliveryRepository.saveAndFlush(delivery);

        // Call manual redelivery
        mockMvc.perform(post("/api/v1/webhooks/" + webhook.getId() + "/deliveries/" + delivery.getId() + "/redeliver")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.eventId").value(stableEventId));

        WebhookDelivery updated = webhookDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("RETRY_PENDING");
        assertThat(updated.getEventId()).isEqualTo(stableEventId); // Preserved!
        assertThat(updated.getAttemptCount()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Observability & Health Indicators
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("11. Actuator health: backgroundWorkers health indicator is UP")
    void testActuatorHealth_backgroundWorkersIndicator() throws Exception {
        mockMvc.perform(get("/actuator/health/backgroundWorkers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.retryWorker").exists())
                .andExpect(jsonPath("$.details.webhookRetryWorker").exists())
                .andExpect(jsonPath("$.details.dataRetentionCleanup").exists());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private void ensureTenant(String tenantId, String name) {
        if (!tenantRepository.existsByTenantId(tenantId)) {
            Tenant tenant = new Tenant(tenantId, name, TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
        }
    }

    private AppUser ensureUser(String username, String email, Role role, String tenantId) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            AppUser u = new AppUser(tenantId, username, email, passwordEncoder.encode("TestPassword123!"), role, UserStatus.ACTIVE);
            return userRepository.save(u);
        });
    }
}
