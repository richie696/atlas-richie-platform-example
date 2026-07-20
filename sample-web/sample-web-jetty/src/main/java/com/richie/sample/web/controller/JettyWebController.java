package com.richie.sample.web.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.registry.CircuitBreakerRegistry;
import com.richie.component.web.core.sse.SseEvent;
import com.richie.component.web.core.sse.SseManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

/**
 * sample-web-jetty 控制器 —— 在 Embedded Jetty 12 上手动验证 web-core + web-jetty 全部特性。
 * <p>
 * 每个端点都是某个 web-core 能力的<strong>最小可复现 demo</strong>：通过 {@code curl} 调用一次即可
 * 观察对应拦截器 / 组件的行为。本类不是业务代码，仅供 sample 引用与端到端验证。
 *
 * <h2>端点清单</h2>
 * <table>
 *   <tr><th>端点</th><th>测试的 web-core 能力</th><th>触发配置</th></tr>
 *   <tr><td>{@link #hello}</td><td>KeyResolver（{@code X-Client-Id} → 限流 key）</td><td>—</td></tr>
 *   <tr><td>{@link #rateLimit}</td><td>RateLimit 全局限流（5 req/s）</td><td>{@code platform.component.web.rate-limit.enabled=true}</td></tr>
 *   <tr><td>{@link #payment}</td><td>CircuitBreaker（{@code /payments/**} 失败率 ≥30% / 窗口 10 熔断）</td><td>{@code circuit-breaker.routes./payments/**}</td></tr>
 *   <tr><td>{@link #sse}</td><td>SSE 长连接 + 心跳</td><td>{@code SseManager}</td></tr>
 *   <tr><td>{@link #slow}</td><td>HangDetection 三档阈值链（warn=1s / dump=5s / kill-switch=30s）</td><td>{@code hang-detection.*}</td></tr>
 *   <tr><td>{@link #login}</td><td>BruteForce 防爆破（5 次/60s 锁定 60s）</td><td>{@code protection.brute-force.*}</td></tr>
 *   <tr><td>{@link #botDetect}</td><td>AnomalyDetection Bot UA 拦截（{@code curl/*} 命中）</td><td>{@code protection.anomaly-detection.bot-user-agents=["curl/*"]}</td></tr>
 *   <tr><td>{@link #info}</td><td>smoke test（确认 Jetty alive）</td><td>—</td></tr>
 * </table>
 *
 * <h2>前置条件</h2>
 * <ul>
 *   <li>所有请求需带 {@code X-Client-Id} 头——未带则 KeyResolver 返回 null → {@code 401 client_unidentified}（由 RateLimit 拦截器前置短路）</li>
 *   <li>Bot UA 列表 {@code ["curl/*"]} 触发条件：默认 curl 的 UA 为 {@code curl/x.y.z}；浏览器 / Postman / 自定义 UA 都不会触发</li>
 * </ul>
 */
@Slf4j
@RestController
public class JettyWebController {

    private final SseManager sseManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public JettyWebController(SseManager sseManager,
                              CircuitBreakerRegistry circuitBreakerRegistry) {
        this.sseManager = sseManager;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * 测试 KeyResolver + 全局限流（{@code /rate-limit} 走全局 5 req/s）。
     * <p><strong>入参</strong>：{@code X-Client-Id} 请求头（可空，空则视为 anonymous）。
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * curl -H 'X-Client-Id: alice' http://localhost:8080/hello
     * }</pre>
     * <p><strong>期望</strong>：连续 5 次/秒内成功 → 第 6 次返回 {@code 429 RATE_LIMITED}（{"code":"RATE_LIMITED","msg":"jetty 请求过于频繁 (key=alice)"}）。
     * 匿名（不带 header）连发同样会触发 429（anonymous 占同一个 slot）。
     */
    @GetMapping("/hello")
    public ApiResult<String> hello(@RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        return ApiResult.success("hello-jetty, " + (clientId == null ? "anonymous" : clientId));
    }

    /**
     * 触发全局 RateLimit（5 req/s）的便捷端点。逻辑同 {@link #hello} 但无 header 拼接噪音。
     * <p><strong>入参</strong>：{@code X-Client-Id}（必带，否则 RateLimit 拦截器前置 401）。
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * for i in $(seq 1 6); do curl -s -o /dev/null -w "%{http_code}\n" \
     *      -H 'X-Client-Id: bob' http://localhost:8080/rate-limit; done
     * }</pre>
     * <p><strong>期望</strong>：前 5 行 200，第 6 行 429。
     */
    @GetMapping("/rate-limit")
    public ApiResult<String> rateLimit() {
        return ApiResult.success("jetty rate-limit ok");
    }

    /**
     * 测试 CircuitBreaker 拦截器（{@code /payments/**} 失败率 ≥30% / 滑动窗口=10s）。
     * <p><strong>关键设计</strong>：本端点用 {@link CircuitBreaker#execute(java.util.concurrent.Callable)} 包裹业务逻辑——
     * 拦截器只检查 state 不计数，业务方必须主动接 {@code cb.execute()} 才能让 CB 看到失败（Java 生态共识，
     * Resilience4j / Sentinel / Hystrix 同理）。
     * <p><strong>入参</strong>：
     * <ul>
     *   <li>{@code id} —— 任意字符串</li>
     *   <li>{@code simulateFailure}（query, 默认 false）—— true 时抛 RuntimeException 模拟失败计数</li>
     *   <li>{@code X-Client-Id} —— 同上</li>
     * </ul>
     * <p><strong>熔断触发流程</strong>：
     * <ol>
     *   <li>连发 10 次带 {@code ?simulateFailure=true}（间隔 ≥250ms 避开全局限流）—— 全部异常 500</li>
     *   <li>CB 进入 OPEN（10 个事件全失败 / 失败率 100% ≥ 30%）</li>
     *   <li>再访问（带或不带 {@code simulateFailure}）→ 立即 503 PAYMENT_CB_OPEN，不进入 controller</li>
     *   <li>等待 {@code waitDurationInOpenState}（默认 30s）后转 HALF_OPEN，放行少量探测</li>
     * </ul>
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * for i in $(seq 1 10); do
     *   curl -s -o /dev/null -w "%{http_code}\n" \
     *        -H 'X-Client-Id: alice' \
     *        "http://127.0.0.1:8080/payments/p1?simulateFailure=true"
     *   sleep 0.25   # > 200ms (5 req/s refill 周期),否则后 5 次被 RateLimit 拦掉,凑不够 10 个事件
     * done           # 全 500
     * curl -H 'X-Client-Id: alice' -H 'User-Agent: Mozilla/5.0' http://127.0.0.1:8080/payments/p2  # 503 PAYMENT_CB_OPEN
     * }</pre>
     */
    @GetMapping("/payments/{id}")
    public ApiResult<String> payment(@PathVariable String id,
                                     @RequestParam(defaultValue = "false") boolean simulateFailure) throws Exception {
        CircuitBreaker cb = circuitBreakerRegistry.find("/payments/**")
                .orElseGet(() -> circuitBreakerRegistry.getOrCreate("/payments/**",
                        k -> CircuitBreaker.ofRate(30, Duration.ofSeconds(10), Duration.ofSeconds(30))));
        return cb.execute(() -> {
            if (simulateFailure) {
                throw new RuntimeException("simulated payment failure for " + id);
            }
            return ApiResult.success("jetty payment-" + id + " (amount=199.00)");
        });
    }

    /**
     * 测试 SSE 长连接 + 心跳（{@code heartbeatInterval=15s} / {@code timeout=1h}）。
     * <p>建立连接后立即推一条 {@code tick} 事件，之后服务端每 15s 推 {@code ping} 心跳。
     * <p><strong>入参</strong>：无（clientId 由服务端按时间戳生成）。
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * curl -N http://localhost:8080/sse
     * # event:tick
     * # data:jetty-sse connected
     * # (15s 后)
     * # event:ping
     * # data:{}  (空心跳)
     * }</pre>
     * <p><strong>观察指标</strong>：{@code GET /actuator/metrics/web.sse.connections} gauge 表示活跃连接数。
     * <p><strong>关闭连接</strong>：客户端断开 → {@code SseManager} 自动 cleanup（{@code DisconnectReason.COMPLETION}）。
     */
    @GetMapping("/sse")
    public SseEmitter sse() {
        String clientId = "jetty-client-" + System.nanoTime();
        SseEmitter emitter = sseManager.connect(clientId);
        sseManager.send(clientId, SseEvent.of("tick", "jetty-sse connected"));
        return emitter;
    }

    /**
     * 测试 HangDetection 三档阈值链。
     * <p>阈值：{@code warn-ms=1000} / {@code dump-ms=5000} / {@code kill-switch-ms=30000}。
     * <p><strong>入参</strong>：
     * <ul>
     *   <li>{@code millis}（query, 默认 6000）—— controller 主动 sleep 的毫秒数</li>
     *   <li>{@code X-Client-Id} —— 同上</li>
     * </ul>
     * <p><strong>三档触发演示</strong>：
     * <ol>
     *   <li>{@code ?millis=2000} —— 超过 1s 触发 warn 日志（不 dump）</li>
     *   <li>{@code ?millis=6000}（默认）—— 超过 5s 触发 dump（jstack 线程栈写入日志）</li>
     *   <li>{@code ?millis=35000} —— 超过 30s 触发 kill-switch（{@code INTERRUPTED} 提前返回）</li>
     * </ol>
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * time curl -s -H 'X-Client-Id: alice' "http://localhost:8080/slow?millis=35000"
     * # 控制台应见：HangDetection 拦截日志 + INTERRUPTED 错误响应（远早于 35s）
     * }</pre>
     */
    @GetMapping("/slow")
    public ApiResult<String> slow(@RequestParam(defaultValue = "6000") long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.error("INTERRUPTED", "请求被中断（kill-switch）");
        }
        return ApiResult.success("jetty slow-response");
    }

    /**
     * 测试 BruteForce 防爆破（5 次/60s 锁定 60s）。
     * <p><strong>入参</strong>：
     * <ul>
     *   <li>{@code username}（query, 必填）—— 用户名（同一 username 累计失败）</li>
     *   <li>{@code password}（query, 必填）—— 密码；非 {@code "ok"} 视为失败</li>
     *   <li>{@code X-Client-Id} —— 同上</li>
     * </ul>
     * <p><strong>锁定触发流程</strong>：
     * <ol>
     *   <li>同一 username 连发 5 次错误密码 → 第 6 次 429 BRUTE_FORCE</li>
     *   <li>锁定 60s 内即使密码正确也拒绝</li>
     *   <li>60s 后自动解锁</li>
     * </ol>
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * for i in $(seq 1 6); do
     *   curl -s -o /dev/null -w "%{http_code}\n" \
     *        -H 'X-Client-Id: alice' \
     *        "http://localhost:8080/login?username=evil&password=wrong"
     * done      # 前 5 行 500，最后 1 行 429
     * }</pre>
     */
    @PostMapping("/login")
    public ApiResult<String> login(@RequestParam String username, @RequestParam String password) {
        if (!"ok".equals(password)) {
            throw new RuntimeException("bad credentials for " + username);
        }
        return ApiResult.success("jetty login-token=" + UUID.randomUUID());
    }

    /**
     * 测试 AnomalyDetection Bot UA 拦截（UA 命中 {@code ["curl/*"]} 模式）。
     * <p><strong>入参</strong>：
     * <ul>
     *   <li>{@code User-Agent} 头 —— Bot 列表中的 UA → 403 BOT_DETECTED</li>
     *   <li>{@code X-Client-Id} —— 同上</li>
     * </ul>
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * # curl 默认 UA 是 'curl/x.y.z' → 触发
     * curl -H 'X-Client-Id: alice' http://localhost:8080/bot-detect        # 403 BOT_DETECTED
     * # 自定义 UA 模拟浏览器 → 不触发
     * curl -H 'X-Client-Id: alice' -H 'User-Agent: Mozilla/5.0' \
     *      http://localhost:8080/bot-detect                                # 200
     * }</pre>
     * <p>生产环境建议把 {@code bot-user-agents} 扩成 {@code ["curl/*", "wget/*", "python-requests/*", "Go-http-client/*", ...]}。
     */
    @GetMapping("/bot-detect")
    public ApiResult<String> botDetect(@RequestHeader(value = "User-Agent", required = false) String ua) {
        return ApiResult.success("jetty ua=" + (ua == null ? "unknown" : ua));
    }

    /**
     * Smoke test 端点（验证 Jetty alive + dispatcher 正常分发 + web-core 拦截器链未短路）。
     * <p><strong>路径选择</strong>：{@code /app-info} 避免与 Spring Boot Actuator 内置
     * {@code /actuator/info} endpoint 冲突；后者优先级更高，原 {@code /actuator/info} 路径
     * 会导致本 controller 方法被 shadow，curl 始终拿到 Actuator 默认响应（{@code {}}）。
     * <p><strong>curl 示例</strong>：
     * <pre>{@code
     * curl http://localhost:8080/app-info
     * # {"success":true,"data":"Sample web app on Jetty 12 — 验证 web-core + web-jetty"}
     * }</pre>
     */
    @GetMapping("/app-info")
    public ApiResult<String> info() {
        return ApiResult.success("Sample web app on Jetty 12 — 验证 web-core + web-jetty");
    }
}