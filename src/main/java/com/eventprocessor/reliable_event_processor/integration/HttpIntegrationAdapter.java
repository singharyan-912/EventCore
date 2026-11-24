package com.eventprocessor.reliable_event_processor.integration;

import com.eventprocessor.reliable_event_processor.entity.ExternalIntegration;
import com.eventprocessor.reliable_event_processor.exception.AmbiguousExternalOutcomeException;
import com.eventprocessor.reliable_event_processor.exception.PermanentOperationException;
import com.eventprocessor.reliable_event_processor.exception.TransientProcessingException;
import com.eventprocessor.reliable_event_processor.security.DestinationValidationService;
import com.eventprocessor.reliable_event_processor.security.IntegrationSecretCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Robust HTTP Integration Adapter for dispatching operations to external third-party applications.
 *
 * Enforces strict SSRF destination validation, timeouts, non-redirect policies,
 * response size limits, and differentiates definite errors from ambiguous outcomes.
 */
@Component
public class HttpIntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpIntegrationAdapter.class);
    private static final int MAX_RESPONSE_SIZE_BYTES = 1024 * 1024; // 1MB response limit

    private final DestinationValidationService destinationValidationService;
    private final IntegrationSecretCryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpIntegrationAdapter(DestinationValidationService destinationValidationService,
                                  IntegrationSecretCryptoService cryptoService,
                                  ObjectMapper objectMapper) {
        this.destinationValidationService = destinationValidationService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record ExternalExecutionResult(
            int statusCode,
            boolean success,
            JsonNode body,
            long durationMs,
            String correlationId
    ) {}

    public ExternalExecutionResult execute(ExternalIntegration integration,
                                          String idempotencyKey,
                                          String operationType,
                                          String resourceId,
                                          JsonNode payload) {
        URI validatedUri = destinationValidationService.validateDestinationUrl(integration.getBaseUrl());

        String rawSecret = null;
        if (integration.getEncryptedSecret() != null) {
            rawSecret = cryptoService.decrypt(integration.getEncryptedSecret());
        }

        int timeoutMs = (integration.getTimeoutMs() != null && integration.getTimeoutMs() > 0)
                ? integration.getTimeoutMs()
                : 5000;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(validatedUri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Idempotency-Key", idempotencyKey)
                .header("X-ReliableFlow-Operation-Type", operationType)
                .header("X-ReliableFlow-Tenant", integration.getTenantId())
                .header("X-ReliableFlow-Resource-Id", resourceId != null ? resourceId : "");

        // Configure authentication header securely
        if ("BEARER_TOKEN".equalsIgnoreCase(integration.getAuthType()) && rawSecret != null) {
            requestBuilder.header("Authorization", "Bearer " + rawSecret);
        } else if ("API_KEY_HEADER".equalsIgnoreCase(integration.getAuthType()) && rawSecret != null) {
            String headerName = (integration.getAuthHeaderName() != null && !integration.getAuthHeaderName().isBlank())
                    ? integration.getAuthHeaderName().trim()
                    : "X-API-Key";
            requestBuilder.header(headerName, rawSecret);
        }

        String bodyString = (payload != null) ? payload.toString() : "{}";
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyString));

        long startTime = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            int status = response.statusCode();

            log.info("External integration HTTP dispatch [tenant={}, url={}, status={}, durationMs={}]",
                    integration.getTenantId(), validatedUri, status, duration);

            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_SIZE_BYTES) {
                throw new PermanentOperationException("External response exceeded maximum size limit of 1MB");
            }

            JsonNode parsedBody = parseJsonSafely(responseBody);

            if (status >= 200 && status < 300) {
                return new ExternalExecutionResult(status, true, parsedBody, duration, idempotencyKey);
            }

            // Error classification:
            if (status == 429 || status == 502 || status == 503 || status == 504) {
                throw new TransientProcessingException("External service temporary failure (HTTP " + status + "): " + responseBody);
            } else if (status >= 400 && status < 500) {
                throw new PermanentOperationException("External service rejected request with permanent HTTP " + status + ": " + responseBody);
            } else {
                throw new TransientProcessingException("External service returned HTTP " + status + ": " + responseBody);
            }
        } catch (HttpTimeoutException timeoutEx) {
            // Request was transmitted but response timed out -> Ambiguous outcome!
            log.warn("External request timed out after transmission [tenant={}, key={}, url={}]: {}",
                    integration.getTenantId(), idempotencyKey, validatedUri, timeoutEx.getMessage());
            throw new AmbiguousExternalOutcomeException(
                    "External request timed out after transmission; outcome is ambiguous and requires reconciliation",
                    idempotencyKey,
                    validatedUri.toString(),
                    timeoutEx
            );
        } catch (TransientProcessingException | PermanentOperationException | AmbiguousExternalOutcomeException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Transport error connecting to external integration [tenant={}, url={}]: {}",
                    integration.getTenantId(), validatedUri, ex.getMessage());
            throw new TransientProcessingException("Transport error during external call: " + ex.getMessage(), ex);
        }
    }

    public enum ProviderStatusResult {
        SUCCESS,
        FAILED,
        UNRESOLVED
    }

    public ProviderStatusResult checkStatus(ExternalIntegration integration, String idempotencyKey) {
        try {
            URI baseUri = destinationValidationService.validateDestinationUrl(integration.getBaseUrl());
            URI statusUri = URI.create(baseUri.toString().replaceAll("/+$", "") + "/status/" + idempotencyKey);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(statusUri)
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("X-ReliableFlow-Tenant", integration.getTenantId())
                    .GET();

            if ("BEARER_TOKEN".equalsIgnoreCase(integration.getAuthType()) && integration.getEncryptedSecret() != null) {
                String raw = cryptoService.decrypt(integration.getEncryptedSecret());
                builder.header("Authorization", "Bearer " + raw);
            } else if ("API_KEY_HEADER".equalsIgnoreCase(integration.getAuthType()) && integration.getEncryptedSecret() != null) {
                String raw = cryptoService.decrypt(integration.getEncryptedSecret());
                String header = (integration.getAuthHeaderName() != null && !integration.getAuthHeaderName().isBlank())
                        ? integration.getAuthHeaderName().trim()
                        : "X-API-Key";
                builder.header(header, raw);
            }

            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return ProviderStatusResult.SUCCESS;
            } else if (code == 404) {
                return ProviderStatusResult.FAILED;
            } else {
                return ProviderStatusResult.UNRESOLVED;
            }
        } catch (Exception e) {
            log.warn("Status check failed or timed out for integration '{}': {}", integration.getName(), e.getMessage());
            return ProviderStatusResult.UNRESOLVED;
        }
    }

    public boolean testConnection(ExternalIntegration integration) {
        try {
            URI validatedUri = destinationValidationService.validateDestinationUrl(integration.getBaseUrl());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(validatedUri)
                    .timeout(Duration.ofSeconds(3))
                    .GET();

            if ("BEARER_TOKEN".equalsIgnoreCase(integration.getAuthType()) && integration.getEncryptedSecret() != null) {
                String raw = cryptoService.decrypt(integration.getEncryptedSecret());
                builder.header("Authorization", "Bearer " + raw);
            } else if ("API_KEY_HEADER".equalsIgnoreCase(integration.getAuthType()) && integration.getEncryptedSecret() != null) {
                String raw = cryptoService.decrypt(integration.getEncryptedSecret());
                String header = (integration.getAuthHeaderName() != null && !integration.getAuthHeaderName().isBlank())
                        ? integration.getAuthHeaderName().trim()
                        : "X-API-Key";
                builder.header(header, raw);
            }

            HttpResponse<Void> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            log.warn("Connection test failed for integration '{}' (url: {}): {}",
                    integration.getName(), integration.getBaseUrl(), e.getMessage());
            return false;
        }
    }

    private JsonNode parseJsonSafely(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("rawResponse", json);
        }
    }
}
