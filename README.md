# Chat Assist App

Chat Assist App is a microservices-based real-time chat platform with two built-in assistants:
- `@bot` for general AI chat help
- `@aid` for appointment assistance

The system uses Spring Boot services for backend APIs, Kafka for async events, WebSocket/STOMP for live updates, Redis for cache, and React for UI.

---

## What This Project Includes

- User registration and login
- One-to-one real-time chat
- Delivered/seen message status
- Assistant routing using `@bot` and `@aid`
- Event-driven assistant processing with Kafka
- Service discovery and gateway routing

---

## High-Level Architecture

- Frontend: `chat-assist-ui` (React + Vite) — a separate frontend module from backend services
- API Gateway: `gateway-service`
- Service Registry: `discovery-service` (Eureka)
- Business Services:
  - `user-service`
  - `chat-service`
  - `bot-service`
  - `aid-service`
- Shared/Infra:
  - `common-service` (shared DTOs/contracts)
  - Kafka
  - Redis
  - MySQL (separate DB per service domain)

---

## Technology Stack

- Java 17
- Spring Boot 3.4
- Spring Cloud 2024 (Gateway, Eureka)
- Spring AI 1.0 (OpenAI integration for bot/aid behavior)
- Spring WebSocket + STOMP
- Apache Kafka
- Redis 7
- MySQL 8
- Maven
- React 18 + Vite
- Docker Compose

## How to run

```bash
docker compose up --build
```

Key URLs:
- UI: `http://localhost:3000`
- API Gateway: `http://localhost:8080`
- Eureka: `http://localhost:8761`
- Kafdrop: `http://localhost:9000`
