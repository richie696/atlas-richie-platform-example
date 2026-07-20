package com.richie.component.nats.sample.controller;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.enums.ConnectionState;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.sample.domain.OrderRequest;
import com.richie.component.nats.sample.domain.OrderResponse;
import com.richie.component.nats.sample.service.NatsDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NATS 演示 REST 控制器。
 *
 * <h2>本控制器的角色</h2>
 * <p>作为 sample-nats 的 HTTP 入口,把 NATS 的三类核心操作以 REST 形式
 * 暴露出去。开发者无需编写 NATS 客户端代码,直接通过 {@code curl} 即可
 * 触发发布、RPC、JetStream 拉取等操作,便于快速验证组件能力和调试。</p>
 *
 * <h2>端点列表</h2>
 * <ul>
 *     <li><b>POST /nats/publish/event</b> — Core NATS 普通发布(fanout)</li>
 *     <li><b>POST /nats/publish/queue</b> — Core NATS 队列组发布(worker 负载均衡)</li>
 *     <li><b>POST /nats/rpc/query</b> — Core NATS 同步 RPC 查询</li>
 *     <li><b>POST /nats/jetstream/publish</b> — JetStream 持久化发布</li>
 *     <li><b>GET /nats/jetstream/pull</b> — JetStream 批量拉取</li>
 *     <li><b>GET /nats/status</b> — 查看 NATS 连接状态</li>
 * </ul>
 *
 * <h2>调用示例</h2>
 * <pre>
 * curl -X POST http://localhost:8892/nats/publish/event \
 *   -H 'Content-Type: application/json' \
 *   -d '{"orderId":"O-001","customer":"alice","amount":99.5,"status":"CREATED"}'
 *
 * curl http://localhost:8892/nats/jetstream/pull?batchSize=10
 * curl http://localhost:8892/nats/status
 * </pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/nats")
@RequiredArgsConstructor
public class NatsDemoController {

    /**
     * 失败注入计数器:由 {@link #injectFail(int)} 写入,
     * 由 {@link com.richie.component.nats.sample.consumer.OrderEventConsumer} 的业务
     * handler 读取并触发 RuntimeException,用于 NATS JetStream max-deliver → DLQ 验证。
     * 采用 static 共享是为了避免在 controller 与 consumer 之间引入新的 Spring 依赖。
     */
    public static final AtomicInteger REMAINING_FAIL_COUNT = new AtomicInteger(0);

    private final NatsDemoService natsDemoService;
    private final NatsComponent nats;

    /**
     * Core NATS 发布普通事件(订单已创建)。
     * 会被所有 {@code orders.event.created} 的普通订阅者收到(fanout)。
     */
    @PostMapping("/publish/event")
    public Map<String, Object> publishEvent(@RequestBody OrderEvent event) {
        natsDemoService.publishOrderCreated(event);
        return Map.of("status", "published", "subject", "orders.event.created", "event", event);
    }

    /**
     * Core NATS 队列组发布(订单派单)。
     * 队列组 {@code order-worker} 内只会有一个 worker 实际消费。
     */
    @PostMapping("/publish/queue")
    public Map<String, Object> publishQueue(@RequestBody OrderEvent event) {
        natsDemoService.publishOrderDispatched(event);
        return Map.of("status", "published", "subject", "orders.queue.dispatched", "event", event);
    }

    /**
     * Core NATS 同步 RPC(订单查询),由
     * {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint#handleQuery}
     * 端点响应。
     */
    @PostMapping("/rpc/query")
    public OrderResponse rpcQuery(@RequestBody OrderRequest request) {
        return natsDemoService.queryOrder(request);
    }

    /**
     * JetStream 持久化发布,消息写入 {@code ORDERS} Stream 的
     * {@code orders.persistent} subject,服务端返回 PublishAck。
     */
    @PostMapping("/jetstream/publish")
    public Map<String, Object> jetstreamPublish(@RequestBody OrderEvent event) {
        natsDemoService.persistOrderEvent(event);
        return Map.of(
                "status", "persisted",
                "stream", "ORDERS",
                "subject", "orders.persistent",
                "event", event);
    }

    /**
     * JetStream 拉取一批持久化消息(走 {@code order-consumer-pull} pull consumer),
     * 适合批处理场景。
     *
     * @param batchSize 最大条数,默认 10
     */
    @GetMapping("/jetstream/pull")
    public Map<String, Object> jetstreamPull(@RequestParam(defaultValue = "10") int batchSize) {
        List<OrderEvent> events = natsDemoService.pullOrderEvents(batchSize);
        return Map.of(
                "status", "fetched",
                "count", events.size(),
                "events", events);
    }

    /**
     * 查看 NATS 连接状态({@link ConnectionState} 枚举名,例如
     * {@code CONNECTED} / {@code RECONNECTING} / {@code CLOSED})。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        ConnectionState state = nats.getState();
        return Map.of(
                "status", "ok",
                "connectionState", state.name());
    }

    /**
     * 失败注入端点:让后续 count 条 OrderEvent 业务 handler 抛 RuntimeException,用于触发 NATS max-deliver → DLQ 验证。
     * 计数用 AtomicInteger,触发后自动归零,无副作用。
     *
     * @param count 期望注入失败次数(默认 3,与业务 consumer max-deliver=3 对齐)
     */
    @PostMapping("/jetstream/inject-fail")
    public void injectFail(@RequestParam(defaultValue = "3") int count) {
        REMAINING_FAIL_COUNT.set(count);
        log.warn("Fail injection armed: {} future handler calls will throw", count);
    }
}