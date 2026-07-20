package com.richie.component.concurrency.service;

import com.richie.component.concurrency.algorithm.Debouncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 防抖器演示 —— {@link Debouncer} 把高频事件合并成"延迟后单次执行"。
 *
 * <p>典型场景:搜索框输入(用户连续敲键只触发一次实际查询)、窗口 resize(连续拖动
 * 只在停止后重排布局)、按钮防重点击(在指定 delay 内的多次点击只触发一次动作)。</p>
 *
 * <h2>核心 API</h2>
 * <ul>
 *   <li>{@code trigger()} — 启动/重置延迟计时器</li>
 *   <li>{@code flush()} — 立即执行并清除挂起状态</li>
 *   <li>{@code cancel()} — 取消挂起动作(不执行)</li>
 *   <li>{@code isPending()} — 是否存在挂起的待执行</li>
 *   <li>{@code close()} — 关闭底层调度器</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class DebouncerService {

    private static final Logger log = LoggerFactory.getLogger(DebouncerService.class);

    /**
     * 演示三种典型用法。
     */
    public void demo() throws Exception {
        log.info("[1/3] trigger * 5 → 最终只触发一次 action (debounce 语义)");
        AtomicInteger fireCount = new AtomicInteger(0);
        try (Debouncer d = Debouncer.of(Duration.ofMillis(150), () -> {
            int n = fireCount.incrementAndGet();
            log.info("    -> action fired #{} (count was {}, this is the debounced call)", n, n);
        })) {
            for (int i = 0; i < 5; i++) {
                d.trigger();
                log.info("    -> trigger #{} (pending={})", i + 1, d.isPending());
                Thread.sleep(40);
            }
            log.info("    -> 等待 200ms 让最后一次 trigger 完成延迟...");
            Thread.sleep(200);
            log.info("    -> 最终触发次数 = {} (期望 1)", fireCount.get());
        }

        log.info("[2/3] flush() — 立即执行挂起动作");
        AtomicInteger fireCount2 = new AtomicInteger(0);
        try (Debouncer d = Debouncer.of(Duration.ofSeconds(10), () -> {
            fireCount2.incrementAndGet();
            log.info("    -> flush executed");
        })) {
            d.trigger();
            log.info("    -> trigger() 后 pending={}", d.isPending());
            Thread.sleep(50);
            d.flush();
            log.info("    -> flush() 后 pending={}, fireCount={}", d.isPending(), fireCount2.get());
        }

        log.info("[3/3] cancel() — 取消挂起动作,不执行");
        AtomicInteger fireCount3 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        try (Debouncer d = Debouncer.of(Duration.ofMillis(100), () -> {
            fireCount3.incrementAndGet();
            latch.countDown();
        })) {
            d.trigger();
            log.info("    -> trigger() 后 pending={}", d.isPending());
            d.cancel();
            log.info("    -> cancel() 后 pending={}, fireCount={}", d.isPending(), fireCount3.get());
            boolean firedInTime = latch.await(300, TimeUnit.MILLISECONDS);
            log.info("    -> 等待 300ms 后是否执行: {} (期望 false)", firedInTime);
        }
    }
}