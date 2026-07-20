package com.richie.component.concurrency.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.richie.component.concurrency.service.BatchProcessorService;
import com.richie.component.concurrency.service.CircuitBreakerService;
import com.richie.component.concurrency.service.DebouncerService;
import com.richie.component.concurrency.service.DynamicExecutorService;
import com.richie.component.concurrency.service.RateLimiterService;
import com.richie.component.concurrency.service.RegistryService;
import com.richie.component.concurrency.service.RetryerService;
import com.richie.component.concurrency.service.StructuredConcurrencyService;
import com.richie.component.concurrency.service.VirtualThreadFactoryService;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sample demo HTTP 控制器 —— 通过 REST 端点触发 9 个 demo。
 *
 * <p>每个端点临时把 root logger 的输出重定向到 {@link ListAppender},
 * demo 运行结束后把捕获到的日志文本作为响应体返回(text/plain;charset=UTF-8),
 * 便于在浏览器或 curl 中直接查看 demo 完整输出。</p>
 *
 * <h2>端点列表</h2>
 * <table>
 *   <caption>Demo 端点映射</caption>
 *   <tr><th>HTTP</th><th>对应 demo</th></tr>
 *   <tr><td>GET /demo/structured-concurrency</td><td>结构化并发 8 种 API</td></tr>
 *   <tr><td>GET /demo/virtual-thread-factory</td><td>虚拟线程工厂 + ScopedValue</td></tr>
 *   <tr><td>GET /demo/batch-processor</td><td>批处理器 forEach / mapParallel</td></tr>
 *   <tr><td>GET /demo/retryer</td><td>指数退避重试 + jitter + fallback</td></tr>
 *   <tr><td>GET /demo/rate-limiter</td><td>令牌桶限流 + 多 token + 8 worker 并发</td></tr>
 *   <tr><td>GET /demo/circuit-breaker</td><td>熔断器三态机</td></tr>
 *   <tr><td>GET /demo/debouncer</td><td>防抖</td></tr>
 *   <tr><td>GET /demo/dynamic-executor</td><td>动态线程池(本组件核心能力)</td></tr>
 *   <tr><td>GET /demo/registry</td><td>按 key 注册中心</td></tr>
 *   <tr><td>GET /demo/all</td><td>顺序运行全部 demo</td></tr>
 *   <tr><td>GET /demo/health</td><td>健康检查(返回 "OK")</td></tr>
 * </table>
 *
 * @author richie696
 * @since 2026-07
 */
@RestController
@RequestMapping("/demo")
public class ConcurrencyDemoController {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ConcurrencyDemoController.class);

    private final StructuredConcurrencyService structuredConcurrency;
    private final VirtualThreadFactoryService virtualThreadFactory;
    private final BatchProcessorService batchProcessor;
    private final RetryerService retryer;
    private final RateLimiterService rateLimiter;
    private final CircuitBreakerService circuitBreaker;
    private final DebouncerService debouncer;
    private final DynamicExecutorService dynamicExecutor;
    private final RegistryService registry;

    public ConcurrencyDemoController(StructuredConcurrencyService structuredConcurrency,
                          VirtualThreadFactoryService virtualThreadFactory,
                          BatchProcessorService batchProcessor,
                          RetryerService retryer,
                          RateLimiterService rateLimiter,
                          CircuitBreakerService circuitBreaker,
                          DebouncerService debouncer,
                          DynamicExecutorService dynamicExecutor,
                          RegistryService registry) {
        this.structuredConcurrency = structuredConcurrency;
        this.virtualThreadFactory = virtualThreadFactory;
        this.batchProcessor = batchProcessor;
        this.retryer = retryer;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.debouncer = debouncer;
        this.dynamicExecutor = dynamicExecutor;
        this.registry = registry;
    }

    /**
     * 健康检查端点 —— 返回 "OK",用于确认容器在线。
     */
    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    public String health() {
        return "OK\n";
    }

    @GetMapping(value = "/structured-concurrency", produces = "text/plain;charset=UTF-8")
    public String runStructuredConcurrency() throws Exception {
        return capture(structuredConcurrency::demo);
    }

    @GetMapping(value = "/virtual-thread-factory", produces = "text/plain;charset=UTF-8")
    public String runVirtualThreadFactory() throws Exception {
        return capture(virtualThreadFactory::demo);
    }

    @GetMapping(value = "/batch-processor", produces = "text/plain;charset=UTF-8")
    public String runBatchProcessor() throws Exception {
        return capture(batchProcessor::demo);
    }

    @GetMapping(value = "/retryer", produces = "text/plain;charset=UTF-8")
    public String runRetryer() throws Exception {
        return capture(retryer::demo);
    }

    @GetMapping(value = "/rate-limiter", produces = "text/plain;charset=UTF-8")
    public String runRateLimiter() throws Exception {
        return capture(rateLimiter::demo);
    }

    @GetMapping(value = "/circuit-breaker", produces = "text/plain;charset=UTF-8")
    public String runCircuitBreaker() throws Exception {
        return capture(circuitBreaker::demo);
    }

    @GetMapping(value = "/debouncer", produces = "text/plain;charset=UTF-8")
    public String runDebouncer() throws Exception {
        return capture(debouncer::demo);
    }

    @GetMapping(value = "/dynamic-executor", produces = "text/plain;charset=UTF-8")
    public String runDynamicExecutor() throws Exception {
        return capture(dynamicExecutor::demo);
    }

    // ----------------------------------------------------------------
    // DynamicExecutor 交互式端点(可通过 curl 传参测试运行时能力)
    // ----------------------------------------------------------------

    /**
     * 提交 N 个 sleep 任务到指定 Spring 注入的线程池。
     * 用法:POST /demo/dynamic-executor/{poolName}/submit?count=20&sleepMs=100
     */
    @PostMapping(value = "/dynamic-executor/{poolName}/submit", produces = "text/plain;charset=UTF-8")
    public String submitTasks(@PathVariable String poolName,
                              @RequestParam(defaultValue = "10") int count,
                              @RequestParam(defaultValue = "50") long sleepMs) throws Exception {
        return capture(() -> {
            try {
                dynamicExecutor.submitTasks(poolName, count, sleepMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 热更新指定 Spring 注入线程池的核心参数。
     * 用法:POST /demo/dynamic-executor/{poolName}/resize?core=4&max=8&keepAliveMs=60000
     */
    @PostMapping(value = "/dynamic-executor/{poolName}/resize", produces = "text/plain;charset=UTF-8")
    public String resize(@PathVariable String poolName,
                         @RequestParam(required = false) Integer core,
                         @RequestParam(required = false) Integer max,
                         @RequestParam(required = false) Long keepAliveMs) throws Exception {
        return capture(() -> {
            try {
                dynamicExecutor.resize(poolName, core, max, keepAliveMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 读取指定线程池的 9 字段快照。
     * 用法:GET /demo/dynamic-executor/{poolName}/snapshot
     */
    @GetMapping(value = "/dynamic-executor/{poolName}/snapshot", produces = "text/plain;charset=UTF-8")
    public String snapshot(@PathVariable String poolName) {
        return dynamicExecutor.snapshot(poolName);
    }

    /**
     * 触发指定线程池的拒绝策略(超额任务)。
     * 用法:POST /demo/dynamic-executor/{poolName}/reject-test?count=100
     */
    @PostMapping(value = "/dynamic-executor/{poolName}/reject-test", produces = "text/plain;charset=UTF-8")
    public String triggerReject(@PathVariable String poolName,
                                @RequestParam(defaultValue = "100") int count) throws Exception {
        return capture(() -> {
            try {
                dynamicExecutor.triggerReject(poolName, count);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 编程式创建独立 DynamicExecutor 并提交任务(脱离 Spring 容器)。
     * 用法:POST /demo/dynamic-executor/adhoc?core=2&max=4&keepAliveMs=30000&queue=10&count=20&sleepMs=200
     */
    @PostMapping(value = "/dynamic-executor/adhoc", produces = "text/plain;charset=UTF-8")
    public String createAdhoc(@RequestParam(defaultValue = "2") int core,
                              @RequestParam(defaultValue = "4") int max,
                              @RequestParam(defaultValue = "30000") long keepAliveMs,
                              @RequestParam(defaultValue = "10") int queue,
                              @RequestParam(defaultValue = "10") int count,
                              @RequestParam(defaultValue = "100") long sleepMs) throws Exception {
        return capture(() -> {
            try {
                dynamicExecutor.createAdhoc(core, max, keepAliveMs, queue, count, sleepMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @GetMapping(value = "/registry", produces = "text/plain;charset=UTF-8")
    public String runRegistry() throws Exception {
        return capture(registry::demo);
    }

    /**
     * 一键跑全部 demo —— 端到端验证场景,适合 CI 烟测。
     */
    @GetMapping(value = "/all", produces = "text/plain;charset=UTF-8")
    public String runAll() throws Exception {
        return capture(structuredConcurrency::demo) +
                "\n\n--- next: VirtualThreadFactory ---\n\n" +
                capture(virtualThreadFactory::demo) +
                "\n\n--- next: BatchProcessor ---\n\n" +
                capture(batchProcessor::demo) +
                "\n\n--- next: Retryer ---\n\n" +
                capture(retryer::demo) +
                "\n\n--- next: RateLimiter ---\n\n" +
                capture(rateLimiter::demo) +
                "\n\n--- next: CircuitBreaker ---\n\n" +
                capture(circuitBreaker::demo) +
                "\n\n--- next: Debouncer ---\n\n" +
                capture(debouncer::demo) +
                "\n\n--- next: DynamicExecutor ---\n\n" +
                capture(dynamicExecutor::demo) +
                "\n\n--- next: Registry ---\n\n" +
                capture(registry::demo);
    }

    /**
     * 在 demo 运行期间把 root logger 的输出重定向到 {@link ListAppender},
     * 结束后把累积日志作为 String 返回(去除 logger 前缀,保留 message + level)。
     *
     * <p>用 {@link Supplier} 接口适配 {@code throws Exception} 的 demo 方法签名,
     * 通过 {@link ExceptionThrower} 桥接重新抛出。</p>
     */
    private static String capture(ExceptionThrower task) throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            task.run();
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
        List<ILoggingEvent> events = appender.list;
        StringBuilder sb = new StringBuilder(events.size() * 64);
        for (ILoggingEvent e : events) {
            Level level = e.getLevel();
            String message = e.getFormattedMessage();
            sb.append('[').append(level).append("] ").append(message).append('\n');
        }
        return sb.toString();
    }

    /**
     * 函数式接口 —— 适配 {@code throws Exception} 的 demo 方法。
     */
    @FunctionalInterface
    private interface ExceptionThrower {
        void run() throws Exception;
    }
}