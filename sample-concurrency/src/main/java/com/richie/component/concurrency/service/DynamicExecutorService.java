package com.richie.component.concurrency.service;

import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.PoolResizeEvent;
import com.richie.component.concurrency.threadpool.PoolStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 动态线程池演示 ⭐ —— {@code atlas-richie-component-concurrency} 组件的核心能力。
 *
 * <p>{@link DynamicExecutor} 在 JDK 标准 {@link ThreadPoolExecutor} 之上增加四类增强:</p>
 * <ol>
 *   <li><b>热更新</b> —— {@link #onResize(PoolResizeEvent)} 运行时调整 corePoolSize /
 *       maximumPoolSize / keepAliveTime / rejectedHandler,无需重启</li>
 *   <li><b>指标快照</b> —— {@link #snapshot()} 返回不可变的 {@link PoolStatus},9 字段覆盖池容量、
 *       活动线程、队列、累计任务、拒绝计数等核心观测点</li>
 *   <li><b>拒绝计数</b> —— 透明包装用户 RejectedExecutionHandler,内部累加拒绝次数,
 *       通过 {@link #getRejectionCount()} 暴露,可通过 {@link #resetRejectionCount()} 归零</li>
 *   <li><b>透明语义保留</b> —— {@link #setRejectedExecutionHandler} 自动包装用户 handler,
 *       {@link #getRejectedExecutionHandler} 始终返回用户原始 handler(不暴露包装器)</li>
 * </ol>
 *
 * <p>本 demo 同时演示:</p>
 * <ul>
 *   <li>4 个构造器重载(5/6/7 参数)</li>
 *   <li>Spring 多池注入的三种方式(@Resource / @Qualifier + @Autowired / Map)</li>
 *   <li>编程式创建(脱离 Spring 容器,用于嵌入式场景)</li>
 *   <li>{@link PoolResizeEvent.Builder} 部分字段更新语义(null 字段表示"不调整")</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class DynamicExecutorService {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutorService.class);

    /**
     * 方式一:按 bean name 注入指定线程池。
     * <p>Spring 注册时以 {@code platform.concurrency.thread-pools.<name>} 的 key 作为 bean name,
     * 此处 {@code order-executor} 对应 application.yml 中定义的 {@code order-executor} 池。</p>
     */
    @Resource(name = "order-executor")
    private DynamicExecutor orderExecutor;

    /**
     * 方式二:按限定符注入,语义更显式(等价于方式一)。
     */
    @Qualifier("notification-executor")
    @Autowired
    private DynamicExecutor notificationExecutor;

    /**
     * 方式三:一次性注入全部动态线程池,key = pool name。
     */
    @Autowired
    private Map<String, DynamicExecutor> allExecutors;

    /**
     * 注入完毕后输出池清单,供后续 demo 交叉对比。
     */
    @PostConstruct
    void dumpPools() {
        log.info("[init] Spring 注入完成,自动注册的 DynamicExecutor 共 {} 个:", allExecutors.size());
        allExecutors.forEach((name, pool) -> log.info("       - beanName={}, poolSize={}/{}, queueRemaining={}",
                name, pool.getPoolSize(), pool.getMaximumPoolSize(), pool.getQueue().remainingCapacity()));
    }

    /**
     * 演示 8 个子场景。
     */
    public void demo() throws Exception {
        log.info("[1/8] 编程式 4 个构造器(5/6/7 参数)");
        constructShowcase();

        log.info("[2/8] snapshot() — 9 字段不可变快照");
        snapshotShowcase();

        log.info("[3/8] getRejectionCount / resetRejectionCount — 拒绝计数");
        rejectionCountShowcase();

        log.info("[4/8] setRejectedExecutionHandler / getRejectedExecutionHandler — 透明包装");
        rejectedHandlerWrapperShowcase();

        log.info("[5/8] onResize — 运行时热更新 core/max/keepAlive/handler(部分字段)");
        onResizeShowcase();

        log.info("[6/8] onResize(null) — 防御性行为(忽略 + warn,不抛异常)");
        onResizeNullShowcase();

        log.info("[7/8] 通过 EnvironmentChangeEvent 模拟配置中心推送触发 ThreadPoolConfigRefresher 热更新");
        configCenterRefreshShowcase();

        log.info("[8/8] Spring 自动装配的池 + 编程式池并行使任务");
        realPoolConcurrentRun();
    }

    // ========================================================================
    // [1] 4 个构造器
    // ========================================================================

    private void constructShowcase() throws Exception {
        ThreadPoolExecutor.AbortPolicy abort = new ThreadPoolExecutor.AbortPolicy();
        RejectedExecutionHandler discard = new ThreadPoolExecutor.DiscardPolicy();

        // 5 参数: core / max / keepAlive / unit / queue
        try (DynamicExecutor p1 = new DynamicExecutor(
                2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100))) {
            log.info("    -> 5-param ctor: core={}, max={}, keepAlive=60s, queue=100",
                    p1.getCorePoolSize(), p1.getMaximumPoolSize());
        }

        // 6 参数(带 ThreadFactory): core / max / keepAlive / unit / queue / ThreadFactory
        try (DynamicExecutor p2 = new DynamicExecutor(
                1, 2, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactory() {
                    private int n = 1;
                    @Override public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ctor6-" + n++);
                        t.setDaemon(true);
                        return t;
                    }
                })) {
            log.info("    -> 6-param ctor: core={}, max={}, keepAlive=30s, queue=50, threadFactory=ctor6-N",
                    p2.getCorePoolSize(), p2.getMaximumPoolSize());
        }

        // 6 参数(带 handler): core / max / keepAlive / unit / queue / RejectedExecutionHandler
        try (DynamicExecutor p3 = new DynamicExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), abort)) {
            log.info("    -> 6-param ctor (handler): handler={}",
                    p3.getRejectedExecutionHandler().getClass().getSimpleName());
            // 注意:getRejectedExecutionHandler 始终返回"用户原始 handler",不暴露内部 CountingHandler
            log.info("       (getRejectedExecutionHandler 返回用户原始 handler,不暴露 CountingHandler 包装器)");
        }

        // 7 参数: core / max / keepAlive / unit / queue / ThreadFactory / RejectedExecutionHandler
        try (DynamicExecutor p4 = new DynamicExecutor(
                1, 2, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                r -> {
                    Thread t = new Thread(r, "ctor7-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                discard)) {
            log.info("    -> 7-param ctor: core={}, max={}, keepAlive=60s, queue=10, handler=DiscardPolicy",
                    p4.getCorePoolSize(), p4.getMaximumPoolSize());
        }
    }

    // ========================================================================
    // [2] snapshot()
    // ========================================================================

    private void snapshotShowcase() {
        // 先在注入的 audit-executor 上提交几个任务,让快照非零
        DynamicExecutor audit = allExecutors.get("audit-executor");
        for (int i = 0; i < 3; i++) {
            audit.submit(() -> {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            });
        }

        PoolStatus s = orderExecutor.snapshot();
        log.info("    PoolStatus for 'order-executor':");
        log.info("      corePoolSize              = {}", s.getCorePoolSize());
        log.info("      maximumPoolSize           = {}", s.getMaximumPoolSize());
        log.info("      poolSize                  = {}", s.getPoolSize());
        log.info("      activeCount               = {}", s.getActiveCount());
        log.info("      queueSize                 = {}", s.getQueueSize());
        log.info("      queueRemainingCapacity    = {}", s.getQueueRemainingCapacity());
        log.info("      completedTaskCount        = {}", s.getCompletedTaskCount());
        log.info("      totalTaskCount            = {}", s.getTotalTaskCount());
        log.info("      rejectedCount             = {}", s.getRejectedCount());

        // PoolStatus 是不可变 record,可安全跨线程发布
        PoolStatus s2 = audit.snapshot();
        log.info("    PoolStatus for 'audit-executor' 立即读取 (任务可能未完成):");
        log.info("      totalTaskCount={}, completedTaskCount={}, rejectedCount={}",
                s2.getTotalTaskCount(), s2.getCompletedTaskCount(), s2.getRejectedCount());
    }

    // ========================================================================
    // [3] 拒绝计数
    // ========================================================================

    private void rejectionCountShowcase() throws Exception {
        // core=1 / max=2 / queue=20 / AbortPolicy 的 audit-executor: 填满队列后再提交应被拒绝
        DynamicExecutor audit = allExecutors.get("audit-executor");
        // 先等之前 snapshot 的 3 个任务跑完
        audit.shutdown();
        boolean terminated = audit.awaitTermination(5, TimeUnit.SECONDS);
        log.info("    -> 重建前已 shutdown: terminated={}", terminated);

        // 重新 new 一个容量更小的池,便于在 demo 中精确触发拒绝
        RejectedExecutionHandler realHandler = new ThreadPoolExecutor.AbortPolicy();
        try (DynamicExecutor tiny = new DynamicExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                realHandler)) {

            CountDownLatch latch = new CountDownLatch(1);
            // 提交 1 个长任务占用唯一线程
            tiny.submit(() -> {
                try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            });

            // 再提交 4 个任务:1 个进队列,3 个超过 queue+max 触发拒绝
            int rejected = 0;
            for (int i = 0; i < 4; i++) {
                try {
                    tiny.submit(() -> {});
                } catch (RejectedExecutionException e) {
                    rejected++;
                }
            }
            log.info("    -> 提交 4 个任务后拒绝数(应用层统计)={}", rejected);
            log.info("    -> getRejectionCount()                = {}", tiny.getRejectionCount());
            log.info("    -> (两者应一致:{}={})", rejected, rejected == tiny.getRejectionCount());

            // 释放长任务,触发 1 个回调任务,然后看 rejectedCount 是否保留(只增不减)
            latch.countDown();
            Thread.sleep(50);

            log.info("    -> 等待任务完成后 resetRejectionCount()");
            tiny.resetRejectionCount();
            log.info("    -> reset 后 getRejectionCount()       = {}", tiny.getRejectionCount());
        }
    }

    // ========================================================================
    // [4] RejectedExecutionHandler 透明包装
    // ========================================================================

    private void rejectedHandlerWrapperShowcase() throws Exception {
        try (DynamicExecutor p = new DynamicExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1))) {

            RejectedExecutionHandler userHandler = new ThreadPoolExecutor.CallerRunsPolicy();
            p.setRejectedExecutionHandler(userHandler);

            // 关键演示点:getRejectedExecutionHandler() 始终返回用户原始 handler,
            // 不返回内部 CountingHandler 包装器,避免调用方被内部实现绑死
            log.info("    -> getRejectedExecutionHandler() = {} (期望 CallerRunsPolicy)",
                    p.getRejectedExecutionHandler().getClass().getSimpleName());

            // 提交一个长任务,触发后续拒绝,观察内部 CountingHandler 计数
            CountDownLatch latch = new CountDownLatch(1);
            p.submit(() -> {
                try { latch.await(); } catch (InterruptedException ignored) {}
            });
            // CallerRunsPolicy 在队列满后会在调用线程同步执行,不会抛异常
            for (int i = 0; i < 5; i++) {
                p.submit(() -> log.info("    -> task-{} running", Thread.currentThread().getName()));
            }
            log.info("    -> 提交后 rejectedCount = {} (CallerRunsPolicy 不计入拒绝)", p.getRejectionCount());

            latch.countDown();
            p.shutdown();
            p.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ========================================================================
    // [5] onResize 部分字段更新
    // ========================================================================

    private void onResizeShowcase() {
        DynamicExecutor audit = allExecutors.get("audit-executor");

        log.info("    当前 audit-executor: core={}, max={}, keepAlive={}, handler={}",
                audit.getCorePoolSize(), audit.getMaximumPoolSize(),
                audit.getKeepAliveTime(TimeUnit.SECONDS),
                audit.getRejectedExecutionHandler().getClass().getSimpleName());

        // 仅扩容 core + max,keepAlive / handler 字段为 null 表示"不调整"
        audit.onResize(PoolResizeEvent.builder()
                .corePoolSize(4)
                .maximumPoolSize(8)
                .build());
        log.info("    onResize(core=4, max=8, keepAlive=null, handler=null) → core={}, max={}, keepAlive={}",
                audit.getCorePoolSize(), audit.getMaximumPoolSize(),
                audit.getKeepAliveTime(TimeUnit.SECONDS));

        // 单独调整 keepAlive
        audit.onResize(PoolResizeEvent.builder()
                .keepAliveTime(Duration.ofSeconds(120))
                .build());
        log.info("    onResize(keepAlive=120s) → keepAlive={}",
                audit.getKeepAliveTime(TimeUnit.SECONDS));

        // 单独调整 rejectedHandler: AbortPolicy → DiscardPolicy
        audit.onResize(PoolResizeEvent.builder()
                .rejectedHandler(new ThreadPoolExecutor.DiscardPolicy())
                .build());
        log.info("    onResize(handler=DiscardPolicy) → handler={}",
                audit.getRejectedExecutionHandler().getClass().getSimpleName());

        // 还原为初始状态,避免影响后续 demo。
        // 注意:onResize 内部顺序是 "先 setMaximumPoolSize 再 setCorePoolSize",
        // 因此不能把 max 调到比当前 core 还小。分两步还原:先把 core 调小,再调 max。
        audit.onResize(PoolResizeEvent.builder()
                .corePoolSize(1)
                .keepAliveTime(Duration.ofSeconds(60))
                .rejectedHandler(new ThreadPoolExecutor.AbortPolicy())
                .build());
        audit.onResize(PoolResizeEvent.builder()
                .maximumPoolSize(2)
                .build());
        log.info("    已还原 audit-executor 到初始配置");
    }

    // ========================================================================
    // [6] onResize(null) 防御性
    // ========================================================================

    private void onResizeNullShowcase() {
        DynamicExecutor audit = allExecutors.get("audit-executor");
        // 不抛异常,只 warn —— 这是 Spring 容器刷新路径需要的能力(空事件不应崩)
        audit.onResize(null);
        log.info("    -> onResize(null) 已安全处理(输出 WARN 但不抛异常)");
    }

    // ========================================================================
    // [7] EnvironmentChangeEvent 触发 ThreadPoolConfigRefresher
    // ========================================================================

    private void configCenterRefreshShowcase() {
        DynamicExecutor order = allExecutors.get("order-executor");
        log.info("    order-executor 当前: core={}, max={}",
                order.getCorePoolSize(), order.getMaximumPoolSize());
        log.info("    (生产场景下,推送 Nacos/Apollo 配置 → Spring Cloud 发布 EnvironmentChangeEvent →");
        log.info("     ThreadPoolConfigRefresher 自动调用 onResize,无需应用代码参与)");
        log.info("    (本 demo 已通过 [5] 直接调用 onResize 演示了 Refresher 的等价行为)");
    }

    // ========================================================================
    // [8] Spring 池 + 编程式池真实并发跑任务
    // ========================================================================

    private void realPoolConcurrentRun() throws Exception {
        DynamicExecutor order = allExecutors.get("order-executor");
        DynamicExecutor notification = notificationExecutor;

        log.info("    提交 10 个 order 任务 → order-executor,5 个 notification 任务 → notification-executor");

        CountDownLatch all = new CountDownLatch(15);
        long t0 = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            order.submit(() -> {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                all.countDown();
            });
        }
        for (int i = 0; i < 5; i++) {
            notification.submit(() -> {
                try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                all.countDown();
            });
        }
        boolean done = all.await(5, TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        log.info("    -> 全部完成: {}, 耗时 {}ms", done, elapsed);

        PoolStatus s = order.snapshot();
        log.info("    -> order-executor 最终快照: completed={}, total={}, rejected={}",
                s.getCompletedTaskCount(), s.getTotalTaskCount(), s.getRejectedCount());
    }

    // ========================================================================
    // 交互式 API —— 由 ConcurrencyDemoController 通过 HTTP 端点调用
    // ========================================================================

    /**
     * 提交 N 个 sleep 任务到指定 Spring 注入的线程池,返回实际完成数。
     *
     * @param poolName bean name(对应 application.yml 的 key,如 {@code order-executor})
     * @param taskCount 任务数
     * @param sleepMs 单个任务 sleep 时长
     * @return 完成的任务数
     */
    public int submitTasks(String poolName, int taskCount, long sleepMs) {
        DynamicExecutor pool = allExecutors.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName + ", available: " + allExecutors.keySet());
        }
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(sleepMs);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                rejected.incrementAndGet();
                latch.countDown();
                log.warn("    -> task #{} rejected", id);
            }
        }
        log.info("submitTasks: pool={}, requested={}, rejected={}", poolName, taskCount, rejected.get());
        try {
            latch.await(Math.max(5, sleepMs * taskCount / 10 + 5), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PoolStatus s = pool.snapshot();
        log.info("    -> 快照: activeCount={}, poolSize={}, queueSize={}, completed={}, total={}, rejected={}",
                s.getActiveCount(), s.getPoolSize(), s.getQueueSize(),
                s.getCompletedTaskCount(), s.getTotalTaskCount(), s.getRejectedCount());
        return completed.get();
    }

    /**
     * 热更新指定 Spring 注入线程池的核心参数(null 字段保持不变)。
     *
     * @param poolName bean name
     * @param core 新 corePoolSize(null 表示不调整)
     * @param max 新 maximumPoolSize(null 表示不调整)
     * @param keepAliveMs 新 keepAliveTime(ms,null 表示不调整)
     * @return 调整后的 PoolStatus
     */
    public PoolStatus resize(String poolName, Integer core, Integer max, Long keepAliveMs) {
        DynamicExecutor pool = allExecutors.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName);
        }
        PoolResizeEvent.Builder builder = PoolResizeEvent.builder();
        if (core != null) builder.corePoolSize(core);
        if (max != null) builder.maximumPoolSize(max);
        if (keepAliveMs != null) builder.keepAliveTime(Duration.ofMillis(keepAliveMs));
        PoolResizeEvent event = builder.build();
        log.info("resize: pool={}, core={}, max={}, keepAliveMs={}", poolName, core, max, keepAliveMs);
        pool.onResize(event);
        PoolStatus s = pool.snapshot();
        log.info("    -> 调整后: corePoolSize={}, maximumPoolSize={}",
                s.getCorePoolSize(), s.getMaximumPoolSize());
        return s;
    }

    /**
     * 读取指定 Spring 注入线程池的 9 字段快照(文本形式)。
     *
     * @param poolName bean name
     * @return 多行文本,每行一个字段
     */
    public String snapshot(String poolName) {
        DynamicExecutor pool = allExecutors.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName);
        }
        PoolStatus s = pool.snapshot();
        return "PoolStatus[" + poolName + "]:\n"
                + "  corePoolSize           = " + s.getCorePoolSize() + "\n"
                + "  maximumPoolSize        = " + s.getMaximumPoolSize() + "\n"
                + "  poolSize               = " + s.getPoolSize() + "\n"
                + "  activeCount            = " + s.getActiveCount() + "\n"
                + "  queueSize              = " + s.getQueueSize() + "\n"
                + "  queueRemainingCapacity = " + s.getQueueRemainingCapacity() + "\n"
                + "  completedTaskCount     = " + s.getCompletedTaskCount() + "\n"
                + "  totalTaskCount         = " + s.getTotalTaskCount() + "\n"
                + "  rejectedCount          = " + s.getRejectedCount();
    }

    /**
     * 触发指定线程池的拒绝策略 —— 提交超额任务直到有任务被拒绝。
     *
     * @param poolName bean name
     * @param taskCount 提交任务数(应远大于池容量 + 队列容量)
     * @return 触发的拒绝计数(执行前 + 执行后)
     */
    public long triggerReject(String poolName, int taskCount) {
        DynamicExecutor pool = allExecutors.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName);
        }
        long before = pool.getRejectionCount();
        log.info("triggerReject: pool={}, submitted={}, beforeRejectionCount={}", poolName, taskCount, before);

        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger rejected = new AtomicInteger(0);
        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(5_000);  // 长 sleep 让任务占满核心线程 + 队列
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                rejected.incrementAndGet();
                latch.countDown();
                if (rejected.get() <= 5) {
                    log.warn("    -> task #{} rejected (AbortPolicy)", id);
                }
            }
        }
        log.info("    -> 本次调用触发拒绝数: {}, 累计拒绝数: {} -> {}",
                rejected.get(), before, pool.getRejectionCount());

        // 让长 sleep 任务在 controller 返回前完成一部分,以便 snapshot 能看到完整数据
        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PoolStatus s = pool.snapshot();
        log.info("    -> 完成后快照: activeCount={}, poolSize={}, queueSize={}, completed={}, rejected={}",
                s.getActiveCount(), s.getPoolSize(), s.getQueueSize(),
                s.getCompletedTaskCount(), s.getRejectedCount());

        // 重置拒绝计数,避免下次 demo 计数叠加
        pool.resetRejectionCount();
        log.info("    -> 已重置拒绝计数: now={}", pool.getRejectionCount());
        return pool.getRejectionCount();
    }

    /**
     * 编程式创建独立 DynamicExecutor 并提交任务(脱离 Spring 容器,适合嵌入式测试)。
     *
     * @param core corePoolSize
     * @param max maximumPoolSize
     * @param keepAliveMs keepAliveTime(ms)
     * @param queueCapacity 任务队列容量
     * @param taskCount 提交任务数
     * @param sleepMs 单任务 sleep
     * @return 完成的 task 数
     */
    public int createAdhoc(int core, int max, long keepAliveMs, int queueCapacity,
                            int taskCount, long sleepMs) {
        log.info("createAdhoc: core={}, max={}, keepAliveMs={}, queue={}, tasks={}, sleepMs={}",
                core, max, keepAliveMs, queueCapacity, taskCount, sleepMs);

        AtomicInteger counter = new AtomicInteger(1);
        DynamicExecutor adhoc = new DynamicExecutor(
                core, max, keepAliveMs, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "adhoc-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            try {
                adhoc.execute(() -> {
                    try {
                        Thread.sleep(sleepMs);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                rejected.incrementAndGet();
                latch.countDown();
                log.warn("    -> task #{} rejected", id);
            }
        }
        log.info("    -> 提交完成: rejected={}", rejected.get());
        try {
            latch.await(Math.max(5, sleepMs * taskCount / 10 + 5), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PoolStatus s = adhoc.snapshot();
        log.info("    -> 完成后快照: completed={}, total={}, rejected={}",
                s.getCompletedTaskCount(), s.getTotalTaskCount(), s.getRejectedCount());
        adhoc.shutdown();
        return completed.get();
    }
}