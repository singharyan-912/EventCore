package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.ConsumerProperties;
import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.IdempotencyConflictException;
import com.eventprocessor.reliable_event_processor.exception.InsufficientInventoryException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Consumer for reliable asynchronous event processing.
 *
 * IDEMPOTENCY & CONCURRENCY:
 * Checks PostgreSQL (the authoritative source of truth) for existing event state.
 * Successfully processed events are recognized and acknowledged without repeating business effects.
 *
 * RETRY COORDINATION:
 * Initial async delivery is handled via Kafka. If transient failures occur,
 * the event transitions to RETRY_PENDING in PostgreSQL and offset is committed,
 * transferring execution authority to the existing scheduled RetryWorker.
 *
 * RECOVERY & DEAD-LETTER:
 * Malformed or unprocessable messages are forwarded to a Dead-Letter Topic (DLT)
 * and acknowledged to prevent infinite redelivery loops.
 */
@Component
public class EventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventKafkaConsumer.class);

    private final EventProcessingService eventProcessingService;
    private final ProcessedEventRepository processedEventRepository;
    private final ConsumerProperties consumerProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventMetricsService metricsService;

    public EventKafkaConsumer(EventProcessingService eventProcessingService,
                              ProcessedEventRepository processedEventRepository,
                              ConsumerProperties consumerProperties,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              EventMetricsService metricsService) {
        this.eventProcessingService = eventProcessingService;
        this.processedEventRepository = processedEventRepository;
        this.consumerProperties = consumerProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "${event-processor.outbox.topic:inventory-events}",
            groupId = "${event-processor.consumer.group-id:event-processor-group}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${event-processor.consumer.enabled:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        int partition = record.partition();
        long offset = record.offset();
        String value = record.value();

        log.info("Received Kafka message [topic={}, partition={}, offset={}, key={}]",
                topic, partition, offset, record.key());

        // 1. Deserialize and validate message
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(value);
        } catch (Exception ex) {
            log.error("Malformed JSON [topic={}, partition={}, offset={}]. Forwarding to DLT: {}",
                    topic, partition, offset, ex.getMessage());
            metricsService.recordConsumerFailure(topic, "MALFORMED_JSON");
            forwardToDlt(record, "Malformed JSON: " + ex.getMessage());
            ack.acknowledge();
            return;
        }

        if (jsonNode == null || !jsonNode.isObject()) {
            log.error("Invalid non-object JSON [topic={}, partition={}, offset={}]. Forwarding to DLT",
                    topic, partition, offset);
            metricsService.recordConsumerFailure(topic, "INVALID_STRUCTURE");
            forwardToDlt(record, "Invalid JSON structure (not an object)");
            ack.acknowledge();
            return;
        }

        String idempotencyKey = extractText(jsonNode, "idempotencyKey");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.error("Message missing required 'idempotencyKey' [topic={}, partition={}, offset={}]. Forwarding to DLT",
                    topic, partition, offset);
            metricsService.recordConsumerFailure(topic, "MISSING_IDEMPOTENCY_KEY");
            forwardToDlt(record, "Missing required field: idempotencyKey");
            ack.acknowledge();
            return;
        }

        String eventType = extractText(jsonNode, "eventType");
        if (eventType == null || eventType.isBlank()) {
            eventType = "INVENTORY_DEDUCT";
        }

        String resourceId = extractText(jsonNode, "resourceId");
        if (resourceId == null || resourceId.isBlank()) {
            resourceId = record.key() != null ? record.key() : "default-resource";
        }

        int quantity = extractQuantity(jsonNode);

        // 2. Query PostgreSQL to verify existing state (Authoritative Source of Truth)
        Optional<ProcessedEvent> existingOpt = processedEventRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            ProcessedEvent existing = existingOpt.get();
            String status = existing.getStatus();

            metricsService.recordConsumerDuplicate(topic);

            if (EventStatus.PROCESSED.name().equals(status)) {
                log.info("Duplicate delivery for event [idempotency_key={}, status=PROCESSED]. Acknowledged without repeating business effects.",
                        idempotencyKey);
                ack.acknowledge();
                return;
            } else if (EventStatus.DEAD_LETTER.name().equals(status)) {
                log.warn("Event [idempotency_key={}, status=DEAD_LETTER, reason={}]. Acknowledged without repeating business effects.",
                        idempotencyKey, existing.getLastFailureReason());
                ack.acknowledge();
                return;
            } else if (EventStatus.RETRY_PENDING.name().equals(status)) {
                log.info("Event [idempotency_key={}, status=RETRY_PENDING, attempts={}/{}]. Acknowledged; scheduled retry worker is authoritative.",
                        idempotencyKey, existing.getRetryCount(), existing.getMaxRetries());
                ack.acknowledge();
                return;
            } else if (EventStatus.PROCESSING.name().equals(status)) {
                log.info("Event [idempotency_key={}, status=PROCESSING]. Acknowledged.", idempotencyKey);
                ack.acknowledge();
                return;
            }
        }

        // 3. Invoke existing business processing engine
        EventRequest eventRequest = new EventRequest(idempotencyKey, eventType, resourceId, quantity);
        try {
            EventResponse response = eventProcessingService.processEvent(eventRequest);
            metricsService.recordConsumerSuccess(topic);
            log.info("Event processed asynchronously via Kafka [idempotency_key={}, status={}, remaining_quantity={}]. Committing offset.",
                    idempotencyKey, response.status(), response.remainingQuantity());
            // Event is either PROCESSED or RETRY_PENDING (durably saved in PostgreSQL)
            ack.acknowledge();
        } catch (InsufficientInventoryException ex) {
            log.warn("Permanent business failure via Kafka [idempotency_key={}]: Insufficient inventory. Forwarding to DLT and committing offset.",
                    idempotencyKey);
            metricsService.recordConsumerFailure(topic, "INSUFFICIENT_INVENTORY");
            forwardToDlt(record, "Insufficient inventory: " + ex.getMessage());
            ack.acknowledge();
        } catch (ResourceNotFoundException ex) {
            log.warn("Permanent failure via Kafka [idempotency_key={}]: {}. Forwarding to DLT and committing offset.",
                    idempotencyKey, ex.getMessage());
            metricsService.recordConsumerFailure(topic, "RESOURCE_NOT_FOUND");
            forwardToDlt(record, "Resource not found: " + ex.getMessage());
            ack.acknowledge();
        } catch (IdempotencyConflictException ex) {
            log.warn("Idempotency conflict via Kafka [idempotency_key={}]: {}. Forwarding to DLT and committing offset.",
                    idempotencyKey, ex.getMessage());
            metricsService.recordConsumerFailure(topic, "IDEMPOTENCY_CONFLICT");
            forwardToDlt(record, "Idempotency conflict: " + ex.getMessage());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Infrastructure/database failure [idempotency_key={}, topic={}, partition={}, offset={}]: {}. Offset will NOT be committed.",
                    idempotencyKey, topic, partition, offset, ex.getMessage(), ex);
            metricsService.recordConsumerFailure(topic, "INFRASTRUCTURE_FAILURE");
            throw ex; // Triggers container retry until database/infrastructure recovers
        }
    }

    private void forwardToDlt(ConsumerRecord<String, String> record, String reason) {
        try {
            ObjectNode dltMessage = objectMapper.createObjectNode();
            dltMessage.put("originalTopic", record.topic());
            dltMessage.put("originalPartition", record.partition());
            dltMessage.put("originalOffset", record.offset());
            dltMessage.put("key", record.key());
            dltMessage.put("value", record.value());
            dltMessage.put("rejectionReason", reason);
            dltMessage.put("rejectedAt", Instant.now().toString());

            kafkaTemplate.send(consumerProperties.getDltTopic(), record.key(), objectMapper.writeValueAsString(dltMessage))
                    .get(3, TimeUnit.SECONDS);
            log.warn("Forwarded unprocessable message [topic={}, partition={}, offset={}] to DLT '{}'",
                    record.topic(), record.partition(), record.offset(), consumerProperties.getDltTopic());
        } catch (Exception e) {
            log.error("Failed to forward unprocessable message to DLT: {}", e.getMessage(), e);
        }
    }

    private String extractText(JsonNode root, String field) {
        if (root.has(field) && !root.get(field).isNull()) {
            return root.get(field).asText();
        }
        if (root.has("payload")) {
            JsonNode payloadNode = root.get("payload");
            if (payloadNode.isObject() && payloadNode.has(field) && !payloadNode.get(field).isNull()) {
                return payloadNode.get(field).asText();
            }
            if (payloadNode.isTextual()) {
                try {
                    JsonNode nested = objectMapper.readTree(payloadNode.asText());
                    if (nested.has(field) && !nested.get(field).isNull()) {
                        return nested.get(field).asText();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private int extractQuantity(JsonNode root) {
        if (root.has("quantity") && root.get("quantity").isNumber()) {
            return root.get("quantity").asInt();
        }
        if (root.has("deductedQuantity") && root.get("deductedQuantity").isNumber()) {
            return root.get("deductedQuantity").asInt();
        }
        if (root.has("payload")) {
            JsonNode payloadNode = root.get("payload");
            if (payloadNode.isObject()) {
                if (payloadNode.has("quantity") && payloadNode.get("quantity").isNumber()) {
                    return payloadNode.get("quantity").asInt();
                }
                if (payloadNode.has("deductedQuantity") && payloadNode.get("deductedQuantity").isNumber()) {
                    return payloadNode.get("deductedQuantity").asInt();
                }
            }
            if (payloadNode.isTextual()) {
                try {
                    JsonNode nested = objectMapper.readTree(payloadNode.asText());
                    if (nested.has("quantity") && nested.get("quantity").isNumber()) {
                        return nested.get("quantity").asInt();
                    }
                    if (nested.has("deductedQuantity") && nested.get("deductedQuantity").isNumber()) {
                        return nested.get("deductedQuantity").asInt();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return 1;
    }
}
