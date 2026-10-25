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

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    private String lastFailureReason;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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
        this.retryCount = 0;
        this.maxRetries = 3;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getRequestPayloadHash() {
        return requestPayloadHash;
    }

    public void setRequestPayloadHash(String requestPayloadHash) {
        this.requestPayloadHash = requestPayloadHash;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public void setResponseStatusCode(Integer responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public void setLastFailureReason(String lastFailureReason) {
        this.lastFailureReason = lastFailureReason;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
