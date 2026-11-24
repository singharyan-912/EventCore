package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByTenantIdAndAppointmentId(String tenantId, String appointmentId);

    List<Appointment> findByTenantIdAndCustomerId(String tenantId, String customerId);

    boolean existsByTenantIdAndAppointmentId(String tenantId, String appointmentId);
}
