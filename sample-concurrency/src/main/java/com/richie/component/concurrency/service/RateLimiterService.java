package com.richie.component.concurrency.service;

import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.measurement.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 令牌桶限流器演示 —— 覆盖 {@link RateLimiter} 的三档等待语义与多 token 接口。
 *
 * <p>{@link RateLimiter} 由 Spring Boot 自动装配(本示例工程启用了
 * {@code platform.concurrency.rate-limiter.enabled=true},{@code permits-per-second=5})。</p>
 *
 * <h2>三档等待语义</h2>
 * <ul>
 *   <li>{@code tryAcquire()} / {@code tryAcquire(int)} — 非阻塞,返回 boolean</li>
 *   <li>{@code tryAcquire(Duration)} / {@code tryAcquire(int, Duration)} — 限时阻塞</li>
 *   <li>{@code acquire()} / {@code acquire(int)} — 无限阻塞(可中断)</li>
 *   <li>{@code acquireUninterruptibly(int)} — 无限阻塞 + 不可中断</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimiter springRateLimiter;

    public RateLimiterService(RateLimiter rateLimiter) {
        this.springRateLimiter = rateLimiter;
    }

    /**
     * 演示限流器 6 种典型用法。
     */
    public void demo() {
        log.info("[1/6] Spring 装配的 RateLimiter 单例: availablePermits={}", springRateLimiter.availablePermits());
        log.info("[2/6] 编程式创建独立 RateLimiter(20 token/s)");
        try (RateLimiter rl = RateLimiter.ofTokensPerSecond(20)) {
            log.info("    -> created with availablePermits={}", rl.availablePermits());

            log.info("[3/6] tryAcquire() 非阻塞消费 5 个 token");
            for (int i = 0; i < 5; i++) {
                boolean ok = rl.tryAcquire();
                log.info("    -> tryAcquire #{}: {}, remaining={}", i + 1, ok, rl.availablePermits());
            }

            log.info("[4/6] tryAcquire(Duration) — 限时阻塞");
            Stopwatch sw = Stopwatch.createStarted();
            boolean got = rl.tryAcquire(Duration.ofMillis(200));
            sw.stop();
            log.info("    -> got={} (after {}), remaining={}", got, sw, rl.availablePermits());

            log.info("[5/6] tryAcquire(3, Duration) — 一次性拿 3 个 token");
            boolean got3 = rl.tryAcquire(3, Duration.ofSeconds(1));
            log.info("    -> got3={}, remaining={}", got3, rl.availablePermits());

            log.info("[6/6] acquire() 阻塞直到拿到 token(可中断)");
            Stopwatch sw2 = Stopwatch.createStarted();
            try {
                rl.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            sw2.stop();
            log.info("    -> acquire() returned after {}, remaining={}", sw2, rl.availablePermits());
        }
        log.info("[extra] Spring 单例限流器在 demo 期间继续令牌补充, remaining={}", springRateLimiter.availablePermits());

        // 额外演示:多个并发线程抢令牌,观察补充节奏
        log.info("[extra] 并发 8 线程抢令牌,每个 take 1 token");
        try (RateLimiter rl = RateLimiter.ofTokensPerSecond(8)) {
            Thread[] ts = new Thread[8];
            Stopwatch[] clocks = new Stopwatch[8];
            for (int i = 0; i < ts.length; i++) {
                clocks[i] = Stopwatch.createStarted();
                final int idx = i;
                ts[i] = new Thread(() -> {
                    try {
                        rl.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    clocks[idx].stop();
                    log.info("    -> worker-{} got token after {}", idx, clocks[idx]);
                }, "rate-worker-" + i);
            }
            for (Thread t : ts) t.start();
            for (Thread t : ts) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}