#!/usr/bin/env bash
#
# 31-burst-ratelimit.sh —— /rate-limit 路由 burst(A.2)
# /rate-limit 全局 5 req/s → 5×200 + 1×429
# 与 30-burst-hello 区别:不同 clientId(bob),互不干扰 alice
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - bob 槽未满
#
# 期望:
#   - 5 行 200 + 1 行 429 RATE_LIMITED
#
# 用法:
#   bash 31-burst-ratelimit.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BOB="bob"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 31 /rate-limit burst · 5 req/s · client=bob ==="
echo "URL: $BASE_URL  client: $BOB"
echo "期望:5×200 + 1×429"
echo ""

two_hundred=0
four_two_nine=0
for i in $(seq 1 6); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-Client-Id: $BOB" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/rate-limit")
    printf "%s " "$code"
    case "$code" in
        200) two_hundred=$((two_hundred + 1)) ;;
        429) four_two_nine=$((four_two_nine + 1)) ;;
    esac
done
echo ""
echo ""
echo "200: $two_hundred   429: $four_two_nine"

[ "$two_hundred" = "5" ] && pass "5×200" || fail "期望 5 个 200,实际 $two_hundred"
[ "$four_two_nine" = "1" ] && pass "1×429" || fail "期望 1 个 429,实际 $four_two_nine"

echo ""
echo "=== 通过 ==="