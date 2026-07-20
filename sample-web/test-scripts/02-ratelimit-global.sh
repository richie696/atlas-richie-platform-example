#!/usr/bin/env bash
#
# 02-ratelimit-global.sh —— 全局 RateLimit 测试
# 测试 RateLimit 拦截器对缺失 X-Client-Id 的前置短路 + 正常调用
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - 2.1 无 X-Client-Id           → 401 {"error":"client_unidentified"}
#   - 2.2 带 X-Client-Id=alice     → 200 {"data":"jetty rate-limit ok"}
#
# burst 验证:见 30-burst-hello.sh / 31-burst-ratelimit.sh
#
# 用法:
#   bash 02-ratelimit-global.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="ratelimit-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 02 Global RateLimit · /rate-limit ==="
echo "URL: $BASE_URL"
echo ""

# 2.1 无 X-Client-Id → 401
echo "--- 2.1 无 X-Client-Id ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/rate-limit")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "401" ] && pass "2.1 返 401 (RateLimit 前置短路)" || fail "2.1 期望 401,实际 $status"
grep -q "client_unidentified" /tmp/r.json \
    && pass "code=client_unidentified" || fail "code 错误"
echo ""

# 2.2 带 X-Client-Id → 200
echo "--- 2.2 X-Client-Id=$CLIENT ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/rate-limit")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "200" ] && pass "2.2 返 200" || fail "2.2 期望 200,实际 $status"
grep -q "rate-limit ok" /tmp/r.json \
    && pass "rate-limit ok" || fail "data 字段错误"

echo ""
echo "=== 全部通过 ==="