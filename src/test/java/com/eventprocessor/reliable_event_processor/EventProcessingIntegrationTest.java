package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "event-processor.outbox.enabled=false",
        "event-processor.consumer.enabled=false"
})
class EventProcessingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private Inventory createIsolatedInventory(String resourceId, int initialQuantity) {
        return inventoryRepository.findByResourceId(resourceId)
                .map(existing -> {
                    existing.setAvailableQuantity(initialQuantity);
                    return inventoryRepository.save(existing);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory(resourceId, initialQuantity)));
    }

    @Test
    @DisplayName("1. New event deducts inventory correctly")
    void test1_newEvent_deductsInventoryCorrectly() throws Exception {
        String resourceId = "test-res-deduct-1";
        createIsolatedInventory(resourceId, 10);
        String idempotencyKey = "key-test-1-" + System.currentTimeMillis();

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 2);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.eventType").value("INVENTORY_DEDUCT"))
                .andExpect(jsonPath("$.resourceId").value(resourceId))
                .andExpect(jsonPath("$.deductedQuantity").value(2))
                .andExpect(jsonPath("$.remainingQuantity").value(8))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("2. Repeating the same request does not deduct inventory twice (idempotent replay)")
    void test2_repeatingSameRequest_doesNotDeductTwice() throws Exception {
        String resourceId = "test-res-repeat-2";
        createIsolatedInventory(resourceId, 10);
        String idempotencyKey = "key-test-2-" + System.currentTimeMillis();

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 2);

        // First submission
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.remainingQuantity").value(8));

        // Replay submission with identical key and payload
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.remainingQuantity").value(8));

        // Inventory must still be 8, not 6
        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("3. Reusing the same key with different payload returns HTTP 409 Conflict")
    void test3_sameKey_differentPayload_returnsConflict() throws Exception {
        String resourceId = "test-res-conflict-3";
        createIsolatedInventory(resourceId, 10);
        String idempotencyKey = "key-test-3-" + System.currentTimeMillis();

        EventRequest originalRequest = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 2);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andExpect(status().isOk());

        // Same key, different quantity (4 instead of 2)
        EventRequest modifiedRequest = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 4);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifiedRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different request payload")));

        // Inventory must remain 8
        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("4. Different keys are processed as separate events")
    void test4_differentKeys_processedSeparately() throws Exception {
        String resourceId = "test-res-diff-keys-4";
        createIsolatedInventory(resourceId, 10);

        String keyA = "key-test-4A-" + System.currentTimeMillis();
        String keyB = "key-test-4B-" + System.currentTimeMillis();

        EventRequest requestA = new EventRequest(keyA, "INVENTORY_DEDUCT", resourceId, 2);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(8));

        EventRequest requestB = new EventRequest(keyB, "INVENTORY_DEDUCT", resourceId, 3);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(5));

        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("5. Invalid quantities are rejected with HTTP 400 Bad Request")
    void test5_invalidQuantities_rejectedWith400() throws Exception {
        // Zero quantity
        EventRequest zeroQty = new EventRequest("key-bad-0", "INVENTORY_DEDUCT", "product-101", 0);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroQty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Negative quantity
        EventRequest negativeQty = new EventRequest("key-bad-neg", "INVENTORY_DEDUCT", "product-101", -3);
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeQty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Blank idempotencyKey
        String blankKeyJson = "{\"idempotencyKey\":\" \",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"product-101\",\"quantity\":2}";
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankKeyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid event type
        String invalidTypeJson = "{\"idempotencyKey\":\"key-inv-type\",\"eventType\":\"UNKNOWN_TYPE\",\"resourceId\":\"product-101\",\"quantity\":2}";
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("6. Unknown inventory resources are handled correctly with HTTP 404")
    void test6_unknownResource_returnsNotFound() throws Exception {
        String unknownResource = "unknown-item-" + System.currentTimeMillis();
        String key = "key-unknown-" + System.currentTimeMillis();

        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", unknownResource, 2);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(unknownResource)));

        // Event should not be cached in processed_events
        assertThat(processedEventRepository.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    @DisplayName("7. Insufficient inventory does not result in a negative balance")
    void test7_insufficientInventory_doesNotResultInNegativeBalance() throws Exception {
        String resourceId = "test-res-insufficient-7";
        createIsolatedInventory(resourceId, 5);
        String key = "key-test-7-" + System.currentTimeMillis();

        // Request 10 units when only 5 are available
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", resourceId, 10);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient inventory")));

        // Inventory must remain strictly 5, never negative
        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);

        // Crucial retry policy: rejection must NOT be cached as processed event
        assertThat(processedEventRepository.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    @DisplayName("8. A failed transaction does not leave a partially applied business effect")
    void test8_failedTransaction_rollsBackCompletely() throws Exception {
        String resourceId = "test-res-rollback-8";
        createIsolatedInventory(resourceId, 3);
        String key = "key-test-8-" + System.currentTimeMillis();

        // Request 5 units when only 3 are available -> triggers rollback
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", resourceId, 5);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        // Verify no partial deduction
        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);

        // Verify no processed event record exists
        assertThat(processedEventRepository.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    @DisplayName("9. Concurrent duplicate submissions apply the business effect only once")
    void test9_concurrentDuplicateSubmissions_applyEffectOnlyOnce() throws Exception {
        String resourceId = "test-res-concurrent-9";
        createIsolatedInventory(resourceId, 10);
        String idempotencyKey = "key-concurrent-9-" + System.currentTimeMillis();

        EventRequest request = new EventRequest(idempotencyKey, "INVENTORY_DEDUCT", resourceId, 2);
        String jsonPayload = objectMapper.writeValueAsString(request);

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                // Wait for all threads to be ready to maximize race condition probability
                startLatch.await();
                return mockMvc.perform(post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                        .andReturn();
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads at once

        List<Integer> statusCodes = new ArrayList<>();
        int replayCount = 0;
        int freshCount = 0;

        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(10, TimeUnit.SECONDS);
            int status = result.getResponse().getStatus();
            statusCodes.add(status);
            assertThat(status).isEqualTo(200);

            String body = result.getResponse().getContentAsString();
            if (body.contains("\"idempotentReplay\":true")) {
                replayCount++;
            } else if (body.contains("\"idempotentReplay\":false")) {
                freshCount++;
            }
        }

        executor.shutdown();

        // Exactly one submission was freshly processed, the others were idempotent replays
        assertThat(freshCount).isEqualTo(1);
        assertThat(replayCount).isEqualTo(threadCount - 1);

        // Inventory must be exactly 8 (deducted 2 units exactly once!)
        Inventory inventory = inventoryRepository.findByResourceId(resourceId).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);

        // Exactly one processed event record stored
        assertThat(processedEventRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    }

    @Test
    @DisplayName("10. Existing health endpoint still works")
    void test10_healthEndpoint_stillWorks() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("Reliable Event Processing Platform is running"));
    }
}
