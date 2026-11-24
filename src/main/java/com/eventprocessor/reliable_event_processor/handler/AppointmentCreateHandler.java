package com.eventprocessor.reliable_event_processor.handler;

import com.eventprocessor.reliable_event_processor.entity.Appointment;
import com.eventprocessor.reliable_event_processor.exception.OperationValidationException;
import com.eventprocessor.reliable_event_processor.repository.AppointmentRepository;
import com.eventprocessor.reliable_event_processor.service.TransientFailureSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class AppointmentCreateHandler implements BusinessOperationHandler<AppointmentCreateHandler.AppointmentResult> {

    public static final String OPERATION_TYPE = "APPOINTMENT_CREATE";

    public record AppointmentResult(
            String appointmentId,
            String customerId,
            String serviceId,
            String appointmentTime,
            String status,
            String notes
    ) {}

    private final AppointmentRepository appointmentRepository;
    private final TransientFailureSimulator failureSimulator;
    private final ObjectMapper objectMapper;

    public AppointmentCreateHandler(AppointmentRepository appointmentRepository,
                                    TransientFailureSimulator failureSimulator,
                                    ObjectMapper objectMapper) {
        this.appointmentRepository = appointmentRepository;
        this.failureSimulator = failureSimulator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getOperationType() {
        return OPERATION_TYPE;
    }

    @Override
    public void validate(String tenantId, String resourceId, JsonNode payload) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new OperationValidationException("resourceId (customer reference) must not be blank for APPOINTMENT_CREATE");
        }
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new OperationValidationException("Payload must be a non-null JSON object for APPOINTMENT_CREATE");
        }

        String serviceId = extractText(payload, "serviceId");
        if (serviceId == null || serviceId.isBlank()) {
            throw new OperationValidationException("Missing required field 'serviceId' in appointment payload");
        }

        String timeStr = extractText(payload, "appointmentTime");
        if (timeStr == null || timeStr.isBlank()) {
            throw new OperationValidationException("Missing required field 'appointmentTime' in appointment payload");
        }

        parseAppointmentTime(timeStr);
    }

    @Override
    public AppointmentResult execute(String tenantId, String idempotencyKey, String resourceId, JsonNode payload) {
        String serviceId = extractText(payload, "serviceId");
        String timeStr = extractText(payload, "appointmentTime");
        String notes = extractText(payload, "notes");
        OffsetDateTime appointmentTime = parseAppointmentTime(timeStr);

        // Transient failure simulation hook
        failureSimulator.maybeSimulateTransientFailure(idempotencyKey, resourceId, 2);

        String appointmentId = "apt-" + idempotencyKey;

        // Save appointment within current database transaction
        Appointment appointment = appointmentRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId)
                .orElseGet(() -> new Appointment(tenantId, appointmentId, resourceId, serviceId, appointmentTime, notes));

        appointment = appointmentRepository.save(appointment);

        return new AppointmentResult(
                appointment.getAppointmentId(),
                appointment.getCustomerId(),
                appointment.getServiceId(),
                appointment.getAppointmentTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                appointment.getStatus(),
                appointment.getNotes()
        );
    }

    @Override
    public String canonicalizePayload(String resourceId, JsonNode payload) {
        String serviceId = extractText(payload, "serviceId");
        String timeStr = extractText(payload, "appointmentTime");
        OffsetDateTime parsedTime = parseAppointmentTime(timeStr);
        String formattedTime = parsedTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Canonical deterministic fingerprint representation
        return "operationType=APPOINTMENT_CREATE|customerId=%s|serviceId=%s|appointmentTime=%s"
                .formatted(
                        resourceId != null ? resourceId.trim() : "",
                        serviceId != null ? serviceId.trim() : "",
                        formattedTime
                );
    }

    @Override
    public boolean isRetryable(Exception ex) {
        if (ex instanceof OperationValidationException) {
            return false;
        }
        if (ex instanceof DateTimeParseException) {
            return false;
        }
        return !(ex instanceof IllegalArgumentException);
    }

    @Override
    public JsonNode serializeResult(AppointmentResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("appointmentId", result.appointmentId());
        node.put("customerId", result.customerId());
        node.put("serviceId", result.serviceId());
        node.put("appointmentTime", result.appointmentTime());
        node.put("status", result.status());
        if (result.notes() != null) {
            node.put("notes", result.notes());
        }
        return node;
    }

    private String extractText(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText().trim();
        }
        if (node.has("payload") && node.get("payload").isObject()) {
            JsonNode inner = node.get("payload");
            if (inner.has(field) && !inner.get(field).isNull()) {
                return inner.get(field).asText().trim();
            }
        }
        return null;
    }

    private OffsetDateTime parseAppointmentTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            throw new OperationValidationException("appointmentTime must not be empty");
        }
        try {
            return OffsetDateTime.parse(timeStr.trim(), DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e1) {
            try {
                // Support LocalDateTime string e.g. 2026-12-01T10:30:00 by interpreting in UTC
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(timeStr.trim());
                return ldt.atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new OperationValidationException("Invalid appointmentTime format (expected ISO-8601): " + timeStr);
            }
        }
    }
}
