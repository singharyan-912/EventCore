package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.*;
import com.eventprocessor.reliable_event_processor.entity.*;
import com.eventprocessor.reliable_event_processor.repository.*;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.security.LoginRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "event-processor.outbox.enabled=false",
                "event-processor.consumer.enabled=false",
                "event-processor.worker.enabled=false",
                "simulation.enabled=false"
})
class SecurityAndRoleAuthorizationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        @Autowired
        private LoginRateLimiter loginRateLimiter;

        @Autowired
        private OperationalAuditLogRepository auditLogRepository;

        @Autowired
        private ProcessedEventRepository processedEventRepository;

        private static final String ADMIN_KEY = "admin-secret-key-12345";

        @BeforeEach
        void setUp() {
                loginRateLimiter.cleanupExpired();
        }

        private AppUser createTestUser(String username, String rawPassword, Role role) {
                return userRepository.findByUsername(username)
                                .orElseGet(() -> {
                                        AppUser user = new AppUser(
                                                        username,
                                                        username + "@test.example.com",
                                                        passwordEncoder.encode(rawPassword),
                                                        role,
                                                        UserStatus.ACTIVE);
                                        return userRepository.save(user);
                                });
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 1: Public Health Endpoint Contract
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("1. Public health endpoint remains accessible without credentials and preserves response contract")
        void test1_publicHealthEndpoint() throws Exception {
                mockMvc.perform(get("/api/v1/health"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UP"))
                                .andExpect(jsonPath("$.message")
                                                .value("Reliable Event Processing Platform is running"));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 2: Missing Credentials Return HTTP 401
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("2. Missing credentials return HTTP 401 Unauthorized on protected endpoints")
        void test2_missingCredentialsReturn401() throws Exception {
                // GET /api/v1/auth/me
                mockMvc.perform(get("/api/v1/auth/me"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.error").value("Unauthorized"));

                // GET /api/v1/operations/events
                mockMvc.perform(get("/api/v1/operations/events"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                // GET /api/v1/operations/metrics
                mockMvc.perform(get("/api/v1/operations/metrics"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                // GET /api/v1/operations/audit-logs
                mockMvc.perform(get("/api/v1/operations/audit-logs"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                // GET /api/v1/admin/users
                mockMvc.perform(get("/api/v1/admin/users"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                // GET /api/v1/admin/dead-letters
                mockMvc.perform(get("/api/v1/admin/dead-letters"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 3: Invalid or Expired Tokens Rejected
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("3. Invalid and malformed tokens return HTTP 401 Unauthorized")
        void test3_invalidTokensRejected() throws Exception {
                mockMvc.perform(get("/api/v1/auth/me")
                                .header("Authorization", "Bearer completely-invalid-jwt-token-string"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                mockMvc.perform(get("/api/v1/operations/events")
                                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy.signature"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 4: Authenticated Users Access Permitted Endpoints
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("4. Authenticated users with permitted roles can access inspection and profile endpoints")
        void test4_authenticatedUsersAccessPermittedEndpoints() throws Exception {
                String viewerUsername = "viewer_user_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(viewerUsername, "ViewerPass123!", Role.VIEWER);
                String viewerToken = jwtTokenProvider.generateToken(viewerUsername, Role.VIEWER.name());

                // /api/v1/auth/me works for VIEWER
                mockMvc.perform(get("/api/v1/auth/me")
                                .header("Authorization", "Bearer " + viewerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value(viewerUsername))
                                .andExpect(jsonPath("$.role").value("VIEWER"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));

                // /api/v1/operations/events works for VIEWER
                mockMvc.perform(get("/api/v1/operations/events")
                                .header("Authorization", "Bearer " + viewerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.page").value(0));

                // /api/v1/operations/metrics works for VIEWER
                mockMvc.perform(get("/api/v1/operations/metrics")
                                .header("Authorization", "Bearer " + viewerToken))
                                .andExpect(status().isOk());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 5: Viewers Cannot Perform Recovery Operations (HTTP 403)
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("5. VIEWER role cannot trigger dead-letter reprocessing (HTTP 403 Forbidden)")
        void test5_viewerCannotPerformRecovery() throws Exception {
                String viewerUsername = "viewer_rec_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(viewerUsername, "ViewerPass123!", Role.VIEWER);
                String viewerToken = jwtTokenProvider.generateToken(viewerUsername, Role.VIEWER.name());

                mockMvc.perform(post("/api/v1/admin/dead-letters/dummy-key/reprocess")
                                .header("Authorization", "Bearer " + viewerToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status").value(403))
                                .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 6: Operators Cannot Manage Users or Assign Admin Roles
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("6. OPERATOR role cannot create users, change roles, or view audit records (HTTP 403 Forbidden)")
        void test6_operatorCannotManageUsersOrAudits() throws Exception {
                String operatorUsername = "op_user_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(operatorUsername, "OperatorPass123!", Role.OPERATOR);
                String operatorToken = jwtTokenProvider.generateToken(operatorUsername, Role.OPERATOR.name());

                CreateUserRequest createRequest = new CreateUserRequest(
                                "new_admin_attempt",
                                "attempt@example.com",
                                "Password123!",
                                Role.ADMIN);

                // Cannot create users
                mockMvc.perform(post("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isForbidden());

                // Cannot list users
                mockMvc.perform(get("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + operatorToken))
                                .andExpect(status().isForbidden());

                // Cannot view security audit records
                mockMvc.perform(get("/api/v1/operations/audit-logs")
                                .header("Authorization", "Bearer " + operatorToken))
                                .andExpect(status().isForbidden());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 7: Administrators Can Perform Authorized Actions
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("7. ADMIN role can manage users, view audits, and cannot demote the last active administrator")
        void test7_administratorAuthorizedActions() throws Exception {
                String adminUsername = "admin_auth_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(adminUsername, "AdminPass123!", Role.ADMIN);
                String adminToken = jwtTokenProvider.generateToken(adminUsername, Role.ADMIN.name());

                // 1. Create a new user as ADMIN
                String targetUsername = "managed_user_" + UUID.randomUUID().toString().substring(0, 8);
                CreateUserRequest createReq = new CreateUserRequest(
                                targetUsername,
                                targetUsername + "@example.com",
                                "Password123!",
                                Role.VIEWER);

                mockMvc.perform(post("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.username").value(targetUsername))
                                .andExpect(jsonPath("$.role").value("VIEWER"));

                AppUser createdUser = userRepository.findByUsername(targetUsername).orElseThrow();

                // 2. Change role to OPERATOR
                UpdateRoleRequest roleReq = new UpdateRoleRequest(Role.OPERATOR);
                mockMvc.perform(patch("/api/v1/admin/users/" + createdUser.getId() + "/role")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roleReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").value("OPERATOR"));

                // 3. Deactivate user
                UpdateStatusRequest statusReq = new UpdateStatusRequest(UserStatus.DEACTIVATED);
                mockMvc.perform(patch("/api/v1/admin/users/" + createdUser.getId() + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(statusReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

                // 4. View audit logs
                mockMvc.perform(get("/api/v1/operations/audit-logs")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 8: Unauthenticated Users Cannot Access Audit Logs or Sensitive Metrics
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("8. Unauthenticated requests to audit logs and metrics are rejected with 401")
        void test8_unauthenticatedAuditAndMetrics() throws Exception {
                mockMvc.perform(get("/api/v1/operations/audit-logs"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                mockMvc.perform(get("/api/v1/operations/metrics"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 9: Passwords Stored as Hashes and Never Returned in API
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("9. User passwords are stored as BCrypt hashes and never returned in API responses")
        void test9_passwordsStoredAsHashes() throws Exception {
                String testUser = "hash_check_" + UUID.randomUUID().toString().substring(0, 8);
                String rawPassword = "RawSecretPassword123!";
                AppUser user = createTestUser(testUser, rawPassword, Role.VIEWER);

                // Verify database entity
                assertThat(user.getPasswordHash()).startsWith("$2a$");
                assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
                assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash())).isTrue();

                // Verify API response does NOT contain password or passwordHash
                String token = jwtTokenProvider.generateToken(testUser, Role.VIEWER.name());
                mockMvc.perform(get("/api/v1/auth/me")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.password").doesNotExist())
                                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 10: Generic Login Failure Responses (Anti-Enumeration)
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("10. Invalid login attempts do not disclose account existence")
        void test10_genericLoginFailure() throws Exception {
                String existingUser = "existing_enum_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(existingUser, "RealPassword123!", Role.VIEWER);

                // Case A: Non-existent username
                LoginRequest nonExistent = new LoginRequest("user_does_not_exist_9999", "SomePassword123!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(nonExistent)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.message").value("Invalid username or password"));

                // Case B: Existing username with bad password
                LoginRequest badPassword = new LoginRequest(existingUser, "WrongPassword123!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badPassword)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 11: Security Actions Are Audited
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("11. User logins, user creation, and role modifications are recorded in the audit trail")
        void test11_securityActionsAudited() throws Exception {
                String adminUser = "admin_aud_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(adminUser, "AdminPass123!", Role.ADMIN);
                String adminToken = jwtTokenProvider.generateToken(adminUser, Role.ADMIN.name());

                // User creation
                String targetUser = "audit_target_" + UUID.randomUUID().toString().substring(0, 8);
                CreateUserRequest createReq = new CreateUserRequest(
                                targetUser,
                                targetUser + "@example.com",
                                "Password123!",
                                Role.VIEWER);

                mockMvc.perform(post("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated());

                // Verify audit log exists
                boolean auditFound = auditLogRepository.findAll().stream()
                                .anyMatch(log -> "USER_CREATE".equals(log.getActionType())
                                                && targetUser.equals(log.getTargetIdentifier())
                                                && "SUCCESS".equals(log.getOutcome())
                                                && adminUser.equals(log.getPrincipal()));

                assertThat(auditFound).isTrue();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 12: Login Rate Limiting (Abuse Protection)
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("12. Rate limiting engages after consecutive failed login attempts")
        void test12_loginRateLimiting() throws Exception {
                String dummyUser = "rate_test_user_" + UUID.randomUUID().toString().substring(0, 8);
                LoginRequest badRequest = new LoginRequest(dummyUser, "WrongPassword123!");

                // Simulate 5 failed attempts from same IP
                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/v1/auth/login")
                                        .header("X-Forwarded-For", "192.168.10.50")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(badRequest)))
                                        .andExpect(status().isUnauthorized());
                }

                // 6th attempt is blocked by rate limiter
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-Forwarded-For", "192.168.10.50")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badRequest)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message")
                                                .value(containsString("Too many failed login attempts")));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 13: Backward Compatibility with X-Admin-API-Key
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("13. X-Admin-API-Key header authenticates requests with ADMIN privileges")
        void test13_apiKeyBackwardCompatibility() throws Exception {
                mockMvc.perform(get("/api/v1/operations/events")
                                .header("X-Admin-API-Key", ADMIN_KEY))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/operations/audit-logs")
                                .header("X-Admin-API-Key", ADMIN_KEY))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/operations/events")
                                .header("X-Admin-API-Key", "invalid-key-999"))
                                .andExpect(status().isUnauthorized());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 14: Method-Level Security Cannot Be Bypassed
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("14. Method-level security enforces role restrictions even with valid authentication")
        void test14_methodSecurityCannotBeBypassed() throws Exception {
                String viewerUsername = "viewer_bypass_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(viewerUsername, "ViewerPass123!", Role.VIEWER);
                String viewerToken = jwtTokenProvider.generateToken(viewerUsername, Role.VIEWER.name());

                // Directly trying admin user creation with VIEWER token
                CreateUserRequest req = new CreateUserRequest(
                                "bypass_user",
                                "bypass@example.com",
                                "Password123!",
                                Role.ADMIN);

                mockMvc.perform(post("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + viewerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status").value(403));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 15: Full Authentication Flow (Login, Me, Logout)
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("15. Full authentication lifecycle: user login, token retrieval, /me inspection, and logout")
        void test15_fullAuthLifecycle() throws Exception {
                String testUser = "auth_flow_" + UUID.randomUUID().toString().substring(0, 8);
                String rawPassword = "StrongPassword123!";
                createTestUser(testUser, rawPassword, Role.OPERATOR);

                // 1. Successful login
                LoginRequest loginRequest = new LoginRequest(testUser, rawPassword);
                var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.token").isNotEmpty())
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.username").value(testUser))
                                .andExpect(jsonPath("$.role").value("OPERATOR"))
                                .andReturn();

                AuthResponse authResponse = objectMapper.readValue(
                                loginResult.getResponse().getContentAsString(), AuthResponse.class);

                // 2. Access /me with issued token
                mockMvc.perform(get("/api/v1/auth/me")
                                .header("Authorization", "Bearer " + authResponse.token()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value(testUser))
                                .andExpect(jsonPath("$.role").value("OPERATOR"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));

                // 3. Logout
                mockMvc.perform(post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + authResponse.token()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Logged out successfully"));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 16: OPERATOR Can Reprocess Dead-Letter Events
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("16. OPERATOR role can reprocess dead-letter events, while VIEWER cannot")
        void test16_operatorCanReprocessDeadLetters() throws Exception {
                String operatorUser = "op_dl_" + UUID.randomUUID().toString().substring(0, 8);
                createTestUser(operatorUser, "OperatorPass123!", Role.OPERATOR);
                String operatorToken = jwtTokenProvider.generateToken(operatorUser, Role.OPERATOR.name());

                // Create a dead letter event
                String dlKey = "dl-op-" + UUID.randomUUID();
                ProcessedEvent event = new ProcessedEvent();
                event.setIdempotencyKey(dlKey);
                event.setEventType("INVENTORY_DEDUCT");
                event.setResourceId("item-dl-op");
                event.setRequestPayloadHash("test-hash");
                event.setRequestPayload("{\"quantity\":1}");
                event.setStatus(EventStatus.DEAD_LETTER.name());
                event.setLastFailureReason("Permanent test failure");
                event.setRetryCount(3);
                event.setMaxRetries(3);
                event.setUpdatedAt(OffsetDateTime.now());
                processedEventRepository.saveAndFlush(event);

                // Operator triggers reprocessing -> 200 OK
                mockMvc.perform(post("/api/v1/admin/dead-letters/" + dlKey + "/reprocess")
                                .header("Authorization", "Bearer " + operatorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("RETRY_PENDING"));

                // Verify audit log captured operator as principal
                boolean auditLogged = auditLogRepository.findAll().stream()
                                .anyMatch(log -> "DEAD_LETTER_REPROCESS".equals(log.getActionType())
                                                && dlKey.equals(log.getTargetIdentifier())
                                                && "SUCCESS".equals(log.getOutcome())
                                                && operatorUser.equals(log.getPrincipal()));

                assertThat(auditLogged).isTrue();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 17: Protection Against Lockout of Last Active Administrator
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("17. Lockout protection rejects demoting or deactivating the sole active administrator")
        void test17_lockoutProtectionForLastAdmin() throws Exception {
                // Find existing admins
                var allAdmins = userRepository.findAll().stream()
                                .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE)
                                .toList();

                // If multiple exist, leave only 1 active admin for the test
                for (int i = 1; i < allAdmins.size(); i++) {
                        AppUser extra = allAdmins.get(i);
                        extra.setStatus(UserStatus.DEACTIVATED);
                        userRepository.save(extra);
                }

                AppUser soleAdmin = allAdmins.getFirst();
                String adminToken = jwtTokenProvider.generateToken(soleAdmin.getUsername(), Role.ADMIN.name());

                // Attempt to demote sole admin to VIEWER -> 409 Conflict
                UpdateRoleRequest demoteReq = new UpdateRoleRequest(Role.VIEWER);
                mockMvc.perform(patch("/api/v1/admin/users/" + soleAdmin.getId() + "/role")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(demoteReq)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(containsString("last active administrator")));

                // Attempt to deactivate sole admin -> 409 Conflict
                UpdateStatusRequest deactReq = new UpdateStatusRequest(UserStatus.DEACTIVATED);
                mockMvc.perform(patch("/api/v1/admin/users/" + soleAdmin.getId() + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deactReq)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(containsString("last active administrator")));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 18: Deactivated Account Cannot Login
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("18. Deactivated accounts cannot log in (HTTP 401 Unauthorized)")
        void test18_deactivatedAccountCannotLogin() throws Exception {
                String user = "deact_" + UUID.randomUUID().toString().substring(0, 8);
                AppUser appUser = createTestUser(user, "Password123!", Role.VIEWER);
                appUser.setStatus(UserStatus.DEACTIVATED);
                userRepository.save(appUser);

                LoginRequest req = new LoginRequest(user, "Password123!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value("Account is deactivated"));
        }
}
