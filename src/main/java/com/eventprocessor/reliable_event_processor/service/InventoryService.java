package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.exception.InsufficientInventoryException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Deducts inventory atomically with pessimistic write locking.
     * Prevents race conditions and guarantees that inventory never becomes negative.
     */
    @Transactional
    public Inventory deductInventory(String resourceId, int quantity) {
        Inventory inventory = inventoryRepository.findByResourceIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory resource not found: " + resourceId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    "Insufficient inventory for resource '%s': requested %d, available %d"
                            .formatted(resourceId, quantity, inventory.getAvailableQuantity())
            );
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Inventory getInventory(String resourceId) {
        return inventoryRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory resource not found: " + resourceId));
    }
}
