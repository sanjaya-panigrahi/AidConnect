# Common Service

## Overview

The **Common Service** is a shared library module (not a deployable application).
It is included as a Maven dependency in all other services and provides shared:

- Data Transfer Objects (DTOs)
- Domain model enumerations
- JWT utility (sign + validate tokens)
- Auth session key constants

## Key Contents

### DTOs (`com.chatassist.common.dto`)

| Class | Description |
|---|---|
| `AuthResponse` | Login/register response: `{ userId, username, token }` |
| `LoginRequest` | Login request: `{ username, password }` |
| `RegisterUserRequest` | Registration request |
| `ChatMessageRequest` | Send message request |
| `ChatMessageResponse` | Message response DTO |
| `ChatMessageEvent` | Kafka event record |
| `StatusUpdateRequest` | Status PATCH request: `{ messageId, status }` |
| `UserSummary` | User profile DTO for directory |
| `AssistantSummary` | Assistant listing DTO |
| `UserActivitySummary` | Daily login/logout count |
| `DailyChatPeerSummary` | Daily distinct chat peer count |
| `AiReplyRequest` / `AiReplyResponse` | AI reply wrapper |
| `ApiErrorResponse` | Standard error response |

### Models (`com.chatassist.common.model`)

| Class | Values |
|---|---|
| `MessageStatus` | `SENT`, `DELIVERED`, `SEEN` |
| `MessageType` | `USER`, `BOT` |
| `AssistantProfile` | `BOT` (id=2, username=bot), `AID` (id=3, username=aid) |

### Security (`com.chatassist.common.security`)

| Class | Description |
|---|---|
| `JwtUtil` | Generate and validate JWT tokens; extract `userId`, `username`, full principal |
| `AuthSessionKeyUtil` | Constants for Spring Session attribute names |

## Usage

This module is consumed as a Maven dependency — it is **not** deployed standalone.

```xml
<dependency>
    <groupId>com.chatassist</groupId>
    <artifactId>common-service</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Build

```bash
mvn clean install -pl common-service
```
