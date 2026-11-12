#!/usr/bin/env sh
# Dedicated, disposable test storage. Never touches Compose's postgres_data volume.
set -eu
name=hotel-stage0-verification
if docker container inspect "$name" >/dev/null 2>&1; then
  label=$(docker inspect --format '{{ index .Config.Labels "hotel.stage0" }}' "$name")
  if [ "$label" != verification ]; then
    printf '%s\n' "Refusing to reuse a container without the Stage 0 verification label." >&2
    exit 1
  fi
  docker start "$name" >/dev/null
else
  docker run -d --name "$name" --label hotel.stage0=verification \
    --tmpfs /var/lib/postgresql/data \
    -e POSTGRES_USER=stage0 -e POSTGRES_PASSWORD=stage0-test-only \
    -e POSTGRES_DB=stage0_test -p 127.0.0.1:55433:5432 postgres:16-alpine >/dev/null
fi
attempt=0
until docker exec "$name" pg_isready -U stage0 -d stage0_test >/dev/null; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || exit 1; sleep 1
done
for database in stage0_demo; do
  if [ "$(docker exec "$name" psql -U stage0 -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$database'")" != "1" ]; then
    docker exec "$name" createdb -U stage0 "$database"
  fi
done
printf '%s\n' 'Stage 0 PostgreSQL ready on 127.0.0.1:55433 (stage0_test, stage0_demo).'
