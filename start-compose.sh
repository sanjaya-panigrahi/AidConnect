#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="$ROOT_DIR/.secrets/runtime"

require_secret() {
  local name="$1"
  local path="$SECRETS_DIR/$name"

  if [[ ! -f "$path" ]]; then
    echo "Missing secret file: $path" >&2
    exit 1
  fi

  if [[ ! -s "$path" ]]; then
    echo "Secret file is empty: $path" >&2
    exit 1
  fi
}

read_secret() {
  local name="$1"
  local path="$SECRETS_DIR/$name"
  tr -d '\r' < "$path"
}

# Required for docker-compose variable interpolation.
require_secret "mysql_root_password"
require_secret "user_db_password"
require_secret "chat_db_password"
require_secret "aid_db_password"

export MYSQL_ROOT_PASSWORD="$(read_secret mysql_root_password)"
export USER_DB_PASSWORD="$(read_secret user_db_password)"
export CHAT_DB_PASSWORD="$(read_secret chat_db_password)"
export AID_DB_PASSWORD="$(read_secret aid_db_password)"

# Optional key for live AI responses.
if [[ -s "$SECRETS_DIR/openai_api_key" ]]; then
  export OPENAI_API_KEY="$(read_secret openai_api_key)"
fi

cd "$ROOT_DIR"

# Default behavior mirrors the common start command.
if [[ "$#" -eq 0 ]]; then
  set -- up --build -d
fi

exec docker compose "$@"

