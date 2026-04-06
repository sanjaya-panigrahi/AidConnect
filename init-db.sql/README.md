# Database Initialisation Scripts

This directory contains the first-boot SQL baselines for the services that own MySQL data.

## Structure

```text
init-db.sql/
├── user-service/
│   ├── 00-grants.sql
│   └── 01-schema.sql
├── chat-service/
│   └── 01-schema.sql
├── aid-service/
│   └── 01-schema.sql
└── README.md
```

## Ownership

| Service | Database | Main tables |
|---|---|---|
| `user-service` | `user_service_db` | `users`, `user_credentials`, `user_activity_log`, `auth_audit_log` |
| `chat-service` | `chat_service_db` | `chat_messages` |
| `aid-service` | `aid_service_db` | `clinic_doctors`, `doctor_availability`, `appointment_bookings`, `aid_conversation_state` |

## How Docker Compose uses these files

Each MySQL container mounts only its own service folder into `/docker-entrypoint-initdb.d`.

The scripts run only on first boot of a fresh volume.

Compose defaults currently expose:

| Container | Host port |
|---|---:|
| `mysql-user` | 3307 |
| `mysql-chat` | 3308 |
| `mysql-aid` | 3309 |

Default DB username in `docker-compose.yml` is `chat_user` unless overridden with environment variables.

## Local connection examples

Use the credentials from your compose environment.

```bash
mysql -h 127.0.0.1 -P 3307 -u chat_user -p user_service_db
mysql -h 127.0.0.1 -P 3308 -u chat_user -p chat_service_db
mysql -h 127.0.0.1 -P 3309 -u chat_user -p aid_service_db
```

## Resetting a service database

If you want the init scripts to run again, remove the corresponding volume and recreate the container.

Example for `mysql-aid`:

```bash
docker compose stop mysql-aid
docker compose rm -f mysql-aid
docker volume ls | grep mysql_aid_data
# remove the matching volume name from the command above
docker volume rm <matching_mysql_aid_data_volume>
docker compose up -d mysql-aid
```

## Important note

The repo currently uses both:

- init SQL for fresh environment bootstrapping
- JPA `ddl-auto: update` for runtime schema evolution

That works for local/dev, but production-grade migrations should eventually move to Flyway or Liquibase.
