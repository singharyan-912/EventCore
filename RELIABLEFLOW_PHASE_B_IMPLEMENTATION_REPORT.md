# ReliableFlow — Phase B: Business Registration & API Credential Management
## Implementation Report

**Date:** 2026-11-20  
**Phase:** B — Business Registration & API Credential Management  
**Status:** ✅ COMPLETE  
**Backend Tests:** 90/90 passed (12 new Phase B tests + 78 existing)  
**Frontend Build:** ✅ 0 TypeScript errors · 0 lint errors/warnings (91 files)

---

## 1. Executive Summary

Phase B extends ReliableFlow with secure business self-registration and API credential management, completing the external on-boarding flow that Phase A's multi-tenant schema made possible. Any business can now register independently, receive a unique API key in the format `rf_live_<48 hex chars>`, and immediately start ingesting events through the same REST API — authenticated by either the API key or the existing JWT flow.

All Phase A tenant isolation, idempotency scoping, and audit-logging behaviour is fully preserved. No existing API contracts, Flyway migrations, or RBAC roles were modified.

---

## 2. Scope

### Implemented in Phase B

| Area | Capability |
|---|---|
| Business Registration | `POST /api/v1/businesses/register` — public endpoint, creates Tenant + OPERATOR AppUser |
| Registration Safety | Email normalisation, duplicate-email rejection, IP-based rate limiting (10 req/hour), Bean validation |
| API Key Generation | `rf_live_<48 hex chars>` format, cryptographically random, full secret shown exactly once |
| API Key Storage | SHA-256 hash stored; raw secret never persisted |
| API Key Authentication | `X-API-Key` header support in `JwtAndApiKeyAuthenticationFilter`, timing-safe comparison |
| API Key CRUD | List, create, revoke, rotate endpoints under `/api/v1/api-keys` |
| Tenant Binding | Authenticated API key sets `TenantContext` server-side; no client-supplied `tenant_id` accepted |
| Privilege Isolation | API key grants `ROLE_OPERATOR` only; ADMIN endpoints remain inaccessible |
| Audit Logging | `BUSINESS_REGISTER`, `API_KEY_CREATE`, `API_KEY_REVOKE`, `API_KEY_ROTATE` events recorded |
| Frontend — Register | `/register` — public page; business registration form with post-success credential display |
| Frontend — API Keys | `/api-keys` — protected page; key table, create modal, one-time secret copy, revoke/rotate dialogs |
| Navigation | "API Keys" nav item added with `KeyRound` icon; "Register a new business" link on Login page |

### Explicitly Out of Scope (Phase B)

- Generic operation handlers / external adapters (Phase C)
- Business analytics / dashboard redesign (Phase F)
- Fine-grained permission scopes per API key
- Automated key-expiry enforcement daemon
- Docker or containerisation

---

## 3. Database Schema — `V8__create_api_keys_table.sql`

```sql
CREATE TABLE IF NOT EXISTS api_keys (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(50)  NOT NULL REFERENCES tenants(tenant_id),
    name         VARCHAR(100) NOT NULL,
    key_prefix   VARCHAR(20)  NOT NULL,   -- rf_live_ + first 8 chars (display only)
    secret_hash  VARCHAR(64)  NOT NULL,   -- SHA-256 hex of full key
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_by   VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_api_keys_prefix_status ON api_keys (key_prefix, status);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant_status ON api_keys (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant        ON api_keys (tenant_id);
```

**Design notes:**
- `tenant_id` carries a foreign key to `tenants.tenant_id` — a key cannot be orphaned.
- `key_prefix` is used for fast prefix lookup during authentication (index on `key_prefix, status`).
- `secret_hash` holds the SHA-256 hex digest only; the plaintext value is returned once at creation and then discarded.
- `expires_at` column is reserved for future automated expiry enforcement.

---

## 4. Business Registration

### Endpoint

```
POST /api/v1/businesses/register
Content-Type: application/json
Authorization: (none — public)
```

**Request body:**
```json
{
  "businessName": "Acme Corp",
  "ownerName":    "Jane Doe",
  "email":        "jane@acme.com",
  "phone":        "+1-555-0100",
  "password":     "SecurePass123!"
}
```

**Success response — 201 Created:**
```json
{
  "tenantId":      "biz-acme-corp-a3f9",
  "businessName":  "Acme Corp",
  "ownerUsername": "jane_acme_a3f9",
  "email":         "jane@acme.com",
  "role":          "OPERATOR",
  "message":       "Business registered successfully"
}
```

### Registration Flow (`BusinessRegistrationService`)

1. **Bean validation** — `@Valid` on the request; 400 returned immediately on constraint violation.
2. **IP-based rate limiting** — `RegistrationRateLimiter` enforces a sliding window of 10 registrations per hour per IP; 429 on breach.
3. **Email normalisation** — lowercased and trimmed before the duplicate check.
4. **Duplicate detection** — `AppUserRepository.existsByEmail()` → 409 if already registered.
5. **Tenant creation** — `tenant_id` generated as `biz-<slugified-name>-<4 hex chars>`; Tenant entity saved.
6. **User creation** — username generated as `<first part of email>_<suffix>`; password BCrypt-hashed; `AppUser` saved with `Role.OPERATOR` and `UserStatus.ACTIVE`.
7. **Audit log** — `BUSINESS_REGISTER` recorded under `DEFAULT_BUSINESS` tenant (TenantContext is not set during unauthenticated registration; using DEFAULT_BUSINESS avoids a FK violation while still persisting the audit event).

**Security properties:**
- Registration never creates an ADMIN user — the role is hardcoded to `OPERATOR` in `BusinessRegistrationService`.
- Passwords are BCrypt-hashed; no raw password is stored or returned.
- The rate limiter is IP-scoped to prevent registration flooding.

---

## 5. API Key Management

### Endpoints

| Method | Path | Role required | Description |
|---|---|---|---|
| `POST` | `/api/v1/api-keys` | OPERATOR | Create a new API key |
| `GET` | `/api/v1/api-keys` | OPERATOR | List keys for the authenticated tenant |
| `DELETE` | `/api/v1/api-keys/{id}` | OPERATOR | Revoke a key |
| `POST` | `/api/v1/api-keys/{id}/rotate` | OPERATOR | Rotate a key (revoke old, create new) |

All endpoints are tenant-scoped — operations are restricted to the calling tenant's keys.

### Key Format

```
rf_live_<48 hex characters>
```

Example: `rf_live_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6`

- Generated via `SecureRandom` (256 bits of randomness in the 48 hex suffix).
- The full key is returned exactly once in the `CreateApiKeyResponse.secret` field.
- Only the SHA-256 hex digest of the full key is stored in the database.
- The `key_prefix` column stores `rf_live_` + first 8 characters for display-only identification.

### Key Lifecycle (`ApiKeyService`)

```
CREATE → ACTIVE → (REVOKED | ROTATED)
```

- **Create:** generate random key → SHA-256 hash → persist `ApiKey` entity with `status=ACTIVE` → return full secret once.
- **List:** return all keys for the tenant; `secret` field is `null` in list responses.
- **Revoke:** set `status=REVOKED`, record `revoked_at`; immediate effect — next request with that key returns 401.
- **Rotate:** revoke the existing key, generate a fresh key, persist the new key, return the new secret once.

### Authentication Flow (`JwtAndApiKeyAuthenticationFilter`)

1. If `Authorization: Bearer ...` is present → standard JWT path (unchanged).
2. If `X-API-Key: rf_live_...` is present:
   a. Extract `key_prefix` (first 15 chars of the value).
   b. Look up `ApiKey` records by prefix with `status=ACTIVE` (index-backed).
   c. For each candidate, compute `SHA-256(presented key)` and compare with `secret_hash` using `MessageDigest.isEqual` (timing-safe).
   d. On match: load the associated `AppUser`, set `TenantContext`, set `UsernamePasswordAuthenticationToken` with `ROLE_OPERATOR`.
   e. Update `last_used_at` asynchronously.
   f. On no match or revoked: 401.

**Privilege isolation:** API key authentication grants only `ROLE_OPERATOR`. Endpoints requiring `ROLE_ADMIN` (e.g. tenant management, platform metrics) return 403 for all API key requests.

---

## 6. Security Properties

| Property | Implementation |
|---|---|
| Tenant isolation preserved | All Phase A `TenantContext` guards remain active; API key auth sets the same server-side context |
| No client-supplied tenant ID | `TenantContext` is derived from the authenticated identity (JWT claim or API key DB lookup) |
| Secret never stored raw | SHA-256 hash only; raw value discarded after `CreateApiKeyResponse` is returned |
| Timing-safe comparison | `MessageDigest.isEqual` used for hash comparison; resistant to timing attacks |
| Registration rate limiting | Sliding window (10/hour/IP) in `RegistrationRateLimiter`; 429 on breach |
| ADMIN privilege isolation | `ROLE_ADMIN` endpoints remain JWT-only; no API key can escalate to ADMIN |
| Audit trail | Registration and all key lifecycle events are written to `operational_audit_logs` |
| Cross-tenant key isolation | `ApiKeyRepository` queries always filter by `tenant_id`; a tenant cannot list, revoke, or rotate another tenant's keys |
| CORS | `X-API-Key` added to `SecurityConfig` `allowedHeaders` |

---

## 7. New Source Files

### Backend

| File | Purpose |
|---|---|
| `db/migration/V8__create_api_keys_table.sql` | Flyway migration — `api_keys` table and indexes |
| `entity/ApiKeyStatus.java` | Enum: `ACTIVE`, `REVOKED`, `EXPIRED` |
| `entity/ApiKey.java` | JPA entity mapping `api_keys` table |
| `repository/ApiKeyRepository.java` | Tenant-scoped JPA queries |
| `dto/CreateApiKeyRequest.java` | Request DTO (name field) |
| `dto/CreateApiKeyResponse.java` | Response DTO including one-time `secret` |
| `dto/ApiKeyResponse.java` | List/revoke response DTO (`secret` always null) |
| `dto/BusinessRegistrationRequest.java` | Registration request DTO with Bean Validation annotations |
| `dto/BusinessRegistrationResponse.java` | Registration success response DTO |
| `security/RegistrationRateLimiter.java` | IP-scoped sliding window rate limiter (10 req/hour) |
| `service/BusinessRegistrationService.java` | Full registration flow |
| `service/ApiKeyService.java` | Full key lifecycle (create, list, revoke, rotate, authenticate) |
| `controller/BusinessRegistrationController.java` | `POST /api/v1/businesses/register` |
| `controller/ApiKeyController.java` | CRUD endpoints for API keys |

### Modified Backend Files

| File | Change |
|---|---|
| `security/JwtAndApiKeyAuthenticationFilter.java` | Added `X-API-Key` header branch |
| `security/TenantContextFilter.java` | Reads `AUTHENTICATED_TENANT_ID` request attribute set by API key path |
| `config/SecurityConfig.java` | Permits registration endpoint; adds API key routes; adds `X-API-Key` to CORS |
| `exception/GlobalExceptionHandler.java` | Added 429 handler for `RateLimitExceededException` |
| `.mvn/maven.config` | Added `-Daether.syncContext.named.factory=noop` (Maven lock file fix on Windows) |

### Frontend

| File | Purpose |
|---|---|
| `frontend/src/types/business.ts` | TypeScript types for registration request/response |
| `frontend/src/types/apiKey.ts` | TypeScript types for API key entities and DTOs |
| `frontend/src/services/businessService.ts` | `registerBusiness()` API client |
| `frontend/src/services/apiKeyService.ts` | `listApiKeys()`, `createApiKey()`, `revokeApiKey()`, `rotateApiKey()` |
| `frontend/src/hooks/useApiKeys.ts` | React Query hook for key management state |
| `frontend/src/pages/RegisterPage.tsx` | Business registration form + post-success credential display |
| `frontend/src/pages/ApiKeysPage.tsx` | Key table, create modal, one-time secret copy, revoke/rotate dialogs |

### Modified Frontend Files

| File | Change |
|---|---|
| `frontend/src/routes.tsx` | Added `/register` (public) and `/api-keys` (protected) routes |
| `frontend/src/routes/navConfig.ts` | Added "API Keys" nav item with `KeyRound` icon |
| `frontend/src/pages/LoginPage.tsx` | Added `Link` import and "Register a new business" link |

---

## 8. Test Coverage — Phase B

**Test class:** `PhaseBBusinessRegistrationAndApiKeyIntegrationTest`  
**Result:** 12/12 PASSED

| # | Test Method | Verification Scenario |
|---|---|---|
| 1 | `testBusinessRegistration_success` | POST `/register` → 201; Tenant row created in DB; AppUser created as OPERATOR; audit log entry recorded |
| 2 | `testBusinessRegistration_duplicateEmailRejected` | Same email twice → second request returns 409 Conflict |
| 3 | `testBusinessRegistration_validationFailure` | Missing required fields → 400 Bad Request with validation details |
| 4 | `testBusinessRegistration_rateLimiterEnforced` | 11 rapid registrations from same IP → 11th returns 429 Too Many Requests |
| 5 | `testApiKey_creation_formatAndSecretReturnedOnce` | `POST /api-keys` → secret matches `rf_live_[0-9a-f]{48}`; DB stores hash only (hash ≠ plaintext); second GET of same key returns null secret |
| 6 | `testApiKey_listing_scopedToTenant` | Tenant A's key list does not include Tenant B's keys |
| 7 | `testApiKey_authentication_validKeyCanIngestEventUnderTenant` | Valid `X-API-Key` → event ingested under correct tenant; ProcessedEvent row verifiable in correct tenant's scope |
| 8 | `testApiKey_crossTenantIsolation_cannotAccessAnotherTenantData` | Tenant B uses its key to revoke Tenant A's key → 403 Forbidden |
| 9 | `testApiKey_revocation_immediatelyRejectsFurtherCalls` | Revoke key → next call with same key → 401 Unauthorized |
| 10 | `testApiKey_rotation_revokesOldAndReturnsNew` | Rotate key → old key returns 401; new key successfully authenticates |
| 11 | `testApiKey_cannotAccessPlatformAdminEndpoints` | API key holder hits admin endpoint → 403 Forbidden |
| 12 | `testApiKey_invalidOrMalformedKey_returns401` | Random / malformed `X-API-Key` values → 401 Unauthorized |

---

## 9. Regression Results

### Backend

```
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Breakdown:

| Test Suite | Tests | Result |
|---|---|---|
| Phase A — MultiTenantIsolationIntegrationTest | 12 | ✅ PASSED |
| Phase B — PhaseBBusinessRegistrationAndApiKeyIntegrationTest | 12 | ✅ PASSED |
| Existing integration tests (baseline) | 66 | ✅ PASSED |
| **Total** | **90** | **✅ ALL PASSED** |

### Frontend

```
tsc -b && vite build  →  BUILD SUCCESS  (0 TypeScript errors)
oxlint                →  0 errors, 0 warnings  (91 files checked)
```

---

## 10. API Reference Summary

### Business Registration

```
POST /api/v1/businesses/register
Authorization: none (public)

Body: { businessName, ownerName, email, phone?, password }

201 Created → { tenantId, businessName, ownerUsername, email, role, message }
400 Bad Request → validation errors
409 Conflict   → email already registered
429 Too Many Requests → rate limit exceeded
```

### API Key Management

```
POST   /api/v1/api-keys
GET    /api/v1/api-keys
DELETE /api/v1/api-keys/{id}
POST   /api/v1/api-keys/{id}/rotate

Authorization: X-API-Key: rf_live_<48hex>
           or: Bearer <JWT>
Role required: OPERATOR
```

---

## 11. Known Limitations

| Limitation | Notes |
|---|---|
| No fine-grained permission scopes per API key | All API keys currently grant `ROLE_OPERATOR`; read-only or restricted-scope keys are not yet supported |
| No automated key expiry enforcement | The `expires_at` column is in the schema; a scheduler to auto-revoke expired keys has not been implemented |
| Audit log during registration uses DEFAULT_BUSINESS tenant | Because `TenantContext` is not set during an unauthenticated registration, `BUSINESS_REGISTER` audit events are recorded under `DEFAULT_BUSINESS` to satisfy the FK constraint on `operational_audit_logs.tenant_id` |
| Registration rate limiter is in-memory | Resets on restart; suitable for a single-instance deployment; a Redis-backed limiter should be substituted before horizontal scaling |

---

## 12. Phase Completion Checklist

| Requirement | Status |
|---|---|
| Secure business self-registration | ✅ |
| Registration never creates ADMIN user | ✅ |
| Registration rate limiting | ✅ |
| Duplicate email detection | ✅ |
| Unique tenant ID per business | ✅ |
| API key format `rf_live_<48hex>` | ✅ |
| SHA-256 hashing — raw key never stored | ✅ |
| Secret returned exactly once | ✅ |
| Timing-safe key comparison | ✅ |
| API key authentication via `X-API-Key` | ✅ |
| TenantContext bound from authenticated identity | ✅ |
| API key cannot gain ADMIN privileges | ✅ |
| Key revocation with immediate effect | ✅ |
| Key rotation (revoke old, issue new) | ✅ |
| Key listing scoped to tenant | ✅ |
| Cross-tenant key isolation | ✅ |
| Audit logging for all key lifecycle events | ✅ |
| Frontend registration page `/register` | ✅ |
| Frontend API keys page `/api-keys` | ✅ |
| Phase A isolation fully preserved | ✅ |
| All 90 backend tests passing | ✅ |
| Frontend 0 TypeScript errors, 0 lint warnings | ✅ |
