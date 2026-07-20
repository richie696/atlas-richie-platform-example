#!/usr/bin/env bash
#
# 30-burst-hello.sh —— 全局限流 burst 验证(A.1)
# /hello 全局 5 req/s → 5×200 + 1×429
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - alice 槽未满(等 1s 后跑)
#
# 期望:
#   - 5 行 200 + 1 行 429 RATE_LIMITED(总耗时 ~1s)
#   - 等待 1s 后槽刷新,可重跑
#
# 用法:
#   bash 30-burst-hello.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ALICE="alice"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 30 全局限流 burst · /hello · 5 req/s ==="
echo "URL: $BASE_URL  client: $ALICE"
echo "期望:5×200 + 1×429"
echo ""

two_hundred=0
four_two_nine=0
for i in $(seq 1 6); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-Client-Id: $ALICE" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/hello")
    printf "%s " "$code"
    case "$code" in
        200) two_hundred=$((two_hundred + 1)) ;;
        429) four_two_nine=$((four_two_nine + 1)) ;;
    esac
done
echo ""
echo ""
echo "200: $two_hundred   429: $four_two_nine"
echo ""

[ "$two_hundred" = "5" ] && pass "5×200" || fail "期望 5 个 200,实际 $two_hundred"
[ "$four_two_nine" = "1" ] && pass "1×429" || fail "期望 1 个 429,实际 $four_two_nine"

echo ""
echo "=== 通过 ==="
echo "💡 等待 1s 后槽刷新,可重跑验证"
sleep 1
echo ""
echo "重新跑(槽应已刷新):"
two_hundred=0
four_two_nine=0
for i in $(seq 1 6); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-Client-Id: $ALICE" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/hello")
    printf "%s " "$code"
    case "$code" in
        200) two_hundred=$((two_hundred + 1)) ;;
        429) four_two_nine=$((four_two_nine + 1)) ;;
    esac
done
echo ""
[ "$two_hundred" = "5" ] && pass "槽刷新:又 5×200" || fail "刷新后预期 5×200,实际 $two_hundred"