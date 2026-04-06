# Bot Service

## Overview

The **Bot Service** is an internal AI assistant microservice that consumes chat events from Kafka
and produces AI-generated replies back to users via the chat-service internal endpoint.
It uses Spring AI (OpenAI) to generate conversational responses.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8083` |

## Key Responsibilities

- Listen to Kafka topic `chat-messages` (group `bot-service`)
- Filter events directed at `bot` username or containing `@bot` mentions
- Build conversation history context from chat-service
- Delegate to Spring AI (OpenAI) for response generation
- Post the reply back to chat-service via `POST /api/chats/messages/internal`
- Maintain an in-memory cache of recent conversation history to reduce HTTP calls

## Package Structure

```
com.chatassist.bot
├── BotServiceApplication.java          # Spring Boot entry point
├── config/
│   ├── OpenApiConfig.java              # Swagger/OpenAPI customisation
│   └── ...                             # Redis, Kafka, AI configs
└── service/
    ├── BotMessageConsumer.java         # Kafka listener + reply orchestration
    ├── AiAssistantService.java         # Spring AI ChatClient wrapper
    ├── BotReplyClient.java             # HTTP client to chat-service internal endpoint
    ├── BotDirectoryClient.java         # HTTP client to user-service (bot identity)
    └── ChatHistoryClient.java          # In-memory conversation history cache
```

## Message Filtering Logic

| Condition | Action |
|---|---|
| `generatedByBot = true` | Skip — prevents reply loops |
| `receiverUsername = "bot"` | Process as direct bot chat |
| Content contains `@bot` | Process as mention in user-to-user chat |
| Otherwise | Skip |

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java 21 |
| AI | Spring AI (spring-ai-starter-model-openai) |
| Messaging | Apache Kafka (spring-kafka) |
| Cache | Redis + Spring Cache |
| HTTP Client | Spring RestClient / RestTemplate |
| Service Discovery | Netflix Eureka Client |
| API Documentation | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, TestContainers (Kafka) |

## Configuration

The service requires an OpenAI API key:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

If the key is absent, the service operates in fallback mode returning a static message.

## Local Development

```bash
mvn clean package -pl bot-service -am -DskipTests
java -jar bot-service/target/bot-service-1.0.0.jar
```
