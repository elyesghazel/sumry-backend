#!/bin/sh
# Dumps the Sumry database to a file. Run from anywhere; the dump lands in ./backups next
# to this script unless a directory is given:  ./backup.sh [target-dir]
# Restore:  docker compose exec -T postgres pg_restore -U sumry -d sumry --clean --if-exists < file.dump
set -eu

cd "$(dirname "$0")"
target=${1:-backups}
mkdir -p "$target"
file="$target/sumry-$(date +%Y%m%d-%H%M%S).dump"

# Custom format: compressed, and pg_restore can pick single tables out of it.
docker compose exec -T postgres pg_dump -U sumry -d sumry --format=custom > "$file.partial"
mv "$file.partial" "$file"
chmod 600 "$file"
echo "$file"
