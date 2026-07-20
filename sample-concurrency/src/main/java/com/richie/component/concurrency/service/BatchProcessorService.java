package com.richie.component.concurrency.service;

import com.richie.component.concurrency.measurement.Stopwatch;
import com.richie.component.concurrency.virtual.BatchMappingResult;
import com.richie.component.concurrency.virtual.BatchProcessor;
import com.richie.component.concurrency.virtual.BatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * 批量并行处理器演示 —— 覆盖 {@link BatchProcessor} 的 {@code forEach} 与
 * {@code mapParallel} 两种语义,以及 {@link Stopwatch} 集成度量。
 *
 * <p>{@link BatchProcessor} 基于 JDK 25 结构化并发与虚拟线程,默认
 * {@code parallelism=max(2, CPU*2)},默认 {@code timeout=30min},适合
 * I/O 密集型批量场景(批量调用外部 API / 批量入库)。</p>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class BatchProcessorService {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessorService.class);

    /**
     * 演示 forEach / mapParallel / 部分失败 / 累计耗时统计。
     */
    public void demo() {
        log.info("[1/3] forEach: 并行处理 12 个任务,统计成功/失败");
        Stopwatch sw1 = Stopwatch.createStarted();
        BatchResult r1 = BatchProcessor.of(intRange(12))
                .parallelism(6)
                .forEach(idx -> simulateIoWork("forEach-item-" + idx, 60));
        sw1.stop();
        log.info("    -> success={}, failure={}, hasError={}, elapsed={}",
                r1.successCount(), r1.failureCount(), r1.hasError(), sw1);

        log.info("[2/3] mapParallel: 并行计算 + 结果顺序对齐输入");
        Stopwatch sw2 = Stopwatch.createStarted();
        BatchMappingResult<Integer, String> r2 = BatchProcessor.of(intRange(10))
                .parallelism(4)
                .mapParallel(idx -> "value-" + idx + "@" + simulateIoWork("map-" + idx, 40));
        sw2.stop();
        log.info("    -> success={}, failure={}, hasError={}, elapsed={}",
                r2.successCount(), r2.failureCount(), r2.hasError(), sw2);
        log.info("    -> results={}", r2.results());

        log.info("[3/3] forEach + 部分失败: 偶数项抛异常,演示成功/失败计数与 errors()");
        BatchResult r3 = BatchProcessor.of(intRange(8))
                .parallelism(4)
                .forEach(idx -> {
                    if (idx % 2 == 0) {
                        throw new IllegalStateException("item-" + idx + " boom");
                    }
                    simulateIoWork("ok-" + idx, 30);
                });
        log.info("    -> success={}, failure={}, hasError={}", r3.successCount(), r3.failureCount(), r3.hasError());
        r3.errors().forEach(err -> log.info("    -> error[{}]: {}", err.getClass().getSimpleName(), err.getMessage()));

        // 演示 BatchResult.empty() 的全局缓存语义
        log.info("[extra] BatchResult.empty(): {}",
                BatchResult.empty() == BatchResult.empty() ? "全局单例缓存生效" : "未缓存");

        // 演示 BatchMappingResult.resultAt 与失败项 null 语义
        log.info("[extra] mapParallel 失败模式: 索引 3 / 7 抛异常,其他成功");
        BatchMappingResult<Integer, String> r4 = BatchProcessor.of(intRange(10))
                .parallelism(4)
                .mapParallel(idx -> {
                    if (idx == 3 || idx == 7) {
                        throw new RuntimeException("intentional-fail-" + idx);
                    }
                    return "ok-" + idx;
                });
        for (int i = 0; i < r4.results().size(); i++) {
            String v = r4.resultAt(i);
            log.info("    -> resultAt[{}] = {}", i, v == null ? "<FAILED>" : v);
        }
    }

    private static List<Integer> intRange(int n) {
        return IntStream.range(0, n).boxed().toList();
    }

    /**
     * 模拟 I/O 密集型工作 —— 让虚拟线程因载体线程阻塞让出。
     */
    private static long simulateIoWork(String name, long baseMs) {
        long jitter = ThreadLocalRandom.current().nextLong(0, 30);
        long total = baseMs + jitter;
        try {
            Thread.sleep(total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("    [{}] done after {}ms", name, total);
        return total;
    }

    @SuppressWarnings("unused")
    private static Duration toDuration(long ms) {
        return Duration.ofMillis(ms);
    }
}