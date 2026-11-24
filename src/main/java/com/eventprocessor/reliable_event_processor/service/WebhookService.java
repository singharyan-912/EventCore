package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.ReliabilityEnhancementProperties;
import com.eventprocessor.reliable_event_processor.dto.CreateWebhookRequest;
import com.eventprocessor.reliable_event_processor.dto.WebhookDeliveryResponse;
import com.eventprocessor.reliable_event_processor.dto.WebhookResponse;
import com.eventprocessor.reliable_event_processor.entity.Webhook;
import com.eventprocessor.reliable_event_processor.entity.WebhookDelivery;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.WebhookDeliveryRepository;
import com.eventprocessor.reliable_event_processor.repository.WebhookRepository;
import com.eventprocessor.reliable_event_processor.security.DestinationValidationService;
import com.eventprocessor.reliable_event_processor.security.IntegrationSecretCryptoService;
import com.eventprocessor.reliable_event_processor.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final IntegrationSecretCryptoService cryptoService;
    private final DestinationValidationService destinationValidationService;
    private final OperationalAuditService auditService;
    private final ReliabilityEnhancementProperties enhancementProperties;
    private final HttpClient httpClient;

    public WebhookService(WebhookRepository webhookRepository,
                          WebhookDeliveryRepository webhookDeliveryRepository,
                          IntegrationSecretCryptoService cryptoService,
                          DestinationValidationService destinationValidationService,
                          OperationalAuditService auditService,
                          ReliabilityEnhancementProperties enhancementProperties) {
        this.webhookRepository = webhookRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.cryptoService = cryptoService;
        this.destinationValidationService = destinationValidationService;
        this.auditService = auditService;
        this.enhancementProperties = enhancementProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional
    public WebhookResponse createWebhook(CreateWebhookRequest request) {
        String tenantId = TenantContext.getTenantId();

        // Validate URL against SSRF policy
        destinationValidationService.validateDestinationUrl(request.destinationUrl());

        String rawSecret = cryptoService.generateSigningSecret();
        String encryptedSecret = cryptoService.encrypt(rawSecret);
        String prefixHint = cryptoService.computePrefixHint(rawSecret);

        Webhook webhook = new Webhook(
                tenantId,
                request.name().trim(),
                request.destinationUrl().trim(),
                request.subscribedEvents().trim().toUpperCase(),
                encryptedSecret,
                prefixHint
        );

        Webhook saved = webhookRepository.save(webhook);
        log.info("Created webhook [id={}, tenant={}, name={}]", saved.getId(), tenantId, saved.getName());

        auditService.recordAuditLog(
                tenantId,
                "WEBHOOK_CREATE",
                "webhook-" + saved.getId(),
                "SUCCESS",
                null,
                "OPERATOR",
                "Registered webhook destination: " + saved.getName()
        );

        return toResponseWithSecret(saved, rawSecret);
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooks() {
        String tenantId = TenantContext.getTenantId();
        return webhookRepository.findByTenantId(tenantId).stream()
                .map(this::toResponseWithoutSecret)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookResponse getWebhook(Long id) {
        String tenantId = TenantContext.getTenantId();
        Webhook webhook = webhookRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
        return toResponseWithoutSecret(webhook);
    }

    @Transactional
    public WebhookResponse rotateSecret(Long id) {
        String tenantId = TenantContext.getTenantId();
        Webhook webhook = webhookRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));

        String newRawSecret = cryptoService.generateSigningSecret();
        webhook.setEncryptedSigningSecret(cryptoService.encrypt(newRawSecret));
        webhook.setSecretPrefix(cryptoService.computePrefixHint(newRawSecret));
        webhook.setUpdatedAt(OffsetDateTime.now());

        Webhook saved = webhookRepository.save(webhook);
        log.info("Rotated signing secret for webhook [id={}, tenant={}]", id, tenantId);

        auditService.recordAuditLog(
                tenantId,
                "WEBHOOK_ROTATE_SECRET",
                "webhook-" + id,
                "SUCCESS",
                null,
                "OPERATOR",
                "Rotated HMAC signing secret"
        );

        return toResponseWithSecret(saved, newRawSecret);
    }

    @Transactional
    public void deleteWebhook(Long id) {
        String tenantId = TenantContext.getTenantId();
        Webhook webhook = webhookRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));

        webhookRepository.delete(webhook);
        log.info("Deleted webhook [id={}, tenant={}]", id, tenantId);

        auditService.recordAuditLog(
                tenantId,
                "WEBHOOK_DELETE",
                "webhook-" + id,
                "SUCCESS",
                null,
                "OPERATOR",
                "Deleted webhook"
        );
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> listDeliveries(Long webhookId) {
        String tenantId = TenantContext.getTenantId();
        // Verify webhook exists and belongs to tenant
        webhookRepository.findByTenantIdAndId(tenantId, webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + webhookId));

        return webhookDeliveryRepository.findByTenantIdAndWebhookIdOrderByCreatedAtDesc(tenantId, webhookId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    @Transactional
    public void dispatchWebhookNotification(String tenantId, String eventType, String idempotencyKey, String payload) {
        String safePayload = (payload != null && !payload.isBlank()) ? payload : "{}";
        List<Webhook> activeWebhooks = webhookRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        for (Webhook webhook : activeWebhooks) {
            if (isSubscribed(webhook, eventType)) {
                sendWebhook(webhook, eventType, idempotencyKey, safePayload);
            }
        }
    }

    private void sendWebhook(Webhook webhook, String eventType, String idempotencyKey, String payload) {
        String eventId = "evt-" + UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                webhook.getTenantId(),
                webhook.getId(),
                eventId,
                eventType,
                idempotencyKey,
                payload
        );
        delivery.setMaxRetries(enhancementProperties.getWebhook().getMaxRetries());
        delivery = webhookDeliveryRepository.save(delivery);

        try {
            URI destinationUri = destinationValidationService.validateDestinationUrl(webhook.getDestinationUrl());
            String rawSecret = cryptoService.decrypt(webhook.getEncryptedSigningSecret());
            long timestamp = Instant.now().getEpochSecond();
            String signature = calculateHmacSha256(timestamp, payload, rawSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(destinationUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ReliableFlow-Webhook/1.0")
                    .header("X-ReliableFlow-Event-Id", eventId)
                    .header("X-ReliableFlow-Event-Type", eventType)
                    .header("X-ReliableFlow-Delivery-Id", String.valueOf(delivery.getId()))
                    .header("X-ReliableFlow-Signature", "t=" + timestamp + ",v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload != null ? payload : "{}"))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();

            delivery.setHttpStatus(statusCode);
            delivery.setAttemptCount(1);
            delivery.setDeliveredAt(OffsetDateTime.now());

            if (statusCode >= 200 && statusCode < 300) {
                delivery.setStatus("SUCCESS");
                delivery.setNextRetryAt(null);
                webhook.setLastDeliveryStatus("SUCCESS");
            } else if (statusCode >= 400 && statusCode < 500) {
                // Permanent client rejection -> no automatic retry
                delivery.setStatus("FAILED");
                delivery.setNextRetryAt(null);
                delivery.setErrorMessage("HTTP response code: " + statusCode);
                webhook.setLastDeliveryStatus("FAILED (" + statusCode + ")");
            } else {
                // Retryable 5xx error
                if (delivery.getMaxRetries() > 1) {
                    delivery.setStatus("RETRY_PENDING");
                    delivery.setNextRetryAt(OffsetDateTime.now().plus(enhancementProperties.getWebhook().calculateBackoff(1)));
                    delivery.setErrorMessage("HTTP response code: " + statusCode);
                    webhook.setLastDeliveryStatus("RETRY_PENDING (" + statusCode + ")");
                } else {
                    delivery.setStatus("EXHAUSTED");
                    delivery.setNextRetryAt(null);
                    delivery.setErrorMessage("HTTP response code: " + statusCode);
                    webhook.setLastDeliveryStatus("EXHAUSTED (" + statusCode + ")");
                }
            }
        } catch (Exception e) {
            log.warn("Webhook delivery error [webhookId={}, url={}]: {}",
                    webhook.getId(), webhook.getDestinationUrl(), e.getMessage());
            delivery.setAttemptCount(1);
            delivery.setErrorMessage(e.getMessage());
            if (delivery.getMaxRetries() > 1) {
                delivery.setStatus("RETRY_PENDING");
                delivery.setNextRetryAt(OffsetDateTime.now().plus(enhancementProperties.getWebhook().calculateBackoff(1)));
                webhook.setLastDeliveryStatus("RETRY_PENDING");
            } else {
                delivery.setStatus("EXHAUSTED");
                delivery.setNextRetryAt(null);
                webhook.setLastDeliveryStatus("EXHAUSTED");
            }
        }

        delivery.setDeliveredAt(OffsetDateTime.now());
        webhook.setLastDeliveryAt(OffsetDateTime.now());
        webhookDeliveryRepository.save(delivery);
        webhookRepository.save(webhook);
    }

    @Transactional
    public WebhookDeliveryResponse redeliver(Long webhookId, Long deliveryId) {
        String tenantId = TenantContext.getTenantId();
        webhookRepository.findByTenantIdAndId(tenantId, webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + webhookId));

        WebhookDelivery delivery = webhookDeliveryRepository.findByTenantIdAndWebhookIdAndId(tenantId, webhookId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found: " + deliveryId));

        delivery.setStatus("RETRY_PENDING");
        delivery.setNextRetryAt(OffsetDateTime.now());
        delivery.setAttemptCount(0); // Reset for operator-initiated redelivery
        delivery.setErrorMessage(null);
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);

        auditService.recordAuditLog(
                tenantId,
                "WEBHOOK_MANUAL_REDELIVERY",
                "webhook-delivery-" + deliveryId,
                "RETRY_PENDING",
                null,
                "OPERATOR",
                "Manual redelivery scheduled for webhook delivery " + deliveryId
        );

        return toDeliveryResponse(saved);
    }

    public static String calculateHmacSha256(long timestamp, String payload, String secret) {
        try {
            String message = timestamp + "." + (payload != null ? payload : "");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private boolean isSubscribed(Webhook webhook, String eventType) {
        if (webhook.getSubscribedEvents() == null || webhook.getSubscribedEvents().isBlank()) {
            return false;
        }
        if ("*".equals(webhook.getSubscribedEvents().trim())) {
            return true;
        }
        String[] events = webhook.getSubscribedEvents().split(",");
        for (String ev : events) {
            if (ev.trim().equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    private WebhookResponse toResponseWithSecret(Webhook webhook, String rawSecret) {
        return new WebhookResponse(
                webhook.getId(),
                webhook.getTenantId(),
                webhook.getName(),
                webhook.getDestinationUrl(),
                webhook.getStatus(),
                webhook.getSubscribedEvents(),
                webhook.getSecretPrefix(),
                rawSecret,
                webhook.getCreatedAt(),
                webhook.getUpdatedAt(),
                webhook.getLastDeliveryAt(),
                webhook.getLastDeliveryStatus()
        );
    }

    private WebhookResponse toResponseWithoutSecret(Webhook webhook) {
        return new WebhookResponse(
                webhook.getId(),
                webhook.getTenantId(),
                webhook.getName(),
                webhook.getDestinationUrl(),
                webhook.getStatus(),
                webhook.getSubscribedEvents(),
                webhook.getSecretPrefix(),
                null, // Never returned on list/get
                webhook.getCreatedAt(),
                webhook.getUpdatedAt(),
                webhook.getLastDeliveryAt(),
                webhook.getLastDeliveryStatus()
        );
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getTenantId(),
                delivery.getWebhookId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getIdempotencyKey(),
                delivery.getPayload(),
                delivery.getStatus(),
                delivery.getHttpStatus(),
                delivery.getAttemptCount(),
                delivery.getErrorMessage(),
                delivery.getCreatedAt(),
                delivery.getDeliveredAt()
        );
    }
}
