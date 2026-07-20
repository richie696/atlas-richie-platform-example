package com.richie.component.nats.sample.jetstream;

import com.richie.component.nats.NatsComponent;
import io.nats.client.ConsumerContext;
import io.nats.client.MessageConsumer;
import io.nats.client.StreamContext;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * JetStream DLQ(死信队列)消费者演示。
 *
 * <h2>演示的 DLQ 核心特性</h2>
 * <ul>
 *   <li><b>死信观察</b> — 订阅由 {@code JetStreamManagementService}
 *       在 {@code platform.nats.jetstream.dlq.enabled=true} 时自动 derive 的
 *       {@code <business>-dlq} 流(本演示中 {@code ORDERS} → {@code ORDERS-dlq}),
 *       业务侧 0 改动。</li>
 *   <li><b>显式 ack</b> — 日志输出后立即 {@code msg.ack()},handler
 *       不抛异常,避免触发 server 端 redelivery。</li>
 *   <li><b>max-deliver=-1 防自反</b> — 业务 stream 的
 *       {@code max-deliver=3} 多次重投失败的死信,在 DLQ 中不应再次被
 *       redelivery 兜底重投,否则会形成「死信 → DLQ consumer 失败 →
 *       死信再次 → ...」的自反循环(R3)。{@code maxDeliver(-1L)} 在
 *       {@link ConsumerConfiguration.Builder} 序列化时被
 *       {@code addFieldWhenGtZero} 跳过,等价于服务端默认值(unlimited)
 *       同时也关闭 ack-wait 失效时的兜底 redelivery。</li>
 *   <li><b>0 业务处理</b> — DLQ consumer 仅负责"看见"死信(打 warn 日志),
 *       禁止 DB 持久化 / metrics 暴露 / webhook 通知 / 重投回业务 stream。
 *       死信的处置由 ops 离线导出 + 人工决策。</li>
 * </ul>
 *
 * <h2>为什么不用 {@code JetStreamBus.consume()}</h2>
 * <p>{@link com.richie.component.nats.bus.JetStreamBus#consume} 默认对
 * handler 异常做自动 nak,在 DLQ 场景下如果日志输出失败会触发死信重投递
 * 循环(R3 自反)。因此本演示走原生
 * {@link StreamContext#createOrUpdateConsumer} +
 * {@link ConsumerContext#consume(io.nats.client.MessageHandler)}
 * 路径,在 handler 内部显式 {@code msg.ack()},不依赖框架的 auto-ack/nak
 * 装饰器。</p>
 *
 * <h2>启动时机的关键决策</h2>
 * <p>本类实现 {@link ApplicationRunner} + {@code @Order(1)},在
 * {@link OrderEventJetStreamConsumer} 之后执行,保证
 * {@link NatsComponent} 的 {@code SmartLifecycle.start()} 已完成
 * 业务 stream / consumer 的声明,且 DLQ 流已就绪(由框架侧确保)。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class OrderEventDlqConsumer implements ApplicationRunner {

    /** 死信流名(由 JetStreamManagementService 按 {@code dlq.stream-name-suffix=-dlq} 从业务 stream 推导)。 */
    private static final String DLQ_STREAM = "ORDERS-dlq";

    /** 死信 consumer 名(在 DLQ 流上自建,跟随应用生命周期)。 */
    private static final String DLQ_CONSUMER = "order-dlq-consumer";

    private final NatsComponent nats;

    /** 死信消费者句柄,容器关闭前调用 {@link #stop()} 释放订阅资源。 */
    private MessageConsumer messageConsumer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            StreamContext streamContext = nats.getConnectionManager().getStreamContext(DLQ_STREAM);

            ConsumerConfiguration config = ConsumerConfiguration.builder()
                    .name(DLQ_CONSUMER)
                    .ackPolicy(AckPolicy.Explicit)
                    .maxDeliver(-1L)
                    .build();

            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(config);

            this.messageConsumer = consumerContext.consume(msg -> {
                try {
                    log.warn("DLQ message received: stream={} seq={} delivered={} subject={} payload={}",
                            DLQ_STREAM,
                            msg.metaData().streamSequence(),
                            msg.metaData().deliveredCount(),
                            msg.getSubject(),
                            new String(msg.getData(), StandardCharsets.UTF_8));
                    msg.ack();
                } catch (Exception e) {
                    // 兜底:DLQ handler 内部任何失败都要 swallow + 强制 ack,
                    // 否则触发自反循环(死信 redelivery 后再失败 → 再 redelivery)。
                    log.error("OrderEventDlqConsumer handler failed (suppressed to prevent redelivery loop)", e);
                    msg.ack();
                }
            });

            log.info("OrderEventDlqConsumer started on stream={} consumer={} "
                            + "(max-deliver=-1, log-only, no business processing)",
                    DLQ_STREAM, DLQ_CONSUMER);
        } catch (java.io.IOException | io.nats.client.JetStreamApiException e) {
            // 启动期 DLQ 流尚未就绪时(框架尚未 auto-derive ORDERS-dlq),
            // 不阻塞 Spring Boot 启动;由运维在 NUI 看到本日志后排查 DLQ 流缺失。
            log.warn("OrderEventDlqConsumer skipped: stream={} not ready yet (will not auto-retry). "
                    + "Confirm JetStreamManagementService has derived the DLQ stream.", DLQ_STREAM, e);
        }
    }

    /**
     * 容器关闭前停止持续订阅,释放 NATS 连接上的订阅资源。
     */
    @PreDestroy
    public void stop() {
        if (messageConsumer != null) {
            try {
                messageConsumer.stop();
                log.info("OrderEventDlqConsumer stopped");
            } catch (Exception e) {
                log.warn("Failed to stop DLQ consumer cleanly", e);
            }
        }
    }
}