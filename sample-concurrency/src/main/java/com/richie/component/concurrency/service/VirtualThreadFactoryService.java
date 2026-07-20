package com.richie.component.concurrency.service;

import com.richie.component.concurrency.virtual.VirtualThreadFactory;
import com.richie.component.concurrency.virtual.VirtualThreadFactory.ScopedValueBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 虚拟线程工厂演示 —— 覆盖 {@link VirtualThreadFactory} 的命名能力与
 * {@link ScopedValue} 透传能力。
 *
 * <p>{@link ScopedValue} 是 JDK 21+ 引入的结构化作用域变量,语义上替代 {@link ThreadLocal}
 * 但不可变且无内存泄漏风险。虚拟线程数量爆发时尤为合适,因为每个虚拟线程不再对应一份
 * {@link ThreadLocal} 拷贝。</p>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
public class VirtualThreadFactoryService {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadFactoryService.class);

    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    /**
     * 依次演示命名工厂、ScopedValue 透传、多 ScopedValue 绑定。
     */
    public void demo() throws Exception {
        log.info("[1/2] of(namePrefix): 简单命名工厂");
        simpleNamedFactory();

        log.info("[2/2] builder() + scopedValue/scopedValues: ScopedValue 透传");
        scopedValuePropagation();
    }

    private static void simpleNamedFactory() throws Exception {
        ThreadFactory factory = VirtualThreadFactory.of("simple-");
        ExecutorService pool = Executors.newThreadPerTaskExecutor(factory);

        for (int i = 0; i < 3; i++) {
            pool.submit(() -> log.info("    -> virtual thread name: {}", Thread.currentThread().getName()));
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void scopedValuePropagation() throws Exception {
        ThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("scoped-")
                .scopedValue(REQUEST_ID, "REQ-9001")
                .scopedValues(new ScopedValueBinding<>(TENANT, "T-ATLAS"))
                .build();
        ExecutorService pool = Executors.newThreadPerTaskExecutor(factory);

        for (int i = 0; i < 3; i++) {
            pool.submit(() -> log.info("    -> virtual thread={}, REQUEST_ID={}, TENANT={}, isBound={}",
                    Thread.currentThread().getName(),
                    REQUEST_ID.get(),
                    TENANT.get(),
                    REQUEST_ID.isBound()));
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}