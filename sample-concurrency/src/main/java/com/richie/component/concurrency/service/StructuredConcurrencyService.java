package com.richie.component.concurrency.service;

import com.richie.component.concurrency.virtual.StructuredConcurrency;
import com.richie.component.concurrency.virtual.StructuredConcurrency.BestEffortResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 结构化并发演示 —— 覆盖 {@link StructuredConcurrency} 全部 8 种 API。
 *
 * <h2>API 全景</h2>
 * <table>
 *   <caption>StructuredConcurrency 8 种 API</caption>
 *   <tr><th>API</th><th>语义</th></tr>
 *   <tr><td>{@code gatherAll}</td><td>全成功才返回;任一失败取消其余</td></tr>
 *   <tr><td>{@code gatherAllSuppliers}</td><td>同 gatherAll,但子任务有返回值</td></tr>
 *   <tr><td>{@code race}</td><td>任一完成即取消其余</td></tr>
 *   <tr><td>{@code raceSuppliers}</td><td>同 race,但子任务有返回值</td></tr>
 *   <tr><td>{@code withDeadline}</td><td>单任务限时执行</td></tr>
 *   <tr><td>{@code gatherBatched}</td><td>大批量分批执行,每批 gatherAll 全部完成才推进</td></tr>
 *   <tr><td>{@code gatherAllBestEffort}</td><td>全尽力执行,不抛异常,返回 BestEffortResult</td></tr>
 *   <tr><td>{@code gatherAllBestEffortSuppliers}</td><td>同 best-effort,但子任务有返回值</td></tr>
 * </table>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class StructuredConcurrencyService {

    private static final Logger log = LoggerFactory.getLogger(StructuredConcurrencyService.class);

    /**
     * 依次演示 8 种结构化并发 API。
     */
    public void demo() throws Exception {
        log.info("[1/8] gatherAll: 全成功才返回,任一失败取消其余");
        try {
            List<String> results = StructuredConcurrency.gatherAll(List.of(
                    task("A", 80),
                    task("B", 120),
                    task("C", 60)
            ));
            log.info("    -> 全部完成: {}", results);
        } catch (Exception e) {
            log.info("    -> gatherAll 任一失败抛出: {}", e.getMessage());
        }

        log.info("[2/8] gatherAllSuppliers: 同上,但子任务带返回值");
        try {
            List<String> results = StructuredConcurrency.gatherAllSuppliers(List.of(
                    supplier("alpha", 50),
                    supplier("beta", 90),
                    supplier("gamma", 30)
            ));
            log.info("    -> 全部完成: {}", results);
        } catch (Exception e) {
            log.info("    -> 任一失败: {}", e.getMessage());
        }

        log.info("[3/8] race: 任一完成即取消其余");
        String winner = StructuredConcurrency.race(List.of(
                task("slow", 200),
                task("fast", 30),
                task("medium", 80)
        ));
        log.info("    -> winner: {}", winner);

        log.info("[4/8] raceSuppliers: race + 返回值");
        Integer value = StructuredConcurrency.raceSuppliers(List.of(
                supplier(10, 200),
                supplier(99, 30),
                supplier(50, 80)
        ));
        log.info("    -> winner value: {}", value);

        log.info("[5/8] withDeadline: 单任务限时(超时则中断)");
        try {
            String r = StructuredConcurrency.withDeadline(task("delayed", 500), Duration.ofMillis(100));
            log.info("    -> completed: {}", r);
        } catch (Exception e) {
            log.info("    -> 超时抛出(预期): {}", e.toString());
        }

        log.info("[6/8] gatherBatched: 大批量分批,每批 gatherAll 后推进");
        List<String> batched = StructuredConcurrency.gatherBatched(
                java.util.stream.IntStream.range(0, 12)
                        .mapToObj(i -> task("item-" + i, ThreadLocalRandom.current().nextInt(20, 80)))
                        .toList(),
                4
        );
        log.info("    -> 12 个任务按 4 个/批完成,首批: {}", batched.subList(0, 4));

        log.info("[7/8] gatherAllBestEffort: 全尽力执行,不抛异常");
        BestEffortResult<String> be = StructuredConcurrency.gatherAllBestEffort(List.of(
                task("ok-1", 40),
                failingTask("fail-A", 30),
                task("ok-2", 20),
                failingTask("fail-B", 10)
        ));
        log.info("    -> success={}, failure={}, successes={}, failures={}",
                be.successCount(), be.failureCount(), be.successes(), be.failures());
        log.info("    -> hasAnySuccess={}, failedIndices={}", be.hasAnySuccess(), be.failedIndices());

        log.info("[8/8] gatherAllBestEffortSuppliers: best-effort + 返回值");
        BestEffortResult<Integer> be2 = StructuredConcurrency.gatherAllBestEffortSuppliers(List.of(
                supplier(1, 30),
                failingSupplier("fail-X", 10),
                supplier(2, 50),
                failingSupplier("fail-Y", 20)
        ));
        log.info("    -> successCount={}, failureCount={}, successes={}",
                be2.successCount(), be2.failureCount(), be2.successes());
    }

    // ------------------------------------------------------------------------
    // 辅助: 任务构造
    // ------------------------------------------------------------------------

    private static Callable<String> task(String name, long sleepMs) {
        return () -> {
            Thread.sleep(sleepMs);
            log.debug("    [task {}] done after {}ms", name, sleepMs);
            return name;
        };
    }

    private static Callable<String> failingTask(String name, long sleepMs) {
        return () -> {
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            throw new RuntimeException("task-" + name + "-failed");
        };
    }

    private static Supplier<String> supplier(String name, long sleepMs) {
        return () -> {
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            return name;
        };
    }

    private static Supplier<Integer> supplier(int value, long sleepMs) {
        return () -> {
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            return value;
        };
    }

    private static Supplier<Integer> failingSupplier(String name, long sleepMs) {
        return () -> {
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            throw new RuntimeException("supplier-" + name + "-failed");
        };
    }
}