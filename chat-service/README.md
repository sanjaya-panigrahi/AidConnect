# Chat Service

## Overview

The **Chat Service** is the core messaging microservice of the Chat Assist application. It handles all message persistence, conversation history retrieval, message delivery/seen status tracking, real-time WebSocket broadcasting via STOMP/SockJS, and Kafka-based event publishing for downstream assistant consumers.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8082` |

## Key Responsibilities

- Send and persist chat messages (user-to-user and user-to-assistant)
- Accept internal bot-generated replies from `bot-service` and `aid-service`
- Retrieve conversation history between two users
- Update message delivery and seen status
- Broadcast new messages and status updates over WebSocket (`/topic/messages/{username}`, `/topic/status/{username}`)
- Publish `ChatMessageEvent` to the Kafka `chat-messages` topic
- Track daily chat activity (number of distinct peers chatted with)

## Package Structure

```
com.chatassist.chat
├── ChatServiceApplication.java         # Spring Boot entry point
├── config/
│   ├── WebSocketConfig.java            # STOMP + SockJS broker configuration
│   ├── OpenApiConfig.java              # Swagger/OpenAPI customisation
│   └── ...                             # Kafka, Redis, cache configs
├── controller/
│   ├── ChatController.java             # REST endpoints
│   └── ChatControllerAdvice.java       # Global exception handler
├── entity/
│   └── ChatMessage.java                # chat_messages JPA entity
├── repository/
│   └── ChatMessageRepository.java      # Spring Data JPA repository
├── service/
│   ├── ChatMessagingService.java       # Core messaging business logic
│   ├── ChatEventPublisher.java         # Kafka publisher
│   ├── WebSocketNotifier.java          # STOMP broadcast helper
│   └── ChatMessageMapper.java          # Entity <-> DTO mapper
└── client/
    └── ...                             # Internal HTTP clients (if any)
```

## WebSocket Configuration

| Property | Value |
|---|---|
| Endpoint | `/ws-chat` |
| Transport | SockJS |
| Topic prefix | `/topic` |
| App destination prefix | `/app` |
| Allowed origins | `*` (configurable for production) |

### Client subscriptions

```
/topic/messages/{username}   — new messages for a user
/topic/status/{username}     — message status updates (DELIVERED / SEEN)
```

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/chats/messages` | Protected | Send a user message |
| `POST` | `/api/chats/messages/internal` | Internal | Send a bot-generated message |
| `GET` | `/api/chats/conversation` | Protected | Get conversation history (`?userA=&userB=`) |
| `GET` | `/api/chats/{username}/activity/today` | Protected (self) | Today's chat peer count |
| `GET` | `/api/chats/activity/today` | Protected | All users' chat activity today |
| `PATCH` | `/api/chats/messages/status` | Protected | Update message status (DELIVERED/SEEN) |

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java 21 |
| Real-time | Spring WebSocket + STOMP + SockJS |
| Persistence | Spring Data JPA + MySQL 8 |
| Messaging | Apache Kafka (`chat-messages` topic) |
| Cache | Redis + Spring Cache |
| Service Discovery | Netflix Eureka Client |
| API Documentation | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, TestContainers (MySQL + Kafka) |

## Database

Owns the **chat-service MySQL database** (port `3308`):

- `chat_messages` — all persisted chat messages with status and timestamps

## Local Development

```bash
# Build
mvn clean package -pl chat-service -am -DskipTests

# Run (requires MySQL + Redis + Kafka)
java -jar chat-service/target/chat-service-1.0.0.jar
```

## Swagger UI

Available at `http://localhost:8082/swagger-ui.html` when running locally.

