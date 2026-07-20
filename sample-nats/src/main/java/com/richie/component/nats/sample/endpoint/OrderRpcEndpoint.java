package com.richie.component.nats.sample.endpoint;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.bus.NatsEndpoint;
import com.richie.component.nats.sample.constants.SubjectConstant;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.sample.domain.OrderRequest;
import com.richie.component.nats.sample.domain.OrderResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 订单 RPC 端点。
 *
 * <h2>本类的演示目标</h2>
 * <p>展示 NATS Request-Reply 模式的服务端注册。客户端调用
 * {@code NatsBus.request(subject, req, OrderResponse.class, timeout)} 后,
 * NATS 组件会自动把请求体反序列化到本端点注册的 handler 参数类型,并把
 * handler 返回值序列化后回写到请求的 {@code replyTo} subject。</p>
 *
 * <h2>两种注册方式</h2>
 * <ul>
 *     <li><b>普通 register</b>(无 queueGroup)— 任意收到该 subject 的实例
 *         都会处理,适合"任意节点都能查"的无状态查询</li>
 *     <li><b>queueGroup register</b>(加入 {@code order-sync-worker})—
 *         同组内只一个实例处理,多副本部署时形成 RPC 层的负载均衡,
 *         适合幂等写下游系统</li>
 * </ul>
 *
 * <h2>响应中的 handledBy 字段</h2>
 * <p>handler 返回的 {@link OrderResponse#getHandledBy()} 形如
 * {@code <spring.application.name>/<短UUID>},便于调用方在多实例场景
 * 观察负载均衡是否生效 —— 通过对比多次响应的实例名前缀是否轮换即可
 * 验证。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRpcEndpoint {

    private final NatsComponent nats;

    /**
     * 处理节点标识,默认从 {@code spring.application.name} 注入,
     * 回写到 {@link OrderResponse#handledBy} 便于调用方观察负载均衡。
     */
    @Value("${spring.application.name:sample-nats}")
    private String handledBy;

    /**
     * 注册两类 RPC handler,容器启动后立即生效。
     */
    @PostConstruct
    public void register() {
        NatsEndpoint endpoint = nats.endpoint();

        // 普通 RPC Handler:订单查询(无 queueGroup,所有节点均可处理)
        endpoint.registerHandler(
                SubjectConstant.ORDER_RPC_QUERY_SUBJECT,
                OrderRequest.class,
                this::handleQuery);

        // queueGroup RPC Handler:订单同步(加入 order-sync-worker 队列组)
        endpoint.registerHandler(
                SubjectConstant.ORDER_RPC_SYNC_SUBJECT,
                "order-sync-worker",
                OrderEvent.class,
                this::handleSync);

        log.info("OrderRpcEndpoint registered. handledBy={}", handledBy);
    }

    /**
     * 订单查询 handler,模拟根据订单 ID 返回订单详情。
     *
     * <p>handler 抛出的异常会被 NATS 组件捕获并回写为错误响应给调用方,
     * 详见 {@code NatsBus.request} 的错误处理约定。</p>
     */
    private OrderResponse handleQuery(OrderRequest request) {
        log.info("[OrderRpcEndpoint] handleQuery: {}", request);
        return new OrderResponse(
                request.getOrderId(),
                "CREATED",
                "demo-customer",
                9999L,
                handledBy + "/" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 订单同步 handler,模拟写入下游系统并回写处理结果。
     */
    private OrderResponse handleSync(OrderEvent event) {
        log.info("[OrderRpcEndpoint] handleSync: {}", event);
        return new OrderResponse(
                event.getOrderId(),
                "SYNCED",
                event.getCustomer(),
                event.getAmount(),
                handledBy + "/" + UUID.randomUUID().toString().substring(0, 8));
    }
}