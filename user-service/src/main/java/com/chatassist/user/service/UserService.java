package com.chatassist.user.service;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserActivitySummary;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.common.model.AssistantProfile;
import com.chatassist.user.config.RedisCacheConfig;
import com.chatassist.user.entity.ActivityEventType;
import com.chatassist.user.entity.AppUser;
import com.chatassist.user.entity.UserActivityLog;
import com.chatassist.user.repository.AppUserRepository;
import com.chatassist.user.repository.UserActivityLogRepository;
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

    public UserService(AppUserRepository userRepository,
                       UserMapper userMapper,
                       UserActivityLogRepository activityLogRepository) {
        this.userRepository       = userRepository;
        this.userMapper           = userMapper;
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST,    allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE,  allEntries = true)
    })
    public AuthResponse register(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        AppUser user = new AppUser(
                request.firstName(), request.lastName(),
                request.username(), request.password(),
                request.email(), false);
        AppUser saved = userRepository.save(user);

        // Record first login (registration = first session)
        activityLogRepository.save(new UserActivityLog(saved.getUsername(), ActivityEventType.LOGIN));

        return userMapper.toAuthResponse(saved, "Registration successful");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!user.getPassword().equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Record login event
        activityLogRepository.save(new UserActivityLog(user.getUsername(), ActivityEventType.LOGIN));

        return userMapper.toAuthResponse(user, "Login successful");
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
        @CacheEvict(value = RedisCacheConfig.USERS_LIST,   allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, key = "#username")
    })
    public void markUserOnline(String username) {
        userRepository.updateOnlineStatus(username, true);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST,   allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE, key = "#username")
    })
    public void markUserOffline(String username) {
        userRepository.updateOnlineStatus(username, false);
    }

    /** Explicit logout: marks offline AND records a logout event. */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.USERS_LIST,        allEntries = true),
        @CacheEvict(value = RedisCacheConfig.USER_PROFILE,      key = "#username"),
        @CacheEvict(value = RedisCacheConfig.USER_ACTIVITY,     key = "#username"),
        @CacheEvict(value = RedisCacheConfig.ALL_USER_ACTIVITY, allEntries = true)
    })
    public void logout(String username) {
        userRepository.updateOnlineStatus(username, false);
        activityLogRepository.save(new UserActivityLog(username, ActivityEventType.LOGOUT));
    }

    /** Returns today's login/logout counts for the given user. */
    @Cacheable(value = RedisCacheConfig.USER_ACTIVITY, key = "#username")
    public UserActivitySummary getActivitySummary(String username) {
        LocalDate today = LocalDate.now();
        long logins  = activityLogRepository.countByUsernameAndDateAndType(username, today, ActivityEventType.LOGIN);
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

    @Transactional
    public void removeLegacyBotUsers() {
        userRepository.deleteLegacyBots();
    }
}

