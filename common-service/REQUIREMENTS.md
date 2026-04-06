# Common Service — Requirements Document

---

## 1. Functional Requirements

### FR-CM-01 JWT Token Generation
- `JwtUtil.generateToken(userId, username)` shall produce a signed JWT with a configurable expiry.
- The token shall embed `userId` (numeric) and `username` (string) claims.

### FR-CM-02 JWT Token Validation
- `JwtUtil.extractPrincipal(token)` shall return an `Optional<JwtPrincipal>` — empty on invalid/expired tokens.
- `JwtUtil.extractUsername(token)` and `JwtUtil.extractUserId(token)` shall return individual claims.
- `JwtUtil.extractTokenFromAuthorizationHeader(header)` shall parse the Bearer header.

### FR-CM-03 Shared DTOs
- All inter-service data contracts (request bodies, response bodies, Kafka event schemas) shall be defined once in this module and shared.

### FR-CM-04 Shared Enums
- `MessageStatus` (`SENT`, `DELIVERED`, `SEEN`) and `MessageType` (`USER`, `BOT`) shall be canonical in this module.

### FR-CM-05 Assistant Profiles
- `AssistantProfile` enum shall define the canonical identity (numeric id + username) of the `bot` and `aid` assistants used by bot-service and aid-service when constructing replies.

### FR-CM-06 Session Key Constants
- `AuthSessionKeyUtil` shall define the Spring Session attribute keys used consistently across user-service and gateway-service.

---

## 2. Non-Functional Requirements

### NFR-CM-01 Compatibility
- The module shall be compiled as a plain JAR with no Spring Boot fat-jar packaging.
- It shall be usable as a dependency in all Spring Boot 3.x modules.

### NFR-CM-02 Maintainability
- Adding a new DTO or enum here propagates to all consumers without code duplication.
- Breaking changes to DTOs must be coordinated across all consuming services.

---

## 3. High-Level Architecture

```
common-service (JAR library)
        ^
        |  dependency
   +----+----+----+----+------+
   |         |    |    |      |
user-svc  chat-svc bot  aid  gateway
```

---

## 4. High-Level Design

| Package | Contents |
|---|---|
| `com.chatassist.common.dto` | All request/response/event DTOs (Java Records) |
| `com.chatassist.common.model` | Enums and value objects |
| `com.chatassist.common.security` | JWT utility and session key constants |
| `com.chatassist.common.config` | Shared Spring configuration (if any) |

---

## 5. Low-Level Design

### JWT Generation
```
JwtUtil.generateToken(userId, username)
  Jwts.builder()
    .subject(username)
    .claim("userId", userId)
    .issuedAt(now)
    .expiration(now + expiryMs)
    .signWith(secretKey, HS256)
    .compact()
```

### JWT Validation
```
JwtUtil.extractPrincipal(token)
  try
    claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
    return Optional.of(new JwtPrincipal(claims.userId, claims.subject))
  catch (JwtException) -> Optional.empty()
```

---

## 6. Technology Mapping

| Concern | Technology |
|---|---|
| Language | Java 21 |
| JWT | JJWT (io.jsonwebtoken) |
| Build | Maven 3 (jar packaging) |
| DTOs | Java Records |

---

## 7. Sequence Diagrams

This module is a library — no runtime sequences apply directly.
See individual service REQUIREMENTS.md for JWT usage sequences.

---

## 8. API Design

No HTTP API. This is a shared library JAR.

---

## 9. Database Diagram

No database. Shared DTOs and utilities only.

---

## 10. UI Design

No UI. Internal library only.
