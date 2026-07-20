package com.richie.component.concurrency.service;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.CircuitBreakerOpenException;
import com.richie.component.concurrency.algorithm.CircuitBreaker.State;
import com.richie.component.concurrency.algorithm.CircuitBreaker.SlidingWindowType;
import com.richie.component.concurrency.measurement.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器演示 —— 覆盖 {@link CircuitBreaker} 三态机 CLOSED → OPEN → HALF_OPEN 全流程,
 * 以及 {@code execute} / {@code execute(fallback)} / {@code executeOrThrow} 三种调用入口,
 * 以及 {@code reset} / {@code forceOpen} 手动控制。
 *
 * <p>本 demo 使用独立的编程式构造的 {@link CircuitBreaker}(更易观察状态变化),
 * 同时也演示 Spring 自动装配的单例熔断器(配置: {@code failureRateThreshold=0.5},
 * {@code slidingWindowSize=5}, {@code waitDuration=3s})。</p>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final CircuitBreaker springCircuitBreaker;

    public CircuitBreakerService(CircuitBreaker circuitBreaker) {
        this.springCircuitBreaker = circuitBreaker;
    }

    /**
     * 演示熔断器全生命周期:正常调用 → 触发 OPEN → 等待 → HALF_OPEN 探测 → 恢复 CLOSED。
     */
    public void demo() throws Exception {
        log.info("[1/5] 编程式构造: slidingWindowSize=10, failurePercent=50%, openDuration=2s");
        CircuitBreaker cb = CircuitBreaker.builder()
                .windowSize(10)
                .failurePercent(50)
                .openDuration(Duration.ofSeconds(2))
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .build();
        AtomicInteger upstream = new AtomicInteger(0);

        log.info("[2/5] 连续 10 次失败调用(填满窗口,失败率 100% 触发 OPEN)");
        for (int i = 0; i < 10; i++) {
            try {
                cb.executeOrThrow(() -> {
                    upstream.incrementAndGet();
                    throw new RuntimeException("upstream-down-" + upstream.get());
                });
            } catch (RuntimeException e) {
                log.info("    -> call #{} failed: {}, cb.state={}", i + 1, e.toString(), cb.state());
            }
        }
        log.info("    -> 期望 state=OPEN, actual state={}", cb.state());

        log.info("[3/5] OPEN 期间 execute(fallback) — 不阻塞,立即降级");
        String fallback = cb.execute(
                () -> "real-result",
                "FALLBACK-DUE-TO-OPEN");
        log.info("    -> fallback value: {}", fallback);

        log.info("[4/5] waitDuration=2s 过期后自动转 HALF_OPEN — 下一次调用作探测");
        Stopwatch sw = Stopwatch.createStarted();
        // 模拟外部依赖恢复
        upstream.set(0);
        for (int i = 0; i < 3; i++) {
            // 等待直至 HALF_OPEN
            while (cb.state() == State.OPEN) {
                Thread.sleep(100);
            }
            sw.stop();
            int n = i + 1;
            log.info("    -> call #{} after {}: state before={}", n, sw, cb.state());
            String result = cb.execute(() -> "real-result-" + upstream.incrementAndGet());
            log.info("    -> call #{}: result={}, state after={}", n, result, cb.state());
        }
        log.info("    -> 多次成功后回到 CLOSED, current state={}", cb.state());

        log.info("[5/5] forceOpen() 手动触发 OPEN + reset() 强制恢复 CLOSED");
        cb.forceOpen();
        log.info("    -> forceOpen 后 state={}", cb.state());
        cb.reset();
        log.info("    -> reset 后 state={}", cb.state());

        // 演示 Spring 装配的单例(共享给其他业务)
        log.info("[extra] Spring 单例熔断器初始 state={}", springCircuitBreaker.state());
        log.info("[extra] 对其 executeOrThrow 调用一次(必定成功,统计窗口仍为空)");
        try {
            String ok = springCircuitBreaker.executeOrThrow(() -> "spring-cb-ok");
            log.info("    -> ok={}, state={}", ok, springCircuitBreaker.state());
        } catch (CircuitBreakerOpenException e) {
            log.info("    -> 被熔断(预期): {}", e.getMessage());
        }
    }
}