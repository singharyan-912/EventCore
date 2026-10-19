package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = new HealthResponse("UP", "Reliable Event Processing Platform is running");
        return ResponseEntity.ok(response);
    }
}
