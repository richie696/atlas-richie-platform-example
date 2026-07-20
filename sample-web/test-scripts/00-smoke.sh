#!/usr/bin/env bash
#
# 00-smoke.sh —— Smoke test
# 验证服务基本可达,排除连接问题(端口、协议、入口映射)
#
# 前置条件:
#   - jetty (8080) 或 tomcat (8081) 已启动
#
# 期望:
#   - 200 + {"success":true,"data":"Sample web app on ..."}
#
# 用法:
#   bash 00-smoke.sh                                    # jetty (8080)
#   BASE_URL=http://127.0.0.1:8081 bash 00-smoke.sh     # tomcat (8081)

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "=== 00 Smoke Test · /app-info ==="
echo "URL: $BASE_URL"
echo ""

status=$(curl -s -o /tmp/smoke.json -w "%{http_code}" \
    -H "X-Client-Id: smoke" -H "User-Agent: Mozilla/5.0" \
    "$BASE_URL/app-info")

echo "status: $status"
echo "body:   $(cat /tmp/smoke.json)"
echo ""

[ "$status" = "200" ] && pass "返回 200" || fail "期望 200,实际 $status"
grep -q '"success":true' /tmp/smoke.json \
    && pass "success=true" \
    || fail "success 字段缺失"

echo ""
echo "=== 全部通过 ==="