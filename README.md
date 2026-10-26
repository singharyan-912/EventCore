# Reliable Event Processing Platform

A resilient, transactional, and observable event-processing platform built with **Java 21**, **Spring Boot 3.4.3**, **PostgreSQL**, **Flyway**, and **Apache Kafka**.

---

## Architecture & Capabilities Overview

The platform guarantees effectively-once business effect delivery across asynchronous event streaming and REST ingestion:

1. **REST API & Idempotent Ingestion**: Ingests event requests via `POST /api/v1/events` using SHA-256 payload hashing and PostgreSQL row locks (`SELECT FOR UPDATE`). Concurrent or duplicate submissions return idempotent cached replays without duplicating business effects (inventory deduction).
2. **Transactional Outbox Pattern**: Automatically records event publications in `outbox_events` within the same ACID database transaction as the business mutation.
3. **Outbox Publisher**: Polling worker with PostgreSQL `FOR UPDATE SKIP LOCKED` claims pending outbox batches and publishes messages to Kafka with guaranteed partition keys.
4. **Kafka Ingestion & Consumer**: `EventKafkaConsumer` consumes from `inventory-events` using `MANUAL_IMMEDIATE` acknowledgment mode. It verifies authoritative state in PostgreSQL, ensuring duplicate message deliveries are safely acknowledged without repeated deductions.
5. **Retry Mechanism & Backoff**: Handles transient failures via scheduled `RetryWorker` using exponential backoff with jitter and `FOR UPDATE SKIP LOCKED`.
6. **Dead-Letter Handling & Reprocessing**: Events exceeding maximum retry attempts transition to `DEAD_LETTER`. Operators can inspect and trigger administrative reprocessing via secured endpoints.
7. **Phase 6 Observability & Operations**:
   - **Micrometer & Spring Boot Actuator**: Bounded metrics for attempts, successes, failures, replays, retries, dead-letters, outbox publications, and consumer deliveries.
   - **Health Indicators**: Real-time bounded checks for application, PostgreSQL (`DataSourceHealthIndicator`), Kafka broker reachability (`KafkaHealthIndicator`), Outbox status (`OutboxHealthIndicator`), and Retry worker queue (`RetryWorkerHealthIndicator`).
   - **Operations REST API (`/api/v1/operations/**`)**: Paginated event inspection with multi-dimensional filtering, event details with correlated outbox status, database-backed summary counts, and unified metric reporting.
   - **Operational Audit Log (`operational_audit_logs`)**: Persists an immutable audit trail for operator actions (such as dead-letter reprocessing) with principal, outcome, and failure details in dedicated transactions (`REQUIRES_NEW`).

---

## Required Configuration & Environment Variables

| Variable / Property | Description | Default / Example |
|---------------------|-------------|-------------------|
| `DB_PASSWORD` | PostgreSQL password for HikariCP & Flyway | `${DB_PASSWORD}` |
| `spring.datasource.url` | JDBC connection string | `jdbc:postgresql://localhost:5432/event_processor` |
| `spring.datasource.username` | PostgreSQL username | `postgres` |
| `spring.kafka.bootstrap-servers` | Kafka bootstrap address | `localhost:9092` |
| `event-processor.admin.api-key` | Secret key for administrative endpoints | `admin-secret-key-12345` |
| `event-processor.outbox.enabled` | Enable/disable transactional outbox publisher | `true` |
| `event-processor.outbox.poll-interval-ms` | Outbox batch polling interval | `2000` |
| `event-processor.consumer.enabled` | Enable/disable Kafka consumer listener | `true` |
| `event-processor.worker.enabled` | Enable/disable background retry worker | `true` |
| `event-processor.worker.poll-interval-ms` | Retry worker batch polling interval | `2000` |
| `management.endpoints.web.exposure.include` | Actuator exposed endpoints | `health,metrics,info` |

---

## API Endpoints & Usage

### 1. Event Ingestion (`POST /api/v1/events`)
Submits a business event with idempotency guarantees.

**Request:**
```http
POST /api/v1/events HTTP/1.1
Content-Type: application/json

{
  "idempotencyKey": "order-evt-1001",
  "eventType": "INVENTORY_DEDUCT",
  "resourceId": "product-101",
  "quantity": 2
}
```

**Response (HTTP 200 OK):**
```json
{
  "idempotencyKey": "order-evt-1001",
  "eventType": "INVENTORY_DEDUCT",
  "resourceId": "product-101",
  "deductedQuantity": 2,
  "remainingQuantity": 8,
  "status": "PROCESSED",
  "idempotentReplay": false,
  "processedAt": "2026-10-26T09:30:00Z"
}
```

---

### 2. Operations & Inspection APIs (`/api/v1/operations/**`)

All operational read endpoints accept the optional header `X-Admin-API-Key: admin-secret-key-12345`.

#### A. Search Events with Pagination & Filters
```http
GET /api/v1/operations/events?status=PROCESSED&resourceId=product-101&page=0&size=20 HTTP/1.1
```
Supports filters: `status`, `eventType`, `resourceId`, `idempotencyKey`, `from` (ISO datetime), `to` (ISO datetime), `page` (>=0), `size` (1 to 100).

**Response (HTTP 200 OK):**
```json
{
  "content": [
    {
      "idempotencyKey": "order-evt-1001",
      "eventType": "INVENTORY_DEDUCT",
      "resourceId": "product-101",
      "status": "PROCESSED",
      "responseStatusCode": 200,
      "retryCount": 0,
      "maxRetries": 3,
      "lastFailureReason": null,
      "nextRetryAt": null,
      "lastAttemptAt": "2026-10-26T09:30:00Z",
      "createdAt": "2026-10-26T09:30:00Z",
      "updatedAt": "2026-10-26T09:30:00Z",
      "requestPayload": "{\"idempotencyKey\":\"order-evt-1001\",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"product-101\",\"quantity\":2}",
      "responsePayload": "{\"idempotencyKey\":\"order-evt-1001\",\"eventType\":\"INVENTORY_DEDUCT\",\"resourceId\":\"product-101\",\"deductedQuantity\":2,\"remainingQuantity\":8,\"status\":\"PROCESSED\",\"idempotentReplay\":false}",
      "outbox": {
        "id": 1,
        "status": "PUBLISHED",
        "topic": "inventory-events",
        "partitionKey": "product-101",
        "retryCount": 0,
        "maxRetries": 3,
        "lastError": null,
        "publishedAt": "2026-10-26T09:30:02Z",
        "createdAt": "2026-10-26T09:30:00Z",
        "updatedAt": "2026-10-26T09:30:02Z"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

#### B. Single Event Detail with Correlated Outbox State
```http
GET /api/v1/operations/events/{idempotencyKey} HTTP/1.1
```
Returns 404 Not Found with standard `ErrorResponse` if key does not exist.

#### C. Database Event Status Summary
```http
GET /api/v1/operations/events/summary HTTP/1.1
```
**Response (HTTP 200 OK):**
```json
{
  "totalEvents": 42,
  "processedCount": 38,
  "retryPendingCount": 2,
  "deadLetterCount": 2,
  "processingCount": 0,
  "countsByStatus": {
    "PROCESSED": 38,
    "RETRY_PENDING": 2,
    "DEAD_LETTER": 2
  }
}
```

#### D. Outbox Summary
```http
GET /api/v1/operations/outbox/summary HTTP/1.1
```
**Response (HTTP 200 OK):**
```json
{
  "totalOutboxEvents": 40,
  "pendingCount": 1,
  "claimedCount": 0,
  "publishedCount": 39,
  "failedCount": 0,
  "retriedCount": 2,
  "countsByStatus": {
    "PENDING": 1,
    "PUBLISHED": 39
  }
}
```

#### E. Unified Operational Metrics
```http
GET /api/v1/operations/metrics HTTP/1.1
```
**Response (HTTP 200 OK):**
```json
{
  "inMemoryMetricsNotice": "In-memory metrics reflect the current JVM process lifetime and reset upon application restart. Use database summaries for durable historical counts.",
  "attempts": 45,
  "successes": 38,
  "failures": 4,
  "duplicates": 3,
  "retries": 5,
  "deadLetters": 2,
  "outboxPublished": 39,
  "outboxFailed": 1,
  "consumerSuccess": 35,
  "consumerFailure": 2,
  "consumerDuplicate": 2,
  "databaseEvents": { ... },
  "databaseOutbox": { ... }
}
```

#### F. Operational Audit Logs
```http
GET /api/v1/operations/audit-logs?targetIdentifier=order-evt-1001 HTTP/1.1
```
**Response (HTTP 200 OK):**
```json
{
  "content": [
    {
      "id": 12,
      "actionType": "DEAD_LETTER_REPROCESS",
      "targetIdentifier": "order-evt-1001",
      "outcome": "SUCCESS",
      "failureReason": null,
      "principal": "admin-api-key",
      "details": "Event reset to RETRY_PENDING with immediate schedule",
      "timestamp": "2026-10-26T09:35:10Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### 3. Actuator Health & Metrics Endpoints

- **Health Endpoint (`GET /actuator/health`)**:
  Inspects components:
  - `db`: PostgreSQL connectivity via `DataSourceHealthIndicator`.
  - `kafka`: Broker reachability and node discovery within 2-second timeout.
  - `outbox`: Active database outbox counts and failure detection.
  - `retryWorker`: Queue counts for pending retries and dead letters.
- **Metrics Catalog (`GET /actuator/metrics`)**:
  Exposes Micrometer counters and timers:
  - `events.processing.attempts` (tags: `event_type`)
  - `events.processing.success` (tags: `event_type`)
  - `events.processing.failure` (tags: `event_type`, `failure_type`)
  - `events.processed.duplicate` (tags: `event_type`, `status`)
  - `events.retry.attempts` (tags: `event_type`)
  - `events.deadletter.total` (tags: `event_type`, `reason_category`)
  - `events.processing.duration` (tags: `event_type`, `outcome`)
  - `outbox.publication.success` (tags: `topic`)
  - `outbox.publication.failure` (tags: `topic`)
  - `consumer.processing.success` (tags: `topic`)
  - `consumer.processing.failure` (tags: `topic`, `failure_type`)
  - `consumer.duplicate.deliveries` (tags: `topic`)
- **Metric Details (`GET /actuator/metrics/{metricName}`)**:
  Example: `GET /actuator/metrics/events.processing.success`

---

### 4. Admin Recovery API (`/api/v1/admin/dead-letters/**`)

Requires header `X-Admin-API-Key: admin-secret-key-12345`.

- `GET /api/v1/admin/dead-letters`: List dead-letter events.
- `GET /api/v1/admin/dead-letters/{idempotencyKey}`: Get dead-letter event details.
- `POST /api/v1/admin/dead-letters/{idempotencyKey}/reprocess`: Reset dead-letter event to `RETRY_PENDING` for immediate execution by the worker. Persists an audit log in `operational_audit_logs`.

---

## In-Memory vs. Database-Derived Metrics

| Metric Type | Storage | Behavior Across Restarts | Usage |
|-------------|---------|--------------------------|-------|
| **Micrometer / Actuator Metrics** | In-Memory (JVM Heap) | **Resets to 0** on service restart | Real-time rate monitoring, Prometheus scraping, Grafana alerting, latency percentiles |
| **Database Summaries (`/events/summary`, `/outbox/summary`)** | PostgreSQL (`processed_events`, `outbox_events`) | **Durable & Permanent** | Long-term historical counts, SLA compliance reporting, audit reconciliation |

---

## Security & Operational Limitations

1. **Pre-Shared API Key**: Administrative operations use header-based API key authentication (`X-Admin-API-Key`). In production, this should be integrated into OAuth2 / OIDC (e.g. Keycloak or AWS Cognito) with role-based access control (RBAC).
2. **Audit Principal**: Without a full security context / JWT, the audit log records `admin-api-key` or `SYSTEM`. Operator usernames are not tracked unless an identity provider is integrated.
3. **Sensitive Data Protection**: Passwords, credentials, and raw database connection strings are masked and never exposed in actuator health details, logs, or error responses.
4. **Metric Label Bounding**: High-cardinality values (idempotency keys, resource IDs, customer names, raw exception stack traces) are strictly forbidden as metric tags to prevent JVM memory exhaustion (cardinality explosion).

---

## How to Run Tests & Verification

Set the `DB_PASSWORD` environment variable and run the Maven wrapper:

```powershell
$env:DB_PASSWORD="your-postgres-password"
.\mvnw.cmd clean verify
```

To run targeted Phase 6 tests only:
```powershell
$env:DB_PASSWORD="your-postgres-password"
.\mvnw.cmd test -Dtest=OperationsAndObservabilityIntegrationTest
```
