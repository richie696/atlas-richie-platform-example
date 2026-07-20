#!/usr/bin/env bash
#
# 06-cb-probe.sh —— CB OPEN 后探测(4.4)
# 验证 CB OPEN 时即使不带 simulateFailure 也立即 503
#
# 强前置条件:
#   - 必须先跑 05-cb-trip.sh 让 CB 进入 OPEN 状态
#   - 进程未重启(重启会清零 CB 状态)
#   - waitDurationInOpenState(默认 30s)未到
#
# 期望:
#   - GET /payments/p4 (X-Client-Id=dave) → 503 {"code":"PAYMENT_CB_OPEN"}
#
# 常见失败原因:
#   - 没跑 05-cb-trip → CB CLOSED → 200(请先跑 05)
#   - 重启过 jetty → 状态清零 → 200(请重新跑 05)
#   - 跑了 30s+ → CB 已 HALF_OPEN → 探测成功转 CLOSED(请重新跑 05)
#
# 用法:
#   bash 05-cb-trip.sh && bash 06-cb-probe.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DAVE="dave"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 06 CircuitBreaker OPEN 后探测 ==="
echo "URL: $BASE_URL  client: $DAVE"
echo ""

status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $DAVE" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/payments/p4")
echo "status: $status  body: $(cat /tmp/r.json)"
echo ""

if [ "$status" = "503" ]; then
    pass "返 503 (CB fast-fail)"
    grep -q "PAYMENT_CB_OPEN" /tmp/r.json \
        && pass "code=PAYMENT_CB_OPEN" \
        || fail "503 但 code 不是 PAYMENT_CB_OPEN"
elif [ "$status" = "200" ]; then
    echo -e "${RED}❌ 返 200,说明 CB 还是 CLOSED — 检查:${NC}"
    echo "   1) 是否先跑了 05-cb-trip.sh?"
    echo "   2) 进程是否重启过?(CB 状态会清零)"
    echo "   3) 是否已过 waitDurationInOpenState(30s)?HALF_OPEN 探测成功会回 CLOSED"
    exit 1
else
    fail "期望 503,实际 $status"
fi

echo ""
echo "=== 通过 ==="