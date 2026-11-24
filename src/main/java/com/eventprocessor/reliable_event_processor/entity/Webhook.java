package com.eventprocessor.reliable_event_processor.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "webhooks")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "destination_url", nullable = false, length = 500)
    private String destinationUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "subscribed_events", nullable = false, length = 255)
    private String subscribedEvents;

    @Column(name = "encrypted_signing_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSigningSecret;

    @Column(name = "secret_prefix", length = 10)
    private String secretPrefix;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_delivery_at")
    private OffsetDateTime lastDeliveryAt;

    @Column(name = "last_delivery_status", length = 50)
    private String lastDeliveryStatus;

    public Webhook() {
    }

    public Webhook(String tenantId, String name, String destinationUrl,
                   String subscribedEvents, String encryptedSigningSecret, String secretPrefix) {
        this.tenantId = tenantId;
        this.name = name;
        this.destinationUrl = destinationUrl;
        this.subscribedEvents = subscribedEvents;
        this.encryptedSigningSecret = encryptedSigningSecret;
        this.secretPrefix = secretPrefix;
        this.status = "ACTIVE";
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public void setDestinationUrl(String destinationUrl) {
        this.destinationUrl = destinationUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public String getEncryptedSigningSecret() {
        return encryptedSigningSecret;
    }

    public void setEncryptedSigningSecret(String encryptedSigningSecret) {
        this.encryptedSigningSecret = encryptedSigningSecret;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
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

    public OffsetDateTime getLastDeliveryAt() {
        return lastDeliveryAt;
    }

    public void setLastDeliveryAt(OffsetDateTime lastDeliveryAt) {
        this.lastDeliveryAt = lastDeliveryAt;
    }

    public String getLastDeliveryStatus() {
        return lastDeliveryStatus;
    }

    public void setLastDeliveryStatus(String lastDeliveryStatus) {
        if (lastDeliveryStatus != null && lastDeliveryStatus.length() > 50) {
            this.lastDeliveryStatus = lastDeliveryStatus.substring(0, 50);
        } else {
            this.lastDeliveryStatus = lastDeliveryStatus;
        }
    }
}
