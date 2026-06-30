#!/usr/bin/env bash
# =============================================================================
# VERG — Crop Registry End-to-End Test
# =============================================================================
# Tests the full pipeline: CREATE → Postgres → Elasticsearch → Redis → READ → SEARCH
#
# Prerequisites:
#   1. Docker Compose services running:  docker compose up -d
#   2. Spring Boot app running:          ./mvnw spring-boot:run
#
# Usage:
#   bash tests/test_crop_registry.sh
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
APP_BASE="http://localhost:8080"
ES_BASE="http://localhost:9200"
ES_INDEX="crop_index"

CROP_CREATE_URL="${APP_BASE}/crop/v1/create"
CROP_READ_URL="${APP_BASE}/crop/v1/read"
CROP_SEARCH_URL="${APP_BASE}/crop/v1/search"

PASS="✅"
FAIL="❌"
TOTAL=0
PASSED=0
FAILED=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
print_header() {
    echo ""
    echo "================================================================"
    echo "  $1"
    echo "================================================================"
}

print_result() {
    TOTAL=$((TOTAL + 1))
    if [ "$1" = "pass" ]; then
        PASSED=$((PASSED + 1))
        echo "  ${PASS} $2"
    else
        FAILED=$((FAILED + 1))
        echo "  ${FAIL} $2"
    fi
}

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
print_header "PRE-FLIGHT CHECKS"

PREFLIGHT_OK=true

if curl -s --connect-timeout 3 "${APP_BASE}" > /dev/null 2>&1; then
    echo "  ✓ Spring Boot App is reachable"
else
    echo "  ✗ Spring Boot App is NOT reachable at ${APP_BASE}"
    PREFLIGHT_OK=false
fi

if curl -s --connect-timeout 3 "${ES_BASE}" > /dev/null 2>&1; then
    echo "  ✓ Elasticsearch is reachable"
else
    echo "  ✗ Elasticsearch is NOT reachable at ${ES_BASE}"
    PREFLIGHT_OK=false
fi

if docker exec verg-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    echo "  ✓ Redis is reachable (via docker exec)"
else
    echo "  ✗ Redis is NOT reachable"
    PREFLIGHT_OK=false
fi

if docker exec verg-postgres pg_isready -U verg_user -d verg_db > /dev/null 2>&1; then
    echo "  ✓ PostgreSQL is reachable (via docker exec)"
else
    echo "  ✗ PostgreSQL is NOT reachable"
    PREFLIGHT_OK=false
fi

if [ "$PREFLIGHT_OK" = false ]; then
    echo ""
    echo "  Some services are unreachable. Make sure:"
    echo "    1. docker compose up -d"
    echo "    2. ./mvnw spring-boot:run"
    echo ""
    echo "  Aborting."
    exit 1
fi

echo ""
echo "  All services reachable. Running tests..."

# ---------------------------------------------------------------------------
# TEST 1: CREATE
# ---------------------------------------------------------------------------
print_header "TEST 1: CREATE — POST /crop/v1/create"

CROP_PAYLOAD='{
  "cropId": "TEST-CROP-001",
  "farmerId": "FARMER-KA-500123",
  "farmId": "KA-FARM-20345",
  "name": "Ragi (Finger Millet)",
  "description": "Organic ragi cultivated in red soil region of Tumkur district",
  "category": "Millet",
  "season": "Kharif",
  "year": 2025,
  "sownAreaHectares": 4.5,
  "sourceName": "Karnataka Agriculture Department",
  "channel": "field_survey",
  "surveyMethod": "GPS_mapped",
  "imgUrl": "https://example.com/crop-images/ragi-tumkur-2025.jpg",
  "createdFor": ["state_registry", "crop_insurance"],
  "createdOn": "2025-06-15T08:30:00.000Z",
  "updatedOn": "2025-06-15T08:30:00.000Z"
}'

CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CROP_CREATE_URL}" \
    -H "Content-Type: application/json" \
    -d "${CROP_PAYLOAD}")

HTTP_CODE=$(echo "$CREATE_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

echo "  HTTP Status: ${HTTP_CODE}"

if [ "$HTTP_CODE" = "200" ]; then
    print_result "pass" "CREATE returned HTTP 200"
else
    print_result "fail" "CREATE returned HTTP ${HTTP_CODE} (expected 200)"
    echo "  Response: ${RESPONSE_BODY}"
    echo ""
    echo "  Aborting remaining tests."
    exit 1
fi

# Extract the generated UUID (cropId in response result)
CROP_UUID=$(echo "$RESPONSE_BODY" | grep -o '"cropId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$CROP_UUID" ]; then
    print_result "pass" "Got cropId UUID: ${CROP_UUID}"
else
    print_result "fail" "Could not extract cropId from response"
    echo "  Response was: ${RESPONSE_BODY}"
    echo "  Aborting remaining tests."
    exit 1
fi

echo ""
echo "  Waiting 2s for indexing..."
sleep 2

# ---------------------------------------------------------------------------
# TEST 2: POSTGRES VERIFICATION (via docker exec)
# ---------------------------------------------------------------------------
print_header "TEST 2: POSTGRES — Verify row in 'crop' table"

PG_COUNT=$(docker exec verg-postgres \
    psql -U verg_user -d verg_db -t -A \
    -c "SELECT count(*) FROM crop WHERE crop_id = '${CROP_UUID}';" 2>/dev/null || echo "error")
PG_COUNT=$(echo "$PG_COUNT" | tr -d '[:space:]')

if [ "$PG_COUNT" = "1" ]; then
    print_result "pass" "Row found in Postgres (crop table, cropId=${CROP_UUID})"
else
    print_result "fail" "Row NOT found in Postgres (result=${PG_COUNT})"
fi

PG_STATUS=$(docker exec verg-postgres \
    psql -U verg_user -d verg_db -t -A \
    -c "SELECT status FROM crop WHERE crop_id = '${CROP_UUID}';" 2>/dev/null || echo "N/A")
echo "  Status in DB: $(echo "$PG_STATUS" | tr -d '[:space:]')"

# ---------------------------------------------------------------------------
# TEST 3: ELASTICSEARCH VERIFICATION
# ---------------------------------------------------------------------------
print_header "TEST 3: ELASTICSEARCH — Verify document in '${ES_INDEX}'"

ES_RESPONSE=$(curl -s "${ES_BASE}/${ES_INDEX}/_doc/${CROP_UUID}" 2>/dev/null)
ES_FOUND=$(echo "$ES_RESPONSE" | grep -o '"found":[a-z]*' | cut -d: -f2)

if [ "$ES_FOUND" = "true" ]; then
    print_result "pass" "Document found in Elasticsearch (${ES_INDEX}/_doc/${CROP_UUID})"
    # Show which fields were indexed
    echo "  Indexed fields:"
    echo "$ES_RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    source = data.get('_source', {})
    for k in sorted(source.keys()):
        v = source[k]
        if isinstance(v, str) and len(v) > 50:
            v = v[:50] + '...'
        print(f'    {k}: {v}')
except: print('    (could not parse)')
" 2>/dev/null || echo "    (python3 not available for pretty-print)"
else
    print_result "fail" "Document NOT found in Elasticsearch"
    echo "  ES response: ${ES_RESPONSE}"
fi

# ---------------------------------------------------------------------------
# TEST 4: REDIS VERIFICATION (via docker exec)
# ---------------------------------------------------------------------------
print_header "TEST 4: REDIS — Verify cache entry"

REDIS_VALUE=$(docker exec verg-redis redis-cli GET "${CROP_UUID}" 2>/dev/null)

if [ -n "$REDIS_VALUE" ] && [ "$REDIS_VALUE" != "(nil)" ]; then
    print_result "pass" "Cache entry found in Redis (key=${CROP_UUID})"
    echo "  Value (first 200 chars): $(echo "$REDIS_VALUE" | head -c 200)"
else
    print_result "fail" "Cache entry NOT found in Redis (key=${CROP_UUID})"
fi

REDIS_TTL=$(docker exec verg-redis redis-cli TTL "${CROP_UUID}" 2>/dev/null)
echo "  TTL: ${REDIS_TTL}s"

# ---------------------------------------------------------------------------
# TEST 5: READ
# ---------------------------------------------------------------------------
print_header "TEST 5: READ — GET /crop/v1/read/${CROP_UUID}"

READ_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${CROP_READ_URL}/${CROP_UUID}")
READ_HTTP=$(echo "$READ_RESPONSE" | tail -n1)
READ_BODY=$(echo "$READ_RESPONSE" | sed '$d')

echo "  HTTP Status: ${READ_HTTP}"

if [ "$READ_HTTP" = "200" ]; then
    print_result "pass" "READ returned HTTP 200"
else
    print_result "fail" "READ returned HTTP ${READ_HTTP} (expected 200)"
fi

if echo "$READ_BODY" | grep -q "Ragi"; then
    print_result "pass" "READ response contains crop name 'Ragi'"
else
    print_result "fail" "READ response does not contain crop name"
    echo "  Response: ${READ_BODY}"
fi

# ---------------------------------------------------------------------------
# TEST 6: SEARCH by filter
# ---------------------------------------------------------------------------
print_header "TEST 6: SEARCH — Filter by season=Kharif"

SEARCH_PAYLOAD='{
  "filterCriteriaMap": {
    "season": "Kharif"
  },
  "pageNumber": 0,
  "pageSize": 10
}'

SEARCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CROP_SEARCH_URL}" \
    -H "Content-Type: application/json" \
    -d "${SEARCH_PAYLOAD}")

SEARCH_HTTP=$(echo "$SEARCH_RESPONSE" | tail -n1)
SEARCH_BODY=$(echo "$SEARCH_RESPONSE" | sed '$d')

echo "  HTTP Status: ${SEARCH_HTTP}"

if [ "$SEARCH_HTTP" = "200" ]; then
    print_result "pass" "SEARCH returned HTTP 200"
else
    print_result "fail" "SEARCH returned HTTP ${SEARCH_HTTP} (expected 200)"
fi

if echo "$SEARCH_BODY" | grep -q "TEST-CROP-001"; then
    print_result "pass" "SEARCH results contain 'TEST-CROP-001'"
else
    print_result "fail" "SEARCH results do not contain 'TEST-CROP-001'"
    echo "  Response (first 500 chars): $(echo "$SEARCH_BODY" | head -c 500)"
fi

# ---------------------------------------------------------------------------
# TEST 7: SEARCH with free text
# ---------------------------------------------------------------------------
print_header "TEST 7: SEARCH — Free text search"

SEARCH_ALL='{
  "pageNumber": 0,
  "pageSize": 10
}'

SEARCH_ALL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CROP_SEARCH_URL}" \
    -H "Content-Type: application/json" \
    -d "${SEARCH_ALL}")

SEARCH_ALL_HTTP=$(echo "$SEARCH_ALL_RESPONSE" | tail -n1)
SEARCH_ALL_BODY=$(echo "$SEARCH_ALL_RESPONSE" | sed '$d')

echo "  HTTP Status: ${SEARCH_ALL_HTTP}"

if [ "$SEARCH_ALL_HTTP" = "200" ]; then
    print_result "pass" "Match-all SEARCH returned HTTP 200"
else
    print_result "fail" "Match-all SEARCH returned HTTP ${SEARCH_ALL_HTTP}"
fi

TOTAL_COUNT=$(echo "$SEARCH_ALL_BODY" | grep -o '"totalCount":[0-9]*' | cut -d: -f2)
echo "  Total documents in crop_index: ${TOTAL_COUNT:-unknown}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
print_header "TEST SUMMARY"

echo "  Total:  ${TOTAL}"
echo "  Passed: ${PASSED}"
echo "  Failed: ${FAILED}"
echo ""

if [ "$FAILED" -eq 0 ]; then
    echo "  ${PASS} ALL TESTS PASSED"
    echo ""
    exit 0
else
    echo "  ${FAIL} SOME TESTS FAILED"
    echo ""
    exit 1
fi
