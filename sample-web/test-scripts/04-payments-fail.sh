#!/usr/bin/env bash
#
# 04-payments-fail.sh —— 单次模拟失败
# 测试 simulateFailure=true 时 controller 抛 RuntimeException → 500
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - GET /payments/p2?simulateFailure=true → 500
#
# 注:CB fast-fail only 设计 —— 业务方必须 cb.execute() 包裹(本 controller 已接)。
#    单次失败不会触发 CB OPEN(需要 10 次失败,见 07-cb-trip.sh)
#
# 用法:
#   bash 04-payments-fail.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="pay-fail-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 04 Payments · 模拟失败 1 次 ==="
echo "URL: $BASE_URL"
echo ""

status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/payments/p2?simulateFailure=true")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "500" ] && pass "返 500 (RuntimeException)" || fail "期望 500,实际 $status"

echo ""
echo "=== 通过 ==="