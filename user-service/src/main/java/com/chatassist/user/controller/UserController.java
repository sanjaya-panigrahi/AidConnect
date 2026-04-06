package com.chatassist.user.controller;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.ApiErrorResponse;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserActivitySummary;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.common.security.AuthSessionKeyUtil;
import com.chatassist.user.service.AuthSessionService;
import com.chatassist.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User authentication, directory, presence, and activity APIs")
public class UserController {

    private static final String AUTH_USER_HEADER = "X-Username";

    private final UserService userService;
    private final AuthSessionService authSessionService;

    public UserController(UserService userService, AuthSessionService authSessionService) {
        this.userService = userService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already exists",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResponse response = userService.login(request);

        HttpSession existing = servletRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }

        HttpSession session = servletRequest.getSession(true);
        authSessionService.initializeAuthenticatedSession(session, response.userId(), response.username());
        return response;
    }

    @GetMapping("/session")
    @Operation(summary = "Get current authenticated server-side session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session active",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "No active authenticated session",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse getCurrentSession(@RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                          HttpSession session) {
        String username = requireAuthenticatedUsername(authenticatedUsername, session);
        return userService.getAuthenticatedSession(username);
    }

    @GetMapping
    @Operation(summary = "List users, optionally excluding one username")
    @ApiResponse(responseCode = "200", description = "User list returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSummary.class))))
    public List<UserSummary> listUsers(@RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                       HttpSession session,
                                       @RequestParam(defaultValue = "") String excludeUsername) {
        requireAuthenticatedUsername(authenticatedUsername, session);
        return userService.listUsers(excludeUsername);
    }

    @GetMapping("/assistants")
    @Operation(summary = "List assistant users")
    @ApiResponse(responseCode = "200", description = "Assistant list returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssistantSummary.class))))
    public List<AssistantSummary> listAssistants() {
        return userService.listAssistants();
    }

    @GetMapping("/by-username/{username}")
    @Operation(summary = "Get user profile by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile returned",
                    content = @Content(schema = @Schema(implementation = UserSummary.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserSummary getUserByUsername(@PathVariable String username) {
        return userService.findByUsername(username);
    }

    @PutMapping("/{username}/online")
    @Operation(summary = "Mark a user as online")
    @ApiResponse(responseCode = "200", description = "Presence updated")
    public void markUserOnline(@PathVariable String username,
                               @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                               HttpSession session) {
        requirePathAccess(authenticatedUsername, session, username);
        userService.markUserOnline(username);
    }

    @PutMapping("/{username}/offline")
    @Operation(summary = "Mark a user as offline")
    @ApiResponse(responseCode = "200", description = "Presence updated")
    public void markUserOffline(@PathVariable String username,
                                @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                HttpSession session) {
        requirePathAccess(authenticatedUsername, session, username);
        userService.markUserOffline(username);
    }

    /** Explicit logout: marks offline + records a logout event. */
    @PostMapping("/logout")
    @Operation(summary = "Log out current authenticated user and record logout activity")
    @ApiResponse(responseCode = "200", description = "Logout recorded")
    public void logoutCurrentUser(@RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                  HttpSession session,
                                  HttpServletResponse response) {
        String username = requireAuthenticatedUsername(authenticatedUsername, session);
        performLogout(username, session, response);
    }

    /** Explicit logout (legacy path): marks offline + records a logout event. */
    @Deprecated
    @PostMapping("/{username}/logout")
    @Operation(summary = "Log out a user and record logout activity (legacy path)", deprecated = true)
    @ApiResponse(responseCode = "200", description = "Logout recorded")
    public void logoutLegacyPath(@PathVariable String username,
                                 @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                 HttpSession session,
                                 HttpServletResponse response) {
        requirePathAccess(authenticatedUsername, session, username);
        performLogout(username, session, response);
    }

    /** Returns today's login/logout counts for the given user. */
    @GetMapping("/{username}/activity/today")
    @Operation(summary = "Get today's login/logout summary for a single user")
    @ApiResponse(responseCode = "200", description = "Activity summary returned",
            content = @Content(schema = @Schema(implementation = UserActivitySummary.class)))
    public UserActivitySummary getTodayActivity(@PathVariable String username,
                                                @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                                HttpSession session) {
        requirePathAccess(authenticatedUsername, session, username);
        return userService.getActivitySummary(username);
    }

    /** Returns today's login/logout counts for ALL users in a single call. */
    @GetMapping("/activity/today")
    @Operation(summary = "Get today's login/logout summaries for all users")
    @ApiResponse(responseCode = "200", description = "All activity summaries returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserActivitySummary.class))))
    public List<UserActivitySummary> getAllTodayActivity(@RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUsername,
                                                         HttpSession session) {
        requireAuthenticatedUsername(authenticatedUsername, session);
        return userService.getAllUsersActivitySummary();
    }

    private void requirePathAccess(String authenticatedUsername, HttpSession session, String pathUsername) {
        String requester = requireAuthenticatedUsername(authenticatedUsername, session);
        if (!requester.equals(pathUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own user resource");
        }
    }

    private String requireAuthenticatedUsername(String authenticatedUsername, HttpSession session) {
        if (authenticatedUsername != null && !authenticatedUsername.isBlank()) {
            return authenticatedUsername;
        }

        Object usernameFromSession = session != null ? session.getAttribute(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE) : null;
        if (!(usernameFromSession instanceof String usernameFromToken) || usernameFromToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user context");
        }
        return usernameFromToken;
    }

    private void performLogout(String username, HttpSession session, HttpServletResponse response) {
        userService.logout(username);
        authSessionService.invalidate(session);
        clearSessionCookies(response);
    }

    private void clearSessionCookies(HttpServletResponse response) {
        // Clear both names to cover servlet and Spring Session cookie strategies.
        Cookie sessionCookie = new Cookie("SESSION", "");
        sessionCookie.setHttpOnly(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0);
        response.addCookie(sessionCookie);

        Cookie jsessionCookie = new Cookie("JSESSIONID", "");
        jsessionCookie.setHttpOnly(true);
        jsessionCookie.setPath("/");
        jsessionCookie.setMaxAge(0);
        response.addCookie(jsessionCookie);
    }
}
