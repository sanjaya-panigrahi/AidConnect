# Aid Service

## Overview

The **Aid Service** is an internal appointment-booking assistant microservice.
It consumes Kafka chat events directed at the `aid` assistant, manages a
multi-turn conversational booking flow using an LLM (OpenAI via Spring AI),
and persists confirmed appointments in its own MySQL database.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8084` |

## Key Responsibilities

- Consume Kafka topic `chat-messages` (group `aid-service`) for `aid`-directed events
- Drive a stateful appointment booking conversation (CHATTING → CONFIRMING → booked)
- Query doctor availability from the aid MySQL database
- Book confirmed appointments in `appointment_bookings`
- Post replies to chat-service via `/api/chats/messages/internal`
- Cache doctor/slot data in Redis to reduce DB load

## Package Structure

```
com.chatassist.aid
├── AidServiceApplication.java              # Spring Boot entry point
├── config/
│   ├── OpenApiConfig.java                  # Swagger/OpenAPI customisation
│   └── ...                                 # Kafka, Redis, AI, JPA configs
├── entity/
│   ├── Doctor.java                         # clinic_doctors entity
│   ├── DoctorAvailability.java             # doctor_availability entity
│   ├── AppointmentBooking.java             # appointment_bookings entity
│   └── AidConversationState.java           # aid_conversation_state entity
├── repository/                             # Spring Data JPA repositories
└── service/
    ├── AidMessageConsumer.java             # Kafka listener
    ├── AppointmentAssistantService.java    # Core conversation state machine + LLM
    ├── DoctorCacheService.java             # Redis-cached doctor/slot queries
    ├── AidReplyClient.java                 # HTTP client → chat-service internal
    └── ChatHistoryClient.java              # Conversation history cache
```

## Conversation State Machine

```
CHATTING   <──────────────────────────────────────────
    |                                                  |
    | user asks about booking                         |
    v                                                  |
LLM proposes slot → append [PROPOSE:doctorId:slotId]  |
    |                                                  |
    v                                                  |
CONFIRMING                                            |
    |                                                  |
    +── "yes" → INSERT appointment_bookings ──────────┘
    |
    +── "no"  → reset to CHATTING ───────────────────►
```

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java 21 |
| AI | Spring AI (spring-ai-starter-model-openai) |
| Messaging | Apache Kafka (spring-kafka) |
| Persistence | Spring Data JPA + MySQL 8 |
| Cache | Redis + Spring Cache |
| Service Discovery | Netflix Eureka Client |
| Testing | JUnit 5, Mockito, TestContainers |

## Database

Owns the **aid-service MySQL database** (port `3309`):

- `clinic_doctors` — doctor directory
- `doctor_availability` — appointment slots
- `appointment_bookings` — confirmed bookings
- `aid_conversation_state` — per-user multi-turn conversation state

## Local Development

```bash
mvn clean package -pl aid-service -am -DskipTests
java -jar aid-service/target/aid-service-1.0.0.jar
```
