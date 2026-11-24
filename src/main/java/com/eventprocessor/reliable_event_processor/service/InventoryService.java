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
     * Deducts inventory atomically with pessimistic write locking for a specific tenant.
     * Prevents race conditions and guarantees that inventory never becomes negative.
     */
    @Transactional
    public Inventory deductInventory(String tenantId, String resourceId, int quantity) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId
                : com.eventprocessor.reliable_event_processor.security.TenantContext.getTenantId();

        Inventory inventory = inventoryRepository.findByTenantIdAndResourceIdForUpdate(effectiveTenant, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory resource not found: " + resourceId + " (tenant: " + effectiveTenant + ")"));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    "Insufficient inventory for resource '%s': requested %d, available %d"
                            .formatted(resourceId, quantity, inventory.getAvailableQuantity())
            );
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory deductInventory(String resourceId, int quantity) {
        return deductInventory(com.eventprocessor.reliable_event_processor.security.TenantContext.getTenantId(), resourceId, quantity);
    }

    @Transactional(readOnly = true)
    public Inventory getInventory(String tenantId, String resourceId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId
                : com.eventprocessor.reliable_event_processor.security.TenantContext.getTenantId();

        return inventoryRepository.findByTenantIdAndResourceId(effectiveTenant, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory resource not found: " + resourceId + " (tenant: " + effectiveTenant + ")"));
    }

    @Transactional(readOnly = true)
    public Inventory getInventory(String resourceId) {
        return getInventory(com.eventprocessor.reliable_event_processor.security.TenantContext.getTenantId(), resourceId);
    }
}
