package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.ReliabilityEnhancementProperties;
import com.eventprocessor.reliable_event_processor.entity.Webhook;
import com.eventprocessor.reliable_event_processor.entity.WebhookDelivery;
import com.eventprocessor.reliable_event_processor.repository.WebhookDeliveryRepository;
import com.eventprocessor.reliable_event_processor.repository.WebhookRepository;
import com.eventprocessor.reliable_event_processor.security.DestinationValidationService;
import com.eventprocessor.reliable_event_processor.security.IntegrationSecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled worker that retries failed webhook deliveries using bounded exponential backoff.
 *
 * CONCURRENCY SAFETY:
 * Uses PostgreSQL 'FOR UPDATE SKIP LOCKED' in short transactions to prevent competing instances
 * from delivering the same pending attempt simultaneously.
 *
 * STABLE EVENT IDENTIFIER:
 * Always preserves the original eventId (X-ReliableFlow-Event-Id) across every retry attempt
 * enabling recipient-side deduplication.
 */
@Component
public class WebhookRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryWorker.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookRepository webhookRepository;
    private final DestinationValidationService destinationValidationService;
    private final IntegrationSecretCryptoService cryptoService;
    private final ReliabilityEnhancementProperties properties;
    private final EventMetricsService metricsService;
    private final HttpClient httpClient;
    private final TransactionTemplate transactionTemplate;

    public WebhookRetryWorker(WebhookDeliveryRepository deliveryRepository,
                              WebhookRepository webhookRepository,
                              DestinationValidationService destinationValidationService,
                              IntegrationSecretCryptoService cryptoService,
                              ReliabilityEnhancementProperties properties,
                              EventMetricsService metricsService,
                              PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.webhookRepository = webhookRepository;
        this.destinationValidationService = destinationValidationService;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.metricsService = metricsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${event-processor.webhook.poll-interval-ms:2000}")
    public void processPendingDeliveries() {
        if (!properties.getWebhook().isEnabled()) {
            return;
        }

        List<WebhookDelivery> batch = fetchEligibleBatch();
        if (batch.isEmpty()) {
            return;
        }

        log.debug("WebhookRetryWorker: processing {} pending delivery attempt(s)", batch.size());

        for (WebhookDelivery delivery : batch) {
            try {
                processSingleDelivery(delivery);
            } catch (Exception ex) {
                log.error("WebhookRetryWorker: error retrying delivery {}: {}", delivery.getId(), ex.getMessage(), ex);
            }
        }
    }

    private List<WebhookDelivery> fetchEligibleBatch() {
        return transactionTemplate.execute(status ->
                deliveryRepository.findEligibleForRetryWithLock(properties.getWebhook().getBatchSize())
        );
    }

    private void processSingleDelivery(WebhookDelivery delivery) {
        Webhook webhook = webhookRepository.findById(delivery.getWebhookId()).orElse(null);
        if (webhook == null) {
            transactionTemplate.execute(status -> {
                delivery.setStatus("FAILED");
                delivery.setErrorMessage("Associated webhook destination was deleted");
                deliveryRepository.save(delivery);
                return null;
            });
            return;
        }

        // Send HTTP request strictly OUTSIDE database transaction
        int statusCode = 0;
        String errorMessage = null;

        try {
            URI destinationUri = destinationValidationService.validateDestinationUrl(webhook.getDestinationUrl());
            String rawSecret = cryptoService.decrypt(webhook.getEncryptedSigningSecret());
            long timestamp = Instant.now().getEpochSecond();
            String signature = WebhookService.calculateHmacSha256(timestamp, delivery.getPayload(), rawSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(destinationUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ReliableFlow-Webhook/1.0")
                    .header("X-ReliableFlow-Event-Id", delivery.getEventId()) // PRESERVES STABLE EVENT ID!
                    .header("X-ReliableFlow-Event-Type", delivery.getEventType())
                    .header("X-ReliableFlow-Delivery-Id", String.valueOf(delivery.getId()))
                    .header("X-ReliableFlow-Signature", "t=" + timestamp + ",v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload() != null ? delivery.getPayload() : "{}"))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            statusCode = response.statusCode();
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        final int finalStatus = statusCode;
        final String finalError = errorMessage;

        // Persist attempt result in short transaction
        transactionTemplate.execute(status -> {
            WebhookDelivery locked = deliveryRepository.findById(delivery.getId()).orElse(null);
            if (locked == null) return null;

            int newAttempt = locked.getAttemptCount() + 1;
            locked.setAttemptCount(newAttempt);
            locked.setDeliveredAt(OffsetDateTime.now());
            locked.setHttpStatus(finalStatus > 0 ? finalStatus : null);

            if (finalStatus >= 200 && finalStatus < 300) {
                locked.setStatus("SUCCESS");
                locked.setNextRetryAt(null);
                locked.setErrorMessage(null);
                webhook.setLastDeliveryStatus("SUCCESS");
                metricsService.recordEventSuccess("WEBHOOK_DELIVERY", null);
            } else if (finalStatus >= 400 && finalStatus < 500) {
                // Permanent client rejection -> no retry
                locked.setStatus("FAILED");
                locked.setNextRetryAt(null);
                locked.setErrorMessage("Permanent failure (HTTP " + finalStatus + ")");
                webhook.setLastDeliveryStatus("FAILED (" + finalStatus + ")");
                metricsService.recordEventFailure("WEBHOOK_DELIVERY", "PERMANENT_CLIENT_ERROR", null);
            } else {
                // Retryable (5xx or timeout / network error)
                if (newAttempt < locked.getMaxRetries()) {
                    locked.setStatus("RETRY_PENDING");
                    locked.setNextRetryAt(OffsetDateTime.now().plus(properties.getWebhook().calculateBackoff(newAttempt)));
                    locked.setErrorMessage(finalError != null ? finalError : "HTTP " + finalStatus);
                    webhook.setLastDeliveryStatus("RETRY_PENDING");
                    metricsService.recordRetryAttempt("WEBHOOK_DELIVERY");
                } else {
                    locked.setStatus("EXHAUSTED");
                    locked.setNextRetryAt(null);
                    locked.setErrorMessage("Max retries (" + locked.getMaxRetries() + ") exhausted. Last error: " + (finalError != null ? finalError : "HTTP " + finalStatus));
                    webhook.setLastDeliveryStatus("EXHAUSTED");
                    metricsService.recordEventFailure("WEBHOOK_DELIVERY", "EXHAUSTED", null);
                }
            }

            webhook.setLastDeliveryAt(OffsetDateTime.now());
            deliveryRepository.save(locked);
            webhookRepository.save(webhook);
            return null;
        });
    }
}
