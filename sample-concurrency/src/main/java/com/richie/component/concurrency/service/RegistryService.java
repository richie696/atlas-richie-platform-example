package com.richie.component.concurrency.service;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.registry.CircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultCircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultRateLimiterRegistry;
import com.richie.component.concurrency.registry.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Registry 演示 —— 按 key 缓存的 {@link RateLimiter} / {@link CircuitBreaker} 注册中心。
 *
 * <p>单例 Bean 形式的 {@code RateLimiter} / {@code CircuitBreaker} 只能做"全局限流 / 全局熔断",
 * 但拦截器层通常需要"按 clientKey / IP 独立"。本组件的 {@link RateLimiterRegistry} /
 * {@link CircuitBreakerRegistry} 即为此场景设计。</p>
 *
 * <h2>并发安全</h2>
 * <p>底层 {@code ConcurrentHashMap.computeIfAbsent} 保证同一 key 仅创建一次,跨线程安全。</p>
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li>{@code remove(key)} 仅从缓存中移除,调用方负责 {@code close()} /
 *       {@code reset()} 释放资源</li>
 *   <li>{@code clear()} 清空但不释放内部实例,主要用于测试</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class RegistryService {

    private static final Logger log = LoggerFactory.getLogger(RegistryService.class);

    /**
     * 演示两个注册中心的全部 API。
     */
    public void demo() {
        log.info("[1/4] RateLimiterRegistry.getOrCreate — 首次按 key 创建,后续返回同一实例");
        rateLimiterShowcase();

        log.info("[2/4] find / remove / size / keys — 运行时管理");
        rateLimiterLifecycleShowcase();

        log.info("[3/4] CircuitBreakerRegistry.getOrCreate — 按 clientKey 独立熔断统计");
        circuitBreakerShowcase();

        log.info("[4/4] clear() — 测试与运维场景");
        clearShowcase();
    }

    private void rateLimiterShowcase() {
        RateLimiterRegistry registry = new DefaultRateLimiterRegistry();

        // 工厂:key → RateLimiter。getOrCreate 第一次调用时执行 factory,后续命中缓存
        RateLimiter alice = registry.getOrCreate("client-alice", k -> RateLimiter.ofTokensPerSecond(10));
        RateLimiter alice2 = registry.getOrCreate("client-alice", k -> {
            log.info("    -> [factory should NOT be called again]");
            return RateLimiter.ofTokensPerSecond(999);
        });
        RateLimiter bob = registry.getOrCreate("client-bob", k -> RateLimiter.ofTokensPerSecond(20));

        log.info("    alice == alice2 ? {} (期望 true,缓存命中)", alice == alice2);
        log.info("    alice != bob     ? {} (期望 true,不同 key)", alice != bob);
        log.info("    registry.size()  = {}", registry.size());
        log.info("    registry.keys()  = {}", registry.keys());
        log.info("    alice.availablePermits() = {} (初始桶满 10)", alice.availablePermits());
    }

    private void rateLimiterLifecycleShowcase() {
        RateLimiterRegistry registry = new DefaultRateLimiterRegistry();
        registry.getOrCreate("user-1", k -> RateLimiter.ofTokensPerSecond(1));
        registry.getOrCreate("user-2", k -> RateLimiter.ofTokensPerSecond(1));
        registry.getOrCreate("user-3", k -> RateLimiter.ofTokensPerSecond(1));
        log.info("    -> 初始化 size={}, keys={}", registry.size(), registry.keys());

        // find
        Optional<RateLimiter> found = registry.find("user-2");
        log.info("    -> find('user-2') present={}", found.isPresent());

        // remove: 调用方负责 close
        Optional<RateLimiter> removed = registry.remove("user-1");
        if (removed.isPresent()) {
            removed.get().close();
        }
        log.info("    -> remove('user-1') 后 size={}, keys={}", registry.size(), registry.keys());
    }

    private void circuitBreakerShowcase() {
        CircuitBreakerRegistry registry = new DefaultCircuitBreakerRegistry();

        // 不同 clientKey 独立统计失败率,实现"某租户熔断但不影响其他租户"
        CircuitBreaker tenantA = registry.getOrCreate("tenant-A",
                k -> CircuitBreaker.ofRate(50, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(3)));
        CircuitBreaker tenantB = registry.getOrCreate("tenant-B",
                k -> CircuitBreaker.ofDefaults());

        // 触发 tenant-A 连续失败 — ofRate 的窗口至少 10 次,需填满窗口才能 100% 触发熔断
        for (int i = 0; i < 12; i++) {
            try {
                tenantA.execute(() -> { throw new RuntimeException("forced failure"); }, "FALLBACK");
            } catch (Exception ignored) {}
        }
        log.info("    -> tenant-A 状态={} (期望 OPEN,触发熔断)", tenantA.state());

        // tenant-B 不受影响
        log.info("    -> tenant-B 状态={} (期望 CLOSED,独立统计)", tenantB.state());

        // remove 后状态保留 —— Registry 不自动 reset,符合"调用方自行决定生命周期"
        registry.remove("tenant-A").ifPresent(CircuitBreaker::reset);
        log.info("    -> remove tenant-A 后,registry size={}, keys={}", registry.size(), registry.keys());
    }

    private void clearShowcase() {
        RateLimiterRegistry registry = new DefaultRateLimiterRegistry();
        registry.getOrCreate("k1", k -> RateLimiter.ofTokensPerSecond(1));
        registry.getOrCreate("k2", k -> RateLimiter.ofTokensPerSecond(1));
        log.info("    -> clear 前 size={}", registry.size());
        registry.clear();
        log.info("    -> clear 后 size={}, keys={} (注意:内部实例未 close,生产慎用)",
                registry.size(), registry.keys());
    }
}