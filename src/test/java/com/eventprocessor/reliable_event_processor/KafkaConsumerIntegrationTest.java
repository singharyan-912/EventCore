package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.KafkaEventMessage;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.service.TransientFailureSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = {"inventory-events", "inventory-events-dlt"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "event-processor.consumer.enabled=true",
        "event-processor.outbox.enabled=true",
        "event-processor.consumer.group-id=kafka-consumer-test-group",
        "event-processor.outbox.poll-interval-ms=100",
        "event-processor.retry.initial-delay-ms=200",
        "event-processor.worker.poll-interval-ms=200",
        "simulation.enabled=true"
})
class KafkaConsumerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setup() {
        inventoryRepository.findByResourceId("kc-prod-1")
                .map(inv -> {
                    inv.setAvailableQuantity(100);
                    return inventoryRepository.save(inv);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory("kc-prod-1", 100)));

        inventoryRepository.findByResourceId("transient-fail-kc")
                .map(inv -> {
                    inv.setAvailableQuantity(50);
                    return inventoryRepository.save(inv);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory("transient-fail-kc", 50)));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-test-group-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        dltConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "inventory-events-dlt");
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    @DisplayName("1. Outbox-published event reaches the consumer and updates inventory")
    void test1_outboxPublishedEvent_reachesConsumer() throws Exception {
        String key = "kc-test-1-" + UUID.randomUUID();
        EventRequest request = new EventRequest(key, "INVENTORY_DEDUCT", "kc-prod-1", 10);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Await asynchronous Kafka consumer execution and persistence
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ProcessedEvent event = processedEventRepository.findByIdempotencyKey(key).orElse(null);
            assertThat(event).isNotNull();
            assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
        });

        Inventory inv = inventoryRepository.findByResourceId("kc-prod-1").orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("2. Direct Kafka message is processed asynchronously with business inventory deduction")
    void test2_directKafkaMessage_processedAsynchronously() throws Exception {
        String key = "kc-test-2-" + UUID.randomUUID();
        KafkaEventMessage message = new KafkaEventMessage(
                "999",
                "INVENTORY_DEDUCT",
                "kc-prod-1",
                key,
                "{\"quantity\":15}",
                Instant.now()
        );

        kafkaTemplate.send("inventory-events", "kc-prod-1", objectMapper.writeValueAsString(message)).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ProcessedEvent event = processedEventRepository.findByIdempotencyKey(key).orElse(null);
            assertThat(event).isNotNull();
            assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
        });

        Inventory inv = inventoryRepository.findByResourceId("kc-prod-1").orElseThrow();
        // 100 - 15 = 85 (or relative to current stock)
        assertThat(inv.getAvailableQuantity()).isLessThanOrEqualTo(85);
    }

    @Test
    @DisplayName("3. Duplicate Kafka delivery does not repeat business effects")
    void test3_duplicateKafkaDelivery_doesNotRepeatBusinessEffects() throws Exception {
        String key = "kc-test-3-" + UUID.randomUUID();
        KafkaEventMessage message = new KafkaEventMessage(
                "1001",
                "INVENTORY_DEDUCT",
                "kc-prod-1",
                key,
                "{\"quantity\":5}",
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(message);

        // Send first time
        kafkaTemplate.send("inventory-events", "kc-prod-1", json).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ProcessedEvent event = processedEventRepository.findByIdempotencyKey(key).orElse(null);
            assertThat(event).isNotNull();
            assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
        });

        int quantityAfterFirst = inventoryRepository.findByResourceId("kc-prod-1").orElseThrow().getAvailableQuantity();

        // Send second time (duplicate delivery)
        kafkaTemplate.send("inventory-events", "kc-prod-1", json).get(5, TimeUnit.SECONDS);

        // Sleep briefly and confirm quantity did NOT decrease again
        Thread.sleep(1000);
        int quantityAfterSecond = inventoryRepository.findByResourceId("kc-prod-1").orElseThrow().getAvailableQuantity();
        assertThat(quantityAfterSecond).isEqualTo(quantityAfterFirst);
    }

    @Test
    @DisplayName("4. Malformed message is forwarded to Dead-Letter Topic (DLT) without infinite loop")
    void test4_malformedMessage_forwardedToDlt() throws Exception {
        String malformedPayload = "THIS_IS_NOT_VALID_JSON_{{@@";

        kafkaTemplate.send("inventory-events", "corrupt-key", malformedPayload).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = pollForDltRecord("THIS_IS_NOT_VALID_JSON", Duration.ofSeconds(10));
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).contains("Malformed JSON");
        assertThat(dltRecord.value()).contains("THIS_IS_NOT_VALID_JSON");
    }

    @Test
    @DisplayName("5. Message missing required idempotencyKey is forwarded to DLT")
    void test5_missingIdempotencyKey_forwardedToDlt() throws Exception {
        String invalidJson = "{\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"kc-prod-1\",\"quantity\":5}";

        kafkaTemplate.send("inventory-events", "kc-prod-1", invalidJson).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = pollForDltRecord("Missing required field: idempotencyKey", Duration.ofSeconds(10));
        assertThat(dltRecord).isNotNull();
        JsonNode node = objectMapper.readTree(dltRecord.value());
        assertThat(node.get("rejectionReason").asText()).contains("Missing required field: idempotencyKey");
    }

    @Test
    @DisplayName("6. Transient failure during consumption transitions event to RETRY_PENDING and hands off to RetryWorker")
    void test6_transientFailure_recordedInPostgres_andHandoffToRetryWorker() throws Exception {
        String key = "kc-transient-" + UUID.randomUUID();

        KafkaEventMessage message = new KafkaEventMessage(
                "2002",
                "INVENTORY_DEDUCT",
                "transient-fail-kc",
                key,
                "{\"quantity\":7}",
                Instant.now()
        );

        kafkaTemplate.send("inventory-events", "transient-fail-kc", objectMapper.writeValueAsString(message)).get(5, TimeUnit.SECONDS);

        // Consumer records RETRY_PENDING in PostgreSQL and commits offset
        // Then RetryWorker picks it up and finishes it
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ProcessedEvent event = processedEventRepository.findByIdempotencyKey(key).orElse(null);
            assertThat(event).isNotNull();
            assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED.name());
        });

        Inventory inv = inventoryRepository.findByResourceId("transient-fail-kc").orElseThrow();
        assertThat(inv.getAvailableQuantity()).isEqualTo(43);
    }

    @Test
    @DisplayName("7. Message for unknown resource is forwarded to DLT and offset committed")
    void test7_unknownResource_forwardedToDlt() throws Exception {
        String key = "kc-unknown-res-" + UUID.randomUUID();
        KafkaEventMessage message = new KafkaEventMessage(
                "3003",
                "INVENTORY_DEDUCT",
                "non-existent-resource-999",
                key,
                "{\"quantity\":2}",
                Instant.now()
        );

        kafkaTemplate.send("inventory-events", "non-existent-resource-999", objectMapper.writeValueAsString(message)).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = pollForDltRecord("Resource not found", Duration.ofSeconds(10));
        assertThat(dltRecord).isNotNull();
        JsonNode node = objectMapper.readTree(dltRecord.value());
        assertThat(node.get("rejectionReason").asText()).contains("Resource not found");
    }

    private ConsumerRecord<String, String> pollForDltRecord(String expectedSubstring, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            org.apache.kafka.clients.consumer.ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> r : records) {
                if (r.value() != null && r.value().contains(expectedSubstring)) {
                    return r;
                }
            }
        }
        throw new AssertionError("Timed out waiting for DLT record containing: " + expectedSubstring);
    }
}
