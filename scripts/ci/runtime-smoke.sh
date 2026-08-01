#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME=${IMAGE_NAME:-vote-system:ci}
NETWORK_NAME=${NETWORK_NAME:-vote-system-ci}
POSTGRES_NAME=${POSTGRES_NAME:-vote-system-postgres-ci}
REDIS_NAME=${REDIS_NAME:-vote-system-redis-ci}
APP_NAME=${APP_NAME:-vote-system-app-ci}
BASE_URL=${BASE_URL:-http://127.0.0.1:10000}
ARTIFACT_DIR=${ARTIFACT_DIR:-runtime-smoke}

mkdir -p "$ARTIFACT_DIR"
APP_LOG="$ARTIFACT_DIR/app.log"
REGISTER_HEADERS=$(mktemp)
CI_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')

cleanup() {
  rm -f "$REGISTER_HEADERS"
  docker logs "$APP_NAME" > "$APP_LOG" 2>&1 || true
  docker rm -f "$APP_NAME" "$POSTGRES_NAME" "$REDIS_NAME" >/dev/null 2>&1 || true
  docker network rm "$NETWORK_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

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

docker network create "$NETWORK_NAME" >/dev/null

docker run -d --name "$POSTGRES_NAME" --network "$NETWORK_NAME" \
  -e POSTGRES_DB=vote_system \
  -e POSTGRES_USER=vote \
  -e POSTGRES_PASSWORD=vote \
  postgres:17-alpine >/dev/null

docker run -d --name "$REDIS_NAME" --network "$NETWORK_NAME" redis:7.4-alpine >/dev/null

wait_for PostgreSQL docker exec "$POSTGRES_NAME" pg_isready -U vote -d vote_system
wait_for Redis docker exec "$REDIS_NAME" redis-cli ping

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
  -e RATE_LIMIT_ENABLED=false \
  -e VOTE_STREAM_HEARTBEAT_MS=250 \
  "$IMAGE_NAME" >/dev/null

wait_for application curl --fail --silent "$BASE_URL/actuator/health"

curl --fail --silent "$BASE_URL/actuator/health" > "$ARTIFACT_DIR/health.json"
grep -q '"status":"UP"' "$ARTIFACT_DIR/health.json"

curl --fail --silent "$BASE_URL/" > "$ARTIFACT_DIR/index.html"
grep -q 'Vote System' "$ARTIFACT_DIR/index.html"

# Production must not expose OpenAPI or Swagger UI.
API_DOCS_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/v3/api-docs")
SWAGGER_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/swagger-ui/index.html")
[ "$API_DOCS_STATUS" = "404" ]
[ "$SWAGGER_STATUS" = "404" ]

# Social login remains optional. With no provider secrets the app must expose
# an empty discovery list and reject a dead provider start instead of failing startup.
curl --fail --silent "$BASE_URL/api/v1/auth/social/providers" > "$ARTIFACT_DIR/social-providers.json"
grep -q '"providers":\[\]' "$ARTIFACT_DIR/social-providers.json"
SOCIAL_START_STATUS=$(curl --silent --output "$ARTIFACT_DIR/social-start-disabled.json" --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"intent":"authenticate"}' \
  "$BASE_URL/api/v1/auth/social/google/start")
[ "$SOCIAL_START_STATUS" = "404" ]

AUTHOR_EMAIL="runtime-author-$(date +%s)-${RANDOM}@example.com"
AUTHOR_SESSION=$(curl --fail --silent --show-error -D "$REGISTER_HEADERS" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${AUTHOR_EMAIL}\",\"password\":\"runtime-password\",\"displayName\":\"Runtime Author\"}" \
  "$BASE_URL/api/v1/auth/register")
AUTHOR_TOKEN=$(printf '%s' "$AUTHOR_SESSION" | json_value accessToken)
REFRESH_COOKIE=$(awk 'BEGIN { IGNORECASE=1 } /^Set-Cookie:/ { gsub("\\r", ""); if ($2 ~ /^__Secure-vote_refresh=/) { split($2, parts, ";"); print parts[1] } }' "$REGISTER_HEADERS")
[ -n "$REFRESH_COOKIE" ]
grep -qi '^Set-Cookie: __Secure-vote_refresh=.*; Path=/api/v1/auth; Max-Age=.*; Expires=.*; Secure; HttpOnly; SameSite=Strict' "$REGISTER_HEADERS"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${AUTHOR_TOKEN}" \
  "$BASE_URL/api/v1/users/me" > "$ARTIFACT_DIR/author-profile.json"
grep -q '"displayName":"Runtime Author"' "$ARTIFACT_DIR/author-profile.json"
grep -q '"linkedProviders":\[\]' "$ARTIFACT_DIR/author-profile.json"

BALLOT=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${AUTHOR_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Production runtime ballot","content":"Created by the production Docker smoke test.","category":"QA"}' \
  "$BASE_URL/api/v1/posts")
BALLOT_ID=$(printf '%s' "$BALLOT" | json_value id)
printf '%s' "$BALLOT" > "$ARTIFACT_DIR/created-ballot.json"

curl --fail --silent --show-error \
  "$BASE_URL/api/v1/posts?feed=LATEST&query=Production%20runtime%20ballot&page=0&size=8" \
  > "$ARTIFACT_DIR/search.json"
grep -q "$BALLOT_ID" "$ARTIFACT_DIR/search.json"

VOTER_EMAIL="runtime-voter-$(date +%s)-${RANDOM}@example.com"
VOTER_SESSION=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${VOTER_EMAIL}\",\"password\":\"runtime-password\",\"displayName\":\"Runtime Voter\"}" \
  "$BASE_URL/api/v1/auth/register")
VOTER_TOKEN=$(printf '%s' "$VOTER_SESSION" | json_value accessToken)

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${VOTER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -X PUT \
  -d '{"type":"UP"}' \
  "$BASE_URL/api/v1/posts/${BALLOT_ID}/vote" > "$ARTIFACT_DIR/vote.json"
grep -q '"upVotes":1' "$ARTIFACT_DIR/vote.json"
grep -q '"verdict":"UP"' "$ARTIFACT_DIR/vote.json"

set +e
timeout 3s curl --silent --show-error --no-buffer \
  -H 'Accept: text/event-stream' \
  "$BASE_URL/api/v1/posts/${BALLOT_ID}/events" > "$ARTIFACT_DIR/stream.txt"
STREAM_EXIT=$?
set -e
if [ "$STREAM_EXIT" -ne 0 ] && [ "$STREAM_EXIT" -ne 124 ]; then
  echo "SSE request failed with exit code ${STREAM_EXIT}." >&2
  exit "$STREAM_EXIT"
fi
grep -q 'event:vote-update' "$ARTIFACT_DIR/stream.txt"
grep -q '"upVotes":1' "$ARTIFACT_DIR/stream.txt"

curl --fail --silent --show-error \
  -H "Cookie: ${REFRESH_COOKIE}" \
  -X POST "$BASE_URL/api/v1/auth/refresh" > "$ARTIFACT_DIR/refreshed-session.json"
grep -q '"accessToken"' "$ARTIFACT_DIR/refreshed-session.json"

# Never persist raw cookies or bearer tokens in uploaded runtime-smoke artifacts.
! grep -R -E '__Secure-vote_refresh=|eyJ[A-Za-z0-9_-]+\.' "$ARTIFACT_DIR"

echo "Production runtime smoke passed for ballot ${BALLOT_ID}."
