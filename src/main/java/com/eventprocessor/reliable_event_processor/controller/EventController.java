package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.EventRequest;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.service.EventProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventProcessingService eventProcessingService;

    public EventController(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> processEvent(@Valid @RequestBody EventRequest request) {
        EventResponse response = eventProcessingService.processEvent(request);
        if (EventStatus.RETRY_PENDING.name().equals(response.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
