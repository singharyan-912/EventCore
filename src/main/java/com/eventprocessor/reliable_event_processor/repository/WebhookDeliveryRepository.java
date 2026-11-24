package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByTenantIdAndWebhookIdOrderByCreatedAtDesc(String tenantId, Long webhookId);

    List<WebhookDelivery> findByTenantIdAndEventId(String tenantId, String eventId);

    Optional<WebhookDelivery> findByTenantIdAndId(String tenantId, Long id);

    Optional<WebhookDelivery> findByTenantIdAndWebhookIdAndId(String tenantId, Long webhookId, Long id);

    Page<WebhookDelivery> findByTenantId(String tenantId, Pageable pageable);

    @Query(value = "SELECT * FROM webhook_deliveries " +
                   "WHERE status = 'RETRY_PENDING' AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP) " +
                   "ORDER BY next_retry_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<WebhookDelivery> findEligibleForRetryWithLock(@Param("limit") int limit);

    @Query(value = "SELECT * FROM webhook_deliveries " +
                   "WHERE status IN ('SUCCESS', 'EXHAUSTED', 'FAILED') AND delivered_at <= :cutoff " +
                   "ORDER BY delivered_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<WebhookDelivery> findEligibleForCleanupWithLock(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);

    long countByTenantIdAndStatus(String tenantId, String status);
}
