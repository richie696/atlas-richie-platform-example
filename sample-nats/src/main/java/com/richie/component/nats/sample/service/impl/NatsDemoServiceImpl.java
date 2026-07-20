package com.richie.component.nats.sample.service.impl;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.bus.JetStreamBus;
import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.sample.constants.SubjectConstant;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.sample.domain.OrderRequest;
import com.richie.component.nats.sample.domain.OrderResponse;
import com.richie.component.nats.sample.service.NatsDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.nats.client.FetchConsumer;
import io.nats.client.Message;

/**
 * NATS 演示服务实现。
 *
 * <h2>本类演示的 NATS API</h2>
 * <ul>
 *     <li>{@link NatsBus#publish(String, Object)} — Core NATS fire-and-forget 发布</li>
 *     <li>{@link NatsBus#request(String, Object, Class, Duration)} — Core NATS 同步 RPC</li>
 *     <li>{@link NatsBus#subscribe(String, NatsMessageHandler)} — Core NATS 动态订阅</li>
 *     <li>{@link JetStreamBus#publish(String, String, Object)} — JetStream 持久化发布</li>
 *     <li>{@link JetStreamBus#fetch(String, String, int)} — JetStream 批量拉取</li>
 * </ul>
 *
 * <h2>门面获取策略</h2>
 * <p>每次调用都从 {@link NatsComponent} 现取 {@link NatsBus} /
 * {@link JetStreamBus},而不是缓存为字段。原因是这些门面内部封装了
 * 客户端连接,在断线重连场景下应使用最新实例,避免持有失效引用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NatsDemoServiceImpl implements NatsDemoService {

    /** RPC 默认超时 3 秒,超时后 {@code NatsBus.request} 抛出 TimeoutException。 */
    private static final Duration RPC_TIMEOUT = Duration.ofSeconds(3);

    private final NatsComponent nats;
    private final NatsMessageSerializer serializer;

    @Override
    public void publishOrderCreated(OrderEvent event) {
        log.info("[publishOrderCreated] subject={} event={}", SubjectConstant.ORDER_EVENT_SUBJECT, event);
        nats.bus().publish(SubjectConstant.ORDER_EVENT_SUBJECT, event);
    }

    @Override
    public void publishOrderDispatched(OrderEvent event) {
        log.info("[publishOrderDispatched] subject={} event={}", SubjectConstant.ORDER_QUEUE_SUBJECT, event);
        // 由 queueGroup "order-worker" 中的某一个 worker 消费(负载均衡)
        nats.bus().publish(SubjectConstant.ORDER_QUEUE_SUBJECT, event);
    }

    @Override
    public OrderResponse queryOrder(OrderRequest request) {
        log.info("[queryOrder] subject={} request={}", SubjectConstant.ORDER_RPC_QUERY_SUBJECT, request);
        return nats.bus().request(
                SubjectConstant.ORDER_RPC_QUERY_SUBJECT,
                request,
                OrderResponse.class,
                RPC_TIMEOUT);
    }

    @Override
    public void persistOrderEvent(OrderEvent event) {
        log.info("[persistOrderEvent] stream={} subject={} event={}",
                SubjectConstant.ORDER_STREAM, SubjectConstant.ORDER_STREAM_SUBJECT, event);
        nats.stream().publish(SubjectConstant.ORDER_STREAM, SubjectConstant.ORDER_STREAM_SUBJECT, event);
    }

    @Override
    public List<OrderEvent> pullOrderEvents(int batchSize) {
        List<OrderEvent> events = new ArrayList<>();
        JetStreamBus stream = nats.stream();
        try {
            FetchConsumer consumer = stream.fetch(
                    SubjectConstant.ORDER_STREAM,
                    SubjectConstant.ORDER_CONSUMER_PULL,
                    batchSize);
            try {
                // nextMessage 在批次耗尽或超时返回 null;用 null 跳出循环避免阻塞
                while (true) {
                    Message msg = consumer.nextMessage();
                    if (msg == null) {
                        break;
                    }
                    events.add(serializer.deserialize(msg.getData(), OrderEvent.class));
                    msg.ack();
                }
            } finally {
                consumer.close();
            }
        } catch (Exception e) {
            log.error("[pullOrderEvents] failed to pull events", e);
            throw new IllegalStateException("Failed to pull JetStream events", e);
        }
        log.info("[pullOrderEvents] pulled {} events", events.size());
        return events;
    }

    @Override
    public void registerTempSubscriber(String subject, NatsMessageHandler handler) {
        log.info("[registerTempSubscriber] subject={}", subject);
        nats.bus().subscribe(subject, handler);
    }
}