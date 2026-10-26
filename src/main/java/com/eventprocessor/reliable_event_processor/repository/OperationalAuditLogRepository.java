package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.OperationalAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationalAuditLogRepository extends JpaRepository<OperationalAuditLog, Long>, JpaSpecificationExecutor<OperationalAuditLog> {

    Page<OperationalAuditLog> findByTargetIdentifier(String targetIdentifier, Pageable pageable);

    Page<OperationalAuditLog> findByActionType(String actionType, Pageable pageable);

    List<OperationalAuditLog> findByTargetIdentifierOrderByTimestampDesc(String targetIdentifier);
}
