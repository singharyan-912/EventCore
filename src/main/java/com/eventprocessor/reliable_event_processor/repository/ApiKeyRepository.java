package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.ApiKey;
import com.eventprocessor.reliable_event_processor.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByTenantId(String tenantId);

    List<ApiKey> findByTenantIdAndStatus(String tenantId, ApiKeyStatus status);

    Optional<ApiKey> findByIdAndTenantId(Long id, String tenantId);

    List<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, ApiKeyStatus status);
}
