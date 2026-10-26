package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import com.eventprocessor.reliable_event_processor.entity.OutboxStatus;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import com.eventprocessor.reliable_event_processor.service.OutboxPublisherService;
import com.eventprocessor.reliable_event_processor.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "event-processor.consumer.enabled=false"
})
@EmbeddedKafka(partitions = 3, topics = {"inventory-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setupInventory() {
        inventoryRepository.findByResourceId("outbox-prod-1")
                .map(inv -> {
                    inv.setAvailableQuantity(50);
                    return inventoryRepository.save(inv);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory("outbox-prod-1", 50)));
    }

    @Test
    @DisplayName("1. Accepted event atomically creates an outbox record in PENDING state")
    void test1_acceptedEvent_createsOutboxRecord() throws Exception {
        String idempotencyKey = "outbox-test-1-" + UUID.randomUUID();
        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", "outbox-prod-1", 5);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        OutboxEvent outbox = outboxEventRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        assertThat(outbox).isNotNull();
        assertThat(outbox.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(outbox.getEventType()).isEqualTo("INVENTORY_DEDUCT");
        assertThat(outbox.getPartitionKey()).isEqualTo("outbox-prod-1");
        assertThat(outbox.getTopic()).isEqualTo("inventory-events");
        assertThat(outbox.getStatus()).isIn(OutboxStatus.PENDING.name(), OutboxStatus.CLAIMED.name(), OutboxStatus.PUBLISHED.name());
        assertThat(outbox.getPayload()).contains("outbox-prod-1");
    }

    @Test
    @DisplayName("2. Idempotent replay does not create a duplicate outbox record")
    void test2_idempotentReplay_doesNotDuplicateOutboxRecord() throws Exception {
        String idempotencyKey = "outbox-test-2-" + UUID.randomUUID();
        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", "outbox-prod-1", 2);

        // First execution
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Idempotent duplicate replay
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        List<OutboxEvent> outboxList = outboxEventRepository.findAllByIdempotencyKey(idempotencyKey);
        assertThat(outboxList).hasSize(1);
    }

    @Test
    @DisplayName("3. Failed business transaction rolls back the outbox record")
    void test3_failedBusinessTransaction_rollsBackOutboxRecord() throws Exception {
        String idempotencyKey = "outbox-test-3-" + UUID.randomUUID();
        // Request quantity (9999) exceeds available inventory (50)
        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", "outbox-prod-1", 9999);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        var outboxOpt = outboxEventRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(outboxOpt).isEmpty();
    }

    @Test
    @DisplayName("4. Outbox publisher claims and successfully publishes event to Kafka, transitioning status to PUBLISHED")
    void test4_outboxPublisher_publishesToKafka() throws Exception {
        String idempotencyKey = "outbox-publish-4-" + UUID.randomUUID();

        // Create a pending outbox event directly inside a short transaction
        OutboxEvent created = transactionTemplate.execute(status ->
                outboxService.recordOutboxEvent(idempotencyKey, "INVENTORY_DEDUCT", "outbox-prod-1", "{\"test\":true}")
        );
        assertThat(created).isNotNull();

        // Claim batch
        List<OutboxEvent> claimed = outboxPublisherService.claimBatch(10);
        assertThat(claimed).anyMatch(e -> e.getId().equals(created.getId()));

        OutboxEvent toPublish = claimed.stream().filter(e -> e.getId().equals(created.getId())).findFirst().orElseThrow();
        outboxPublisherService.publishEvent(toPublish);
        outboxPublisherService.markPublished(toPublish.getId());

        OutboxEvent updated = outboxEventRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED.name());
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getLastError()).isNull();
    }

    @Test
    @DisplayName("5. Concurrent outbox publishers using SKIP LOCKED never claim the same event")
    void test5_concurrentPublishers_skipLockedGuarantee() throws Exception {
        int eventCount = 10;
        List<Long> createdIds = new ArrayList<>();

        for (int i = 0; i < eventCount; i++) {
            String key = "outbox-skip-5-" + UUID.randomUUID();
            OutboxEvent ev = transactionTemplate.execute(status ->
                    outboxService.recordOutboxEvent(key, "INVENTORY_DEDUCT", "outbox-prod-1", "{\"index\":" + System.currentTimeMillis() + "}")
            );
            createdIds.add(ev.getId());
        }

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Long> claimedIds = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    List<OutboxEvent> batch = outboxPublisherService.claimBatch(5);
                    for (OutboxEvent e : batch) {
                        claimedIds.add(e.getId());
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Concurrency guarantee: Every claimed event ID must be unique (no duplicate claims)
        long distinctClaimed = claimedIds.stream().distinct().count();
        assertThat(claimedIds.size()).isEqualTo((int) distinctClaimed);
    }
}
