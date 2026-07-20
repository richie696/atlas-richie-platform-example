# Sample Web · Test Scripts

Shell 脚本测试集,逐场景覆盖 `sample-web-jetty` / `sample-web-tomcat` 所有 controller 端点 + 状态化场景(CB / BruteForce)。

## 用法

```bash
# 跑单个场景
bash 01-hello-keyresolver.sh

# 跑全部(按编号顺序)
bash run-all.sh

# 切换到 tomcat(默认 8080)
BASE_URL=http://127.0.0.1:8081 bash 01-hello-keyresolver.sh
BASE_URL=http://127.0.0.1:8081 bash run-all.sh
```

所有脚本支持 `BASE_URL` 环境变量,默认 `http://127.0.0.1:8080`。

## 脚本清单

### 单次调用(00-15)

| 编号 | 文件 | 场景 | HTTP 端点 |
|---|---|---|---|
| 00 | `00-smoke.sh` | Smoke test | `GET /app-info` |
| 01 | `01-hello-keyresolver.sh` | KeyResolver(2 子用例) | `GET /hello` |
| 02 | `02-ratelimit-global.sh` | 全局 RateLimit + 401 短路 | `GET /rate-limit` |
| 03 | `03-payments-normal.sh` | 正常支付 | `GET /payments/p1` |
| 04 | `04-payments-fail.sh` | 单次失败 500 | `GET /payments/p2?simulateFailure=true` |
| 05 | `05-cb-trip.sh` | **CB OPEN 触发**(10 失败 + sleep 0.25) | `GET /payments/p3?simulateFailure=true` ×10 |
| 06 | `06-cb-probe.sh` | CB OPEN 后 probe → 503 | `GET /payments/p4` |
| 07 | `07-cb-halfopen.sh` | CB OPEN → 30s → HALF_OPEN 探测 | `GET /payments/p4` |
| 08 | `08-sse.sh` | SSE 5s 长连接 | `GET /sse` |
| 09 | `09-slow-warn.sh` | HangDetection warn 档 | `GET /slow?millis=2000` |
| 10 | `10-slow-dump.sh` | HangDetection dump 档(jstack) | `GET /slow?millis=6000` |
| 11 | `11-slow-killswitch.sh` | HangDetection kill-switch | `GET /slow?millis=35000` |
| 12 | `12-botdetect.sh` | Bot UA 匹配(3 UA 变体) | `GET /bot-detect` |
| 13 | `13-login-success.sh` | 登录成功(密码=ok) | `POST /login` |
| 14 | `14-brute-lockout.sh` | BruteForce 触发(简化版) | `POST /login` ×6 |
| 15 | `15-actuator.sh` | Actuator 3 端点 | `GET /actuator/{health,metrics,prometheus}` |

### 状态化 / burst(30-32)

| 编号 | 文件 | 场景 |
|---|---|---|
| 30 | `30-burst-hello.sh` | /hello 全局限流 burst(5 req/s)+ 槽刷新验证 |
| 31 | `31-burst-ratelimit.sh` | /rate-limit burst(bob 槽独立于 alice) |
| 32 | `32-brute-lockout-full.sh` | BruteForce 完整锁演示(含解锁前探测) |

### 编排

| 编号 | 文件 | 场景 |
|---|---|---|
| - | `run-all.sh` | 顺序执行 00-31 全部(每个脚本独立 PASS/FAIL) |

## 关键设计

### 1. 每个脚本独立可跑

不像 IDEA Http Client 每个 `###` 块依赖共享上下文,这里每个脚本是完整闭环 —— 检查前置条件、发请求、断言响应、打印通过/失败原因。

### 2. 状态化场景拆脚本

CB OPEN / BruteForce LOCKOUT 这类**依赖内存状态**的场景:
- **05-cb-trip** 触发 CB OPEN
- **06-cb-probe** 验证 OPEN 状态(单独脚本)
- **07-cb-halfopen** 等 30s 后验证 HALF_OPEN

每个脚本独立可跑,失败时输出明确指引(例:"请先跑 05-cb-trip")。

### 3. sleep 显式标注

CB burst 必须 `sleep 0.25` 绕开 RateLimit;BruteForce 必须 sleep 60 等解锁。脚本注释里都标注了为什么。

### 4. 颜色输出

- `✓` 绿色 = 通过
- `✗` 红色 = 失败(`exit 1`)

`run-all.sh` 汇总通过/失败列表。

## 已知限制(已在脚本注释说明)

| 限制 | 影响脚本 | 原因 |
|---|---|---|
| 14 第 6 次仍 500 | 14, 32 | sample controller 未接 `LoginAttemptTracker.recordFailure` hook |
| Tomcat 8080 冲突 | - | 默认 BASE_URL=8080 是 jetty,tomcat 用 8081 |

## 与 IDEA Http Client 的对比

| 维度 | IDEA .http 文件 | 这些 shell 脚本 |
|---|---|---|
| 跑循环 | 不支持 | 原生 for |
| 状态断言 | `<> xxx.json` 引用 | shell `if` + `exit 1` |
| 跨脚本状态依赖 | 不明显 | 脚本注释明确说"先跑 XX" |
| 输出可读性 | JSON dump | 结构化日志 + ✓/✗ |
| IDE 集成 | IDEA Http Client 面板 | 终端 / CI / 任何 shell |
| 失败定位 | 看响应 JSON diff | 看脚本输出 + exit code |

## 端口速查

```bash
# Jetty (默认)
BASE_URL=http://127.0.0.1:8080 bash run-all.sh

# Tomcat
BASE_URL=http://127.0.0.1:8081 bash run-all.sh

# 远程
BASE_URL=http://prod-server:8080 bash 00-smoke.sh
```