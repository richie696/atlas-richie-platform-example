#!/usr/bin/env bash
#
# 10-slow-dump.sh —— HangDetection dump 档
# 阈值:dump=5000ms(>5s 触发 jstack dump 线程栈)
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - GET /slow?millis=6000 → 200,响应时间 ~6s
#   - 控制台: HangDetection WARN + jstack dump 日志
#
# 用法:
#   bash 10-slow-dump.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="slow-dump-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 10 HangDetection dump 档 · /slow?millis=6000 ==="
echo "URL: $BASE_URL"
echo "期望:响应 ~6s,控制台打 jstack dump 日志"
echo ""

start=$(date +%s.%N)
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/slow?millis=6000")
end=$(date +%s.%N)
elapsed=$(echo "$end - $start" | bc)

echo "status: $status  elapsed: ${elapsed}s"
echo "body:   $(cat /tmp/r.json)"
echo ""

[ "$status" = "200" ] && pass "返 200" || fail "期望 200,实际 $status"
ok=$(awk "BEGIN {print ($elapsed >= 5.5 && $elapsed <= 7.0) ? 1 : 0}")
[ "$ok" = "1" ] && pass "响应时间 ~6s" || fail "响应时间 ${elapsed}s 不在 [5.5, 7.0] 范围"

echo ""
echo "=== 通过 ==="
echo "💡 检查 jetty 控制台应有 jstack dump(线程栈快照)"