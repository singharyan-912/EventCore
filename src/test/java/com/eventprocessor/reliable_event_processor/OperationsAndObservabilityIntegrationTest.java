package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.OperationalAuditLogRepository;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"inventory-events", "inventory-events-dlt"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "event-processor.outbox.enabled=true",
        "event-processor.consumer.enabled=false",
        "event-processor.worker.enabled=true",
        "event-processor.worker.poll-interval-ms=60000",
        "simulation.enabled=false",
        "management.endpoints.web.exposure.include=health,metrics,info",
        "management.endpoint.health.show-details=always"
})
class OperationsAndObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OperationalAuditLogRepository auditLogRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private static final String ADMIN_KEY = "admin-secret-key-12345";

    @BeforeEach
    void setUp() {
        inventoryRepository.findByResourceId("ops-item-1")
                .map(inv -> {
                    inv.setAvailableQuantity(100);
                    return inventoryRepository.save(inv);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory("ops-item-1", 100)));
    }

    private double getCounterValue(String name) {
        return Search.in(meterRegistry).name(name).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    @Test
    @DisplayName("1. Metrics increment on real successful and failed processing operations")
    void test1_metricsIncrementOnSuccessAndFailure() throws Exception {
        double attemptsBefore = getCounterValue("events.processing.attempts");
        double successBefore = getCounterValue("events.processing.success");
        double failureBefore = getCounterValue("events.processing.failure");

        // Success operation
        String keySuccess = "ops-metric-succ-" + UUID.randomUUID();
        EventRequest succRequest = new EventRequest(keySuccess, "INVENTORY_DEDUCT", "ops-item-1", 5);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(succRequest)))
                .andExpect(status().isOk());

        assertThat(getCounterValue("events.processing.attempts")).isEqualTo(attemptsBefore + 1.0);
        assertThat(getCounterValue("events.processing.success")).isEqualTo(successBefore + 1.0);

        // Permanent failure operation (exceeds inventory)
        String keyFail = "ops-metric-fail-" + UUID.randomUUID();
        EventRequest failRequest = new EventRequest(keyFail, "INVENTORY_DEDUCT", "ops-item-1", 9999);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(getCounterValue("events.processing.attempts")).isEqualTo(attemptsBefore + 2.0);
        assertThat(getCounterValue("events.processing.failure")).isGreaterThanOrEqualTo(failureBefore + 1.0);
    }

    @Test
    @DisplayName("2. Duplicate request does not increment success counter, but increments duplicate metric")
    void test2_duplicateRequestDoesNotIncrementSuccess() throws Exception {
        String key = "ops-dup-" + UUID.randomUUID();
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", "ops-item-1", 2);

        // First attempt: success
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        double successAfterFirst = getCounterValue("events.processing.success");
        double duplicateBefore = getCounterValue("events.processed.duplicate");

        // Duplicate replay
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        double successAfterDuplicate = getCounterValue("events.processing.success");
        double duplicateAfter = getCounterValue("events.processed.duplicate");

        // CRITICAL CHECK: Success counter did NOT increment on replay!
        assertThat(successAfterDuplicate).isEqualTo(successAfterFirst);
        // Duplicate metric incremented
        assertThat(duplicateAfter).isEqualTo(duplicateBefore + 1.0);
    }

    @Test
    @DisplayName("3. Health checks report UP for dependencies and preserve Phase 1 /api/v1/health contract")
    void test3_healthEndpoints() throws Exception {
        // Phase 1 health endpoint contract check
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("Reliable Event Processing Platform is running"));

        // Actuator health check
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.kafka.status").value("UP"))
                .andExpect(jsonPath("$.components.outbox.status").value("UP"))
                .andExpect(jsonPath("$.components.retryWorker.status").value("UP"));
    }

    @Test
    @DisplayName("4. Event inspection returns full details and outbox publication state")
    void test4_eventDetailInspection() throws Exception {
        String key = "ops-detail-" + UUID.randomUUID();
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", "ops-item-1", 4);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/operations/events/" + key)
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(key))
                .andExpect(jsonPath("$.eventType").value("INVENTORY_DEDUCT"))
                .andExpect(jsonPath("$.resourceId").value("ops-item-1"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.outbox").isMap())
                .andExpect(jsonPath("$.outbox.topic").value("inventory-events"))
                .andExpect(jsonPath("$.outbox.status").value(in(java.util.List.of("PENDING", "CLAIMED", "PUBLISHED"))));
    }

    @Test
    @DisplayName("5. Search events with pagination and filtering by status and resourceId")
    void test5_searchEventsWithFiltersAndPagination() throws Exception {
        String key = "ops-search-" + UUID.randomUUID();
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", "ops-item-1", 1);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Filter by resourceId and status
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("resourceId", "ops-item-1")
                        .param("status", "PROCESSED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].resourceId").value("ops-item-1"))
                .andExpect(jsonPath("$.content[0].status").value("PROCESSED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("6. Empty search results and missing single event are handled correctly")
    void test6_emptyResultsAndMissingEvent() throws Exception {
        // Missing single event returns 404
        mockMvc.perform(get("/api/v1/operations/events/non-existent-key-12345")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Event not found")));

        // Non-matching search query returns empty page (HTTP 200)
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("resourceId", "completely-non-existent-resource-99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Invalid date range from > to returns 400 Bad Request
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("from", "2026-12-31T23:59:59Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("must not be after")));
    }

    @Test
    @DisplayName("7. Event summary counts match persisted database state")
    void test7_eventSummaryMatchesDatabase() throws Exception {
        long dbTotal = processedEventRepository.count();

        mockMvc.perform(get("/api/v1/operations/events/summary")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value((int) dbTotal))
                .andExpect(jsonPath("$.countsByStatus").isMap());
    }

    @Test
    @DisplayName("8. Outbox summary reporting matches database records")
    void test8_outboxSummaryMatchesDatabase() throws Exception {
        long dbOutboxTotal = outboxEventRepository.count();

        mockMvc.perform(get("/api/v1/operations/outbox/summary")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutboxEvents").value((int) dbOutboxTotal))
                .andExpect(jsonPath("$.countsByStatus").isMap());
    }

    @Test
    @DisplayName("9. Audit records are created for both successful and failed administrative reprocessing")
    void test9_auditRecordsCreatedForAdminActions() throws Exception {
        // A: Create a dead-letter event in DB
        String dlKey = "ops-audit-dl-" + UUID.randomUUID();
        ProcessedEvent deadLetterEvent = new ProcessedEvent();
        deadLetterEvent.setIdempotencyKey(dlKey);
        deadLetterEvent.setEventType("INVENTORY_DEDUCT");
        deadLetterEvent.setResourceId("ops-item-1");
        deadLetterEvent.setRequestPayloadHash("dummy-hash");
        deadLetterEvent.setRequestPayload("{\"quantity\":2}");
        deadLetterEvent.setStatus(EventStatus.DEAD_LETTER.name());
        deadLetterEvent.setLastFailureReason("Permanent failure simulated");
        deadLetterEvent.setRetryCount(3);
        deadLetterEvent.setMaxRetries(3);
        deadLetterEvent.setUpdatedAt(OffsetDateTime.now());
        processedEventRepository.saveAndFlush(deadLetterEvent);

        // Reprocess successfully
        mockMvc.perform(post("/api/v1/admin/dead-letters/" + dlKey + "/reprocess")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"));

        // Verify successful audit entry
        mockMvc.perform(get("/api/v1/operations/audit-logs")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("targetIdentifier", dlKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].actionType").value("DEAD_LETTER_REPROCESS"))
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].targetIdentifier").value(dlKey));

        // B: Reprocess non-existent event -> Fails with 404
        String missingKey = "ops-audit-missing-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/dead-letters/" + missingKey + "/reprocess")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isNotFound());

        // Verify failure audit entry
        mockMvc.perform(get("/api/v1/operations/audit-logs")
                        .header("X-Admin-API-Key", ADMIN_KEY)
                        .param("targetIdentifier", missingKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].actionType").value("DEAD_LETTER_REPROCESS"))
                .andExpect(jsonPath("$.content[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$.content[0].failureReason").value(containsString("Event not found")));
    }

    @Test
    @DisplayName("10. Actuator /metrics and /api/v1/operations/metrics endpoints expose unified operational state")
    void test10_metricsEndpoints() throws Exception {
        // Standard Actuator metrics index
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItem("events.processing.attempts")))
                .andExpect(jsonPath("$.names", hasItem("events.processing.success")));

        // Specific metric details
        mockMvc.perform(get("/actuator/metrics/events.processing.success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("events.processing.success"));

        // Unified Operations metrics endpoint
        mockMvc.perform(get("/api/v1/operations/metrics")
                        .header("X-Admin-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inMemoryMetricsNotice").isNotEmpty())
                .andExpect(jsonPath("$.databaseEvents").isMap())
                .andExpect(jsonPath("$.databaseOutbox").isMap());
    }
}
