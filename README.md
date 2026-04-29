# AidConnect

A connected clinical chat platform for patients, doctors, and AI assistants in one flow.

## What is implemented today

- React/Vite UI in `aidconnect-ui`
- Spring Cloud Gateway in `gateway-service`
- Eureka discovery in `discovery-service`
- User management and authentication in `user-service`
- Chat APIs and WebSocket messaging in `chat-service`
- AI assistant consumers in `bot-service` and `aid-service`
- Redis for caching and server-side sessions
- Kafka for assistant/event fan-out
- MySQL database-per-service setup for user, chat, and aid domains

## Current runtime shape

| Module | Port | Role |
|---|---:|---|
| `aidconnect-ui` | 3000 | Browser UI |
| `gateway-service` | 8080 | Entry point, auth, routing |
| `user-service` | 8081 | Registration, login, directory, activity |
| `chat-service` | 8082 | Message APIs, WebSocket, Kafka publish |
| `bot-service` | 8083 | Kafka consumer for `@bot` replies |
| `aid-service` | 8084 | Kafka consumer for `@aid` replies and appointment flow |
| `discovery-service` | 8761 | Eureka registry |
| Kafka | 9092 | Message broker |
| Redis | 6379 | Cache and session store |
| Kafdrop | 9000 | Kafka inspection UI |
| MySQL user/chat/aid | 3307/3308/3309 | Per-service databases |

## Verified implementation notes

- Public REST controllers exist in:
  - `user-service`
  - `chat-service`
- `bot-service` and `aid-service` are currently internal assistant processors; they do **not** expose public REST controllers in the codebase.
- Assistant routing happens through `POST /api/chats/messages`:
  - direct chat to `bot` / `aid`
  - or `@bot` / `@aid` mention inside a normal conversation
- Gateway auth order is:
  1. `Authorization: Bearer <jwt>`
  2. Redis-backed `SESSION` cookie fallback

## Run with Docker Compose

Preferred (loads secrets from `secrets/runtime` automatically):

```bash
./start-compose.sh
```

Direct compose usage (only if you export vars manually in the same shell):

```bash
export MYSQL_ROOT_PASSWORD=dummy
export USER_DB_PASSWORD=dummy
export CHAT_DB_PASSWORD=dummy
export AID_DB_PASSWORD=dummy
export OPENAI_API_KEY=your_key_here

docker compose up --build
```

## Main URLs

- UI: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Eureka: `http://localhost:8761`
- Kafdrop: `http://localhost:9000`

