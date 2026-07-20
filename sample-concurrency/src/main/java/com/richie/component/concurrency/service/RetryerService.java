package com.richie.component.concurrency.service;

import com.richie.component.concurrency.algorithm.RetryExhaustedException;
import com.richie.component.concurrency.algorithm.Retryer;
import com.richie.component.concurrency.measurement.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指数退避重试器演示 —— 覆盖 {@link Retryer} 的全部执行入口与配置项。
 *
 * <p>{@link Retryer} 在指数退避基础上可选 jitter,默认仅重试 {@link RuntimeException},
 * 通过 {@code retryOn(...)} 可指定异常类型(如 {@link IOException}),通过
 * {@code execute(task, fallback)} 可避免重试耗尽时抛 {@link RetryExhaustedException}。</p>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class RetryerService {

    private static final Logger log = LoggerFactory.getLogger(RetryerService.class);

    /**
     * 演示:
     * <ol>
     *   <li>{@code execute(Callable)} 最终成功</li>
     *   <li>{@code execute(Callable, fallback)} 失败兜底</li>
     *   <li>{@code execute(Runnable)} 无返回值</li>
     *   <li>jitter 对退避时长的影响</li>
     * </ol>
     */
    public void demo() {
        log.info("[1/4] execute(Callable) — 第 3 次成功");
        AtomicInteger attempt1 = new AtomicInteger(0);
        Stopwatch sw1 = Stopwatch.createStarted();
        String r1 = Retryer.of(Duration.ofMillis(50))
                .maxAttempts(5)
                .maxBackoff(Duration.ofMillis(800))
                .jitter(true)
                .retryOn(IOException.class)
                .execute((Callable<String>) () -> {
                    int n = attempt1.incrementAndGet();
                    if (n < 3) {
                        throw new IOException("transient-" + n);
                    }
                    return "OK-on-" + n;
                });
        sw1.stop();
        log.info("    -> result={}, total attempts={}, elapsed={}", r1, attempt1.get(), sw1);

        log.info("[2/4] execute(Callable, fallback) — 耗尽后返回 fallback");
        AtomicInteger attempt2 = new AtomicInteger(0);
        Stopwatch sw2 = Stopwatch.createStarted();
        String r2 = Retryer.of(Duration.ofMillis(30))
                .maxAttempts(3)
                .maxBackoff(Duration.ofMillis(200))
                .execute((Callable<String>) () -> {
                    attempt2.incrementAndGet();
                    throw new RuntimeException("always-fail");
                }, "DEFAULT-FALLBACK");
        sw2.stop();
        log.info("    -> fallback={}, attempts={}, elapsed={}", r2, attempt2.get(), sw2);

        log.info("[3/4] execute(Runnable) — 重试到第 2 次成功");
        AtomicInteger attempt3 = new AtomicInteger(0);
        Stopwatch sw3 = Stopwatch.createStarted();
        Retryer.of(Duration.ofMillis(40))
                .maxAttempts(4)
                .maxBackoff(Duration.ofMillis(500))
                .execute((Runnable) () -> {
                    int n = attempt3.incrementAndGet();
                    if (n < 2) {
                        throw new RuntimeException("flaky-" + n);
                    }
                    log.info("    -> runnable done on attempt {}", n);
                });
        sw3.stop();
        log.info("    -> total attempts={}, elapsed={}", attempt3.get(), sw3);

        log.info("[4/4] jitter=false vs jitter=true — 对比退避时长");
        Stopwatch sw4a = Stopwatch.createStarted();
        try {
            Retryer.of(Duration.ofMillis(50))
                    .maxAttempts(4)
                    .maxBackoff(Duration.ofMillis(400))
                    .jitter(false)
                    .retryOn(RuntimeException.class)
                    .execute((Callable<Void>) () -> {
                        throw new RuntimeException("never-ok");
                    });
        } catch (RetryExhaustedException e) {
            sw4a.stop();
            log.info("    -> jitter=false exhausted after {} (no jitter scatter)", sw4a);
        }
        Stopwatch sw4b = Stopwatch.createStarted();
        try {
            Retryer.of(Duration.ofMillis(50))
                    .maxAttempts(4)
                    .maxBackoff(Duration.ofMillis(400))
                    .jitter(true)
                    .retryOn(RuntimeException.class)
                    .execute((Callable<Void>) () -> {
                        throw new RuntimeException("never-ok");
                    });
        } catch (RetryExhaustedException e) {
            sw4b.stop();
            log.info("    -> jitter=true exhausted after {} (含 ±jitter scatter)", sw4b);
        }
    }
}