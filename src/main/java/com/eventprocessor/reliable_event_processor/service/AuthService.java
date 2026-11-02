package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.AuthResponse;
import com.eventprocessor.reliable_event_processor.dto.LoginRequest;
import com.eventprocessor.reliable_event_processor.dto.UserProfileResponse;
import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.exception.UnauthorizedException;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.security.LoginRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter rateLimiter;
    private final OperationalAuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       LoginRateLimiter rateLimiter,
                       OperationalAuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        String rateLimitKey = (clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown-ip";

        if (rateLimiter.isBlocked(rateLimitKey)) {
            auditService.recordAuditLog(
                    "USER_LOGIN",
                    request.username(),
                    "FAILED",
                    "Rate limit exceeded",
                    "ANONYMOUS",
                    "Login rejected due to rate limiting from IP: " + rateLimitKey
            );
            throw new UnauthorizedException("Too many failed login attempts. Please try again later.");
        }

        AppUser user = userRepository.findByUsername(request.username()).orElse(null);

        // Constant-time check mitigation: verify password hash only if user found,
        // otherwise perform dummy comparison to resist timing-based user enumeration.
        boolean passwordMatches = false;
        if (user != null) {
            passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        } else {
            passwordEncoder.matches("dummyPasswordToEqualizeTiming", "$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/iQkCiqe81K25g1k61Hl2Zt2YVv1m");
        }

        if (user == null || !passwordMatches) {
            rateLimiter.recordFailedAttempt(rateLimitKey);
            auditService.recordAuditLog(
                    "USER_LOGIN",
                    request.username(),
                    "FAILED",
                    "Invalid credentials",
                    "ANONYMOUS",
                    "Authentication failed for user: " + request.username()
            );
            throw new UnauthorizedException("Invalid username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.recordAuditLog(
                    "USER_LOGIN",
                    user.getUsername(),
                    "FAILED",
                    "Account is deactivated",
                    user.getUsername(),
                    "Login attempt rejected for inactive user"
            );
            throw new UnauthorizedException("Account is deactivated");
        }

        rateLimiter.reset(rateLimitKey);

        auditService.recordAuditLog(
                "USER_LOGIN",
                user.getUsername(),
                "SUCCESS",
                null,
                user.getUsername(),
                "User authenticated successfully"
        );

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, jwtTokenProvider.getExpirationMs(), user.getUsername(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }

    public void logout(String username) {
        if (username != null && !username.isBlank()) {
            auditService.recordAuditLog(
                    "USER_LOGOUT",
                    username,
                    "SUCCESS",
                    null,
                    username,
                    "User logged out successfully"
            );
        }
    }
}

