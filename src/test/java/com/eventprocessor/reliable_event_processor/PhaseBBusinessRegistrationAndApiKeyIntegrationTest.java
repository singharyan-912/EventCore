package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.BusinessRegistrationRequest;
import com.eventprocessor.reliable_event_processor.dto.CreateApiKeyRequest;
import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.*;
import com.eventprocessor.reliable_event_processor.repository.*;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.security.RegistrationRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
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
class PhaseBBusinessRegistrationAndApiKeyIntegrationTest {

    private static final String TEST_TENANT_A = "tenant-phaseb-a";
    private static final String TEST_TENANT_B = "tenant-phaseb-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OperationalAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RegistrationRateLimiter registrationRateLimiter;

    private String tokenTenantA;
    private String tokenTenantB;

    @BeforeEach
    void setUp() {
        // Ensure tenants exist
        ensureTenant(TEST_TENANT_A, "Tenant Phase B A");
        ensureTenant(TEST_TENANT_B, "Tenant Phase B B");

        // Seed inventory for both tenants
        ensureInventory(TEST_TENANT_A, "SKU-PHASEB-01", 100);
        ensureInventory(TEST_TENANT_B, "SKU-PHASEB-01", 100);

        // Ensure users exist
        AppUser userA = ensureUser("user-phaseb-a", "user-a@phaseb.test", Role.OPERATOR, TEST_TENANT_A);
        AppUser userB = ensureUser("user-phaseb-b", "user-b@phaseb.test", Role.OPERATOR, TEST_TENANT_B);

        tokenTenantA = jwtTokenProvider.generateToken(userA.getUsername(), userA.getRole().name(), TEST_TENANT_A);
        tokenTenantB = jwtTokenProvider.generateToken(userB.getUsername(), userB.getRole().name(), TEST_TENANT_B);
    }

    private void ensureTenant(String tenantId, String name) {
        if (!tenantRepository.existsByTenantId(tenantId)) {
            Tenant tenant = new Tenant(tenantId, name, TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
        }
    }

    private AppUser ensureUser(String username, String email, Role role, String tenantId) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            AppUser u = new AppUser(
                    tenantId,
                    username,
                    email,
                    passwordEncoder.encode("TestPassword123!"),
                    role,
                    UserStatus.ACTIVE
            );
            return userRepository.save(u);
        });
    }

    private void ensureInventory(String tenantId, String sku, int qty) {
        if (inventoryRepository.findByTenantIdAndResourceId(tenantId, sku).isEmpty()) {
            Inventory inv = new Inventory(tenantId, sku, qty);
            inventoryRepository.save(inv);
        }
    }

    // =========================================================================
    // 1. Business Registration Tests
    // =========================================================================

    @Test
    @DisplayName("Business Registration: Success assigns Tenant and OPERATOR AppUser (never ADMIN)")
    void testBusinessRegistration_success() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        BusinessRegistrationRequest request = new BusinessRegistrationRequest(
                "Acme Logistics " + uniqueSuffix,
                "Jane Doe",
                "jane." + uniqueSuffix + "@acme-logistics.test",
                "StrongPassword123!",
                "+1-555-0199"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value(request.businessName()))
                .andExpect(jsonPath("$.ownerEmail").value(request.email().toLowerCase()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tenantId", startsWith("biz-acme-logistics-")))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String tenantId = responseNode.get("tenantId").asText();
        String ownerUsername = responseNode.get("ownerUsername").asText();

        // Verify Tenant in database
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        assertThat(tenant).isNotNull();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        // Verify Owner User in database
        AppUser owner = userRepository.findByUsername(ownerUsername).orElse(null);
        assertThat(owner).isNotNull();
        assertThat(owner.getRole()).isEqualTo(Role.OPERATOR); // Must be OPERATOR, NOT ADMIN
        assertThat(owner.getTenantId()).isEqualTo(tenantId);
        assertThat(passwordEncoder.matches("StrongPassword123!", owner.getPasswordHash())).isTrue();

        // Verify Audit Log was recorded
        List<OperationalAuditLog> logs = auditLogRepository.findByTargetIdentifierOrderByTimestampDesc(tenantId);
        assertThat(logs).isNotEmpty();
        assertThat(logs.stream().anyMatch(l -> "BUSINESS_REGISTER".equals(l.getActionType()))).isTrue();
    }

    @Test
    @DisplayName("Business Registration: Duplicate email registration is rejected with 400 Bad Request")
    void testBusinessRegistration_duplicateEmailRejected() throws Exception {
        String email = "duplicate." + UUID.randomUUID() + "@business.test";
        BusinessRegistrationRequest req1 = new BusinessRegistrationRequest(
                "First Biz", "Owner One", email, "Password123!", null
        );

        mockMvc.perform(post("/api/v1/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Duplicate with casing differences
        BusinessRegistrationRequest req2 = new BusinessRegistrationRequest(
                "Second Biz", "Owner Two", email.toUpperCase(), "Password123!", null
        );

        mockMvc.perform(post("/api/v1/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("Business Registration: Malformed request is rejected with 400 Bad Request")
    void testBusinessRegistration_validationFailure() throws Exception {
        BusinessRegistrationRequest invalidReq = new BusinessRegistrationRequest(
                "", // Blank business name
                "Owner",
                "invalid-email-format",
                "123", // Short password
                null
        );

        mockMvc.perform(post("/api/v1/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("Business Registration: Registration rate limiter blocks excessive requests with 429")
    void testBusinessRegistration_rateLimiterEnforced() throws Exception {
        String testIp = "198.51.100.42";
        // Consume available registration tokens for this IP
        for (int i = 0; i < 10; i++) {
            registrationRateLimiter.allowRegistration(testIp);
        }

        BusinessRegistrationRequest request = new BusinessRegistrationRequest(
                "Rate Limited Biz",
                "Owner",
                "ratelimited." + UUID.randomUUID() + "@test.com",
                "Password123!",
                null
        );

        mockMvc.perform(post("/api/v1/businesses/register")
                        .with(req -> {
                            req.setRemoteAddr(testIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message", containsString("Too many registration requests")));
    }

    // =========================================================================
    // 2. API Key Management Tests
    // =========================================================================

    @Test
    @DisplayName("API Key Creation: returns rf_live_ secret once, stores only SHA-256 hash in DB")
    void testApiKey_creation_formatAndSecretReturnedOnce() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest("Backend Service Key", null);

        MvcResult result = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Backend Service Key"))
                .andExpect(jsonPath("$.keyPrefix", startsWith("rf_live_")))
                .andExpect(jsonPath("$.secretKey", startsWith("rf_live_")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long keyId = json.get("id").asLong();
        String secretKey = json.get("secretKey").asText();
        String keyPrefix = json.get("keyPrefix").asText();

        assertThat(secretKey.length()).isGreaterThanOrEqualTo(48);
        assertThat(keyPrefix.length()).isEqualTo(16);

        // Inspect database directly: verify secret is hashed, NOT plaintext
        ApiKey dbKey = apiKeyRepository.findById(keyId).orElse(null);
        assertThat(dbKey).isNotNull();
        assertThat(dbKey.getSecretHash()).isNotEqualTo(secretKey);
        assertThat(dbKey.getSecretHash().length()).isEqualTo(64); // SHA-256 hex string
        assertThat(dbKey.getKeyPrefix()).isEqualTo(keyPrefix);
        assertThat(dbKey.getTenantId()).isEqualTo(TEST_TENANT_A);
    }

    @Test
    @DisplayName("API Key Listing: strictly scoped to current tenant and hides secrets")
    void testApiKey_listing_scopedToTenant() throws Exception {
        // Create key for Tenant A
        CreateApiKeyRequest reqA = new CreateApiKeyRequest("Tenant A Key", null);
        mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        // Create key for Tenant B
        CreateApiKeyRequest reqB = new CreateApiKeyRequest("Tenant B Key", null);
        mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated());

        // Tenant A lists keys: must contain Tenant A Key, NOT Tenant B Key
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Tenant A Key")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Tenant B Key"))))
                .andExpect(jsonPath("$[*].secretKey").doesNotExist()); // Secret must never be in list

        // Tenant B lists keys: must contain Tenant B Key, NOT Tenant A Key
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Tenant B Key")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Tenant A Key"))));
    }

    // =========================================================================
    // 3. API Key Authentication & Isolation Tests
    // =========================================================================

    @Test
    @DisplayName("API Key Authentication: X-API-Key authenticates and binds operations to tenant")
    void testApiKey_authentication_validKeyCanIngestEventUnderTenant() throws Exception {
        // 1. Generate an API Key for Tenant A
        CreateApiKeyRequest keyReq = new CreateApiKeyRequest("Ingestion Worker", null);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String rawSecret = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("secretKey").asText();

        // 2. Ingest event using X-API-Key without any JWT
        String idempotencyKey = UUID.randomUUID().toString();
        EventRequest eventRequest = new EventRequest(
                idempotencyKey,
                "INVENTORY_DEDUCT",
                "SKU-PHASEB-01",
                1,
                TEST_TENANT_A
        );

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", rawSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify in database that event was persisted under Tenant A
        ProcessedEvent event = processedEventRepository.findByTenantIdAndIdempotencyKey(TEST_TENANT_A, idempotencyKey).orElse(null);
        assertThat(event).isNotNull();
        assertThat(event.getTenantId()).isEqualTo(TEST_TENANT_A);
    }

    @Test
    @DisplayName("API Key Isolation: Tenant A's key cannot access or impersonate Tenant B")
    void testApiKey_crossTenantIsolation_cannotAccessAnotherTenantData() throws Exception {
        // Create key for Tenant A
        CreateApiKeyRequest keyReq = new CreateApiKeyRequest("Key Tenant A", null);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String secretA = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("secretKey").asText();
        Long keyIdA = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("id").asLong();

        // Attempt 1: Ingest event with Tenant A's key but specify Tenant B in header
        EventRequest eventRequest = new EventRequest(
                UUID.randomUUID().toString(),
                "INVENTORY_DEDUCT",
                "SKU-PHASEB-01",
                1,
                TEST_TENANT_A
        );

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", secretA)
                        .header("X-Tenant-ID", TEST_TENANT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isForbidden());

        // Attempt 2: Tenant B tries to revoke Tenant A's key
        mockMvc.perform(delete("/api/v1/api-keys/" + keyIdA)
                        .header("Authorization", "Bearer " + tokenTenantB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API Key Revocation: Revoked key immediately returns 401 Unauthorized")
    void testApiKey_revocation_immediatelyRejectsFurtherCalls() throws Exception {
        // 1. Create key
        CreateApiKeyRequest keyReq = new CreateApiKeyRequest("Temporary Key", null);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long keyId = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("id").asLong();
        String rawSecret = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("secretKey").asText();

        // 2. Query operations endpoint: success
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", rawSecret))
                .andExpect(status().isOk());

        // 3. Revoke key
        mockMvc.perform(delete("/api/v1/api-keys/" + keyId)
                        .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        // 4. Subsequent call with revoked key: must be 401 Unauthorized
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", rawSecret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API Key Rotation: Old key revoked, new key functional")
    void testApiKey_rotation_revokesOldAndReturnsNew() throws Exception {
        // 1. Create key
        CreateApiKeyRequest keyReq = new CreateApiKeyRequest("Rotatable Key", null);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long keyId = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("id").asLong();
        String oldSecret = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("secretKey").asText();

        // 2. Rotate key
        MvcResult rotateResult = mockMvc.perform(post("/api/v1/api-keys/" + keyId + "/rotate")
                        .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String newSecret = objectMapper.readTree(rotateResult.getResponse().getContentAsString()).get("secretKey").asText();
        assertThat(newSecret).isNotEqualTo(oldSecret);

        // 3. Old key is rejected
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", oldSecret))
                .andExpect(status().isUnauthorized());

        // 4. New key is accepted
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", newSecret))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API Key Privileges: Cannot access platform ADMIN endpoints (returns 403 Forbidden)")
    void testApiKey_cannotAccessPlatformAdminEndpoints() throws Exception {
        // Create key
        CreateApiKeyRequest keyReq = new CreateApiKeyRequest("Service Key", null);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String rawSecret = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("secretKey").asText();

        // Attempt to access /api/v1/admin/users
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-API-Key", rawSecret))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Key Authentication: Invalid or malformed key returns 401 Unauthorized")
    void testApiKey_invalidOrMalformedKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", "rf_live_000000000000000000000000000000000000000000000000"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/operations/events")
                        .header("X-API-Key", "completely_bogus_key"))
                .andExpect(status().isUnauthorized());
    }
}
