package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.CreateWebhookRequest;
import com.eventprocessor.reliable_event_processor.dto.WebhookDeliveryResponse;
import com.eventprocessor.reliable_event_processor.dto.WebhookResponse;
import com.eventprocessor.reliable_event_processor.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<WebhookResponse> createWebhook(@Valid @RequestBody CreateWebhookRequest request) {
        WebhookResponse response = webhookService.createWebhook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<WebhookResponse>> listWebhooks() {
        return ResponseEntity.ok(webhookService.listWebhooks());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<WebhookResponse> getWebhook(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.getWebhook(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id) {
        webhookService.deleteWebhook(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate-secret")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<WebhookResponse> rotateSecret(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.rotateSecret(id));
    }

    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<WebhookDeliveryResponse>> listDeliveries(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.listDeliveries(id));
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/redeliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<WebhookDeliveryResponse> redeliver(
            @PathVariable Long id,
            @PathVariable Long deliveryId) {
        return ResponseEntity.ok(webhookService.redeliver(id, deliveryId));
    }
}
