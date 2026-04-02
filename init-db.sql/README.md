# Database Initialisation Scripts

This directory contains per-service SQL init scripts for the
**database per service** architecture.

## Structure

```
init-db.sql/
├── user-service/
│   └── 01-schema.sql   ← users, user_activity_log tables
├── chat-service/
│   └── 01-schema.sql   ← chat_messages table
├── aid-service/
│   └── 01-schema.sql   ← clinic_doctors, doctor_availability,
│                          appointment_bookings, aid_conversation_state + seeds
└── README.md
```

## How it works

Each service has a **dedicated MySQL container** (`mysql-user`, `mysql-chat`,
`mysql-aid`) that mounts only its own subdirectory as
`/docker-entrypoint-initdb.d`. MySQL executes scripts there alphabetically on
**first boot only** (when the volume is empty).

| Service       | Container      | Port | Database          | Volume            |
|---------------|----------------|------|-------------------|-------------------|
| user-service  | db-user-service| 3307 | `user_service_db` | `mysql_user_data` |
| chat-service  | db-chat-service| 3308 | `chat_service_db` | `mysql_chat_data` |
| aid-service   | db-aid-service | 3309 | `aid_service_db`  | `mysql_aid_data`  |

## Why separate databases?

- **Strict data isolation** — no service can accidentally read another's tables
- **Independent scaling** — each DB can be tuned for its workload
- **Independent schema evolution** — migrations do not affect other services
- **Clear ownership** — each team knows exactly which data they own
- **Easier compliance** — data can be stored in different regions/policies

## Adding tables

Add SQL `CREATE TABLE IF NOT EXISTS` statements to the relevant service script.
JPA `ddl-auto: update` handles column additions at runtime; the init script
guarantees correct indexes and seeds on fresh installations.

## Local development

Connect directly to each DB from your IDE or CLI:

```bash
# user-service DB
mysql -h 127.0.0.1 -P 3307 -u user_svc_user -p user_service_db

# chat-service DB
mysql -h 127.0.0.1 -P 3308 -u chat_svc_user -p chat_service_db

# aid-service DB
mysql -h 127.0.0.1 -P 3309 -u aid_svc_user -p aid_service_db
```

## Resetting a single service DB

```bash
# Stop & remove one service's DB container and volume, then restart
docker compose stop mysql-aid
docker compose rm -f mysql-aid
docker volume rm chat-bot-app_mysql_aid_data
docker compose up -d mysql-aid
```

