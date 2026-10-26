package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
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
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    List<OutboxEvent> findAllByIdempotencyKey(String idempotencyKey);

    Page<OutboxEvent> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    /**
     * Atomically locks and returns pending or stale-claimed outbox events using SKIP LOCKED.
     * Prevents competing worker instances from claiming the same events.
     */
    @Query(value = "SELECT * FROM outbox_events " +
                   "WHERE status = 'PENDING' " +
                   "   OR (status = 'CLAIMED' AND updated_at <= :staleTime) " +
                   "ORDER BY id ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<OutboxEvent> findEligibleForPublishingWithLock(@Param("limit") int limit,
                                                       @Param("staleTime") OffsetDateTime staleTime);
}
