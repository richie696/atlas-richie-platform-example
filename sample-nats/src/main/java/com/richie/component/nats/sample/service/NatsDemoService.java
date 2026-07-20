package com.richie.component.nats.sample.service;

import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.sample.domain.OrderEvent;
import com.richie.component.nats.sample.domain.OrderRequest;
import com.richie.component.nats.sample.domain.OrderResponse;

import java.util.List;

/**
 * NATS 组件演示服务门面。
 *
 * <h2>为什么需要这层门面</h2>
 * <p>本接口把 NATS 三类核心操作({@code NatsBus} / {@code NatsEndpoint} /
 * {@code JetStreamBus})封装成业务可直接调用的方法。这样做有两个好处:</p>
 * <ul>
 *     <li>业务侧(本工程的 REST Controller)只依赖本服务接口,不直接耦合
 *         NATS 协议层,未来替换实现时影响面最小</li>
 *     <li>展示"如何把 NATS API 包装成业务语义",例如
 *         {@link #publishOrderCreated} / {@link #publishOrderDispatched}
 *         在调用方看来只是业务事件,不需要关心底层 subject</li>
 * </ul>
 *
 * <h2>方法分组</h2>
 * <ul>
 *     <li><b>Core NATS 发布</b>:{@link #publishOrderCreated}、
 *         {@link #publishOrderDispatched}</li>
 *     <li><b>Core NATS RPC</b>:{@link #queryOrder}</li>
 *     <li><b>JetStream 持久化与消费</b>:{@link #persistOrderEvent}、
 *         {@link #pullOrderEvents}</li>
 *     <li><b>动态订阅</b>:{@link #registerTempSubscriber}</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsDemoService {

    /**
     * Core NATS 事件发布(订单已创建)。
     *
     * @param event 订单事件载荷
     */
    void publishOrderCreated(OrderEvent event);

    /**
     * Core NATS 队列组发布(订单已派单,负载均衡到 worker)。
     *
     * @param event 订单事件载荷
     */
    void publishOrderDispatched(OrderEvent event);

    /**
     * Core NATS RPC 同步调用(查询订单),由
     * {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint}
     * 端点响应。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    OrderResponse queryOrder(OrderRequest request);

    /**
     * JetStream 持久化发布(关键订单事件,可被重投递消费)。
     *
     * @param event 订单事件载荷
     */
    void persistOrderEvent(OrderEvent event);

    /**
     * 拉取一批 JetStream 持久化消息。
     *
     * @param batchSize 最大条数
     * @return 反序列化后的 OrderEvent 列表(可能为空)
     */
    List<OrderEvent> pullOrderEvents(int batchSize);

    /**
     * 注册一个临时订阅者,用于演示发布订阅。
     *
     * @param subject 待订阅的 subject
     * @param handler 消息处理回调
     */
    void registerTempSubscriber(String subject, NatsMessageHandler handler);
}