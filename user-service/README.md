# User Service

## Overview

The **User Service** is a Spring Boot microservice responsible for user identity, authentication, presence management, and activity tracking in the Chat Assist application. It is the authoritative source for all user-related data — registration, login/logout, online/offline status, and daily activity summaries.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8081` |

## Key Responsibilities

- User registration with BCrypt password hashing
- JWT-based and Redis-backed session authentication
- Online / offline presence tracking
- User directory and assistant listing
- Daily login/logout activity tracking
- Auth audit log maintenance

## Package Structure

```
com.chatassist.user
├── UserServiceApplication.java       # Spring Boot entry point
├── config/
│   ├── OpenApiConfig.java            # Swagger/OpenAPI customisation
│   └── ...                           # Security, session configs
├── controller/
│   ├── UserController.java           # REST endpoints
│   └── UserControllerAdvice.java     # Global exception handler
├── entity/
│   ├── AppUser.java                  # User profile entity
│   ├── UserCredential.java           # Password hash entity
│   ├── UserActivityLog.java          # LOGIN/LOGOUT event log
│   ├── AuthAuditLog.java             # Security audit log
│   ├── ActivityEventType.java        # LOGIN | LOGOUT enum
│   └── AuthAuditEventType.java       # Audit event type enum
├── repository/                       # Spring Data JPA repositories
└── service/
    ├── UserService.java              # Core user business logic
    └── AuthSessionService.java       # Session lifecycle management
```

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/users/register` | Public | Register a new user |
| `POST` | `/api/users/login` | Public | Authenticate and start session |
| `GET` | `/api/users/session` | Protected | Restore current server-side session |
| `GET` | `/api/users` | Protected | List all users (excluding self) |
| `GET` | `/api/users/assistants` | Public | List assistant (bot/aid) users |
| `GET` | `/api/users/by-username/{username}` | Protected | Get profile by username |
| `PUT` | `/api/users/{username}/online` | Protected (self) | Mark user online |
| `PUT` | `/api/users/{username}/offline` | Protected (self) | Mark user offline |
| `POST` | `/api/users/logout` | Protected | Logout current user |
| `POST` | `/api/users/{username}/logout` | Protected (self) | Logout (legacy path) |
| `GET` | `/api/users/{username}/activity/today` | Protected (self) | Today's login/logout summary |
| `GET` | `/api/users/activity/today` | Protected | Activity summary for all users |

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java 21 |
| Persistence | Spring Data JPA + MySQL 8 |
| Session Store | Redis (Spring Session) |
| Security | BCrypt + JWT (`common-service`) |
| Service Discovery | Netflix Eureka Client |
| API Documentation | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, TestContainers |

## Database

Owns the **user-service MySQL database** (port `3307`):

- `users` — user profile records
- `user_credentials` — BCrypt password hashes
- `user_activity_log` — per-user LOGIN/LOGOUT events
- `auth_audit_log` — security-focused authentication audit trail

## Local Development

```bash
# Build
mvn clean package -pl user-service -am -DskipTests

# Run (requires MySQL + Redis)
java -jar user-service/target/user-service-1.0.0.jar
```

## Swagger UI

Available at `http://localhost:8081/swagger-ui.html` when running locally.

