package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.ConnectionTestResponse;
import com.eventprocessor.reliable_event_processor.dto.CreateIntegrationRequest;
import com.eventprocessor.reliable_event_processor.dto.IntegrationResponse;
import com.eventprocessor.reliable_event_processor.dto.UpdateIntegrationRequest;
import com.eventprocessor.reliable_event_processor.service.IntegrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<IntegrationResponse> createIntegration(@Valid @RequestBody CreateIntegrationRequest request) {
        IntegrationResponse response = integrationService.createIntegration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<IntegrationResponse>> listIntegrations() {
        return ResponseEntity.ok(integrationService.listIntegrations());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<IntegrationResponse> getIntegration(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.getIntegration(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<IntegrationResponse> updateIntegration(@PathVariable Long id,
                                                                 @RequestBody UpdateIntegrationRequest request) {
        return ResponseEntity.ok(integrationService.updateIntegration(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id) {
        integrationService.deleteIntegration(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ConnectionTestResponse> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.testConnection(id));
    }
}
