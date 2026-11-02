package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.CreateUserRequest;
import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.dto.UserResponse;
import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.Role;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import com.eventprocessor.reliable_event_processor.exception.InvalidStateTransitionException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationalAuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       OperationalAuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, String principal) {
        if (userRepository.existsByUsername(request.username())) {
            auditService.recordAuditLog(
                    "USER_CREATE",
                    request.username(),
                    "FAILED",
                    "Username already exists",
                    principal,
                    "Attempted to create duplicate user: " + request.username()
            );
            throw new IllegalArgumentException("Username already exists: " + request.username());
        }

        if (userRepository.existsByEmail(request.email())) {
            auditService.recordAuditLog(
                    "USER_CREATE",
                    request.email(),
                    "FAILED",
                    "Email already exists",
                    principal,
                    "Attempted to create duplicate email: " + request.email()
            );
            throw new IllegalArgumentException("Email already exists: " + request.email());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        AppUser user = new AppUser(
                request.username(),
                request.email(),
                encodedPassword,
                request.role(),
                UserStatus.ACTIVE
        );

        AppUser saved = userRepository.save(user);

        auditService.recordAuditLog(
                "USER_CREATE",
                saved.getUsername(),
                "SUCCESS",
                null,
                principal,
                "User created with role: " + saved.getRole()
        );

        log.info("User created successfully [id={}, username={}, role={}, actor={}]",
                saved.getId(), saved.getUsername(), saved.getRole(), principal);

        return toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        Page<AppUser> userPage = userRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PageResponse.from(userPage.map(this::toUserResponse));
    }

    @Transactional
    public UserResponse updateUserRole(Long userId, Role newRole, String principal) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Role oldRole = user.getRole();
        if (oldRole == Role.ADMIN && newRole != Role.ADMIN) {
            long activeAdmins = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdmins <= 1) {
                auditService.recordAuditLog(
                        "USER_ROLE_CHANGE",
                        user.getUsername(),
                        "FAILED",
                        "Cannot demote the last active administrator",
                        principal,
                        "Attempted role change from ADMIN to " + newRole + " blocked"
                );
                throw new InvalidStateTransitionException("Cannot change role: user is the last active administrator");
            }
        }

        user.setRole(newRole);
        AppUser updated = userRepository.save(user);

        auditService.recordAuditLog(
                "USER_ROLE_CHANGE",
                updated.getUsername(),
                "SUCCESS",
                null,
                principal,
                "Role changed from " + oldRole + " to " + newRole
        );

        log.info("Role updated for user '{}' from {} to {} by principal '{}'",
                updated.getUsername(), oldRole, newRole, principal);

        return toUserResponse(updated);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, UserStatus newStatus, String principal) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        UserStatus oldStatus = user.getStatus();
        if (user.getRole() == Role.ADMIN && newStatus == UserStatus.DEACTIVATED) {
            long activeAdmins = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdmins <= 1) {
                auditService.recordAuditLog(
                        "USER_STATUS_CHANGE",
                        user.getUsername(),
                        "FAILED",
                        "Cannot deactivate the last active administrator",
                        principal,
                        "Attempted deactivation of sole active administrator blocked"
                );
                throw new InvalidStateTransitionException("Cannot deactivate the last active administrator");
            }
        }

        user.setStatus(newStatus);
        AppUser updated = userRepository.save(user);

        auditService.recordAuditLog(
                "USER_STATUS_CHANGE",
                updated.getUsername(),
                "SUCCESS",
                null,
                principal,
                "Status changed from " + oldStatus + " to " + newStatus
        );

        log.info("Status updated for user '{}' from {} to {} by principal '{}'",
                updated.getUsername(), oldStatus, newStatus, principal);

        return toUserResponse(updated);
    }

    private UserResponse toUserResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

