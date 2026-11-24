package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.ExternalIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, Long> {

    List<ExternalIntegration> findByTenantId(String tenantId);

    List<ExternalIntegration> findByTenantIdAndStatus(String tenantId, String status);

    Optional<ExternalIntegration> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
