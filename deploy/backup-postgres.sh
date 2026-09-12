#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${REPOSITORY_DIR}/compose.production.yml"
ENV_FILE="${ENV_FILE:-${REPOSITORY_DIR}/.env.production}"
BACKUP_DIR="${BACKUP_DIR:-${REPOSITORY_DIR}/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FINAL_FILE="${BACKUP_DIR}/bhgroup_postgres_${TIMESTAMP}.dump"
PARTIAL_FILE="${FINAL_FILE}.part"

if [[ ! "${RETENTION_DAYS}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: BACKUP_RETENTION_DAYS must be a non-negative integer." >&2
  exit 2
fi

if [[ ! -r "${ENV_FILE}" ]]; then
  echo "ERROR: production environment file is not readable: ${ENV_FILE}" >&2
  exit 2
fi

mkdir -p -- "${BACKUP_DIR}"
chmod 700 -- "${BACKUP_DIR}"
trap 'rm -f -- "${PARTIAL_FILE}"' EXIT

echo "Creating PostgreSQL backup at ${FINAL_FILE}"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgres \
  sh -c 'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --compress=6 --no-owner --no-acl' \
  > "${PARTIAL_FILE}"

if [[ ! -s "${PARTIAL_FILE}" ]]; then
  echo "ERROR: pg_dump produced an empty backup." >&2
  exit 1
fi

# Verify that pg_restore can read the archive before publishing it as a backup.
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgres \
  pg_restore --list < "${PARTIAL_FILE}" > /dev/null

chmod 600 -- "${PARTIAL_FILE}"
mv -- "${PARTIAL_FILE}" "${FINAL_FILE}"
trap - EXIT

find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'bhgroup_postgres_*.dump' \
  -mtime "+${RETENTION_DAYS}" -delete

echo "Backup completed successfully. Retention: ${RETENTION_DAYS} day(s)."
