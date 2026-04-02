package com.chatassist.aid.repository;

import com.chatassist.aid.entity.AppointmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AppointmentBookingRepository extends JpaRepository<AppointmentBooking, Long> {
    boolean existsByDoctorIdAndAppointmentTimeAndStatus(Long doctorId, LocalDateTime appointmentTime, String status);
}

