#!/usr/bin/env bash
#
# 15-actuator.sh —— Actuator 端点
# 测试 /actuator/health + /actuator/metrics + /actuator/prometheus
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - 9.1 /actuator/health              → 200 UP
#   - 9.2 /actuator/metrics             → 200 (web.sse.connections 等)
#   - 9.3 /actuator/prometheus          → 200 + prometheus 文本格式
#
# 注:Actuator URL 也走 InterceptingFilter,需带 X-Client-Id
#
# 用法:
#   bash 15-actuator.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="actuator-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 15 Actuator 端点 ==="
echo "URL: $BASE_URL"
echo ""

# 9.1 health
echo "--- 9.1 /actuator/health ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/actuator/health")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "200" ] && pass "health 返 200" || fail "health 期望 200,实际 $status"
grep -q '"status":"UP"' /tmp/r.json \
    && pass "status=UP" || fail "status 非 UP"
echo ""

# 9.2 metrics (just check it returns 200)
echo "--- 9.2 /actuator/metrics ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/actuator/metrics")
echo "status: $status"
[ "$status" = "200" ] && pass "metrics 返 200" || fail "metrics 期望 200,实际 $status"
echo ""

# 9.3 prometheus
echo "--- 9.3 /actuator/prometheus ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/actuator/prometheus")
echo "status: $status  size: $(wc -c < /tmp/r.json) bytes"
[ "$status" = "200" ] && pass "prometheus 返 200" || fail "prometheus 期望 200,实际 $status"
grep -q "# HELP\|# TYPE" /tmp/r.json \
    && pass "含 prometheus 元数据" || fail "格式异常"

echo ""
echo "=== 全部通过 ==="