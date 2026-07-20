#!/usr/bin/env bash
#
# 01-hello-keyresolver.sh —— KeyResolver 测试
# 测试 X-Client-Id → KeyResolver → controller 三段链路
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - 1.1 带 X-Client-Id=alice  → 200 "hello-jetty, alice"
#   - 1.2 字面量 X-Client-Id=anonymous → 200 "hello-jetty, anonymous"
#
# 注:RateLimit 拦截器对所有路径前置校验 KeyResolver,缺 X-Client-Id 永远 401;
#    controller 的 @RequestHeader(required=false) 在 HTTP 入口不可达。
#
# 用法:
#   bash 01-hello-keyresolver.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
# 用脚本专属 clientId(避免被其它脚本的 burst 测试污染槽位)
CLIENT="hello-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 01 KeyResolver · /hello ==="
echo "URL: $BASE_URL"
echo ""

# 1.1 正常调用
echo "--- 1.1 X-Client-Id=$CLIENT ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/hello")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "200" ] && pass "1.1 返 200" || fail "1.1 期望 200,实际 $status"
grep -q "$CLIENT" /tmp/r.json \
    && pass "data 含 clientId" || fail "data 缺少 clientId"
echo ""

# 1.2 字面量 anonymous
echo "--- 1.2 X-Client-Id=anonymous ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: anonymous" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/hello")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "200" ] && pass "1.2 返 200" || fail "1.2 期望 200,实际 $status"
grep -q "anonymous" /tmp/r.json \
    && pass "data 含 anonymous" || fail "data 缺少 anonymous"

echo ""
echo "=== 全部通过 ==="