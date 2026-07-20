#!/usr/bin/env bash
#
# 07-cb-halfopen.sh —— CB HALF_OPEN 探测(A.4)
# CB OPEN 状态维持 waitDurationInOpenState(默认 30s)后自动转 HALF_OPEN
# HALF_OPEN 接受单次探测:成功 → CLOSED,失败 → 继续 OPEN 30s
#
# 前置条件:
#   - 跑过 05-cb-trip.sh(CB 已 OPEN)
#   - 等 30s(waitDurationInOpenState)
#
# 期望:
#   - simulateFailure=false(/payments/p4 不带参数) → 200 CLOSED,或 503 继续 OPEN
#
# 用法:
#   bash 05-cb-trip.sh
#   echo "等 30s..."
#   sleep 30
#   bash 07-cb-halfopen.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DAVE="dave"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
info() { echo -e "ℹ️  $1"; }

echo "=== 07 CircuitBreaker HALF_OPEN 探测 ==="
echo "URL: $BASE_URL  client: $DAVE"
echo ""
info "必须先跑 05-cb-trip.sh,再等 ≥30s 再跑本脚本"
echo ""

# 检测当前状态
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $DAVE" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/payments/p4")
echo "探测状态: $status"
echo "body: $(cat /tmp/r.json)"
echo ""

case "$status" in
    200)
        info "返 200 → CB 转 CLOSED(探测成功)"
        pass "CLOSED"
        ;;
    503)
        info "返 503 → CB 仍 OPEN/HALF_OPEN(探测失败,需再等 30s)"
        echo "再跑 05-cb-trip 重置 CB 后等 30s 重新探测"
        ;;
    *)
        echo "⚠️  异常状态 $status,可能 CB 不在 OPEN 范围(没跑 05)"
        exit 1
        ;;
esac