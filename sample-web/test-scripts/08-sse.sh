#!/usr/bin/env bash
#
# 08-sse.sh —— SSE 长连接
# 测试 SseManager 推 tick 事件 + 15s 心跳
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望(timeout 5s):
#   - 立即收到 "event:tick" 帧 + "data:jetty-sse connected"
#   - 后续每 15s 收到 "event:ping" 心跳(5s 超时内可能见不到)
#
# 用法:
#   bash 08-sse.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="sse-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 08 SSE · /sse ==="
echo "URL: $BASE_URL"
echo "SSE 5s 超时,期望收到 tick 帧 ..."
echo ""

output=$(timeout 5s curl -sN \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/sse" 2>/dev/null || true)

echo "$output"
echo ""

echo "$output" | grep -q "event:tick" \
    && pass "收到 tick 帧" \
    || fail "未收到 tick 帧"

echo "$output" | grep -q "sse connected" \
    && pass "data 含 sse connected" \
    || fail "data 字段异常"

echo ""
echo "=== 通过 ==="