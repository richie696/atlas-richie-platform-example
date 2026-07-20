#!/usr/bin/env bash
#
# 12-botdetect.sh —— AnomalyDetection Bot UA 检测
# 配置:bot-user-agents=["curl/*"] —— 匹配 curl 默认 UA "curl/x.y.z"
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - Mozilla/IntelliJ/Wget UA → 200
#   - curl/x.y.z UA          → 403 BOT_DETECTED
#
# 用法:
#   bash 12-botdetect.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="botdetect-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 12 Bot UA Detection · /bot-detect ==="
echo "URL: $BASE_URL"
echo ""

# 8.1 Mozilla UA
echo "--- 8.1 Mozilla/5.0 ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/bot-detect")
echo "status: $status"
[ "$status" = "200" ] && pass "Mozilla UA → 200" || fail "Mozilla 期望 200,实际 $status"
echo ""

# 8.2 curl UA → 403
echo "--- 8.2 curl/8.7.1 (黑名单) ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: curl/8.7.1" \
    "$BASE_URL/bot-detect")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "403" ] && pass "curl UA → 403" || fail "curl 期望 403,实际 $status"
grep -q "BOT_DETECTED" /tmp/r.json \
    && pass "code=BOT_DETECTED" || fail "code 错误"
echo ""

# 8.3 wget UA → 200 (不在黑名单)
echo "--- 8.3 Wget/1.21.4 (不在黑名单) ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Wget/1.21.4" \
    "$BASE_URL/bot-detect")
echo "status: $status"
[ "$status" = "200" ] && pass "Wget UA → 200" || fail "Wget 期望 200,实际 $status"

echo ""
echo "=== 全部通过 ==="