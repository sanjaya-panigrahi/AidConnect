# user-service DB bootstrap

Use the files in this folder for a fresh `user-service` database bootstrap:

- `00-grants.sql`
- `01-schema.sql`

`01-schema.sql` currently defines the active auth-domain split:

- `users`
- `user_credentials`
- `user_activity_log`
- `auth_audit_log`

For a fresh Docker Compose environment, MySQL runs these scripts automatically on first volume creation.
