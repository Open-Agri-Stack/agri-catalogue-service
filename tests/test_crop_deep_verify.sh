#!/usr/bin/env bash
# Deep verification tests for Crop Registry
set -uo pipefail

PASS="✅"
FAIL="❌"
APP="http://localhost:8080"

echo ""
echo "================================================================"
echo "  DEEP VERIFICATION: Crop Registry"
echo "================================================================"

# ---------------------------------------------------------------
# TEST A: Payload Validation — missing required fields
# ---------------------------------------------------------------
echo ""
echo "--- TEST A: Reject invalid payload (missing required fields) ---"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$APP/crop/v1/create" \
  -H "Content-Type: application/json" \
  -d '{"cropId":"X","name":"test"}')
HTTP=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "  HTTP: $HTTP"
echo "  Body: $BODY"
if [ "$HTTP" != "200" ]; then
    echo "  $PASS Correctly rejected invalid payload (HTTP $HTTP)"
else
    echo "  $FAIL Should have rejected — missing farmerId, farmId, season, year, sownAreaHectares"
fi

# ---------------------------------------------------------------
# TEST B: Payload Validation — wrong type for year
# ---------------------------------------------------------------
echo ""
echo "--- TEST B: Reject payload with wrong type (year as string) ---"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$APP/crop/v1/create" \
  -H "Content-Type: application/json" \
  -d '{"cropId":"X","farmerId":"F1","farmId":"FA1","name":"test","season":"Kharif","year":"not-a-number","sownAreaHectares":1.0}')
HTTP=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "  HTTP: $HTTP"
echo "  Body: $BODY"
if [ "$HTTP" != "200" ]; then
    echo "  $PASS Correctly rejected wrong type (HTTP $HTTP)"
else
    echo "  $FAIL Should have rejected — year should be integer, not string"
fi

# ---------------------------------------------------------------
# TEST C: Read non-existent ID
# ---------------------------------------------------------------
echo ""
echo "--- TEST C: Read non-existent ID ---"
RESP=$(curl -s -w "\n%{http_code}" "$APP/crop/v1/read/nonexistent-id-12345")
HTTP=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "  HTTP: $HTTP"
echo "  Body: $BODY"
if echo "$BODY" | grep -qi "invalid\|not.found"; then
    echo "  $PASS Correctly returned not-found message"
else
    echo "  $FAIL Expected not-found/invalid-id message"
fi

# ---------------------------------------------------------------
# TEST D: Read from Postgres (evict cache first)
# ---------------------------------------------------------------
echo ""
echo "--- TEST D: Read from Postgres (bypass Redis cache) ---"
# Get an existing crop ID from Postgres
CROP_ID=$(docker exec verg-postgres \
    psql -U verg_user -d verg_db -t -A \
    -c "SELECT crop_id FROM crop LIMIT 1;" 2>/dev/null)
CROP_ID=$(echo "$CROP_ID" | tr -d '[:space:]')

if [ -z "$CROP_ID" ]; then
    echo "  $FAIL No crop records found in Postgres"
else
    echo "  Found crop_id in Postgres: $CROP_ID"
    # Delete from Redis cache
    docker exec verg-redis redis-cli DEL "$CROP_ID" > /dev/null 2>&1
    REDIS_CHECK=$(docker exec verg-redis redis-cli GET "$CROP_ID" 2>/dev/null)
    echo "  Redis after DEL: ${REDIS_CHECK:-(nil)}"

    # Now read via API — should hit Postgres
    RESP=$(curl -s -w "\n%{http_code}" "$APP/crop/v1/read/$CROP_ID")
    HTTP=$(echo "$RESP" | tail -n1)
    BODY=$(echo "$RESP" | sed '$d')
    echo "  HTTP: $HTTP"
    if [ "$HTTP" = "200" ] && echo "$BODY" | grep -q "cropId"; then
        echo "  $PASS Read from Postgres succeeded (cache was empty)"
    else
        echo "  $FAIL Read from Postgres failed"
        echo "  Body: $BODY"
    fi

    # Verify Redis was re-populated (write-through)
    REDIS_REPOP=$(docker exec verg-redis redis-cli GET "$CROP_ID" 2>/dev/null)
    if [ -n "$REDIS_REPOP" ] && [ "$REDIS_REPOP" != "(nil)" ]; then
        echo "  $PASS Redis cache was re-populated after Postgres read (write-through)"
    else
        echo "  $FAIL Redis cache was NOT re-populated"
    fi
fi

# ---------------------------------------------------------------
# TEST E: Search with multiple filters
# ---------------------------------------------------------------
echo ""
echo "--- TEST E: Search with multiple filter criteria ---"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$APP/crop/v1/search" \
  -H "Content-Type: application/json" \
  -d '{"filterCriteriaMap":{"season":"Kharif","category":"Millet"},"pageNumber":0,"pageSize":10}')
HTTP=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "  HTTP: $HTTP"
if [ "$HTTP" = "200" ]; then
    echo "  $PASS Multi-filter search returned HTTP 200"
    TOTAL=$(echo "$BODY" | grep -o '"totalCount":[0-9]*' | cut -d: -f2)
    echo "  Results: ${TOTAL:-unknown}"
else
    echo "  $FAIL Multi-filter search failed (HTTP $HTTP)"
    echo "  Body: $BODY"
fi

# ---------------------------------------------------------------
# TEST F: Search with facets (aggregations)
# ---------------------------------------------------------------
echo ""
echo "--- TEST F: Search with facets ---"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$APP/crop/v1/search" \
  -H "Content-Type: application/json" \
  -d '{"pageNumber":0,"pageSize":10,"facets":["season","category"]}')
HTTP=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "  HTTP: $HTTP"
if [ "$HTTP" = "200" ]; then
    echo "  $PASS Faceted search returned HTTP 200"
    if echo "$BODY" | grep -q "facets"; then
        echo "  $PASS Facets present in response"
    else
        echo "  $FAIL No facets in response"
    fi
else
    echo "  $FAIL Faceted search failed (HTTP $HTTP)"
    echo "  Body: $BODY"
fi

# ---------------------------------------------------------------
# TEST G: Verify ES document field integrity
# ---------------------------------------------------------------
echo ""
echo "--- TEST G: Verify ES indexed fields match schema whitelist ---"
if [ -n "$CROP_ID" ]; then
    ES_FIELDS=$(curl -s "http://localhost:9200/crop_index/_doc/$CROP_ID" 2>/dev/null | \
        python3 -c "import sys,json; d=json.load(sys.stdin).get('_source',{}); print(' '.join(sorted(d.keys())))" 2>/dev/null)
    echo "  Indexed fields: $ES_FIELDS"

    # These should NOT be in ES (not in the ES mapping whitelist)
    if echo "$ES_FIELDS" | grep -q "CropID"; then
        echo "  $FAIL 'CropID' (uppercase) should not be in ES — it's added by the service but not in the schema whitelist"
    else
        echo "  $PASS No extra fields leaked into ES index"
    fi
else
    echo "  Skipped (no crop ID available)"
fi

# ---------------------------------------------------------------
# Summary
# ---------------------------------------------------------------
echo ""
echo "================================================================"
echo "  DEEP VERIFICATION COMPLETE"
echo "================================================================"
echo ""
