package com.chatassist.aid.repository;

import com.chatassist.aid.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    boolean existsByDoctorIdAndAvailableAtAndEnabledTrue(Long doctorId, LocalDateTime availableAt);

    Optional<DoctorAvailability> findFirstByDoctorIdAndEnabledTrueAndAvailableAtGreaterThanEqualOrderByAvailableAtAsc(
            Long doctorId,
            LocalDateTime availableAt
    );

    List<DoctorAvailability> findTop5ByDoctorIdAndEnabledTrueAndAvailableAtGreaterThanEqualOrderByAvailableAtAsc(
            Long doctorId,
            LocalDateTime availableAt
    );

    List<DoctorAvailability> findTop8ByDoctorIdAndEnabledTrueAndAvailableAtGreaterThanEqualOrderByAvailableAtAsc(
            Long doctorId,
            LocalDateTime availableAt
    );
}

