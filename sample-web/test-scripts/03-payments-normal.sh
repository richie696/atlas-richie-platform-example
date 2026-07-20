#!/usr/bin/env bash
#
# 03-payments-normal.sh —— 正常支付
# 测试 CircuitBreaker 拦截器对健康请求的放行
#
# 前置条件:
#   - jetty/tomcat 已启动
#
# 期望:
#   - GET /payments/p1 (X-Client-Id=alice) → 200 {"data":"... payment-p1 (amount=199.00)"}
#
# 注:此请求**不**触发 CB,即使 CB 处于 OPEN 状态,只要 clientKey 不在 OPEN 范围内就放行
#
# 用法:
#   bash 03-payments-normal.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CLIENT="pay-normal-$$"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 03 Payments · 正常调用 ==="
echo "URL: $BASE_URL"
echo ""

status=$(curl -s -o /tmp/r.json -w "%{http_code}" \
    -H "X-Client-Id: $CLIENT" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/payments/p1")
echo "status: $status  body: $(cat /tmp/r.json)"
[ "$status" = "200" ] && pass "返 200" || fail "期望 200,实际 $status"
grep -q "payment-p1" /tmp/r.json \
    && pass "data 含 payment-p1" || fail "data 字段错误"
grep -q "199.00" /tmp/r.json \
    && pass "amount=199.00" || fail "金额字段错误"

echo ""
echo "=== 通过 ==="