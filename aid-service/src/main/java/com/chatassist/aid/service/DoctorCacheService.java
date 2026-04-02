package com.chatassist.aid.service;

import com.chatassist.aid.config.RedisCacheConfig;
import com.chatassist.aid.entity.Doctor;
import com.chatassist.aid.entity.DoctorAvailability;
import com.chatassist.aid.repository.DoctorAvailabilityRepository;
import com.chatassist.aid.repository.DoctorRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Thin read-only wrapper around doctor-related repositories.
 *
 * <p>All methods are annotated with {@code @Cacheable} so the expensive DB
 * queries are served from Redis on subsequent calls.  Write paths in
 * {@link AppointmentAssistantService} call {@link #evictDoctorSlots} to
 * invalidate slot caches after a booking is confirmed.</p>
 *
 * <p>This class is intentionally <em>not</em> {@code @Transactional} at class
 * level so that Spring's caching proxy can intercept calls without conflict.</p>
 */
@Service
public class DoctorCacheService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;

    public DoctorCacheService(DoctorRepository doctorRepository,
                               DoctorAvailabilityRepository availabilityRepository) {
        this.doctorRepository      = doctorRepository;
        this.availabilityRepository = availabilityRepository;
    }

    // ── Doctor reads ──────────────────────────────────────────────────────────

    /**
     * Returns all active doctors sorted by display name.
     * Cached for 1 hour — doctor roster changes rarely.
     */
    @Cacheable(value = RedisCacheConfig.AID_DOCTORS)
    @Transactional(readOnly = true)
    public List<Doctor> findActiveDoctors() {
        return doctorRepository.findByActiveTrueOrderByDisplayNameAsc();
    }

    /**
     * Looks up a single doctor by primary key.
     * Cached for 1 hour — doctor entities are immutable in practice.
     */
    @Cacheable(value = RedisCacheConfig.AID_DOCTOR_BY_ID, key = "#id")
    @Transactional(readOnly = true)
    public Optional<Doctor> findDoctorById(Long id) {
        return doctorRepository.findById(id);
    }

    // ── Availability reads ────────────────────────────────────────────────────

    /**
     * Returns up to 8 upcoming slots for a doctor (used in the slot-list menu).
     * Cached per {@code doctorId} for 5 minutes.
     */
    @Cacheable(value = RedisCacheConfig.AID_DOCTOR_SLOTS, key = "#doctorId")
    @Transactional(readOnly = true)
    public List<DoctorAvailability> findUpcomingSlots(Long doctorId, LocalDateTime from) {
        return availabilityRepository
                .findTop8ByDoctorIdAndEnabledTrueAndAvailableAtGreaterThanEqualOrderByAvailableAtAsc(
                        doctorId, from);
    }

    /**
     * Looks up a single availability slot by id.
     * No cache — called only during the confirmation step where freshness matters.
     */
    @Transactional(readOnly = true)
    public Optional<DoctorAvailability> findSlotById(Long id) {
        return availabilityRepository.findById(id);
    }

    // ── Cache eviction ────────────────────────────────────────────────────────

    /**
     * Evict slot cache for a specific doctor after a booking is confirmed or
     * cancelled so the next interaction reflects real availability.
     */
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.AID_DOCTOR_SLOTS, key = "#doctorId")
    })
    public void evictDoctorSlots(Long doctorId) {
        // eviction only — no DB call needed
    }

    /**
     * Full eviction of all aid caches — use sparingly (e.g., admin slot refresh).
     */
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.AID_DOCTORS,      allEntries = true),
        @CacheEvict(value = RedisCacheConfig.AID_DOCTOR_SLOTS, allEntries = true),
        @CacheEvict(value = RedisCacheConfig.AID_DOCTOR_BY_ID, allEntries = true)
    })
    public void evictAll() {
        // eviction only
    }
}

