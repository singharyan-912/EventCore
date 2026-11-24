package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.CleanupPreviewResponse;
import com.eventprocessor.reliable_event_processor.dto.CleanupResponse;
import com.eventprocessor.reliable_event_processor.service.DataRetentionCleanupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/cleanup")
public class CleanupController {

    private final DataRetentionCleanupService cleanupService;

    public CleanupController(DataRetentionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<CleanupPreviewResponse> previewCleanup() {
        return ResponseEntity.ok(cleanupService.previewCleanup());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CleanupResponse> executeCleanup(@RequestParam(defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(cleanupService.executeCleanup(dryRun));
    }
}
