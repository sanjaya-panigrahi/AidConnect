package com.chatassist.user.service;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserActivitySummary;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.common.model.AssistantProfile;
import com.chatassist.common.security.JwtUtil;
import com.chatassist.common.security.PasswordUtil;
import com.chatassist.user.config.RedisCacheConfig;
import com.chatassist.user.entity.ActivityEventType;
import com.chatassist.user.entity.AppUser;
import com.chatassist.user.entity.AuthAuditEventType;
import com.chatassist.user.entity.AuthAuditLog;
import com.chatassist.user.entity.UserActivityLog;
import com.chatassist.user.entity.UserCredential;
import com.chatassist.user.repository.AppUserRepository;
import com.chatassist.user.repository.AuthAuditLogRepository;
import com.chatassist.user.repository.UserActivityLogRepository;
import com.chatassist.user.repository.UserCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class UserService {
    private final AppUserRepository userRepository;
    private final UserMapper userMapper;
    private final UserActivityLogRepository activityLogRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final AuthAuditLogRepository authAuditLogRepository;

    @Autowired
    public UserService(AppUserRepository userRepository,
                       UserMapper userMapper,
                       UserActivityLogRepository activityLogRepository,
                       UserCredentialRepository userCredentialRepository,
                       AuthAuditLogRepository authAuditLogRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.activityLogRepository = activityLogRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.authAuditLogRepository = authAuditLogRepository;
    }

    // Backward-compatible ctor used by existing standalone tests that new-up UserService directly.
    public UserService(AppUserRepository userRepository,
                       UserMapper userMapper,
                       UserActivityLogRepository activityLogRepository) {
        this(userRepository, userMapper, activityLogRepository, null, null);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST, allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, allEntries = true)
    })
    public AuthResponse register(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        // Hash password using BCrypt before storing.
        String hashedPassword = PasswordUtil.hashPassword(request.password());

        AppUser user = new AppUser(
                request.firstName(),
                request.lastName(),
                request.username(),
                request.email(),
                false);
        AppUser saved = userRepository.save(user);

        // Credentials are stored only in the dedicated auth table.
        requireUserCredentialRepository().save(new UserCredential(saved.getId(), hashedPassword));

        // Registration creates account only; authentication starts at sign-in.
        return userMapper.toAuthResponse(saved, null, "Registration successful. Please sign in.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        String storedHash = resolvePasswordHash(user);
        if (!PasswordUtil.verifyPassword(request.password(), storedHash)) {
            recordAuthAudit(user.getId(), user.getUsername(), AuthAuditEventType.LOGIN_FAILED, "INVALID_CREDENTIALS");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Record login in activity dashboard + auth audit trail.
        activityLogRepository.save(new UserActivityLog(user.getUsername(), ActivityEventType.LOGIN));
        recordAuthAudit(user.getId(), user.getUsername(), AuthAuditEventType.LOGIN_SUCCESS, null);

        // Generate JWT token – returned to the client so API requests can use Bearer auth.
        String jwtToken = JwtUtil.generateToken(user.getId(), user.getUsername());

        return userMapper.toAuthResponse(user, jwtToken, "Login successful");
    }

    @Cacheable(value = RedisCacheConfig.USERS_LIST, key = "#excludeUsername")
    public List<UserSummary> listUsers(String excludeUsername) {
        return userRepository.findByUsernameNotOrderByBotAscFirstNameAsc(excludeUsername)
                .stream()
                .filter(user -> !user.isBot())
                .map(userMapper::toSummary)
                .toList();
    }

    @Cacheable(value = RedisCacheConfig.ASSISTANTS)
    public List<AssistantSummary> listAssistants() {
        return AssistantProfile.all();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST, allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, key = "#username")
    })
    public void markUserOnline(String username) {
        userRepository.updateOnlineStatus(username, true);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST, allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, key = "#username")
    })
    public void markUserOffline(String username) {
        userRepository.updateOnlineStatus(username, false);
    }

    /** Explicit logout: marks offline and records logout/auth audit events. */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST, allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, key = "#username"),
        @CacheEvict(value = RedisCacheConfig.USER_ACTIVITY, key = "#username"),
        @CacheEvict(value = RedisCacheConfig.ALL_USER_ACTIVITY, allEntries = true)
    })
    public void logout(String username) {
        userRepository.updateOnlineStatus(username, false);
        activityLogRepository.save(new UserActivityLog(username, ActivityEventType.LOGOUT));

        userRepository.findByUsername(username)
                .ifPresent(user -> recordAuthAudit(user.getId(), username, AuthAuditEventType.LOGOUT, null));
    }

    /** Returns today's login/logout counts for the given user. */
    @Cacheable(value = RedisCacheConfig.USER_ACTIVITY, key = "#username")
    public UserActivitySummary getActivitySummary(String username) {
        LocalDate today = LocalDate.now();
        long logins = activityLogRepository.countByUsernameAndDateAndType(username, today, ActivityEventType.LOGIN);
        long logouts = activityLogRepository.countByUsernameAndDateAndType(username, today, ActivityEventType.LOGOUT);
        return new UserActivitySummary(username, today, logins, logouts);
    }

    /** Returns today's login/logout counts for ALL users (one query). */
    @Cacheable(value = RedisCacheConfig.ALL_USER_ACTIVITY)
    public List<UserActivitySummary> getAllUsersActivitySummary() {
        LocalDate today = LocalDate.now();
        return activityLogRepository.findActivityCountsForDate(today).stream()
                .map(row -> new UserActivitySummary(
                        (String) row[0],
                        today,
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    @Cacheable(value = RedisCacheConfig.USER_PROFILE, key = "#username")
    public UserSummary findByUsername(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return userMapper.toSummary(user);
    }

    public AuthResponse getAuthenticatedSession(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return userMapper.toAuthResponse(user, null, "Session active");
    }

    @Transactional
    public void removeLegacyBotUsers() {
        userRepository.deleteLegacyBots();
    }

    private String resolvePasswordHash(AppUser user) {
        return requireUserCredentialRepository().findById(user.getId())
                .map(UserCredential::getPasswordHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"));
    }

    private UserCredentialRepository requireUserCredentialRepository() {
        if (userCredentialRepository == null) {
            throw new IllegalStateException("UserCredentialRepository is required for auth operations");
        }
        return userCredentialRepository;
    }

    private void recordAuthAudit(Long userId, String username, AuthAuditEventType eventType, String reasonCode) {
        if (authAuditLogRepository == null) {
            return;
        }
        authAuditLogRepository.save(new AuthAuditLog(userId, username, eventType, reasonCode));
    }
}
