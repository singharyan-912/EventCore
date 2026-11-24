package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.entity.Tenant;
import com.eventprocessor.reliable_event_processor.entity.TenantStatus;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    public static final String DEFAULT_TENANT_ID = "DEFAULT_BUSINESS";

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Tenant getTenant(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional(readOnly = true)
    public boolean isTenantActive(String tenantId) {
        return tenantRepository.existsByTenantIdAndStatus(tenantId, TenantStatus.ACTIVE);
    }

    @Transactional
    public Tenant createTenant(String tenantId, String businessName) {
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("Tenant already exists: " + tenantId);
        }
        Tenant tenant = new Tenant(tenantId, businessName, TenantStatus.ACTIVE);
        return tenantRepository.save(tenant);
    }
}
