package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.dto.ReconcileRequest;
import com.eventprocessor.reliable_event_processor.dto.ReconcileResponse;
import com.eventprocessor.reliable_event_processor.dto.ReconciliationDetailResponse;
import com.eventprocessor.reliable_event_processor.service.OperationReconciliationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/reconciliations")
public class ReconciliationController {

    private final OperationReconciliationService reconciliationService;

    public ReconciliationController(OperationReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<PageResponse<ReconciliationDetailResponse>> listReconciliations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reconciliationService.listReconciliations(page, size));
    }

    @GetMapping("/{idempotencyKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<ReconciliationDetailResponse> getReconciliation(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(reconciliationService.getReconciliation(idempotencyKey));
    }

    @PostMapping("/{idempotencyKey}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ReconcileResponse> verifyAutomatically(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(reconciliationService.reconcileAutomatically(idempotencyKey));
    }

    @PostMapping("/{idempotencyKey}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ReconcileResponse> resolveManually(
            @PathVariable String idempotencyKey,
            @Valid @RequestBody ReconcileRequest request) {
        return ResponseEntity.ok(reconciliationService.reconcileManually(idempotencyKey, request));
    }
}
