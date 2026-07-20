#!/usr/bin/env bash
#
# 05-cb-trip.sh —— CircuitBreaker OPEN 触发(A.3 完整版)
# 测试 CB 拦截器累计失败率触发 OPEN 转换
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - 进程刚启动(<5 分钟,内存 CB 状态还没被之前的测试污染)
#
# 期望(总耗时 ~2.5s):
#   - 10× 500(连续 10 次失败,sleep 0.25 绕开全局 RateLimit 5 req/s)
#   - CB 转 OPEN(失败率 100% > 阈值 30%,窗口 10s)
#
# ⚠ 关键陷阱:
#   - sleep 0.25 是必需的:全局 5 req/s 不加 sleep 会被拦 5 次
#     → 拦掉的请求不进 controller → CB 收不到事件 → 永不 OPEN
#   - CB 状态在内存,重启后清零
#
# 用法:
#   bash 05-cb-trip.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DAVE="dave"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 05 CircuitBreaker OPEN 触发 ==="
echo "URL: $BASE_URL  client: $DAVE"
echo "发送 10 次 simulateFailure=true,sleep 0.25 绕开 RateLimit ..."
echo ""

# 先探测 CB 当前状态(避免被之前测试污染)
first_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: $DAVE" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/payments/p3?simulateFailure=true")
sleep 0.25

if [ "$first_code" = "503" ]; then
    info "CB 已 OPEN(被之前测试触发),跳过 10 次失败"
    pass "CB 已处于 OPEN 状态"
    echo ""
    echo "下一步:跑 06-cb-probe.sh 验证 probe 返 503"
    exit 0
fi

results=("$first_code")
for i in $(seq 2 10); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-Client-Id: $DAVE" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/payments/p3?simulateFailure=true")
    results+=("$code")
    printf "%s " "$code"
    sleep 0.25
done
echo ""
echo ""

five_hundred_count=0
for code in "${results[@]}"; do
    [ "$code" = "500" ] && five_hundred_count=$((five_hundred_count + 1))
done

echo "500 计数: $five_hundred_count / 10"
[ "$five_hundred_count" = "10" ] \
    && pass "全部 500(无 RateLimit 干扰)" \
    || fail "期望 10×500,实际只有 $five_hundred_count 个,可能 RateLimit 拦了"

echo ""
echo "=== CB 状态机应在内存中 OPEN ==="
echo "下一步:跑 06-cb-probe.sh 验证 probe 返 503"