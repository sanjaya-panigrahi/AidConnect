# Gateway Service

## Overview

The **Gateway Service** is the single public entry point for the Chat Assist application.
It handles all incoming HTTP and WebSocket traffic, performs JWT / session-based authentication,
injects trusted identity headers, and routes requests to the appropriate downstream microservices.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8080` |

## Key Responsibilities

- Route HTTP requests to user-service (`/api/users/**`) and chat-service (`/api/chats/**`)
- Proxy WebSocket connections to chat-service (`/ws-chat/**`)
- Validate JWT Bearer tokens (rejects immediately on invalid/expired)
- Resolve Redis-backed Spring Session for browser clients using SESSION cookie
- Inject `X-User-Id` and `X-Username` trusted headers downstream
- Block unauthenticated access to protected routes with `HTTP 401`
- Serve the React UI via a catch-all route to `chat-assist-ui`

## Package Structure

```
com.chatassist.gateway
├── GatewayServiceApplication.java       # Spring Boot entry point
├── config/
│   └── WebSessionConfig.java            # Redis session config
└── filter/
    ├── AuthenticationFilterConfig.java  # Global authentication filter bean
    ├── GatewaySessionService.java       # Session resolution interface
    └── RedisGatewaySessionService.java  # Redis-backed session implementation
```

## Public Routes

| Path | Match type |
|---|---|
| `/` | Exact |
| `/index.html` | Exact |
| `/ws-chat/info` | Exact |
| `/api/users/login` | Exact |
| `/api/users/register` | Exact |
| `/api/users/assistants` | Exact |
| `/actuator/health` | Exact |
| `/default-user.svg` | Exact |
| `/favicon.ico` | Exact |
| `/v3/api-docs` | Prefix |
| `/swagger-ui` | Prefix |
| `/static/` | Prefix |
| `/assets/` | Prefix |
| `/.well-known/` | Prefix |

## Route Configuration

| Route | Upstream | Notes |
|---|---|---|
| `/api/users/**` | `lb://user-service` | Eureka load-balanced |
| `/api/chats/**` | `lb://chat-service` | Retry on 502/503/504 |
| `/ws-chat/**` | `lb:ws://chat-service` | WebSocket proxy with retry |
| `/**` | `http://chat-assist-ui:80` | Catch-all (order 9999) |

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Cloud Gateway |
| Language | Java 21 |
| Session Store | Redis (Spring Session) |
| Service Discovery | Netflix Eureka Client |
| Load Balancing | Spring Cloud LoadBalancer |
| Security | Custom JWT validation (JwtUtil from common-service) |

## Local Development

```bash
mvn clean package -pl gateway-service -am -DskipTests
java -jar gateway-service/target/gateway-service-1.0.0.jar
```
