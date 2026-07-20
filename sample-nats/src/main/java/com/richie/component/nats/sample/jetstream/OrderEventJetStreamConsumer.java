package com.richie.component.nats.sample.jetstream;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.bus.JetStreamBus;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.sample.constants.SubjectConstant;
import com.richie.component.nats.sample.controller.NatsDemoController;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * JetStream 订单事件持续消费者。
 *
 * <h2>演示的 JetStream 核心特性</h2>
 * <ul>
 *     <li><b>消息持久化</b> — 消息先写入 Stream,服务端确认后才返回 PublishAck;
 *         与 Core NATS 的 fire-and-forget 相比,即使消费者下线也不会丢消息</li>
 *     <li><b>自动 ack</b> — Handler 正常返回(不抛异常)时,组件自动 ack,
 *         服务端推进投递位点</li>
 *     <li><b>自动 nak</b> — Handler 抛出异常时,组件自动 nak;NATS 服务端
 *         按 {@code ack-wait} 超时和 {@code max-deliver} 上限做重投递</li>
 *     <li><b>优雅停机</b> — Spring 容器关闭时调用
 *         {@link MessageConsumer#stop()} 停止订阅,避免丢最后一批在途消息</li>
 * </ul>
 *
 * <h2>启动时机的关键决策</h2>
 * <p>本类实现 {@link ApplicationRunner} 而非 {@code @PostConstruct},目的是
 * 确保 {@link NatsComponent} 的 {@code SmartLifecycle.start()} 已完成
 * Stream/Consumer 声明(由 {@code application-nats.yml} 中的
 * {@code platform.nats.jetstream.auto-provision: true} 驱动),否则会
 * 报 {@code stream not found} 错误。{@code @Order(0)} 保证它在 sample
 * 内其他 Runner 之前执行。</p>
 *
 * <h2>Stream 与 Consumer 的关系</h2>
 * <p>同一个 Stream 下可以声明多个 Consumer,各自独立维护投递位点;同名
 * Consumer 在不同 group 内分别投递。本演示仅注册了
 * {@link SubjectConstant#ORDER_CONSUMER_CONTINUOUS} push 消费者,
 * 拉取消费者由
 * {@link com.richie.component.nats.sample.service.impl.NatsDemoServiceImpl#pullOrderEvents}
 * 通过 {@code JetStreamBus.fetch} 调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class OrderEventJetStreamConsumer implements ApplicationRunner {

    private final NatsComponent nats;
    private final NatsMessageSerializer serializer;

    /** 持续消费者句柄,容器关闭时调用 {@link #stop()} 释放订阅。 */
    private MessageConsumer messageConsumer;

    @Override
    public void run(ApplicationArguments args) {
        JetStreamBus stream = nats.stream();

        NatsMessageHandler handler = (Message msg) -> {
            // 失败注入:由 NatsDemoController.injectFail 设置;抛异常会触发 broker 重投
            // (max-deliver 上限) → advisory DLQ,演示 DLQ 全链路
            if (NatsDemoController.REMAINING_FAIL_COUNT.getAndDecrement() > 0) {
                log.error("[FAIL INJECTION] JetStream handler throwing on purpose to trigger DLQ");
                throw new RuntimeException("Injected failure for DLQ verification");
            }
            try {
                OrderEvent event = serializer.deserialize(msg.getData(), OrderEvent.class);
                log.info("JetStreamConsumer received from stream={} subject={}: {}",
                        SubjectConstant.ORDER_STREAM, msg.getSubject(), event);
            } catch (Exception e) {
                log.error("JetStreamConsumer deserialize failed for subject={}",
                        msg.getSubject(), e);
                // 抛出异常会触发 nak,服务端按 ack-wait/max-deliver 重投递
                throw e;
            }
        };

        this.messageConsumer = stream.consume(
                SubjectConstant.ORDER_STREAM,
                SubjectConstant.ORDER_CONSUMER_CONTINUOUS,
                handler);

        log.info("OrderEventJetStreamConsumer started on stream={} consumer={}",
                SubjectConstant.ORDER_STREAM, SubjectConstant.ORDER_CONSUMER_CONTINUOUS);
    }

    /**
     * 容器关闭前停止持续订阅,释放 NATS 连接上的订阅资源。
     */
    @PreDestroy
    public void stop() {
        if (messageConsumer != null) {
            try {
                messageConsumer.stop();
                log.info("OrderEventJetStreamConsumer stopped");
            } catch (Exception e) {
                log.warn("Failed to stop JetStream consumer cleanly", e);
            }
        }
    }
}
