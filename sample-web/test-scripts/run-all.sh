#!/usr/bin/env bash
#
# run-all.sh —— 顺序执行所有测试脚本
# 按编号顺序跑(NN-* 然后 3X-* burst)
#
# 前置条件:
#   - jetty/tomcat 已启动(默认 8080,可 BASE_URL 覆盖)
#
# 用法:
#   bash run-all.sh                          # 默认 jetty
#   BASE_URL=http://127.0.0.1:8081 bash run-all.sh  # tomcat

set -uo pipefail

cd "$(dirname "$0")"

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'

SCRIPTS=(
    00-smoke.sh
    01-hello-keyresolver.sh
    02-ratelimit-global.sh
    03-payments-normal.sh
    04-payments-fail.sh
    05-cb-trip.sh
    06-cb-probe.sh
    07-cb-halfopen.sh
    08-sse.sh
    09-slow-warn.sh
    10-slow-dump.sh
    11-slow-killswitch.sh
    12-botdetect.sh
    13-login-success.sh
    14-brute-lockout.sh
    15-actuator.sh
    30-burst-hello.sh
    31-burst-ratelimit.sh
)

passed=0
failed=0
failed_list=()

echo "================================================"
echo "  Sample Web · Test Suite"
echo "  BASE_URL: ${BASE_URL:-http://127.0.0.1:8080}"
echo "================================================"
echo ""

for script in "${SCRIPTS[@]}"; do
    echo ""
    echo "################################################"
    echo "  [$script]"
    echo "################################################"

    if bash "$script"; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
        failed_list+=("$script")
    fi
done

echo ""
echo "================================================"
echo "  Summary: $passed passed, $failed failed"
echo "================================================"

if [ "$failed" -gt 0 ]; then
    echo ""
    echo -e "${RED}失败脚本:${NC}"
    for s in "${failed_list[@]}"; do
        echo "  - $s"
    done
    exit 1
fi

echo ""
echo -e "${GREEN}全部通过 ✓${NC}"