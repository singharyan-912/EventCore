package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.BusinessRegistrationRequest;
import com.eventprocessor.reliable_event_processor.dto.BusinessRegistrationResponse;
import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.Role;
import com.eventprocessor.reliable_event_processor.entity.Tenant;
import com.eventprocessor.reliable_event_processor.entity.TenantStatus;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import com.eventprocessor.reliable_event_processor.repository.TenantRepository;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class BusinessRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(BusinessRegistrationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationalAuditService auditService;

    public BusinessRegistrationService(TenantRepository tenantRepository,
                                       UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       OperationalAuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public BusinessRegistrationResponse registerBusiness(BusinessRegistrationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Email uniqueness check
        if (userRepository.existsByEmail(normalizedEmail)) {
            auditService.recordAuditLog(
                    "DEFAULT_BUSINESS",
                    "BUSINESS_REGISTER",
                    normalizedEmail,
                    "FAILED",
                    "Email already registered",
                    "ANONYMOUS",
                    "Attempted registration with existing email: " + normalizedEmail
            );
            throw new IllegalArgumentException("Email address is already registered: " + normalizedEmail);
        }

        // 2. Derive unique Tenant ID
        String tenantId = generateUniqueTenantId(request.businessName());

        // 3. Create Tenant
        Tenant tenant = new Tenant(tenantId, request.businessName().trim(), TenantStatus.ACTIVE);
        Tenant savedTenant = tenantRepository.save(tenant);

        // 4. Derive unique Username for the registering owner
        String username = generateUniqueUsername(request.ownerName(), normalizedEmail);

        // 5. Create registering user with initial OPERATOR role (never platform ADMIN)
        AppUser owner = new AppUser(
                savedTenant.getTenantId(),
                username,
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                Role.OPERATOR,
                UserStatus.ACTIVE
        );
        AppUser savedUser = userRepository.save(owner);

        // 6. Record audit log under platform default tenant with new tenant as target
        auditService.recordAuditLog(
                "DEFAULT_BUSINESS",
                "BUSINESS_REGISTER",
                savedTenant.getTenantId(),
                "SUCCESS",
                null,
                savedUser.getUsername(),
                "Business registered successfully with owner: " + savedUser.getUsername()
        );

        log.info("Business registered successfully [tenantId={}, businessName={}, owner={}]",
                savedTenant.getTenantId(), savedTenant.getBusinessName(), savedUser.getUsername());

        return new BusinessRegistrationResponse(
                savedTenant.getTenantId(),
                savedTenant.getBusinessName(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedTenant.getStatus().name(),
                savedTenant.getCreatedAt() != null ? savedTenant.getCreatedAt() : OffsetDateTime.now(),
                "Business and initial owner account provisioned successfully"
        );
    }

    private String generateUniqueTenantId(String businessName) {
        String baseSlug = businessName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isBlank()) {
            baseSlug = "biz";
        }
        if (baseSlug.length() > 30) {
            baseSlug = baseSlug.substring(0, 30);
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = String.format("%04x", RANDOM.nextInt(0x10000));
            String candidate = "biz-" + baseSlug + "-" + suffix;
            if (candidate.length() > 50) {
                candidate = candidate.substring(0, 50);
            }
            if (!tenantRepository.existsByTenantId(candidate)) {
                return candidate;
            }
        }

        // Fallback with timestamp
        return "biz-" + System.currentTimeMillis();
    }

    private String generateUniqueUsername(String ownerName, String email) {
        String base = ownerName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (base.isBlank()) {
            base = email.split("@")[0].replaceAll("[^a-z0-9]", "_");
        }
        if (base.isBlank()) {
            base = "owner";
        }
        if (base.length() > 35) {
            base = base.substring(0, 35);
        }

        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        for (int i = 1; i <= 100; i++) {
            String candidate = base + "_" + String.format("%03d", i);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        return base + "_" + System.currentTimeMillis();
    }
}
