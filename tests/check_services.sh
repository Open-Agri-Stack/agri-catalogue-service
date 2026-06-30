#!/usr/bin/env bash
echo "--- Checking Elasticsearch ---"
for i in $(seq 1 30); do
    if curl -s http://localhost:9200/_cluster/health 2>/dev/null | grep -qE 'green|yellow'; then
        echo "ES ready!"
        break
    fi
    echo "Waiting for ES... ($i/30)"
    sleep 2
done

echo "--- Checking Postgres ---"
if command -v psql &>/dev/null; then
    PGPASSWORD=verg_password psql -h localhost -p 5433 -U verg_user -d verg_db -c "SELECT 'Postgres ready!'" 2>&1 | tail -3
else
    echo "psql not installed — will install"
    sudo apt-get update -qq && sudo apt-get install -y -qq postgresql-client redis-tools 2>&1 | tail -3
    PGPASSWORD=verg_password psql -h localhost -p 5433 -U verg_user -d verg_db -c "SELECT 'Postgres ready!'" 2>&1 | tail -3
fi

echo "--- Checking Redis ---"
if command -v redis-cli &>/dev/null; then
    redis-cli -h localhost -p 6379 ping
else
    echo "redis-cli not installed — installed above with redis-tools"
fi
