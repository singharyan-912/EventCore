package com.eventprocessor.reliable_event_processor.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "request_payload_hash", nullable = false, length = 64)
    private String requestPayloadHash;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "response_payload", nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "response_status_code", nullable = false)
    private Integer responseStatusCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String idempotencyKey, String eventType, String resourceId,
                          String requestPayloadHash, String status,
                          String responsePayload, Integer responseStatusCode) {
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.resourceId = resourceId;
        this.requestPayloadHash = requestPayloadHash;
        this.status = status;
        this.responsePayload = responsePayload;
        this.responseStatusCode = responseStatusCode;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getRequestPayloadHash() {
        return requestPayloadHash;
    }

    public String getStatus() {
        return status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
