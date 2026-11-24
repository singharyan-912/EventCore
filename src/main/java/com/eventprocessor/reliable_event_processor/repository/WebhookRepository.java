package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByTenantId(String tenantId);

    List<Webhook> findByTenantIdAndStatus(String tenantId, String status);

    Optional<Webhook> findByTenantIdAndId(String tenantId, Long id);
}
