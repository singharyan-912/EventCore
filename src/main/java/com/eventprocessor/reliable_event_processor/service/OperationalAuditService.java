package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.entity.OperationalAuditLog;
import com.eventprocessor.reliable_event_processor.repository.OperationalAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalAuditService {

    private static final Logger log = LoggerFactory.getLogger(OperationalAuditService.class);

    private final OperationalAuditLogRepository auditLogRepository;

    public OperationalAuditService(OperationalAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists an audit entry in an independent transaction (REQUIRES_NEW).
     * Ensures audit entries are retained even if the encompassing business operation fails or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperationalAuditLog recordAuditLog(String actionType,
                                             String targetIdentifier,
                                             String outcome,
                                             String failureReason,
                                             String principal,
                                             String details) {
        OperationalAuditLog auditLog = new OperationalAuditLog(
                actionType,
                targetIdentifier,
                outcome,
                failureReason,
                principal != null ? principal : "SYSTEM",
                details
        );
        OperationalAuditLog saved = auditLogRepository.save(auditLog);
        log.info("Operational audit recorded [id={}, action={}, target={}, outcome={}, principal={}]",
                saved.getId(), actionType, targetIdentifier, outcome, auditLog.getPrincipal());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OperationalAuditLog> queryAuditLogs(String actionType,
                                                    String targetIdentifier,
                                                    String outcome,
                                                    int page,
                                                    int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp", "id"));

        Specification<OperationalAuditLog> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (actionType != null && !actionType.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("actionType"), actionType.trim()));
            }
            if (targetIdentifier != null && !targetIdentifier.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("targetIdentifier"), targetIdentifier.trim()));
            }
            if (outcome != null && !outcome.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("outcome"), outcome.trim().toUpperCase()));
            }
            return predicates;
        };

        return auditLogRepository.findAll(spec, pageable);
    }
}
