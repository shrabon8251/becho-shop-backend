#!/usr/bin/env bash
# Nightly MySQL backup -> gzip -> Cloudflare R2 (off-server, so a blocked/lost
# VPS never means lost data). Run from cron:
#     0 2 * * * /opt/becho/deploy/backup.sh >> /var/log/becho-backup.log 2>&1
#
# One-time setup: configure awscli for R2 (uses the same credentials as .env):
#     aws configure set aws_access_key_id "$R2_ACCESS_KEY_ID"
#     aws configure set aws_secret_access_key "$R2_SECRET_ACCESS_KEY"
#     aws configure set default.region auto
set -euo pipefail
cd "$(dirname "$0")"
set -a; source .env; set +a

: "${DB_NAME:?}" "${DB_ROOT_PASSWORD:?}" "${R2_ACCOUNT_ID:?}" "${R2_BUCKET:?}"

STAMP=$(date +%F_%H%M)
FILE="/tmp/becho_db_${STAMP}.sql.gz"
ENDPOINT="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"

echo "[$(date)] dumping database..."
docker compose exec -T mysql mysqldump -u root -p"$DB_ROOT_PASSWORD" "$DB_NAME" | gzip > "$FILE"

echo "[$(date)] uploading to R2..."
aws --endpoint-url "$ENDPOINT" s3 cp "$FILE" "s3://${R2_BUCKET}/backups/db/becho_db_${STAMP}.sql.gz"
rm -f "$FILE"

echo "[$(date)] pruning backups older than 14 days..."
aws --endpoint-url "$ENDPOINT" s3 ls "s3://${R2_BUCKET}/backups/db/" | while read -r _ date time name; do
  filedate=$(date -d "$date" +%s)
  cutoff=$(date -d "14 days ago" +%s)
  if [ "$filedate" -lt "$cutoff" ]; then
    aws --endpoint-url "$ENDPOINT" s3 rm "s3://${R2_BUCKET}/backups/db/${name}"
    echo "deleted old backup: $name"
  fi
done

echo "[$(date)] backup OK"
