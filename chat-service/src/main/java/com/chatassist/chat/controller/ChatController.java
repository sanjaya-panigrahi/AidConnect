package com.chatassist.chat.controller;

import com.chatassist.chat.service.ChatMessagingService;
import com.chatassist.common.dto.ApiErrorResponse;
import com.chatassist.common.dto.ChatMessageRequest;
import com.chatassist.common.dto.ChatMessageResponse;
import com.chatassist.common.dto.DailyChatPeerSummary;
import com.chatassist.common.dto.StatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chat", description = "Chat messaging and activity APIs")
public class ChatController {

    private final ChatMessagingService chatMessagingService;

    public ChatController(ChatMessagingService chatMessagingService) {
        this.chatMessagingService = chatMessagingService;
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a chat message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChatMessageResponse sendMessage(@Valid @RequestBody ChatMessageRequest request) {
        return chatMessagingService.send(request, false);
    }

    @PostMapping("/messages/internal")
    @Operation(summary = "Send an internal bot-generated chat message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Internal message sent",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChatMessageResponse sendInternalMessage(@Valid @RequestBody ChatMessageRequest request) {
        return chatMessagingService.send(request, true);
    }

    @GetMapping("/conversation")
    @Operation(summary = "Get conversation history between two users")
    @ApiResponse(responseCode = "200", description = "Conversation loaded",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatMessageResponse.class))))
    public List<ChatMessageResponse> getConversation(@RequestParam String userA, @RequestParam String userB) {
        return chatMessagingService.getConversation(userA, userB);
    }

    @GetMapping("/{username}/activity/today")
    @Operation(summary = "Get today's chat activity summary for one user")
    @ApiResponse(responseCode = "200", description = "Activity summary returned",
            content = @Content(schema = @Schema(implementation = DailyChatPeerSummary.class)))
    public DailyChatPeerSummary getDailyChatPeerSummary(@PathVariable String username) {
        return chatMessagingService.getDailyChatPeerSummary(username);
    }

    @GetMapping("/activity/today")
    @Operation(summary = "Get today's chat activity summaries for all users")
    @ApiResponse(responseCode = "200", description = "All user activity summaries returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyChatPeerSummary.class))))
    public List<DailyChatPeerSummary> getAllDailyChatPeerSummaries() {
        return chatMessagingService.getAllDailyChatPeerSummaries();
    }

    @PatchMapping("/messages/status")
    @Operation(summary = "Update chat message delivery/seen status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message status updated",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Message not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChatMessageResponse updateStatus(@Valid @RequestBody StatusUpdateRequest request) {
        return chatMessagingService.updateStatus(request);
    }
}
