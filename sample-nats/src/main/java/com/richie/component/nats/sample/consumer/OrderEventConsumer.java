package com.richie.component.nats.sample.consumer;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.sample.constants.SubjectConstant;
import com.richie.component.nats.sample.controller.NatsDemoController;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Core NATS 订单事件订阅者。
 *
 * <h2>本类的演示目标</h2>
 * <p>集中展示 NATS 协议的三大核心订阅模式,所有订阅在容器启动后立即
 * 注册,可通过 {@code POST /nats/publish/event} / {@code /nats/publish/queue}
 * 触发,并在控制台日志中看到对应 tag 的接收记录。</p>
 *
 * <h2>三种订阅模式</h2>
 * <ol>
 *     <li><b>普通订阅(fanout)</b> — {@code bus.subscribe(subject, handler)},
 *         每个订阅实例都会收到一份消息副本,适合事件通知、缓存刷新等
 *         "每个节点都需要感知"的场景</li>
 *     <li><b>队列组订阅(queueGroup)</b> —
 *         {@code bus.subscribe(subject, queueGroup, handler)},
 *         同名 queueGroup 内只有一个实例拿到消息,实现工作负载均衡,
 *         适合派单/任务分发等"消息只应被处理一次"的场景</li>
 *     <li><b>通配符订阅(wildcard)</b> — {@code bus.subscribe("orders.>", handler)},
 *         {@code >} 匹配多段,通常用于审计/观测消费者一次订阅多个业务事件</li>
 * </ol>
 *
 * <h2>订阅注册时机</h2>
 * <p>在 {@code @PostConstruct} 阶段注册,该阶段晚于构造器注入、NATS
 * 组件的连接建立,但早于 HTTP 端口监听。REST 端点被首次调用时,所有
 * 订阅关系已经就绪。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NatsComponent nats;
    private final NatsMessageSerializer serializer;

    @PostConstruct
    public void register() {
        NatsBus bus = nats.bus();

        bus.subscribe(SubjectConstant.ORDER_EVENT_SUBJECT, buildHandler("[fanout]"));
        bus.subscribe(SubjectConstant.ORDER_QUEUE_SUBJECT, "order-worker", buildHandler("[worker]"));
        bus.subscribe(SubjectConstant.ORDER_WILDCARD_SUBJECT, buildHandler("[wildcard]"));
    }

    /**
     * 构造统一的事件 Handler:反序列化 + 业务日志输出。
     *
     * <p>反序列化失败时仅记录错误日志,不重新抛出 —— Core NATS 模式下
     * 服务端没有"消费位点"概念,失败的消息无法回退/重投。</p>
     */
    private NatsMessageHandler buildHandler(String tag) {
        return (Message msg) -> {
            // 失败注入:触发 DLQ 验证(由 NatsDemoController.injectFail 设置)
            if (NatsDemoController.REMAINING_FAIL_COUNT.getAndDecrement() > 0) {
                throw new RuntimeException("Injected failure for DLQ verification");
            }
            try {
                OrderEvent event = serializer.deserialize(msg.getData(), OrderEvent.class);
                log.info("OrderEventConsumer {} received: {}", tag, event);
            } catch (Exception e) {
                log.error("OrderEventConsumer {} failed to deserialize message on subject={}",
                        tag, msg.getSubject(), e);
            }
        };
    }
}