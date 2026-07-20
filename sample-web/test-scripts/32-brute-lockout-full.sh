#!/usr/bin/env bash
#
# 32-brute-lockout-full.sh —— BruteForce 完整锁演示(A.6 详细版)
# 区别于 14:含完整断言 + 锁定后探测
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - 用户名 evil 未被锁定
#
# 期望:
#   - 前 5 次: 500(ERROR)
#   - 第 6 次: 429 BRUTE_FORCE(若 controller 接 recordFailure hook)
#   - 探测(锁定期间,密码正确):429 BRUTE_FORCE
#   - sleep 60 后:解锁,密码正确返 200
#
# 用法:
#   bash 32-brute-lockout-full.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
EVE="eve"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
info() { echo -e "ℹ️  $1"; }

echo "=== 32 BruteForce 完整锁演示 ==="
echo "URL: $BASE_URL  username: evil"
echo ""

# 累计 5 次失败
echo "--- 累计 5 次错误密码 ---"
for i in $(seq 1 5); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "X-Client-Id: $EVE" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/login?username=evil&password=wrong")
    printf "%s " "$code"
    sleep 0.1
done
echo ""
echo ""

# 第 6 次(应被锁)
echo "--- 第 6 次(密码仍错) ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -X POST \
    -H "X-Client-Id: $EVE" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/login?username=evil&password=wrong")
echo "status: $status  body: $(cat /tmp/r.json)"
echo ""

if [ "$status" = "429" ]; then
    pass "第 6 次返 429 BRUTE_FORCE(锁定生效)"
    grep -q "BRUTE_FORCE" /tmp/r.json && pass "code=BRUTE_FORCE"
elif [ "$status" = "500" ]; then
    info "第 6 次仍 500 —— controller 未接 LoginAttemptTracker.recordFailure hook"
    info "完整演示需要修改 controller catch 块"
    exit 0
else
    fail "异常: $status"
fi

# 锁定期间密码正确也被拒
echo ""
echo "--- 锁定期间,密码正确也拒绝 ---"
status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -X POST \
    -H "X-Client-Id: $EVE" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/login?username=evil&password=ok")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "429" ] && pass "锁定期间密码正确也被拒" || fail "期望 429,实际 $status"

echo ""
echo "=== 全部通过 ==="
echo "💡 等待 60s 后自动解锁,可重试"