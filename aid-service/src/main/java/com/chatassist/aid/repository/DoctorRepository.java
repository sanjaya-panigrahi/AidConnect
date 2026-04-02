package com.chatassist.aid.repository;

import com.chatassist.aid.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByActiveTrueOrderByDisplayNameAsc();
}

