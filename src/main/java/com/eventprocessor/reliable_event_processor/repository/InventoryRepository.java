package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByResourceId(String resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.resourceId = :resourceId")
    Optional<Inventory> findByResourceIdForUpdate(@Param("resourceId") String resourceId);
}
