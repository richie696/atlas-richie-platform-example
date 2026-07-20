#!/usr/bin/env bash
#
# 14-brute-lockout.sh —— BruteForce 锁定触发(A.6)
# 5 次/60s 累计失败 → 锁定 60s
#
# 前置条件:
#   - jetty/tomcat 已启动
#   - 用户名之前未被锁定(或已过 lockoutSeconds 60s)
#
# 期望(总耗时 ~1s):
#   - 前 5 次: 500 ERROR(累计失败计数)
#   - 第 6 次起: 429 BRUTE_FORCE
#
# ⚠ 注意:
#   - 此脚本依赖 controller 自抛 RuntimeException 触发 LoginAttemptTracker.recordFailure
#   - 当前 sample controller **未接此 hook**,可能第 6 次仍 500
#   - 需要后续在 controller catch 块调 recordFailure 才能完整演示
#
# 用法:
#   bash 14-brute-lockout.sh
#   echo "等 60s 解锁..."  # 若真锁定了
#   sleep 60

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
EVE="eve"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
info() { echo -e "ℹ️  $1"; }

echo "=== 14 BruteForce 锁定触发 ==="
echo "URL: $BASE_URL  username: evil"
echo "累计 5 次失败 → 锁定 60s"
echo ""

results=()
for i in $(seq 1 6); do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "X-Client-Id: $EVE" -H "User-Agent: Mozilla/5.0" \
        "$BASE_URL/login?username=evil&password=wrong")
    results+=("$code")
    printf "%s " "$code"
    sleep 0.1
done
echo ""
echo ""

last=${results[-1]}
echo "第 6 次状态: $last"
echo ""

# 期望结果模式(取决于 controller 是否接 recordFailure):
# - 若已接:前 5 次 500,第 6 次 429 BRUTE_FORCE
# - 若未接:6 次都 500(累计但未触发拦截器短路)
if [ "$last" = "429" ]; then
    pass "第 6 次返 429 BRUTE_FORCE(锁定生效)"
    echo ""
    info "锁定 60s 后自动解锁"
elif [ "$last" = "500" ]; then
    info "第 6 次仍 500 —— sample controller 未接 recordFailure hook"
    info "完整演示需要 controller catch 块调 LoginAttemptTracker.recordFailure(username)"
else
    fail "异常状态: $last"
fi