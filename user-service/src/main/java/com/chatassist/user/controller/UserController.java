package com.chatassist.user.controller;

import com.chatassist.common.dto.AuthResponse;
import com.chatassist.common.dto.AssistantSummary;
import com.chatassist.common.dto.ApiErrorResponse;
import com.chatassist.common.dto.LoginRequest;
import com.chatassist.common.dto.RegisterUserRequest;
import com.chatassist.common.dto.UserActivitySummary;
import com.chatassist.common.dto.UserSummary;
import com.chatassist.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User authentication, directory, presence, and activity APIs")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping
    @Operation(summary = "List users, optionally excluding one username")
    @ApiResponse(responseCode = "200", description = "User list returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSummary.class))))
    public List<UserSummary> listUsers(@RequestParam(defaultValue = "") String excludeUsername) {
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
    public void markUserOnline(@PathVariable String username) {
        userService.markUserOnline(username);
    }

    @PutMapping("/{username}/offline")
    @Operation(summary = "Mark a user as offline")
    @ApiResponse(responseCode = "200", description = "Presence updated")
    public void markUserOffline(@PathVariable String username) {
        userService.markUserOffline(username);
    }

    /** Explicit logout: marks offline + records a logout event. */
    @PostMapping("/{username}/logout")
    @Operation(summary = "Log out a user and record logout activity")
    @ApiResponse(responseCode = "200", description = "Logout recorded")
    public void logout(@PathVariable String username) {
        userService.logout(username);
    }

    /** Returns today's login/logout counts for the given user. */
    @GetMapping("/{username}/activity/today")
    @Operation(summary = "Get today's login/logout summary for a single user")
    @ApiResponse(responseCode = "200", description = "Activity summary returned",
            content = @Content(schema = @Schema(implementation = UserActivitySummary.class)))
    public UserActivitySummary getTodayActivity(@PathVariable String username) {
        return userService.getActivitySummary(username);
    }

    /** Returns today's login/logout counts for ALL users in a single call. */
    @GetMapping("/activity/today")
    @Operation(summary = "Get today's login/logout summaries for all users")
    @ApiResponse(responseCode = "200", description = "All activity summaries returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserActivitySummary.class))))
    public List<UserActivitySummary> getAllTodayActivity() {
        return userService.getAllUsersActivitySummary();
    }
}
