#!/usr/bin/env bash
#
# 13-login-success.sh —— 正常登录(密码 = "ok")
# 测试 LoginAttemptTracker 不计数成功请求
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - 用户名未被之前的失败测试锁定(若刚跑过 14-brute-lockout.sh,需 sleep 60 等解锁)
#
# 期望:
#   - POST /login?username=alice&password=ok → 200 + UUID token
#
# 用法:
#   bash 13-login-success.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="login-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 13 Login · 密码正确 ==="
echo "URL: $BASE_URL"
echo ""

status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -X POST \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/login?username=$CLIENT&password=ok")
echo "status: $status  body: $(cat /tmp/r.json)"
echo ""

[ "$status" = "200" ] && pass "返 200" || fail "期望 200,实际 $status"
grep -q "login-token" /tmp/r.json \
    && pass "data 含 login-token" \
    || fail "缺少 login-token"

echo ""
echo "=== 通过 ==="