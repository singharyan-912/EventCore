package com.eventprocessor.reliable_event_processor.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "operational_audit_logs")
public class OperationalAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_identifier", nullable = false, length = 255)
    private String targetIdentifier;

    @Column(name = "outcome", nullable = false, length = 50)
    private String outcome;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "principal", nullable = false, length = 100)
    private String principal = "SYSTEM";

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp", insertable = false, updatable = false)
    private OffsetDateTime timestamp;

    public OperationalAuditLog() {
    }

    public OperationalAuditLog(String actionType, String targetIdentifier, String outcome,
                               String failureReason, String principal, String details) {
        this.actionType = actionType;
        this.targetIdentifier = targetIdentifier;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.principal = principal != null ? principal : "SYSTEM";
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getTargetIdentifier() {
        return targetIdentifier;
    }

    public void setTargetIdentifier(String targetIdentifier) {
        this.targetIdentifier = targetIdentifier;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
