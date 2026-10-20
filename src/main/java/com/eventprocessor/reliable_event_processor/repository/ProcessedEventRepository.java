package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByIdempotencyKey(String idempotencyKey);
}
