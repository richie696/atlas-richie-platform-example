package com.richie.component.concurrency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 并发组件示例工程启动类。
 *
 * <p>本应用作为 {@code atlas-richie-component-concurrency} 组件的端到端演示入口,启动后由
 * {@link SampleOrchestrator} 顺序触发 9 个 demo:</p>
 *
 * <ol>
 *   <li>{@code StructuredConcurrencySample} — 结构化并发 8 种模式</li>
 *   <li>{@code VirtualThreadFactorySample} — 虚拟线程命名 + ScopedValue</li>
 *   <li>{@code BatchProcessorSample} — 批量并行处理</li>
 *   <li>{@code RetryerSample} — 指数退避重试</li>
 *   <li>{@code RateLimiterSample} — 令牌桶限流</li>
 *   <li>{@code CircuitBreakerSample} — 三态熔断</li>
 *   <li>{@code DebouncerSample} — 防抖</li>
 *   <li>{@code DynamicExecutorSample} ⭐ — 动态线程池(本组件核心能力)</li>
 *   <li>{@code RegistrySample} — 按 key 注册中心</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-07
 */
@SpringBootApplication
public class SampleConcurrencyApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SampleConcurrencyApplication.class, args);
    }
}