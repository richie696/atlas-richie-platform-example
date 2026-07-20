#!/usr/bin/env bash
#
# 11-slow-killswitch.sh —— HangDetection kill-switch 档
# 阈值:killSwitch=30000ms(>30s 触发 INTERRUPTED,远早于 35s 提前返回)
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - GET /slow?millis=35000 → 响应远早于 35s(被 Watchdog 中断)
#   - 响应:{"code":"INTERRUPTED",...}
#
# 用法:
#   bash 11-slow-killswitch.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="slow-killswitch-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 11 HangDetection kill-switch 档 · /slow?millis=35000 ==="
echo "URL: $BASE_URL"
echo "期望:被 kill-switch 中断,响应远早于 35s,code=INTERRUPTED"
echo ""

start=$(date +%s.%N)
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/slow?millis=35000")
end=$(date +%s.%N)
elapsed=$(echo "$end - $start" | bc)

echo "status: $status  elapsed: ${elapsed}s"
echo "body:   $(cat /tmp/r.json)"
echo ""

[ "$status" = "200" ] && pass "返 200" || fail "期望 200,实际 $status"
# kill-switch 在 30s 触发,所以响应时间应该 << 35s(实际 ~30-31s)
ok=$(awk "BEGIN {print ($elapsed < 33.0) ? 1 : 0}")
[ "$ok" = "1" ] && pass "响应时间 ${elapsed}s < 33s (kill-switch 生效)" || fail "响应 ${elapsed}s 太久,kill-switch 未生效"
grep -q "INTERRUPTED" /tmp/r.json \
    && pass "code=INTERRUPTED" \
    || fail "缺少 INTERRUPTED"

echo ""
echo "=== 通过 ==="