package com.chatassist.aid.service;

import com.chatassist.aid.entity.Doctor;
import com.chatassist.aid.entity.DoctorAvailability;
import com.chatassist.aid.repository.DoctorAvailabilityRepository;
import com.chatassist.aid.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis caching behaviour in {@link DoctorCacheService}.
 *
 * <p>Containers:
 * <ul>
 *   <li>MySQL 8.0 – persists doctor and availability data</li>
 *   <li>Redis 7   – backs the Spring Cache</li>
 * </ul>
 *
 * Kafka is configured with a non-existent broker address and
 * {@code spring.kafka.listener.auto-startup=false} so the Kafka consumer
 * never connects during this Redis-only test.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DoctorCacheService – Redis Cache Integration Tests")
class DoctorCacheRedisIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("aid_service_db")
            .withUsername("test_user")
            .withPassword("test_password")
            .withCommand("--default-authentication-plugin=mysql_native_password")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO", "yes");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    // ── Dynamic properties ────────────────────────────────────────────────────

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379).toString());
        registry.add("spring.cache.type", () -> "redis");
        // Prevent Kafka from trying to reach a real broker in this Redis-only test
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29099");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private DoctorCacheService doctorCacheService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        availabilityRepository.deleteAll();
        doctorRepository.deleteAll();
        var conn = stringRedisTemplate.getConnectionFactory().getConnection();
        conn.serverCommands().flushAll();
        conn.close();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Containers should be running before any test")
    void containersShouldBeRunning() {
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(redisContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("findActiveDoctors() should write an entry to the Redis aid:doctors cache")
    void findActiveDoctorsShouldPopulateCache() {
        // Arrange
        doctorRepository.save(new Doctor(null, "dr-smith", "Dr. Smith", "Cardiology", true));
        doctorRepository.save(new Doctor(null, "dr-jones", "Dr. Jones", "Neurology", true));

        // Act
        List<Doctor> doctors = doctorCacheService.findActiveDoctors();

        // Assert – result is correct
        assertThat(doctors).hasSize(2);

        // Assert – Redis entry exists for the doctors cache
        Set<String> keys = stringRedisTemplate.keys("aid-svc:v2::aid:doctors::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("findActiveDoctors() second call should be served from Redis (DB bypassed)")
    void findActiveDoctorsSecondCallShouldUseCache() {
        // Arrange
        doctorRepository.save(new Doctor(null, "dr-brown", "Dr. Brown", "Dermatology", true));

        // First call – populates cache
        List<Doctor> firstCall = doctorCacheService.findActiveDoctors();
        assertThat(firstCall).hasSize(1);

        // Mutate DB directly (bypassing cache)
        doctorRepository.save(new Doctor(null, "dr-white", "Dr. White", "Oncology", true));

        // Second call – cache should still return 1 doctor, not 2
        List<Doctor> secondCall = doctorCacheService.findActiveDoctors();
        assertThat(secondCall).hasSize(1);
    }

    @Test
    @DisplayName("findUpcomingSlots() should write an entry to the Redis aid:doctor:slots cache")
    void findUpcomingSlotsShouldPopulateSlotsCache() {
        // Arrange
        Doctor doctor = doctorRepository.save(
                new Doctor(null, "dr-taylor", "Dr. Taylor", "Orthopaedics", true));
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        availabilityRepository.save(new DoctorAvailability(null, doctor, future, true));

        // Act
        List<DoctorAvailability> slots =
                doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());

        // Assert – result
        assertThat(slots).hasSize(1);

        // Assert – Redis entry in aid:doctor:slots cache
        Set<String> keys = stringRedisTemplate.keys("aid-svc:v2::aid:doctor:slots::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("findUpcomingSlots() second call should be served from Redis (DB bypassed)")
    void findUpcomingSlotsShouldServeSecondCallFromCache() {
        // Arrange
        Doctor doctor = doctorRepository.save(
                new Doctor(null, "dr-lee", "Dr. Lee", "Psychiatry", true));
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        availabilityRepository.save(new DoctorAvailability(null, doctor, future, true));

        // First call – populate cache
        List<DoctorAvailability> firstCall =
                doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());
        assertThat(firstCall).hasSize(1);

        // Add another slot directly to DB (bypass cache eviction)
        availabilityRepository.save(
                new DoctorAvailability(null, doctor, future.plusHours(2), true));

        // Second call – should return cached result (1 slot, not 2)
        List<DoctorAvailability> secondCall =
                doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());
        assertThat(secondCall).hasSize(1);
    }

    @Test
    @DisplayName("evictDoctorSlots() should remove the aid:doctor:slots cache entry for that doctor")
    void evictDoctorSlotsShouldClearSlotsCache() {
        // Arrange
        Doctor doctor = doctorRepository.save(
                new Doctor(null, "dr-martin", "Dr. Martin", "Gynaecology", true));
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        availabilityRepository.save(new DoctorAvailability(null, doctor, future, true));

        // Populate cache
        doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());
        Set<String> keysBefore = stringRedisTemplate.keys("aid-svc:v2::aid:doctor:slots::*");
        assertThat(keysBefore).isNotNull().isNotEmpty();

        // Act – evict
        doctorCacheService.evictDoctorSlots(doctor.getId());

        // Assert – cache entry gone
        Set<String> keysAfter = stringRedisTemplate.keys("aid-svc:v2::aid:doctor:slots::*");
        assertThat(keysAfter).isNullOrEmpty();
    }

    @Test
    @DisplayName("findDoctorById() should write an entry to the Redis aid:doctor:by-id cache")
    void findDoctorByIdShouldPopulateCache() {
        // Arrange
        Doctor doctor = doctorRepository.save(
                new Doctor(null, "dr-ng", "Dr. Ng", "Radiology", true));

        // Act
        var result = doctorCacheService.findDoctorById(doctor.getId());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("dr-ng");

        Set<String> keys = stringRedisTemplate.keys("aid-svc:v2::aid:doctor:by-id::*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("evictAll() should clear all aid service caches")
    void evictAllShouldClearAllCaches() {
        // Arrange – populate multiple caches
        Doctor doctor = doctorRepository.save(
                new Doctor(null, "dr-patel", "Dr. Patel", "Gastroenterology", true));
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        availabilityRepository.save(new DoctorAvailability(null, doctor, future, true));

        doctorCacheService.findActiveDoctors();
        doctorCacheService.findDoctorById(doctor.getId());
        doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());

        Set<String> keysBefore = stringRedisTemplate.keys("aid-svc:v2::*");
        assertThat(keysBefore).isNotNull().hasSizeGreaterThanOrEqualTo(3);

        // Act
        doctorCacheService.evictAll();

        // Assert – all aid-svc keys gone
        Set<String> keysAfter = stringRedisTemplate.keys("aid-svc:v2::*");
        assertThat(keysAfter).isNullOrEmpty();
    }
}

