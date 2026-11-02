# Reliable Event Processing Platform

A resilient, transactional, observable, and secured event-processing platform built with **Java 21**, **Spring Boot 3.4.3**, **PostgreSQL**, **Flyway**, and **Apache Kafka**.

---

## Architecture & Capabilities Overview

The platform guarantees effectively-once business effect delivery across asynchronous event streaming and REST ingestion:

1. **REST API & Idempotent Ingestion**: Ingests event requests via `POST /api/v1/events` using SHA-256 payload hashing and PostgreSQL row locks (`SELECT FOR UPDATE`). Concurrent or duplicate submissions return idempotent cached replays without duplicating business effects (inventory deduction).
2. **Transactional Outbox Pattern**: Automatically records event publications in `outbox_events` within the same ACID database transaction as the business mutation.
3. **Outbox Publisher**: Polling worker with PostgreSQL `FOR UPDATE SKIP LOCKED` claims pending outbox batches and publishes messages to Kafka with guaranteed partition keys.
4. **Kafka Ingestion & Consumer**: `EventKafkaConsumer` consumes from `inventory-events` using `MANUAL_IMMEDIATE` acknowledgment mode. It verifies authoritative state in PostgreSQL, ensuring duplicate message deliveries are safely acknowledged without repeated deductions.
5. **Retry Mechanism & Backoff**: Handles transient failures via scheduled `RetryWorker` using exponential backoff with jitter and `FOR UPDATE SKIP LOCKED`.
6. **Dead-Letter Handling & Reprocessing**: Events exceeding maximum retry attempts transition to `DEAD_LETTER`. Operators can inspect and trigger administrative reprocessing via secured endpoints.
7. **Observability & Operations**:
   - **Micrometer & Spring Boot Actuator**: Bounded metrics for attempts, successes, failures, replays, retries, dead-letters, outbox publications, and consumer deliveries.
   - **Health Indicators**: Real-time bounded checks for application, PostgreSQL (`DataSourceHealthIndicator`), Kafka broker reachability (`KafkaHealthIndicator`), Outbox status (`OutboxHealthIndicator`), and Retry worker queue (`RetryWorkerHealthIndicator`).
   - **Operations REST API (`/api/v1/operations/**`)**: Paginated event inspection with multi-dimensional filtering, event details with correlated outbox status, database-backed summary counts, and unified metric reporting.
   - **Operational Audit Log (`operational_audit_logs`)**: Persists an immutable audit trail for operator actions (such as dead-letter reprocessing) with principal, outcome, and failure details in dedicated transactions (`REQUIRES_NEW`).
8. **Phase 7 Authentication, RBAC & Security Hardening**:
   - **Stateless JWT Authentication**: Secure HMAC-SHA256 tokens signed with rotating/environment secrets, validated per request without server-side session state.
   - **Role-Based Access Control (RBAC)**: Fine-grained method and URL-level authorization enforcing `ADMIN`, `OPERATOR`, and `VIEWER` roles.
   - **Dual Authentication Support**: Accommodates standard `Authorization: Bearer <jwt>` alongside backward-compatible `X-Admin-API-Key` headers.
   - **Password Security**: Passwords hashed using industry-standard `BCryptPasswordEncoder`. Plaintext passwords and hashes are never returned in responses or logs.
   - **Lockout & Timing Attack Protections**: Constant-time dummy password checks prevent user enumeration. The platform prevents demoting or deactivating the last active administrator.
   - **Abuse Protections & Rate Limiting**: In-memory token-bucket rate limiters guard event ingestion and login endpoints against brute-force abuse.
   - **Automated Security Audit Trail**: Captures security-sensitive lifecycle actions (login success/failure, user creation, role changes, deactivations, access denials).

---

## Role-Based Access Control (RBAC) Matrix

| Endpoint / Capability | Public | VIEWER | OPERATOR | ADMIN | Legacy API Key |
|-----------------------|:------:|:------:|:--------:|:-----:|:--------------:|
| `GET /api/v1/health` (Liveness / Readiness) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /api/v1/events` (Ingestion) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /api/v1/auth/login` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /actuator/health`, `/info`, `/metrics` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/auth/me` (Current Profile) | ❌ | ✅ | ✅ | ✅ | ❌ |
| `POST /api/v1/auth/logout` | ❌ | ✅ | ✅ | ✅ | ❌ |
| `GET /api/v1/operations/events/**` (Event Search & Detail) | ❌ | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/operations/events/summary` | ❌ | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/operations/outbox/summary` | ❌ | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/operations/metrics` (Unified Reporting) | ❌ | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/admin/dead-letters/**` (Inspection) | ❌ | ❌ | ✅ | ✅ | ✅ |
| `POST /api/v1/admin/dead-letters/*/reprocess` | ❌ | ❌ | ✅ | ✅ | ✅ |
| `GET /api/v1/operations/audit-logs/**` (Security Audit) | ❌ | ❌ | ❌ | ✅ | ✅ |
| `POST /api/v1/admin/users` (Create Users) | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/v1/admin/users` (List Users) | ❌ | ❌ | ❌ | ✅ | ❌ |
| `PATCH /api/v1/admin/users/{id}/role` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `PATCH /api/v1/admin/users/{id}/status` | ❌ | ❌ | ❌ | ✅ | ❌ |

---

## Required Configuration & Environment Variables

| Variable / Property | Description | Default / Example | Production Requirement |
|---------------------|-------------|-------------------|------------------------|
| `DB_PASSWORD` | PostgreSQL password for HikariCP & Flyway | `${DB_PASSWORD}` | **Required** |
| `JWT_SECRET` | Secret key for HMAC-SHA256 JWT signing | Ephemeral auto-key in dev | **Required in Production** (>= 256 bits) |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial password for the bootstrapped `admin` user | `AdminSecret123!` (dev only) | **Required in Production** |
| `BOOTSTRAP_ADMIN_USERNAME` | Initial admin username | `admin` | Optional |
| `BOOTSTRAP_ADMIN_EMAIL` | Initial admin email address | `admin@eventprocessor.local` | Optional |
| `event-processor.security.jwt.expiration-ms` | Access token lifespan in milliseconds | `3600000` (1 hour) | Configurable |
| `event-processor.admin.api-key` | Secret key for legacy administrative header | `admin-secret-key-12345` | Configurable |
| `spring.datasource.url` | JDBC connection string | `jdbc:postgresql://localhost:5432/event_processor` | Environment |
| `spring.kafka.bootstrap-servers` | Kafka bootstrap broker address | `localhost:9092` | Environment |
| `event-processor.outbox.enabled` | Enable/disable transactional outbox publisher | `true` | Configurable |
| `event-processor.consumer.enabled` | Enable/disable Kafka consumer listener | `true` | Configurable |
| `event-processor.worker.enabled` | Enable/disable background retry worker | `true` | Configurable |

---

## Authentication & User Administration API

### 1. User Login (`POST /api/v1/auth/login`)

Authenticates an active user and issues a stateless JWT.

**Request:**
```http
POST /api/v1/auth/login HTTP/1.1
Content-Type: application/json

{
  "username": "admin",
  "password": "AdminSecret123!"
}
```

**Response (HTTP 200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc5MzYzNTUwMCwiZXhwIjoxNzkzNjM5MTAwfQ...",
  "tokenType": "Bearer",
  "expiresInMs": 3600000,
  "username": "admin",
  "role": "ADMIN"
}
```

*Generic error on invalid credentials (HTTP 401 Unauthorized):*
```json
{
  "timestamp": "2026-11-02T16:00:00Z",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Invalid username or password"
}
```

---

### 2. Current User Profile (`GET /api/v1/auth/me`)

Retrieves the authenticated principal's profile details.

**Request:**
```http
GET /api/v1/auth/me HTTP/1.1
Authorization: Bearer <your-jwt-token>
```

**Response (HTTP 200 OK):**
```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@eventprocessor.local",
  "role": "ADMIN",
  "status": "ACTIVE",
  "createdAt": "2026-11-02T15:00:00Z"
}
```

---

### 3. User Administration (`/api/v1/admin/users/**`)

Requires `ADMIN` role.

#### A. Create a New User
```http
POST /api/v1/admin/users HTTP/1.1
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "username": "operator1",
  "email": "operator1@company.com",
  "password": "SecurePassword123!",
  "role": "OPERATOR"
}
```
**Response (HTTP 201 Created):**
```json
{
  "id": 2,
  "username": "operator1",
  "email": "operator1@company.com",
  "role": "OPERATOR",
  "status": "ACTIVE",
  "createdAt": "2026-11-02T16:05:00Z",
  "updatedAt": "2026-11-02T16:05:00Z"
}
```

#### B. List Users (Paginated)
```http
GET /api/v1/admin/users?page=0&size=20 HTTP/1.1
Authorization: Bearer <admin-jwt-token>
```

#### C. Update User Role
```http
PATCH /api/v1/admin/users/2/role HTTP/1.1
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "role": "VIEWER"
}
```

#### D. Update User Status (Deactivate / Activate)
```http
PATCH /api/v1/admin/users/2/status HTTP/1.1
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "status": "DEACTIVATED"
}
```
> **Lockout Protection**: Attempting to deactivate or demote the last remaining active administrator returns `HTTP 409 Conflict` to prevent system lockout.

---

## Sending Authenticated Requests

Clients must include the bearer token in the HTTP `Authorization` header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

For backward compatibility with automated Phase 6 monitoring and test harnesses, operational and dead-letter endpoints also accept the pre-shared key header:
```http
X-Admin-API-Key: admin-secret-key-12345
```

---

## Initial Administrator Bootstrapping

On application startup, `AdminBootstrapService` verifies whether an active administrator exists in `app_users`:
- **Development / Test**: If no administrator exists and `BOOTSTRAP_ADMIN_PASSWORD` is unset, the service initializes a default administrator (`admin` / `AdminSecret123!`).
- **Production**: When `spring.profiles.active=prod`, `BOOTSTRAP_ADMIN_PASSWORD` must be provided as an environment variable (or the bootstrap disabled via `event-processor.security.bootstrap.enabled=false`). The service fails safely and throws a startup exception if required production credentials are absent.
- Once created, administrators can provision additional operators and viewers via `POST /api/v1/admin/users`.

---

## Security Audit Trail

Security actions are automatically recorded in `operational_audit_logs` with the actor derived authoritatively from `SecurityContextHolder`:

| Action Type | Target | Description |
|-------------|--------|-------------|
| `LOGIN_SUCCESS` | Username | Successful JWT token issuance |
| `LOGIN_FAILED` | Username | Failed authentication attempt (bad credentials or deactivated account) |
| `USER_CREATED` | Username | New user account provisioned with assigned role |
| `USER_ROLE_UPDATED` | User ID | Administrator updated user role (`ADMIN`, `OPERATOR`, `VIEWER`) |
| `USER_STATUS_UPDATED` | User ID | Administrator changed account status (`ACTIVE`, `DEACTIVATED`) |
| `ACCESS_DENIED` | Request URI | Authenticated actor attempted an unauthorized privileged route |
| `DEAD_LETTER_REPROCESS` | Idempotency Key | Operator or Admin triggered dead-letter recovery re-queue |

Inspect audit logs with `ADMIN` privileges:
```http
GET /api/v1/operations/audit-logs?page=0&size=50 HTTP/1.1
Authorization: Bearer <admin-jwt-token>
```

---

## Production Security Recommendations & Limitations

1. **JWT Secret Management**: Never check signing secrets into source control. In production, configure `JWT_SECRET` via AWS Secrets Manager, HashiCorp Vault, or Kubernetes Secrets. Secret must be at least 256 bits (32 bytes).
2. **Rate Limiting Architecture**: In-memory rate limiting (`LoginRateLimiter`, `IngestionRateLimiter`) provides single-node abuse defense. In horizontally scaled multi-pod production environments, front the cluster with an API Gateway (e.g. Kong, AWS API Gateway) or Redis-backed distributed token bucket.
3. **HTTPS / TLS Termination**: JWT bearer tokens must only be transmitted over secure TLS connections (`https://`) to prevent credential sniffing on transit networks.
4. **Token Expiration & Revocation**: Access tokens are stateless with a 1-hour default TTL. In high-security environments, reduce TTL (e.g. 15 minutes) and implement a server-side revocation blacklist (Redis or database) or an OAuth2/OIDC identity provider (Keycloak, Auth0).

---

## Running the Automated Test Suite

Set your database password and execute Maven:

```powershell
$env:DB_PASSWORD="your-postgres-password"
.\mvnw.cmd test
```

To run targeted Phase 7 security and authorization tests:
```powershell
$env:DB_PASSWORD="your-postgres-password"
.\mvnw.cmd test -Dtest=SecurityAndRoleAuthorizationIntegrationTest
```

To run the complete verification suite across all phases:
```powershell
$env:DB_PASSWORD="your-postgres-password"
.\mvnw.cmd clean test
```
