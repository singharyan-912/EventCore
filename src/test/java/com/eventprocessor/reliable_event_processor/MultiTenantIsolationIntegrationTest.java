package com.eventprocessor.reliable_event_processor;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.entity.Role;
import com.eventprocessor.reliable_event_processor.entity.Tenant;
import com.eventprocessor.reliable_event_processor.entity.TenantStatus;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import com.eventprocessor.reliable_event_processor.repository.InventoryRepository;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.eventprocessor.reliable_event_processor.repository.TenantRepository;
import com.eventprocessor.reliable_event_processor.repository.UserRepository;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.service.TenantService;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA = "tenant-beta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        ensureTenant(TENANT_ALPHA, "Tenant Alpha Logistics");
        ensureTenant(TENANT_BETA, "Tenant Beta E-Commerce");
    }

    private void ensureTenant(String tenantId, String name) {
        if (!tenantRepository.existsByTenantId(tenantId)) {
            tenantService.createTenant(tenantId, name);
        }
    }

    private Inventory createIsolatedInventory(String tenantId, String resourceId, int initialQuantity) {
        return inventoryRepository.findByTenantIdAndResourceId(tenantId, resourceId)
                .map(existing -> {
                    existing.setAvailableQuantity(initialQuantity);
                    return inventoryRepository.save(existing);
                })
                .orElseGet(() -> inventoryRepository.save(new Inventory(tenantId, resourceId, initialQuantity)));
    }

    private String createTenantUserAndToken(String username, Role role, String tenantId) {
        AppUser user = userRepository.findByUsername(username)
                .orElseGet(() -> {
                    AppUser u = new AppUser(
                            tenantId,
                            username,
                            username + "@test.com",
                            passwordEncoder.encode("TestPassword123!"),
                            role,
                            UserStatus.ACTIVE
                    );
                    return userRepository.save(u);
                });
        return jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name(), user.getTenantId());
    }

    @Test
    @DisplayName("1. Tenant Persistence & Active Status Validation")
    void tenantPersistenceAndMetadata() {
        Tenant alpha = tenantRepository.findByTenantId(TENANT_ALPHA).orElse(null);
        assertThat(alpha).isNotNull();
        assertThat(alpha.getBusinessName()).isEqualTo("Tenant Alpha Logistics");
        assertThat(alpha.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenantService.isTenantActive(TENANT_ALPHA)).isTrue();

        Tenant beta = tenantRepository.findByTenantId(TENANT_BETA).orElse(null);
        assertThat(beta).isNotNull();
        assertThat(beta.getBusinessName()).isEqualTo("Tenant Beta E-Commerce");
        assertThat(beta.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenantService.isTenantActive(TENANT_BETA)).isTrue();
    }

    @Test
    @DisplayName("2. Inactive Tenant Submission is Rejected with HTTP 400")
    void inactiveTenant_submissionRejectedWithBadRequest() throws Exception {
        String inactiveTenantId = "tenant-suspended-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant inactive = new Tenant(inactiveTenantId, "Suspended Store", TenantStatus.INACTIVE);
        tenantRepository.save(inactive);

        EventRequest request = new EventRequest(
                "evt-inactive-test",
                "INVENTORY_DEDUCT",
                "SKU-INACTIVE",
                1,
                inactiveTenantId
        );

        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", inactiveTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or inactive tenant")));
    }

    @Test
    @DisplayName("3. Tenant-Scoped Idempotency: Same key succeeds independently for distinct tenants")
    void tenantScopedIdempotency_sameKeyDifferentTenants_bothSucceedIndependently() throws Exception {
        String sharedKey = "shared-order-" + UUID.randomUUID();
        String skuAlpha = "SKU-ALPHA-" + UUID.randomUUID().toString().substring(0, 6);
        String skuBeta = "SKU-BETA-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, skuAlpha, 10);
        createIsolatedInventory(TENANT_BETA, skuBeta, 20);

        EventRequest requestAlpha = new EventRequest(
                sharedKey,
                "INVENTORY_DEDUCT",
                skuAlpha,
                2,
                TENANT_ALPHA
        );

        EventRequest requestBeta = new EventRequest(
                sharedKey,
                "INVENTORY_DEDUCT",
                skuBeta,
                5,
                TENANT_BETA
        );

        // Tenant Alpha submits sharedKey
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(sharedKey))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Tenant Beta submits THE SAME sharedKey — MUST SUCCEED (tenant-scoped idempotency)
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBeta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(sharedKey))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify database holds 2 distinct records for the same key
        ProcessedEvent alphaEvent = processedEventRepository.findByTenantIdAndIdempotencyKey(TENANT_ALPHA, sharedKey).orElse(null);
        ProcessedEvent betaEvent = processedEventRepository.findByTenantIdAndIdempotencyKey(TENANT_BETA, sharedKey).orElse(null);

        assertThat(alphaEvent).isNotNull();
        assertThat(betaEvent).isNotNull();
        assertThat(alphaEvent.getId()).isNotEqualTo(betaEvent.getId());
        assertThat(alphaEvent.getTenantId()).isEqualTo(TENANT_ALPHA);
        assertThat(betaEvent.getTenantId()).isEqualTo(TENANT_BETA);
        assertThat(alphaEvent.getResourceId()).isEqualTo(skuAlpha);
        assertThat(betaEvent.getResourceId()).isEqualTo(skuBeta);

        // Verify independent inventory deductions
        Inventory alphaInv = inventoryRepository.findByTenantIdAndResourceId(TENANT_ALPHA, skuAlpha).orElseThrow();
        Inventory betaInv = inventoryRepository.findByTenantIdAndResourceId(TENANT_BETA, skuBeta).orElseThrow();
        assertThat(alphaInv.getAvailableQuantity()).isEqualTo(8); // 10 - 2
        assertThat(betaInv.getAvailableQuantity()).isEqualTo(15); // 20 - 5
    }

    @Test
    @DisplayName("4. Tenant-Scoped Idempotent Replay: Cached response returned per tenant without crosstalk")
    void tenantScopedReplay_resubmittingReturnsCachedResponsePerTenant() throws Exception {
        String sharedKey = "replay-test-" + UUID.randomUUID();
        String skuAlpha = "SKU-REP-A-" + UUID.randomUUID().toString().substring(0, 6);
        String skuBeta = "SKU-REP-B-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, skuAlpha, 50);
        createIsolatedInventory(TENANT_BETA, skuBeta, 50);

        EventRequest reqA = new EventRequest(sharedKey, "INVENTORY_DEDUCT", skuAlpha, 3);
        EventRequest reqB = new EventRequest(sharedKey, "INVENTORY_DEDUCT", skuBeta, 7);

        // Initial submissions
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk());

        // Replay Tenant Alpha
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(sharedKey))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Replay Tenant Beta
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(sharedKey))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify quantities were NOT double-deducted
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_ALPHA, skuAlpha).orElseThrow().getAvailableQuantity()).isEqualTo(47);
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_BETA, skuBeta).orElseThrow().getAvailableQuantity()).isEqualTo(43);
    }

    @Test
    @DisplayName("5. Tenant-Scoped Inventory: Same SKU can exist in different tenants with isolated quantities")
    void tenantScopedInventory_sameSkuIsolatedQuantities() throws Exception {
        String commonSku = "SKU-SHARED-CATALOG-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, commonSku, 10);
        createIsolatedInventory(TENANT_BETA, commonSku, 100);

        String keyA = "key-a-" + UUID.randomUUID();
        EventRequest reqA = new EventRequest(keyA, "INVENTORY_DEDUCT", commonSku, 4);

        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk());

        // Alpha deducted 4: 10 -> 6
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_ALPHA, commonSku).orElseThrow().getAvailableQuantity()).isEqualTo(6);
        // Beta remains unchanged: 100
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_BETA, commonSku).orElseThrow().getAvailableQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("6. Cross-Tenant IDOR Protection: Tenant B cannot access Tenant A's event detail")
    void crossTenantIdorProtection_eventDetailBlocked() throws Exception {
        String alphaPrivateEventKey = "alpha-confidential-" + UUID.randomUUID();
        String sku = "SKU-IDOR-" + UUID.randomUUID().toString().substring(0, 6);
        createIsolatedInventory(TENANT_ALPHA, sku, 10);

        EventRequest reqA = new EventRequest(alphaPrivateEventKey, "INVENTORY_DEDUCT", sku, 1);
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk());

        // Create user for Tenant Beta
        String betaUserToken = createTenantUserAndToken("beta_viewer_user", Role.VIEWER, TENANT_BETA);

        // Tenant Beta tries to inspect Tenant Alpha's event -> MUST return 404 Not Found (IDOR blocked)
        mockMvc.perform(get("/api/v1/operations/events/" + alphaPrivateEventKey)
                        .header("Authorization", "Bearer " + betaUserToken)
                        .header("X-Tenant-ID", TENANT_BETA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Event not found with idempotency key")));
    }

    @Test
    @DisplayName("7. Cross-Tenant Search Isolation: Query only returns caller tenant's events")
    void crossTenantSearchIsolation_queryReturnsOnlyCallerEvents() throws Exception {
        String keyAlpha = "search-alpha-" + UUID.randomUUID();
        String keyBeta = "search-beta-" + UUID.randomUUID();
        String sku = "SKU-SEARCH-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, sku, 10);
        createIsolatedInventory(TENANT_BETA, sku, 10);

        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest(keyAlpha, "INVENTORY_DEDUCT", sku, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest(keyBeta, "INVENTORY_DEDUCT", sku, 1))))
                .andExpect(status().isOk());

        String betaToken = createTenantUserAndToken("beta_operator_user", Role.OPERATOR, TENANT_BETA);

        // Search events as Tenant Beta
        mockMvc.perform(get("/api/v1/operations/events")
                        .header("Authorization", "Bearer " + betaToken)
                        .header("X-Tenant-ID", TENANT_BETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].idempotencyKey", hasItem(keyBeta)))
                .andExpect(jsonPath("$.content[*].idempotencyKey", not(hasItem(keyAlpha))));
    }

    @Test
    @DisplayName("8. Cross-Tenant Dead-Letter Protection: Tenant B cannot reprocess Tenant A's dead letter")
    void crossTenantDeadLetterReprocess_blocked() throws Exception {
        String alphaDlKey = "dl-alpha-" + UUID.randomUUID();
        ProcessedEvent dlEvent = new ProcessedEvent(
                TENANT_ALPHA,
                alphaDlKey,
                "INVENTORY_DEDUCT",
                "SKU-MISSING",
                "hash-dl",
                EventStatus.DEAD_LETTER.name(),
                null,
                500
        );
        processedEventRepository.save(dlEvent);

        String betaOpToken = createTenantUserAndToken("beta_op_dl", Role.OPERATOR, TENANT_BETA);

        // Tenant Beta operator attempts to reprocess Tenant Alpha's dead letter -> MUST return 404
        mockMvc.perform(post("/api/v1/admin/dead-letters/" + alphaDlKey + "/reprocess")
                        .header("Authorization", "Bearer " + betaOpToken)
                        .header("X-Tenant-ID", TENANT_BETA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Event not found")));
    }

    @Test
    @DisplayName("9. Payload Fingerprint Conflict is Scoped by Tenant")
    void payloadConflictScopedByTenant() throws Exception {
        String key = "fingerprint-test-" + UUID.randomUUID();
        String sku = "SKU-FP-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, sku, 20);
        createIsolatedInventory(TENANT_BETA, sku, 20);

        EventRequest reqA1 = new EventRequest(key, "INVENTORY_DEDUCT", sku, 1);
        EventRequest reqB1 = new EventRequest(key, "INVENTORY_DEDUCT", sku, 19); // different payload!

        // Tenant Alpha succeeds with quantity 1
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA1)))
                .andExpect(status().isOk());

        // Tenant Beta submits SAME key with DIFFERENT payload (quantity 19)
        // MUST SUCCEED because tenants are different!
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB1)))
                .andExpect(status().isOk());

        // Tenant Alpha re-submits SAME key with DIFFERENT payload (quantity 5)
        // MUST FAIL with 409 Conflict within the same tenant
        EventRequest reqAConflict = new EventRequest(key, "INVENTORY_DEDUCT", sku, 5);
        mockMvc.perform(post("/api/v1/events")
                        .header("X-Tenant-ID", TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAConflict)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("different request payload")));
    }

    @Test
    @DisplayName("10. Concurrent Submissions for Different Tenants with Identical Keys")
    void concurrentSubmissions_differentTenants_noCollisions() throws Exception {
        int threads = 10;
        String sharedKey = "concurrent-multi-" + UUID.randomUUID();
        String skuAlpha = "SKU-CONC-A-" + UUID.randomUUID().toString().substring(0, 6);
        String skuBeta = "SKU-CONC-B-" + UUID.randomUUID().toString().substring(0, 6);

        createIsolatedInventory(TENANT_ALPHA, skuAlpha, 100);
        createIsolatedInventory(TENANT_BETA, skuBeta, 100);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String tenant = (i % 2 == 0) ? TENANT_ALPHA : TENANT_BETA;
            final String sku = (i % 2 == 0) ? skuAlpha : skuBeta;
            EventRequest request = new EventRequest(sharedKey, "INVENTORY_DEDUCT", sku, 1);

            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);

                MvcResult result = mockMvc.perform(post("/api/v1/events")
                                .header("X-Tenant-ID", tenant)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Integer> f : futures) {
            int status = f.get(10, TimeUnit.SECONDS);
            assertThat(status).isEqualTo(200);
        }

        executor.shutdown();

        // Exactly 1 deduction per tenant (100 - 1 = 99 for both)
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_ALPHA, skuAlpha).orElseThrow().getAvailableQuantity()).isEqualTo(99);
        assertThat(inventoryRepository.findByTenantIdAndResourceId(TENANT_BETA, skuBeta).orElseThrow().getAvailableQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("11. Client-Supplied Tenant ID Cannot Override Authenticated Server-Side Tenant Identity")
    void clientSuppliedTenantId_cannotOverrideServerSideIdentity() throws Exception {
        String alphaUserToken = createTenantUserAndToken("alpha_submitter", Role.OPERATOR, TENANT_ALPHA);
        String sku = "SKU-SPOOF-" + UUID.randomUUID().toString().substring(0, 6);
        createIsolatedInventory(TENANT_ALPHA, sku, 10);
        createIsolatedInventory(TENANT_BETA, sku, 10);

        // Authenticated user belonging to Tenant Alpha tries to submit with body tenantId = Tenant Beta
        EventRequest spoofRequest = new EventRequest("spoof-key-01", "INVENTORY_DEDUCT", sku, 1, TENANT_BETA);

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + alphaUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spoofRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("conflicts with authenticated/verified server-side tenant")));
    }

    @Test
    @DisplayName("12. Platform Administrator vs Tenant-Level User Authorization Boundaries")
    void platformAdminVsTenantUser_authorizationBoundaries() throws Exception {
        String adminToken = createTenantUserAndToken("platform_admin", Role.ADMIN, "DEFAULT_BUSINESS");
        String betaOpToken = createTenantUserAndToken("beta_boundary_op", Role.OPERATOR, TENANT_BETA);

        // 1. Platform admin can access audit logs
        mockMvc.perform(get("/api/v1/operations/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 2. Tenant operator cannot access audit logs (403 Forbidden)
        mockMvc.perform(get("/api/v1/operations/audit-logs")
                        .header("Authorization", "Bearer " + betaOpToken))
                .andExpect(status().isForbidden());

        // 3. Tenant operator cannot access user management (403 Forbidden)
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + betaOpToken))
                .andExpect(status().isForbidden());

        // 4. Platform admin without X-Tenant-ID operates in bypass mode and sees cross-tenant metrics
        mockMvc.perform(get("/api/v1/operations/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseEvents.totalEvents", greaterThan(0)));
    }
}
