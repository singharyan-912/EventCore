package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.service.RetryExecutionService;
import com.eventprocessor.reliable_event_processor.service.RetryWorker;
import com.eventprocessor.reliable_event_processor.service.TransientFailureSimulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * Phase 4 Integration Tests — Retry Mechanism, Failure Recovery, Dead-Letter Queue
 *
 * Tests run against a real PostgreSQL database (same as application.properties).
 * Simulation is activated via TestPropertySource to inject deterministic transient failures.
 *
 * Each test uses unique resourceId/idempotencyKey prefixes to avoid inter-test interference.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "simulation.enabled=true",
        "event-processor.retry.initial-delay-ms=100",     // fast retries for testing
        "event-processor.retry.max-delay-ms=500",
        "event-processor.retry.multiplier=2.0",
        "event-processor.retry.max-attempts=3",
        "event-processor.worker.poll-interval-ms=200",    // rapid polling
        "event-processor.worker.batch-size=10",
        "event-processor.outbox.enabled=false",
        "event-processor.consumer.enabled=false"
})
class RetryAndDeadLetterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private RetryWorker retryWorker;
    @Autowired private RetryExecutionService retryExecutionService;
    @Autowired private TransientFailureSimulator failureSimulator;

    private static final String ADMIN_KEY = "admin-secret-key-12345";

    @BeforeEach
    void resetSimulator() {
        failureSimulator.clear();
    }

    private Inventory createInventory(String resourceId, int quantity) {
        return inventoryRepository.findByResourceId(resourceId)
                .map(existing -> {
                    existing.setAvailableQuantity(quantity);
                    return inventoryRepository.save(existing);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory(resourceId, quantity)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: First submission with simulated transient failure → RETRY_PENDING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-1. Transient failure on first attempt → 202 RETRY_PENDING, event persisted")
    void test_transientFailure_firstAttempt_returns202AndPersistsRetryPending() throws Exception {
        String resourceId = "transient-fail-p4-t1";
        String idempotencyKey = "p4-t1-" + System.currentTimeMillis();
        createInventory(resourceId, 100);

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 5);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        ProcessedEvent persisted = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(EventStatus.RETRY_PENDING.name());
        assertThat(persisted.getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(persisted.getLastFailureReason()).isNotBlank();
        assertThat(persisted.getNextRetryAt()).isNotNull();
        assertThat(persisted.getRequestPayload()).isNotBlank();

        // Inventory should NOT have been deducted (first attempt failed)
        Inventory inv = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(100);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Replay of a RETRY_PENDING event returns same pending status
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-2. Replaying a RETRY_PENDING event returns the current pending state (idempotent)")
    void test_replay_retryPendingEvent_returnsCurrentStatus() throws Exception {
        String resourceId = "transient-fail-p4-t2";
        String idempotencyKey = "p4-t2-" + System.currentTimeMillis();
        createInventory(resourceId, 100);

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 5);
        String requestBody = objectMapper.writeValueAsString(request);

        // First submission — will fail transiently → RETRY_PENDING
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        // Second (replay) submission with same key and same payload
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Worker retries succeed after transient failures are exhausted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-3. RetryWorker processes RETRY_PENDING event successfully (event reaches PROCESSED)")
    void test_retryWorker_processesPendingEvent_successfullyReachesProcessed() throws Exception {
        String resourceId = "transient-fail-p4-t3";
        String idempotencyKey = "p4-t3-" + System.currentTimeMillis();
        createInventory(resourceId, 50);

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 5);

        // First attempt will fail transiently (simulator fails first 2 calls per key)
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"));

        // Clear simulator so retry worker succeeds on the re-attempt
        failureSimulator.clear();

        // Move next_retry_at to past so the worker picks it up immediately
        setNextRetryAtToPast(idempotencyKey);

        // Wait for RetryWorker to process and succeed
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ProcessedEvent e = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
                    assertThat(e.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
                });

        // Inventory should now be deducted
        Inventory inv = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(45);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Exponential backoff times out → event moves to DEAD_LETTER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-4. All retry attempts exhausted → event moves to DEAD_LETTER")
    void test_allRetriesExhausted_eventMovesToDeadLetter() {
        String resourceId = "transient-fail-p4-t4";
        String idempotencyKey = "p4-t4-" + System.currentTimeMillis();
        createInventory(resourceId, 100);

        // Insert a RETRY_PENDING event that is already at max retries
        ProcessedEvent event = createRetryPendingEvent(idempotencyKey, resourceId, 3, 3);

        // Run the retry execution directly — should immediately move to DEAD_LETTER
        retryExecutionService.executeRetry(event);

        ProcessedEvent result = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(EventStatus.DEAD_LETTER.name());
        assertThat(result.getLastFailureReason()).isNotBlank();

        // Inventory must not have been touched
        Inventory inv = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(100);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Dead-letter listing endpoint (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-5. GET /api/v1/admin/dead-letters returns DEAD_LETTER events with pagination")
    void test_listDeadLetters_returnsPaginatedDeadLetterEvents() throws Exception {
        String resourceId = "p4-t5-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t5-" + System.currentTimeMillis();
        createInventory(resourceId, 10);
        createDeadLetterEvent(idempotencyKey, resourceId);

        mockMvc.perform(get("/api/v1/admin/dead-letters")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: Dead-letter detail endpoint (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-6. GET /api/v1/admin/dead-letters/{key} returns detail for a specific dead-letter event")
    void test_getDeadLetterDetail_returnsEventDetail() throws Exception {
        String resourceId = "p4-t6-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t6-" + System.currentTimeMillis();
        createInventory(resourceId, 10);
        createDeadLetterEvent(idempotencyKey, resourceId);

        mockMvc.perform(get("/api/v1/admin/dead-letters/" + idempotencyKey)
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.lastFailureReason").isString())
                .andExpect(jsonPath("$.retryCount").isNumber());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: Dead-letter admin endpoint rejects wrong API key
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-7. Admin endpoints reject requests with wrong or missing API key → 401")
    void test_adminEndpoints_rejectInvalidApiKey_returns401() throws Exception {
        // Wrong key
        mockMvc.perform(get("/api/v1/admin/dead-letters")
                        .header("X-Admin-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());

        // Missing key
        mockMvc.perform(get("/api/v1/admin/dead-letters"))
                .andExpect(status().isBadRequest()); // Spring returns 400 for missing required header
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 8: Manual reprocessing via admin endpoint
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-8. POST /reprocess schedules a dead-letter event back to RETRY_PENDING")
    void test_reprocessDeadLetter_schedulesForRetry() throws Exception {
        String resourceId = "p4-t8-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t8-" + System.currentTimeMillis();
        createInventory(resourceId, 100);
        createDeadLetterEvent(idempotencyKey, resourceId);

        mockMvc.perform(post("/api/v1/admin/dead-letters/" + idempotencyKey + "/reprocess")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"));

        ProcessedEvent requeued = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(EventStatus.RETRY_PENDING.name());
        assertThat(requeued.getRetryCount()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 9: Reprocessing of non-dead-letter event is rejected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-9. Reprocessing a non-DEAD_LETTER event returns 409")
    void test_reprocess_nonDeadLetterEvent_returns409() throws Exception {
        String resourceId = "p4-t9-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t9-" + System.currentTimeMillis();
        createInventory(resourceId, 100);

        // Insert as RETRY_PENDING, not DEAD_LETTER
        createRetryPendingEvent(idempotencyKey, resourceId, 1, 3);

        mockMvc.perform(post("/api/v1/admin/dead-letters/" + idempotencyKey + "/reprocess")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 10: After successful reprocessing by worker, replay returns PROCESSED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-10. After reprocess + worker execution → event reaches PROCESSED, inventory deducted")
    void test_reprocess_thenWorkerSucceeds_inventoryDeducted() throws Exception {
        String resourceId = "p4-t10-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t10-" + System.currentTimeMillis();
        createInventory(resourceId, 50);

        // Create dead-letter event with stored payload (quantity=5)
        createDeadLetterEventWithPayload(idempotencyKey, resourceId, 5);

        // Admin requests reprocessing
        mockMvc.perform(post("/api/v1/admin/dead-letters/" + idempotencyKey + "/reprocess")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"));

        // Worker should process it
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ProcessedEvent e = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
                    assertThat(e.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
                });

        Inventory inv = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(45);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 11: Payload hash mismatch on existing idempotency key → 409 Conflict
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-11. Same idempotency key, different payload → 409 Conflict (payload mismatch)")
    void test_payloadMismatch_returns409() throws Exception {
        String resourceId = "p4-t11-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t11-" + System.currentTimeMillis();
        createInventory(resourceId, 100);

        EventRequest original = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 5);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(original)))
                .andExpect(status().is2xxSuccessful());

        // Same idempotency key, different quantity
        EventRequest different = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 99);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(different)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 12: Concurrency — two workers processing same RETRY_PENDING event
    //          Only one should execute; the other should skip (SKIP LOCKED)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-12. Concurrent worker threads do not double-process the same RETRY_PENDING event")
    void test_concurrentWorkers_doNotDoubleProcess() throws InterruptedException {
        String resourceId = "p4-t12-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t12-" + System.currentTimeMillis();
        createInventory(resourceId, 100);
        createDeadLetterEventWithPayload(idempotencyKey, resourceId, 5);

        // Move to RETRY_PENDING
        ProcessedEvent event = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        event.setStatus(EventStatus.RETRY_PENDING.name());
        event.setRetryCount(0);
        event.setNextRetryAt(OffsetDateTime.now().minusSeconds(10));
        processedEventRepository.save(event);

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger executionCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    latch.await();
                    List<ProcessedEvent> eligible = retryWorker.fetchEligibleBatch();
                    if (!eligible.isEmpty()) {
                        retryExecutionService.executeRetry(eligible.get(0));
                        executionCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // The event should be processed exactly once
        ProcessedEvent result = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(EventStatus.PROCESSED.name());

        Inventory inv = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(95); // deducted exactly once
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 13: Dead-letter event detail endpoint returns 409 for non-dead-letter event
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-13. GET dead-letter detail for a non-DEAD_LETTER event returns 409")
    void test_getDeadLetterDetail_forNonDeadLetterEvent_returns409() throws Exception {
        String resourceId = "p4-t13-res-" + System.currentTimeMillis();
        String idempotencyKey = "p4-t13-" + System.currentTimeMillis();
        createInventory(resourceId, 10);

        // Create a normal PROCESSED event via API
        EventRequest req = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 1);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is2xxSuccessful());

        // Attempt to get it as dead-letter
        mockMvc.perform(get("/api/v1/admin/dead-letters/" + idempotencyKey)
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 14: Health endpoint still works (Phase 1 regression)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4-14. GET /api/v1/health returns 200 OK (Phase 1 regression)")
    void test_healthEndpoint_regression() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setNextRetryAtToPast(String idempotencyKey) {
        ProcessedEvent event = processedEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        event.setNextRetryAt(OffsetDateTime.now().minusSeconds(60));
        processedEventRepository.save(event);
    }

    private ProcessedEvent createRetryPendingEvent(String idempotencyKey, String resourceId,
                                                   int retryCount, int maxRetries) {
        ProcessedEvent event = new ProcessedEvent();
        event.setIdempotencyKey(idempotencyKey);
        event.setEventType("INVENTORY_DEDUCT");
        event.setResourceId(resourceId);
        event.setRequestPayloadHash("fakehash-" + idempotencyKey);
        event.setRequestPayload("{\"idempotencyKey\":\"" + idempotencyKey + "\",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"" + resourceId + "\",\"quantity\":5}");
        event.setStatus(EventStatus.RETRY_PENDING.name());
        event.setRetryCount(retryCount);
        event.setMaxRetries(maxRetries);
        event.setLastFailureReason("Simulated failure");
        event.setNextRetryAt(OffsetDateTime.now().minusSeconds(1));
        event.setLastAttemptAt(OffsetDateTime.now().minusSeconds(10));
        event.setUpdatedAt(OffsetDateTime.now());
        return processedEventRepository.save(event);
    }

    private ProcessedEvent createDeadLetterEvent(String idempotencyKey, String resourceId) {
        ProcessedEvent event = new ProcessedEvent();
        event.setIdempotencyKey(idempotencyKey);
        event.setEventType("INVENTORY_DEDUCT");
        event.setResourceId(resourceId);
        event.setRequestPayloadHash("fakehash-dl-" + idempotencyKey);
        event.setRequestPayload("{\"idempotencyKey\":\"" + idempotencyKey + "\",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"" + resourceId + "\",\"quantity\":5}");
        event.setStatus(EventStatus.DEAD_LETTER.name());
        event.setRetryCount(3);
        event.setMaxRetries(3);
        event.setLastFailureReason("Max retries exhausted");
        event.setLastAttemptAt(OffsetDateTime.now().minusSeconds(30));
        event.setUpdatedAt(OffsetDateTime.now());
        return processedEventRepository.save(event);
    }

    private ProcessedEvent createDeadLetterEventWithPayload(String idempotencyKey, String resourceId, int quantity) {
        ProcessedEvent event = new ProcessedEvent();
        event.setIdempotencyKey(idempotencyKey);
        event.setEventType("INVENTORY_DEDUCT");
        event.setResourceId(resourceId);
        event.setRequestPayloadHash("fakehash-dlp-" + idempotencyKey);
        event.setRequestPayload("{\"idempotencyKey\":\"" + idempotencyKey + "\",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"" + resourceId + "\",\"quantity\":" + quantity + "}");
        event.setStatus(EventStatus.DEAD_LETTER.name());
        event.setRetryCount(3);
        event.setMaxRetries(3);
        event.setLastFailureReason("Max retries exhausted during test setup");
        event.setLastAttemptAt(OffsetDateTime.now().minusSeconds(30));
        event.setUpdatedAt(OffsetDateTime.now());
        return processedEventRepository.save(event);
    }
}
