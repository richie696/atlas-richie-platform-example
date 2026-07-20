#!/usr/bin/env bash
#
# 09-slow-warn.sh —— HangDetection warn 档
# 阈值:warn=1000ms / dump=5000ms / killSwitch=30000ms
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - GET /slow?millis=2000 → 200,响应时间 ~2s
#   - 控制台: HangDetection warn 日志(>1s 但 <5s,不 dump)
#
# 用法:
#   bash 09-slow-warn.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="slow-warn-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 09 HangDetection warn 档 · /slow?millis=2000 ==="
echo "URL: $BASE_URL"
echo "期望:响应 ~2s,控制台打 warn 日志"
echo ""

start=$(date +%s.%N)
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/slow?millis=2000")
end=$(date +%s.%N)
elapsed=$(echo "$end - $start" | bc)

echo "status: $status  elapsed: ${elapsed}s"
echo "body:   $(cat /tmp/r.json)"
echo ""

[ "$status" = "200" ] && pass "返 200" || fail "期望 200,实际 $status"
# 用 awk 做浮点比较
ok=$(awk "BEGIN {print ($elapsed >= 1.8 && $elapsed <= 2.5) ? 1 : 0}")
[ "$ok" = "1" ] && pass "响应时间 ~2s" || fail "响应时间 ${elapsed}s 不在 [1.8, 2.5] 范围"

echo ""
echo "=== 通过 ==="
echo "💡 检查 jetty 控制台应有 HangDetection WARN 日志(无 jstack dump)"