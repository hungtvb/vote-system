#!/usr/bin/env bash
set -euo pipefail

: "${IMAGE_NAME:?IMAGE_NAME is required}"
: "${NETWORK_NAME:?NETWORK_NAME is required}"
: "${POSTGRES_NAME:?POSTGRES_NAME is required}"
: "${REDIS_NAME:?REDIS_NAME is required}"
: "${APP_NAME:?APP_NAME is required}"
: "${BASE_URL:?BASE_URL is required}"
: "${ARTIFACT_DIR:?ARTIFACT_DIR is required}"
: "${CI_JWT_SECRET:?CI_JWT_SECRET is required}"
: "${VOTER_TOKEN:?VOTER_TOKEN is required}"
: "${BALLOT_ID:?BALLOT_ID is required}"

APP_LOG="$ARTIFACT_DIR/app.log"
ADMIN_REGISTER_HEADERS=$(mktemp)
ADMIN_REFRESH_HEADERS=$(mktemp)
ADMIN_RECOVERY_HEADERS=$(mktemp)
CORS_HEADERS=$(mktemp)

cleanup_files() {
  rm -f "$ADMIN_REGISTER_HEADERS" "$ADMIN_REFRESH_HEADERS" "$ADMIN_RECOVERY_HEADERS" "$CORS_HEADERS"
}
trap cleanup_files EXIT

json_value() {
  local key=$1
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$key"
}

wait_for() {
  local description=$1
  shift
  for attempt in $(seq 1 80); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "Timed out waiting for ${description}." >&2
  return 1
}

extract_refresh_cookie() {
  local headers=$1
  awk 'BEGIN { IGNORECASE=1 } /^Set-Cookie:/ { gsub("\\r", ""); if ($2 ~ /^__Secure-vote_refresh=/) { split($2, parts, ";"); print parts[1] } }' "$headers"
}

capture_app_logs() {
  docker logs "$APP_NAME" >> "$APP_LOG" 2>&1 || true
}

start_app() {
  docker run -d --name "$APP_NAME" --network "$NETWORK_NAME" -p 10000:10000 \
    -e PORT=10000 \
    -e SPRING_PROFILES_ACTIVE=production \
    -e DB_URL="jdbc:postgresql://${POSTGRES_NAME}:5432/vote_system" \
    -e DB_USERNAME=vote \
    -e DB_PASSWORD=vote \
    -e REDIS_URL="redis://${REDIS_NAME}:6379" \
    -e JWT_SECRET="$CI_JWT_SECRET" \
    -e CORS_ALLOWED_ORIGINS=https://app.ballotbox.io.vn \
    -e REFRESH_COOKIE_NAME=__Secure-vote_refresh \
    -e OAUTH_SESSION_COOKIE_NAME=__Secure-vote_oauth \
    -e RATE_LIMIT_ENABLED=true \
    -e RATE_LIMIT_FAIL_OPEN=true \
    -e VOTE_STREAM_HEARTBEAT_MS=250 \
    "$@" \
    "$IMAGE_NAME" >/dev/null
  wait_for application curl --fail --silent "$BASE_URL/actuator/health"
}

restart_app() {
  capture_app_logs
  docker rm -f "$APP_NAME" >/dev/null 2>&1 || true
  start_app "$@"
}

status_code() {
  curl --silent --output "$1" --write-out '%{http_code}' "${@:2}"
}

ADMIN_EMAIL="runtime-admin-$(date +%s)-${RANDOM}@example.com"
ADMIN_REGISTER=$(curl --fail --silent --show-error -D "$ADMIN_REGISTER_HEADERS" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"runtime-password\",\"displayName\":\"Runtime Administrator\"}" \
  "$BASE_URL/api/v1/auth/register")
ADMIN_USER_TOKEN=$(printf '%s' "$ADMIN_REGISTER" | json_value accessToken)
ADMIN_REFRESH_COOKIE=$(extract_refresh_cookie "$ADMIN_REGISTER_HEADERS")
[ -n "$ADMIN_REFRESH_COOKIE" ]

USER_ADMIN_READ_STATUS=$(status_code "$ARTIFACT_DIR/user-admin-read-forbidden.json" \
  -H "Authorization: Bearer ${ADMIN_USER_TOKEN}" \
  "$BASE_URL/api/v1/admin/system/status")
[ "$USER_ADMIN_READ_STATUS" = "403" ]

USER_ADMIN_WRITE_STATUS=$(status_code "$ARTIFACT_DIR/user-admin-write-forbidden.json" \
  -X PUT \
  -H "Authorization: Bearer ${ADMIN_USER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"mode":"READ_ONLY","reason":"Unauthorized runtime probe"}' \
  "$BASE_URL/api/v1/admin/system/status")
[ "$USER_ADMIN_WRITE_STATUS" = "403" ]

# Promote the already-created account through the controlled one-deployment bootstrap.
# The existing refresh session must survive the role change and restore the current ADMIN profile.
restart_app \
  -e ADMIN_BOOTSTRAP_ENABLED=true \
  -e ADMIN_BOOTSTRAP_EMAIL="$ADMIN_EMAIL"

ADMIN_REFRESHED=$(curl --fail --silent --show-error -D "$ADMIN_REFRESH_HEADERS" \
  -H "Cookie: ${ADMIN_REFRESH_COOKIE}" \
  -X POST "$BASE_URL/api/v1/auth/refresh")
ADMIN_TOKEN=$(printf '%s' "$ADMIN_REFRESHED" | json_value accessToken)
ADMIN_REFRESH_COOKIE=$(extract_refresh_cookie "$ADMIN_REFRESH_HEADERS")
[ -n "$ADMIN_REFRESH_COOKIE" ]
printf '%s' "$ADMIN_REFRESHED" | grep -q '"role":"ADMIN"'

# Production-profile READ_ONLY matrix: safe reads stay available while business writes and registration fail closed.
curl --fail --silent --show-error \
  -X PUT \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'X-Request-ID: runtime-read-only-enable' \
  -H 'Content-Type: application/json' \
  -d '{"mode":"READ_ONLY","messageVi":"Hệ thống đang ở chế độ chỉ đọc để kiểm thử.","messageEn":"The system is read-only for controlled verification.","reason":"TON-177 read-only production-profile matrix"}' \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/read-only-enabled.json"
grep -q '"mode":"READ_ONLY"' "$ARTIFACT_DIR/read-only-enabled.json"

curl --fail --silent --show-error "$BASE_URL/api/v1/posts?feed=LATEST&page=0&size=8" \
  > "$ARTIFACT_DIR/read-only-feed.json"
grep -q "$BALLOT_ID" "$ARTIFACT_DIR/read-only-feed.json"

READ_ONLY_VOTE_STATUS=$(status_code "$ARTIFACT_DIR/read-only-vote-rejected.json" \
  -X PUT \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"type":"DOWN"}' \
  "$BASE_URL/api/v1/posts/${BALLOT_ID}/vote")
[ "$READ_ONLY_VOTE_STATUS" = "503" ]
grep -q '"code":"SYSTEM_READ_ONLY"' "$ARTIFACT_DIR/read-only-vote-rejected.json"

READ_ONLY_CREATE_STATUS=$(status_code "$ARTIFACT_DIR/read-only-create-rejected.json" \
  -X POST \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Rejected read-only ballot","content":"This write must not reach the controller.","category":"QA"}' \
  "$BASE_URL/api/v1/posts")
[ "$READ_ONLY_CREATE_STATUS" = "503" ]
grep -q '"code":"SYSTEM_READ_ONLY"' "$ARTIFACT_DIR/read-only-create-rejected.json"

READ_ONLY_REGISTER_STATUS=$(status_code "$ARTIFACT_DIR/read-only-register-rejected.json" \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"email":"read-only-probe@example.invalid","password":"runtime-password","displayName":"Rejected Probe"}' \
  "$BASE_URL/api/v1/auth/register")
[ "$READ_ONLY_REGISTER_STATUS" = "503" ]
grep -q '"code":"SYSTEM_READ_ONLY"' "$ARTIFACT_DIR/read-only-register-rejected.json"

curl --fail --silent --show-error \
  -X PUT \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'X-Request-ID: runtime-read-only-disable' \
  -H 'Content-Type: application/json' \
  -d '{"mode":"NORMAL","reason":"TON-177 read-only matrix completed"}' \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/read-only-restored-normal.json"
grep -q '"mode":"NORMAL"' "$ARTIFACT_DIR/read-only-restored-normal.json"

ESTIMATED_END=$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
print((datetime.now(timezone.utc) + timedelta(hours=1)).isoformat().replace('+00:00', 'Z'))
PY
)
ENABLE_PAYLOAD=$(python3 - "$ESTIMATED_END" <<'PY'
import json, sys
print(json.dumps({
    "mode": "MAINTENANCE",
    "messageVi": "Hệ thống đang bảo trì theo kiểm thử phục hồi có kiểm soát.",
    "messageEn": "The system is under a controlled recovery verification.",
    "estimatedEndAt": sys.argv[1],
    "reason": "TON-177 production-profile recovery kill-test"
}))
PY
)

curl --fail --silent --show-error \
  -X PUT \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'X-Request-ID: runtime-maintenance-enable' \
  -H 'Content-Type: application/json' \
  -d "$ENABLE_PAYLOAD" \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/maintenance-enabled.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/maintenance-enabled.json"

curl --fail --silent --show-error "$BASE_URL/api/v1/system/status" > "$ARTIFACT_DIR/maintenance-public-status.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/maintenance-public-status.json"

MAINTENANCE_FEED_STATUS=$(status_code "$ARTIFACT_DIR/maintenance-feed-rejected.json" \
  -D "$ARTIFACT_DIR/maintenance-feed-headers.txt" \
  -H 'X-Request-ID: runtime-maintenance-feed-rejected' \
  "$BASE_URL/api/v1/posts")
[ "$MAINTENANCE_FEED_STATUS" = "503" ]
grep -q '"code":"SYSTEM_MAINTENANCE"' "$ARTIFACT_DIR/maintenance-feed-rejected.json"
grep -qi '^Retry-After:' "$ARTIFACT_DIR/maintenance-feed-headers.txt"

CORS_STATUS=$(curl --silent --output /dev/null --dump-header "$CORS_HEADERS" --write-out '%{http_code}' \
  -X OPTIONS \
  -H 'Origin: https://app.ballotbox.io.vn' \
  -H 'Access-Control-Request-Method: POST' \
  "$BASE_URL/api/v1/posts")
[ "$CORS_STATUS" = "200" ] || [ "$CORS_STATUS" = "204" ]
grep -qi '^Access-Control-Allow-Origin: https://app.ballotbox.io.vn' "$CORS_HEADERS"

MAINTENANCE_USER_READ=$(status_code "$ARTIFACT_DIR/maintenance-user-admin-read-forbidden.json" \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  "$BASE_URL/api/v1/admin/system/status")
[ "$MAINTENANCE_USER_READ" = "403" ]
MAINTENANCE_USER_WRITE=$(status_code "$ARTIFACT_DIR/maintenance-user-admin-write-forbidden.json" \
  -X PUT \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"mode":"NORMAL","reason":"Unauthorized recovery attempt"}' \
  "$BASE_URL/api/v1/admin/system/status")
[ "$MAINTENANCE_USER_WRITE" = "403" ]

# Backend restart must reload MAINTENANCE from PostgreSQL, with bootstrap disabled again.
restart_app
curl --fail --silent --show-error "$BASE_URL/api/v1/system/status" > "$ARTIFACT_DIR/maintenance-after-restart.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/maintenance-after-restart.json"
RESTARTED_FEED_STATUS=$(status_code "$ARTIFACT_DIR/maintenance-feed-after-restart.json" \
  -H 'X-Request-ID: runtime-maintenance-after-restart' \
  "$BASE_URL/api/v1/posts")
[ "$RESTARTED_FEED_STATUS" = "503" ]
grep -q '"code":"SYSTEM_MAINTENANCE"' "$ARTIFACT_DIR/maintenance-feed-after-restart.json"

# A hard refresh after backend restart must restore ADMIN while Redis protection is healthy.
RECOVERED_SESSION=$(curl --fail --silent --show-error -D "$ADMIN_RECOVERY_HEADERS" \
  -H "Cookie: ${ADMIN_REFRESH_COOKIE}" \
  -X POST "$BASE_URL/api/v1/auth/refresh")
RECOVERED_ADMIN_TOKEN=$(printf '%s' "$RECOVERED_SESSION" | json_value accessToken)
ADMIN_REFRESH_COOKIE=$(extract_refresh_cookie "$ADMIN_RECOVERY_HEADERS")
[ -n "$ADMIN_REFRESH_COOKIE" ]
printf '%s' "$RECOVERED_SESSION" | grep -q '"role":"ADMIN"'

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${RECOVERED_ADMIN_TOKEN}" \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/admin-status-after-hard-refresh.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/admin-status-after-hard-refresh.json"

# Redis is derived infrastructure. Losing it must not change the PostgreSQL-backed mode.
docker stop "$REDIS_NAME" >/dev/null
curl --fail --silent --show-error "$BASE_URL/api/v1/system/status" > "$ARTIFACT_DIR/maintenance-without-redis.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/maintenance-without-redis.json"

# Authentication rate limiting is security-critical and correctly fails closed while Redis is unavailable.
REDIS_DOWN_REFRESH_STATUS=$(status_code "$ARTIFACT_DIR/refresh-without-redis.json" \
  -H "Cookie: ${ADMIN_REFRESH_COOKIE}" \
  -X POST "$BASE_URL/api/v1/auth/refresh")
[ "$REDIS_DOWN_REFRESH_STATUS" = "503" ]
grep -q '"code":"RATE_LIMIT_UNAVAILABLE"' "$ARTIFACT_DIR/refresh-without-redis.json"

# The already-restored administrator access token remains sufficient for the unconditional recovery endpoint.
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${RECOVERED_ADMIN_TOKEN}" \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/admin-status-without-redis.json"
grep -q '"mode":"MAINTENANCE"' "$ARTIFACT_DIR/admin-status-without-redis.json"

curl --fail --silent --show-error \
  -X PUT \
  -H "Authorization: Bearer ${RECOVERED_ADMIN_TOKEN}" \
  -H 'X-Request-ID: runtime-maintenance-disable' \
  -H 'Content-Type: application/json' \
  -d '{"mode":"NORMAL","reason":"TON-177 recovery verification completed"}' \
  "$BASE_URL/api/v1/admin/system/status" > "$ARTIFACT_DIR/maintenance-restored-normal.json"
grep -q '"mode":"NORMAL"' "$ARTIFACT_DIR/maintenance-restored-normal.json"

# Restore derived infrastructure, then prove public reads and a business write recover.
docker start "$REDIS_NAME" >/dev/null
wait_for Redis docker exec "$REDIS_NAME" redis-cli ping
curl --fail --silent --show-error "$BASE_URL/api/v1/posts?feed=LATEST&page=0&size=8" > "$ARTIFACT_DIR/feed-after-maintenance.json"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -X PUT \
  -d '{"type":"DOWN"}' \
  "$BASE_URL/api/v1/posts/${BALLOT_ID}/vote" > "$ARTIFACT_DIR/vote-after-maintenance.json"
grep -q '"downVotes":1' "$ARTIFACT_DIR/vote-after-maintenance.json"
grep -q '"verdict":"DOWN"' "$ARTIFACT_DIR/vote-after-maintenance.json"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${RECOVERED_ADMIN_TOKEN}" \
  "$BASE_URL/api/v1/admin/audit-logs?action=SYSTEM_MODE_CHANGED&targetType=SYSTEM&targetId=GLOBAL&size=10" \
  > "$ARTIFACT_DIR/maintenance-audit.json"
grep -q 'runtime-maintenance-enable' "$ARTIFACT_DIR/maintenance-audit.json"
grep -q 'runtime-maintenance-disable' "$ARTIFACT_DIR/maintenance-audit.json"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${RECOVERED_ADMIN_TOKEN}" \
  "$BASE_URL/actuator/metrics/vote.system.mode.requests?tag=code:SYSTEM_MAINTENANCE&tag=route:posts&tag=status:503" \
  > "$ARTIFACT_DIR/maintenance-rejection-metric.json"
grep -q '"name":"vote.system.mode.requests"' "$ARTIFACT_DIR/maintenance-rejection-metric.json"
grep -Eq '"value":[1-9][0-9]*([.]0)?' "$ARTIFACT_DIR/maintenance-rejection-metric.json"

capture_app_logs
grep -q 'requestId=runtime-read-only-enable' "$APP_LOG"
grep -q 'requestId=runtime-read-only-disable' "$APP_LOG"
grep -q 'requestId=runtime-maintenance-enable' "$APP_LOG"
grep -q 'requestId=runtime-maintenance-disable' "$APP_LOG"
! grep -E '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$APP_LOG"
! grep -E '__Secure-vote_refresh=|eyJ[A-Za-z0-9_-]+\.' "$APP_LOG"

# Uploaded evidence must never contain bearer tokens, refresh cookies, or the bootstrap email.
# Existing authenticated-profile evidence may contain the runtime author's email, so the broader email rule applies to logs.
! grep -R -F "$ADMIN_EMAIL" "$ARTIFACT_DIR"
! grep -R -E '__Secure-vote_refresh=|eyJ[A-Za-z0-9_-]+\.' "$ARTIFACT_DIR"

echo "Maintenance recovery smoke passed across restart and Redis loss."
