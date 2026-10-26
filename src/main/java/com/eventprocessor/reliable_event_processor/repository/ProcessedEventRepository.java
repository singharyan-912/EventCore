package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long>, JpaSpecificationExecutor<ProcessedEvent> {

    Optional<ProcessedEvent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ProcessedEvent e WHERE e.idempotencyKey = :idempotencyKey")
    Optional<ProcessedEvent> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Atomically selects and locks eligible RETRY_PENDING events whose scheduled retry time
     * has arrived, skipping rows already claimed by another worker instance (SKIP LOCKED).
     */
    @Query(value = "SELECT * FROM processed_events " +
                   "WHERE status = 'RETRY_PENDING' AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP) " +
                   "ORDER BY next_retry_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<ProcessedEvent> findEligibleForRetryWithLock(@Param("limit") int limit);

    Page<ProcessedEvent> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT e.status, COUNT(e) FROM ProcessedEvent e GROUP BY e.status")
    List<Object[]> countGroupedByStatus();
}
