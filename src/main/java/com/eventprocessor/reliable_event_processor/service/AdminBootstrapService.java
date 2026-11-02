package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.AdminBootstrapProperties;
import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.Role;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties bootstrapProperties;
    private final OperationalAuditService auditService;
    private final Environment environment;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AdminBootstrapProperties bootstrapProperties,
                                 OperationalAuditService auditService,
                                 Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapProperties = bootstrapProperties;
        this.auditService = auditService;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapProperties.isEnabled()) {
            log.info("Administrator auto-bootstrap is disabled.");
            return;
        }

        long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (activeAdminCount > 0) {
            log.info("At least one active administrator already exists. Bootstrap provisioning skipped.");
            return;
        }

        String password = bootstrapProperties.getPassword();
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (password == null || password.isBlank()) {
            if (isProduction) {
                log.warn("No administrator account exists, and BOOTSTRAP_ADMIN_PASSWORD was not provided. " +
                         "Initial admin provisioning skipped. Set BOOTSTRAP_ADMIN_PASSWORD to initialize the first administrator.");
                return;
            } else {
                // Safe deterministic fallback for development/test profile
                password = "AdminSecret123!";
                log.info("Provisioning initial development/test administrator account for '{}'", bootstrapProperties.getUsername());
            }
        }

        String username = bootstrapProperties.getUsername();
        String email = bootstrapProperties.getEmail();

        AppUser admin = new AppUser(
                username,
                email,
                passwordEncoder.encode(password),
                Role.ADMIN,
                UserStatus.ACTIVE
        );

        userRepository.save(admin);

        auditService.recordAuditLog(
                "USER_CREATE",
                username,
                "SUCCESS",
                null,
                "BOOTSTRAP",
                "Initial administrator account provisioned automatically"
        );

        log.info("Initial administrator account successfully provisioned for username '{}'.", username);
    }
}

