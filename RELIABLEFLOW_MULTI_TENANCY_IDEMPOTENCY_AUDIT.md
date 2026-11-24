# RELIABLEFLOW — MULTI-TENANT & IDEMPOTENCY ARCHITECTURE AUDIT REPORT

**Date:** November 15, 2026  
**Auditor:** Antigravity System Audit Engine  
**Target Repository:** `c:\Users\karan\Downloads\reliable-event-processor`  
**Target Components:** Reliable Event Processing Platform (Spring Boot 3.4.3, Java 25 / PostgreSQL 18 / Kafka 3.8.1) & ReliableFlow Console (React 19 / Vite / Tailwind)  
**Audit Mode:** READ-ONLY TECHNICAL AUDIT (Zero application code modified)

---

## 1. Executive Summary

ReliableFlow (Reliable Event Processing Platform) was audited to evaluate its readiness as a reusable, multi-tenant idempotency and reliable transaction platform for small businesses.

### Overall Architecture Maturity
The platform demonstrates **high technical maturity in single-tenant transactional reliability, local concurrency control, failure retry orchestration, and internal operations monitoring**. It cleanly implements the Transactional Outbox pattern, pessimistic row locking, deterministic SHA-256 payload fingerprinting, asynchronous dead-letter queue (DLQ) transitions with exponential backoff, and dual-layer duplicate submission defense.

### Current Implementation Stage
The current system is at **Stage 1 (Single-Tenant Internal Service)**, functioning as a specialized internal inventory-deduction engine equipped with an operator/administrator operations console (`ReliableFlow Console`).

### Is the System Genuinely Multi-Tenant?
**NO.** The platform contains **zero multi-tenancy implementation**.
- There is no tenant or business entity in the database schema (Flyway migrations `V1` through `V6`).
- Idempotency keys are enforced via a **global `UNIQUE` constraint** on `processed_events.idempotency_key`. If Business A and Business B submit the same idempotency key (e.g., `order-1001`), the second business is either blocked with HTTP `409 Conflict` (cross-tenant collision) or receives the first business's cached response (cross-tenant data leakage).
- All operational inspection, search, metrics, outbox, and DLQ APIs lack tenant context and ownership validation.
- User management is strictly global RBAC (`ADMIN`, `OPERATOR`, `VIEWER`), with no concept of business accounts or tenant segregation.

### Is It Ready for External Small-Business Integration?
**NO.** The platform cannot currently be integrated safely by external businesses:
1. **Public Ingestion Endpoint:** `POST /api/v1/events` is completely unauthenticated (`.permitAll()`), with rate limiting applied solely per client IP address.
2. **Coupled Business Logic:** The ingestion DTO (`EventRequest`) strictly enforces `@Pattern(regexp = "^INVENTORY_DEDUCT$")`. Any arbitrary business payload is rejected with HTTP `400 Bad Request`.
3. **Internal Database Mutation:** Business operations directly modify an internal `inventory` table using Spring Data JPA. There are no webhooks, callback dispatchers, external integration adapters, or client SDKs.
4. **No Lifecycle / Retention Policies:** Idempotency records, outbox events, and audit logs accumulate indefinitely with zero TTL, expiry, or automated cleanup mechanism.

### Most Critical Missing Capabilities
1. **Tenant Model & Scoped Idempotency:** Composite uniqueness on `(tenant_id, idempotency_key)` and tenant-aware persistence queries.
2. **Business Authentication & API Key Management:** Per-tenant hashed API keys with rotation and scoped permissions.
3. **Generic Operation Contracts & Pluggable Handlers:** Decoupling the reliability engine from `INVENTORY_DEDUCT` and supporting arbitrary JSON payloads.
4. **External Integration Adapters / Webhooks:** Durable callback mechanism with retry and HMAC signature verification for calling small-business external APIs.
5. **Data Lifecycle & Retention:** Automated, tenant-configurable retention policies with safe archiving.

---

## 2. Feature Status Matrix

| # | Requirement | Status | Evidence (Files & Entities) | Missing Work | Priority |
|---|---|---|---|---|---|
| **1** | **Multi-tenancy** | **MISSING** | `V2__create_inventory_and_event_tables.sql`<br>`ProcessedEvent.java`<br>`ProcessedEventRepository.java` | Tenant entity, `tenant_id` columns, composite uniqueness, query isolation, tenant context propagation. | **P0 (Blocker)** |
| **2** | **Business registration & credentials** | **MISSING** | `V6__create_users_table.sql`<br>`SecurityConfig.java`<br>`JwtAndApiKeyAuthenticationFilter.java` | Business registration flow, tenant-scoped API keys, key hashing, secret rotation, per-tenant rate limits. | **P0 (Blocker)** |
| **3** | **Generic operation contracts** | **PARTIALLY IMPLEMENTED** | `ReliabilityEngine.java`<br>`EventRequest.java`<br>`RetryExecutionService.java` | Request validation decoupled from inventory, event handler registry, generic response envelope, domain exception isolation. | **P1 (High)** |
| **4** | **Integration adapters** | **MISSING** | `EventController.java`<br>`InventoryService.java`<br>`EventKafkaConsumer.java` | Webhook dispatcher, HTTP callback adapter, HMAC signature verification, durable delivery tracking, client SDK. | **P1 (High)** |
| **5** | **Concurrency safety** | **FULLY IMPLEMENTED AND VERIFIED** | `ReliabilityEngine.java:88-157`<br>`EventProcessingIntegrationTest.java:276-337`<br>`audit_concurrency_verifier.mjs` | Multi-tenant concurrency tests; current implementation verified for single-tenant global scope. | **P2 (Medium)** |
| **6** | **Response replay** | **FULLY IMPLEMENTED AND VERIFIED** | `ReliabilityEngine.java:207-256`<br>`processed_events (response_payload, request_payload_hash)` | Scoping replay cache to tenant; verified working for identical payload and 409 conflict on altered payload. | **P2 (Medium)** |
| **7** | **Retention & data lifecycle** | **MISSING** | `ProcessedEvent.java`<br>`OutboxEvent.java`<br>`OperationalAuditLog.java` | Retention periods, `expires_at` indexing, scheduled purge/archive worker, expired key reuse rules. | **P1 (High)** |
| **8** | **Business dashboard** | **PARTIALLY IMPLEMENTED** | `frontend/src/pages/DashboardPage.tsx`<br>`frontend/src/types/auth.ts`<br>`OperationsController.java` | Tenant filtering in backend controllers, business switcher/views, tenant API consumption analytics, self-service settings. | **P1 (High)** |

---

## 3. Evidence for Each Requirement

### Part 1 — Multi-Tenancy

**Status: MISSING**

#### Technical Evidence:
1. **Database Schema:**
   - In `src/main/resources/db/migration/V2__create_inventory_and_event_tables.sql`:
     ```sql
     CREATE TABLE processed_events (
         id BIGSERIAL PRIMARY KEY,
         idempotency_key VARCHAR(255) NOT NULL UNIQUE,
         ...
     );
     ```
     Constraint `processed_events_idempotency_key_key UNIQUE (idempotency_key)` is **global**. There is no `tenant_id` or `business_id` column in any table across all 6 migrations.
2. **JPA Entities & Repositories:**
   - `src/main/java/com/eventprocessor/reliable_event_processor/entity/ProcessedEvent.java` does not contain any tenant field.
   - `src/main/java/com/eventprocessor/reliable_event_processor/repository/ProcessedEventRepository.java` queries by global key:
     ```java
     Optional<ProcessedEvent> findByIdempotencyKey(String idempotencyKey);
     ```
3. **Outbox & Kafka:**
   - `outbox_events` (`V4`) and `KafkaEventMessage.java` contain only `eventId`, `eventType`, `resourceId`, `idempotencyKey`, `payload`, `timestamp`. Zero tenant identifiers exist in messaging headers or payloads.
4. **Audit Logs:**
   - `operational_audit_logs` (`V5`) stores `principal` and `target_identifier`, with no tenant attribution.

#### Verification Scenario & Live Test Results:
A live verification script (`scratch/audit_concurrency_verifier.mjs`) was executed against the running backend:
- **Scenario:** Business A submitted an event with idempotency key `tenant-shared-order-1794729752216` and quantity `1`.
- **Result A:** Returned HTTP `200 OK`, `idempotentReplay: false`.
- **Scenario:** Business B submitted the **same idempotency key** `tenant-shared-order-1794729752216` with its own payload (quantity `2`).
- **Result B:** Returned HTTP `409 Conflict` with error:
  `"Idempotency key 'tenant-shared-order-1794729752216' has already been used with a different request payload"`.
- **Finding:** Business B was completely blocked from processing its order because Business A had registered the key first.

---

### Part 2 — Business Registration and Credential Management

**Status: MISSING**

#### Technical Evidence:
1. **User Table & Roles:**
   - `src/main/resources/db/migration/V6__create_users_table.sql` and `AppUser.java`:
     Columns: `id`, `username`, `email`, `password_hash`, `role`, `status`.
   - `Role.java`:
     ```java
     public enum Role { ADMIN, OPERATOR, VIEWER }
     ```
     Only global system administration roles exist. There is no concept of a Business / Tenant Owner or API consumer.
2. **Registration:**
   - There is no self-registration endpoint. Users can only be created by an active `ADMIN` via `POST /api/v1/admin/users` (`AdminUserController.java:28`).
3. **API Keys:**
   - The platform supports only a **single static administrative API key** configured in `application.properties`:
     ```properties
     event-processor.admin.api-key=admin-secret-key-12345
     ```
   - In `JwtAndApiKeyAuthenticationFilter.java:54`:
     ```java
     if (apiKeyHeader.equals(adminSecurityProperties.getApiKey())) { ... }
     ```
     Comparison is performed directly against a plaintext string. There is no database table for API keys, no cryptographic hashing, no scoping, no rotation, and no revocation.
4. **Rate Limiting:**
   - `IngestionRateLimiter.java:38-57` maintains an in-memory `ConcurrentHashMap<String, Counter>` keyed strictly by `clientIp`. No per-business or per-key quotas exist.

---

### Part 3 — Generic Operation Contracts

**Status: PARTIALLY IMPLEMENTED**

#### Technical Evidence:
1. **The Reusable Core:**
   - `ReliabilityEngine.java:67-76` defines a generic method signature:
     ```java
     public <R> R executeIdempotent(
             String idempotencyKey, String eventType, String resourceId,
             String payloadToHash, String rawPayloadJson, Class<R> responseType,
             Supplier<R> businessOperation, UnaryOperator<R> replayTransformer)
     ```
2. **Coupling Defect 1 — JSR-303 Hardcoded Validation:**
   - `src/main/java/com/eventprocessor/reliable_event_processor/dto/EventRequest.java:13`:
     ```java
     @Pattern(regexp = "^INVENTORY_DEDUCT$", message = "eventType must be INVENTORY_DEDUCT")
     String eventType,
     ...
     Integer quantity
     ```
     Any request that is not `INVENTORY_DEDUCT` with an integer `quantity` is immediately rejected at the controller boundary.
3. **Coupling Defect 2 — Hardcoded Exception Handling:**
   - In `ReliabilityEngine.java:140-149`, the engine catches domain-specific inventory exceptions:
     ```java
     catch (InsufficientInventoryException ex) { ... }
     catch (ResourceNotFoundException ex) { ... }
     ```
4. **Coupling Defect 3 — Unchecked ClassCast in Replay and Transient Failure:**
   - In `ReliabilityEngine.java:189-203` and `242-255`, the engine constructs a concrete `EventResponse` (with `deductedQuantity` and `remainingQuantity`) and casts it to `(R) response`. If a caller passes any other `responseType`, a `ClassCastException` is thrown.
5. **Coupling Defect 4 — Retry Execution:**
   - `RetryExecutionService.java:115-117` and `184-194` explicitly parses `"quantity"` from the stored JSON and invokes `inventoryService.deductInventory(...)`.
   - There is no `OperationHandlerRegistry` or strategy pattern.

---

### Part 4 — Integration Adapters

**Status: MISSING**

#### Technical Evidence:
1. **No External Integration Layer:**
   - A recursive scan across `src/main` for `webhook`, `callback`, `adapter`, `RestTemplate`, `WebClient`, `HttpClient`, or `SDK` yields **zero occurrences**.
2. **Architecture Model:**
   - ReliableFlow operates solely as an **internal stateful monolith**. All business effects are applied directly to its internal PostgreSQL database (`inventory` table).
   - Kafka integration (`OutboxPublisherWorker` & `EventKafkaConsumer`) is internal to the application's own cluster, not an external webhook dispatch mechanism.
3. **No External Webhook Delivery:**
   - There is no mechanism to dispatch signed HTTP POST requests to a business's webhook URL, verify HMAC signatures (`X-ReliableFlow-Signature`), handle backoff for failing endpoints, or decouple transaction commits from external HTTP latency.

---

### Part 5 — Concurrency Safety

**Status: FULLY IMPLEMENTED AND VERIFIED**

#### Technical Evidence:
1. **Three-Layer Concurrency Defense:**
   - **Layer 1 (Fast-Path Read):** `ReliabilityEngine.java:80` reads `processedEventRepository.findByIdempotencyKey(...)` outside the transaction to short-circuit known requests without lock contention.
   - **Layer 2 (Row Locking):** Inside `transactionTemplate.execute(...)`, the business operation executes with row-level locks on the resource (e.g., `inventoryRepository.findByResourceIdWithLock(...)` with `PESSIMISTIC_WRITE`).
   - **Layer 3 (Database Uniqueness & Race Recovery):** `processed_events.idempotency_key` is backed by a PostgreSQL unique B-tree index. If two threads race past Layer 1 simultaneously, one commits while the other triggers a `DataIntegrityViolationException`. In `ReliabilityEngine.java:150-157`:
     ```java
     } catch (DataIntegrityViolationException ex) {
         log.info("Detected concurrent duplicate submission...");
         ProcessedEvent winnerEvent = processedEventRepository.findByIdempotencyKey(idempotencyKey)
                 .orElseThrow(() -> ex);
         return handleExistingEvent(winnerEvent, payloadHash, responseType, replayTransformer);
     }
     ```
2. **Automated Test Coverage:**
   - `EventProcessingIntegrationTest.java:276-337` (`test9_concurrentDuplicateSubmissions_applyEffectOnlyOnce`) tests 6 concurrent threads with `CountDownLatch` barrier.
3. **Live Verification Test Results:**
   - Executed `scratch/audit_concurrency_verifier.mjs` against live server on port 8080:
     - 10 simultaneous HTTP requests issued for key `audit-concurrent-key-1794729749723`.
     - **Fresh executions:** Exactly 1 (`idempotentReplay: false`).
     - **Replay responses:** Exactly 9 (`idempotentReplay: true`).
     - **HTTP Status:** 200 OK on all 10 requests.
     - **Data consistency:** Inventory deducted exactly once (from 7 to 6).

---

### Part 6 — Response Replay

**Status: FULLY IMPLEMENTED AND VERIFIED**

#### Technical Evidence:
1. **Storage of Response State:**
   - `processed_events` table stores:
     - `response_payload TEXT` (full JSON response).
     - `response_status_code INT` (e.g., 200).
     - `request_payload_hash VARCHAR(64)` (SHA-256 fingerprint).
     - `status VARCHAR(50)` (`PROCESSED`, `RETRY_PENDING`, `DEAD_LETTER`).
2. **Payload Fingerprint Verification:**
   - In `ReliabilityEngine.java:211-218`:
     If an existing record is found but `!event.getRequestPayloadHash().equals(currentPayloadHash)`, it throws `IdempotencyConflictException`, which `GlobalExceptionHandler.java:31` maps to HTTP `409 Conflict`.
3. **Replay Mechanism:**
   - In `ReliabilityEngine.java:224-231`:
     ```java
     if (EventStatus.PROCESSED.name().equals(event.getStatus())) {
         R cachedResponse = objectMapper.readValue(event.getResponsePayload(), responseType);
         return replayTransformer != null ? replayTransformer.apply(cachedResponse) : cachedResponse;
     }
     ```
     The actual cached response is deserialized and returned with `idempotentReplay = true`.
4. **Live Verification Test Results:**
   - Identical replay: Returns original response body with `idempotentReplay: true`.
   - Modified payload on same key: Returns HTTP `409 Conflict` with:
     `"Idempotency key '...' has already been used with a different request payload"`.

---

### Part 7 — Retention and Data Lifecycle Policies

**Status: MISSING**

#### Technical Evidence:
1. **No Expiry or TTL Columns:**
   - Neither `processed_events`, `outbox_events`, nor `operational_audit_logs` contains an `expires_at`, `ttl`, or `retention_until` timestamp.
2. **No Scheduled Cleanup Worker:**
   - `@Scheduled` annotations in the project are confined to:
     - `RetryWorker.java:49` (every 2000ms for `RETRY_PENDING` polling).
     - `OutboxPublisherWorker.java:48` (every 2000ms for `PENDING` outbox polling).
     - `IngestionRateLimiter.java:59` and `LoginRateLimiter.java:85` (in-memory IP counters).
   - There is no scheduled task to delete, archive, or partition old records.
3. **Risk Analysis:**
   - Tables will grow monotonically in production.
   - Deleting an idempotency record without proper lifecycle safeguards would allow an older duplicate request from a client or network retry to re-execute against the business database.

---

### Part 8 — Business-Level Dashboard

**Status: PARTIALLY IMPLEMENTED**

#### Technical Evidence:
1. **Frontend Architecture:**
   - `frontend/src/` provides an administrative console (`ReliableFlow Console`) containing:
     - `DashboardPage.tsx` (system-wide event counts, outbox publication status, subsystem health).
     - `EventExplorerPage.tsx` (global event search with status, resource, date filters).
     - `DeadLetterPage.tsx` (global DLQ list and operator-initiated reprocess modal).
     - `OutboxPage.tsx` (global transactional outbox monitor).
     - `MonitoringPage.tsx` (Actuator and JVM operational metrics).
     - `AuditLogsPage.tsx` (system-wide administrative audit logs).
     - `UsersPage.tsx` (global user administration).
2. **Absence of Tenant Filtering:**
   - `frontend/src/types/auth.ts`: `UserProfileResponse` contains only `id`, `username`, `email`, `role`, `status`. No tenant affiliation exists.
   - `frontend/src/services/eventService.ts`: `searchEvents(...)` calls `/api/v1/operations/events` with no tenant headers or query parameters.
3. **Backend Exposure (IDOR):**
   - In `OperationsController.java:48-63` and `DeadLetterController.java:32-41`, all queries fetch rows directly across the entire database. Any user with role `VIEWER` or `OPERATOR` can view and inspect all events across all businesses.

---

## 4. Security Findings

| Severity | ID | Vulnerability / Risk | Affected Components | Impact |
|---|---|---|---|---|
| **CRITICAL** | **SEC-01** | **Global Cross-Tenant Idempotency Collision** | `processed_events.idempotency_key`<br>`ReliabilityEngine.java:211` | Business B using a common key format (e.g. `order-101`) is blocked with HTTP 409 or receives Business A's cached response. |
| **CRITICAL** | **SEC-02** | **Insecure Direct Object Reference (IDOR) & Cross-Tenant Data Leakage** | `OperationsController.java:70`<br>`DeadLetterController.java:47` | Any logged-in user with `VIEWER` role can query all events and payloads across all businesses by key. |
| **CRITICAL** | **SEC-03** | **Unauthenticated Public Ingestion Endpoint** | `SecurityConfig.java:82`<br>`EventController.java:30` | `POST /api/v1/events` is `.permitAll()`. Anyone on the network can ingest events without authentication or API key. |
| **HIGH** | **SEC-04** | **Static Plaintext Administrative API Key** | `AdminSecurityProperties.java`<br>`JwtAndApiKeyAuthenticationFilter.java:54` | A single hardcoded key (`admin-secret-key-12345`) grants full `ROLE_ADMIN` without hashing, rotation, or audit trail. |
| **HIGH** | **SEC-05** | **Cross-Tenant Unauthorized Reprocessing** | `DeadLetterController.java:63` | An operator can trigger reprocessing on any business's dead-lettered event without tenant authorization checks. |
| **MEDIUM** | **SEC-06** | **Unbounded Table Growth & Denial of Service** | `processed_events`<br>`outbox_events`<br>`operational_audit_logs` | Lack of retention policy causes indefinite database growth, degrading index performance over time. |
| **LOW** | **SEC-07** | **In-Memory Volumetric Rate Limiting** | `IngestionRateLimiter.java` | Rate limits are kept in JVM memory per IP; does not enforce per-business tier quotas and resets on restart. |

---

## 5. Architecture Gap Analysis

```
========================================================================================
CURRENT ARCHITECTURE (Single-Tenant Internal Monolith)
========================================================================================
Client Request ---> [POST /api/v1/events (Public)]
                          │
                          ▼
            [EventController (INVENTORY_DEDUCT only)]
                          │
                          ▼
            [ReliabilityEngine (Global Key Check)]
                          │
                          ├──> [processed_events (idempotency_key UNIQUE)]
                          │
                          ├──> [InventoryService (Pessimistic Lock on internal inventory table)]
                          │
                          └──> [outbox_events -> Kafka Consumer -> (Re-deduct internal inventory)]
                          
Console Frontend ---> [Global ADMIN / OPERATOR / VIEWER] ---> [View All Events Globally]

========================================================================================
REQUIRED MULTI-TENANT ARCHITECTURE (Reusable Small-Business Platform)
========================================================================================
Small Business App ---> [POST /api/v1/operations]
                              │  Header: X-Tenant-API-Key: rflow_live_...
                              ▼
            [TenantAuthenticationFilter & Per-Tenant Rate Limiter]
                              │
                              ▼
            [Generic Operation Gateway (Arbitrary JSON Payload)]
                              │
                              ▼
            [Multi-Tenant Reliability Engine]
                              │
                              ├──> [processed_events (tenant_id + idempotency_key UNIQUE)]
                              │
                              ├──> [Pluggable Integration Adapter / Webhook Dispatcher]
                              │         │  (Dispatches signed HTTP POST to Business API)
                              │         ▼
                              │    [External Small Business Backend (External DB)]
                              │
                              └──> [Retention & Archival Worker (Per-Tenant Retention Policy)]

Tenant Console / Portal ---> [Tenant JWT (tenant_id claim)] ---> [Scoped Queries (tenant_id = :tenant)]
```

---

## 6. Implementation Roadmap

### PHASE A — Critical Correctness and Tenant Isolation (P0)
1. **Schema Migration `V7`:**
   - Create `tenants` table (`id`, `tenant_id VARCHAR(50) UNIQUE`, `name`, `status`, `created_at`).
   - Alter `processed_events`, `outbox_events`, `operational_audit_logs` to add `tenant_id NOT NULL REFERENCES tenants(tenant_id)`.
   - Drop global unique constraint on `processed_events.idempotency_key`.
   - Add composite unique constraint: `UNIQUE (tenant_id, idempotency_key)`.
2. **Backend Engine Refactoring:**
   - Update `ProcessedEvent` and `OutboxEvent` entities with `tenantId`.
   - Update `ProcessedEventRepository` to require `tenantId` on all lookups and queries.
   - Refactor `ReliabilityEngine.executeIdempotent` to accept `tenantId`.
3. **Tenant Security Filter:**
   - Introduce `TenantContext` ThreadLocal populated during authentication.

### PHASE B — Business Registration and Credential Management (P0)
1. **Schema Migration `V8`:**
   - Create `tenant_api_keys` table (`id`, `tenant_id`, `key_prefix`, `key_hash`, `scopes`, `status`, `expires_at`, `created_at`).
2. **Business Registration API:**
   - `POST /api/v1/tenants/register` (creates business account and returns initial secret API key `rflow_live_...`).
3. **Authentication Hardening:**
   - Secure SHA-256 API key hashing.
   - Key rotation (`POST /api/v1/tenants/api-keys/rotate`) and revocation endpoints.
   - Protect `POST /api/v1/operations` with tenant API key validation.

### PHASE C — Generic Operation Contracts & Integration Adapters (P1)
1. **Decouple Event Request DTO:**
   - Remove `@Pattern(regexp = "^INVENTORY_DEDUCT$")` from ingestion model.
   - Define `OperationRequest(idempotencyKey, operationType, resourceId, payload: JsonNode, metadata: JsonNode)`.
2. **Operation Handler Registry & Webhook Adapter:**
   - Create `OperationHandler` interface with `execute(...)` and `compensate(...)`.
   - Implement `WebhookOperationAdapter` that dispatches signed HTTP calls (`HMAC-SHA256`) to the small business's external webhook endpoint.
   - Remove hardcoded `InsufficientInventoryException` and `InventoryService` calls from `ReliabilityEngine` and `RetryExecutionService`.

### PHASE D — Concurrency Safety and Response Replay Verification (P2)
1. **Tenant-Scoped Concurrency Tests:**
   - Automated integration tests verifying 10+ concurrent requests sharing the same `idempotencyKey` across **different tenants** run independently without interference.
2. **Multi-Tenant Replay Verification:**
   - Verify that Business A and Business B can reuse identical key names with independent payloads and independent cached responses.

### PHASE E — Retention Policies and Lifecycle Management (P1)
1. **Retention Configuration:**
   - Add `retention_days` column to `tenants` table.
   - Add `expires_at` column and B-tree index to `processed_events` and `outbox_events`.
2. **Scheduled Archival Worker:**
   - Implement `DataRetentionWorker` that safely purges or archives expired records.
   - Guard against active/in-flight (`PROCESSING`, `RETRY_PENDING`) records being deleted.

### PHASE F — Business-Specific Dashboard and Analytics (P1)
1. **Backend Scoped APIs:**
   - Ensure all endpoints in `OperationsController` and `DeadLetterController` enforce `WHERE tenant_id = :currentTenantId`.
2. **Frontend Tenant Context:**
   - Update `ReliableFlow Console` to support tenant switching for platform admins and strict tenant views for business accounts.
   - Add per-tenant API usage, quota consumption, and dead-letter metrics.

---

## 7. Final Verdict

1. **Can two independent small businesses use the current platform safely?**  
   **No.** Idempotency keys are global. If both businesses use the same key, one will be blocked with a 409 error or receive the other's cached response.

2. **Can each business generate and manage its own credentials?**  
   **No.** There is only a single static administrator API key and global user accounts. No business credential management exists.

3. **Can an external business application integrate without sharing its internal database?**  
   **No.** The current platform directly mutates an internal PostgreSQL `inventory` table. It does not provide webhooks or external HTTP adapters.

4. **Is idempotency scoped correctly by business?**  
   **No.** Idempotency is scoped globally across the entire database table.

5. **Is concurrency safety proven?**  
   **Yes (for single-tenant scope).** Verified via 10 concurrent requests and automated integration tests using row locking and database constraint race handling.

6. **Does duplicate response replay work correctly?**  
   **Yes (for single-tenant scope).** Identical requests return original cached responses with `idempotentReplay: true`; altered payloads return HTTP 409 Conflict.

7. **Are retention and expiry policies implemented?**  
   **No.** There are zero TTL columns, zero retention configurations, and zero scheduled purge jobs.

8. **Does each business have its own isolated dashboard?**  
   **No.** The dashboard is a single-tenant administrative console displaying all records globally to any authenticated operator.

9. **What is the biggest architectural limitation?**  
   The complete absence of a **Tenant ID column and composite uniqueness constraint** across the database schema, combined with the hardcoding of inventory domain logic directly inside the reliability engine.

10. **What should be implemented first?**  
    **Phase A (Multi-Tenant Schema & Isolation):** Introduce the `tenants` table, add `tenant_id` to all tables, and change `processed_events` to `UNIQUE (tenant_id, idempotency_key)`.

---
*Report generated and grounded against actual source code, database migrations, and live verification test results.*
