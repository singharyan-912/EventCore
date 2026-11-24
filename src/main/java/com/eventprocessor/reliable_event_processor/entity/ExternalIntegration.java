package com.eventprocessor.reliable_event_processor.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "external_integrations")
public class ExternalIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "integration_type", nullable = false, length = 50)
    private String integrationType = "HTTP_REST";

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "supported_operation_types", nullable = false, length = 255)
    private String supportedOperationTypes;

    @Column(name = "auth_type", nullable = false, length = 30)
    private String authType = "NONE";

    @Column(name = "auth_header_name", length = 100)
    private String authHeaderName;

    @Column(name = "encrypted_secret", columnDefinition = "TEXT")
    private String encryptedSecret;

    @Column(name = "secret_prefix", length = 10)
    private String secretPrefix;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 5000;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_connection_at")
    private OffsetDateTime lastConnectionAt;

    @Column(name = "last_connection_status", length = 50)
    private String lastConnectionStatus;

    @Column(name = "last_connection_error", columnDefinition = "TEXT")
    private String lastConnectionError;

    public ExternalIntegration() {
    }

    public ExternalIntegration(String tenantId, String name, String integrationType, String baseUrl,
                               String supportedOperationTypes, String authType, String authHeaderName,
                               String encryptedSecret, String secretPrefix, Integer timeoutMs) {
        this.tenantId = tenantId;
        this.name = name;
        this.integrationType = integrationType != null ? integrationType : "HTTP_REST";
        this.baseUrl = baseUrl;
        this.supportedOperationTypes = supportedOperationTypes;
        this.authType = authType != null ? authType : "NONE";
        this.authHeaderName = authHeaderName;
        this.encryptedSecret = encryptedSecret;
        this.secretPrefix = secretPrefix;
        this.timeoutMs = timeoutMs != null ? timeoutMs : 5000;
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

    public String getIntegrationType() {
        return integrationType;
    }

    public void setIntegrationType(String integrationType) {
        this.integrationType = integrationType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getSupportedOperationTypes() {
        return supportedOperationTypes;
    }

    public void setSupportedOperationTypes(String supportedOperationTypes) {
        this.supportedOperationTypes = supportedOperationTypes;
    }

    public boolean supportsOperation(String operationType) {
        if (supportedOperationTypes == null || supportedOperationTypes.isBlank() || operationType == null) {
            return false;
        }
        if ("*".equals(supportedOperationTypes.trim())) {
            return true;
        }
        for (String op : supportedOperationTypes.split(",")) {
            if (op.trim().equalsIgnoreCase(operationType.trim())) {
                return true;
            }
        }
        return false;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
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

    public OffsetDateTime getLastConnectionAt() {
        return lastConnectionAt;
    }

    public void setLastConnectionAt(OffsetDateTime lastConnectionAt) {
        this.lastConnectionAt = lastConnectionAt;
    }

    public String getLastConnectionStatus() {
        return lastConnectionStatus;
    }

    public void setLastConnectionStatus(String lastConnectionStatus) {
        this.lastConnectionStatus = lastConnectionStatus;
    }

    public String getLastConnectionError() {
        return lastConnectionError;
    }

    public void setLastConnectionError(String lastConnectionError) {
        this.lastConnectionError = lastConnectionError;
    }
}
