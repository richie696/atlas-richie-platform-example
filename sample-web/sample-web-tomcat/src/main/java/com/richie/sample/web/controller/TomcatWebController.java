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

import java.time.Duration;

/**
 * sample-web-tomcat 控制器 —— 在 Embedded Tomcat 上验证 web-core + web-tomcat 生产调优。
 * 端点覆盖：KeyResolver / RateLimit / CircuitBreaker / AnomalyDetection /
 *         BruteForce / SSE / HangDetection / Micrometer 指标。
 */
@Slf4j
@RestController
public class TomcatWebController {

    private final SseManager sseManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public TomcatWebController(SseManager sseManager,
                              CircuitBreakerRegistry circuitBreakerRegistry) {
        this.sseManager = sseManager;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/hello")
    public ApiResult<String> hello(@RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        return ApiResult.success("hello-tomcat, " + (clientId == null ? "anonymous" : clientId));
    }

    @GetMapping("/rate-limit")
    public ApiResult<String> rateLimit() {
        return ApiResult.success("tomcat rate-limit ok");
    }

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
            return ApiResult.success("tomcat payment-" + id + " (amount=199.00)");
        });
    }

    @GetMapping("/sse")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter sse() {
        String clientId = "tomcat-client-" + System.nanoTime();
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = sseManager.connect(clientId);
        sseManager.send(clientId, SseEvent.of("tick", "tomcat-sse connected"));
        return emitter;
    }

    @GetMapping("/slow")
    public ApiResult<String> slow(@RequestParam(defaultValue = "6000") long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.error("INTERRUPTED", "请求被中断（kill-switch）");
        }
        return ApiResult.success("tomcat slow-response");
    }

    @PostMapping("/login")
    public ApiResult<String> login(@RequestParam String username, @RequestParam String password) {
        if (!"ok".equals(password)) {
            throw new RuntimeException("bad credentials for " + username);
        }
        return ApiResult.success("tomcat login-token=" + java.util.UUID.randomUUID());
    }

    @GetMapping("/bot-detect")
    public ApiResult<String> botDetect(@RequestHeader(value = "User-Agent", required = false) String ua) {
        return ApiResult.success("tomcat ua=" + (ua == null ? "unknown" : ua));
    }

    @GetMapping("/actuator/info")
    public ApiResult<String> info() {
        return ApiResult.success("Sample web app on Tomcat — 验证 web-core + web-tomcat");
    }
}