package com.richie.component.nats.sample.constants;

/**
 * NATS subject 与 JetStream stream / consumer 命名常量。
 *
 * <h2>为什么集中管理</h2>
 * <p>NATS 协议下,生产端、订阅端、JetStream 流的 {@code subjects} 配置
 * 必须使用完全一致的字符串字面量,任何一处拼写差异都会导致消息"看上去发了
 * 但收不到"或"收到了但 Stream 不接收"。把全部命名收敛到本接口,既便于
 * 跨进程协调(本工程未拆分多模块时也维持一致的查找入口),又能在重构时
 * 借助 IDE 的重命名能力一次性覆盖所有引用点。</p>
 *
 * <h2>命名空间约定</h2>
 * <ul>
 *     <li>{@code orders.*} — 业务事件 subject 前缀(Core NATS 区域)</li>
 *     <li>{@code rpc.order.*} — RPC 端点 subject(Core NATS Request-Reply)</li>
 *     <li>{@code orders.persistent} — JetStream 持久化 subject,必须落在
 *         {@link #ORDER_STREAM} 的 {@code subjects} 列表内</li>
 *     <li>流(Stream)名采用大写、消费者(Consumer)名采用小写中划线风格,
 *         与 NATS 官方文档示例保持一致</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface SubjectConstant {

    /**
     * 普通发布订阅 subject(订单事件,fire-and-forget fanout)。
     *
     * <p>订阅方参见 {@link com.richie.component.nats.sample.consumer.OrderEventConsumer}
     * 中第一种 subscribe —— 该订阅没有 queueGroup,每个订阅实例都会收到
     * 一份相同的消息,适合事件通知类业务。</p>
     */
    String ORDER_EVENT_SUBJECT = "orders.event.created";

    /**
     * 队列组模式 subject(同一 queueGroup 内仅一个订阅者消费,实现工作负载均衡)。
     *
     * <p>生产端发布到该 subject 时,NATS 会从所有同属 {@code order-worker}
     * 队列组的订阅者中挑选一个投递,适合"派单/任务分发"等只应被处理一次
     * 的场景。</p>
     */
    String ORDER_QUEUE_SUBJECT = "orders.queue.dispatched";

    /**
     * 通配符 subject 演示(订阅 {@code orders.>},匹配所有以 {@code orders.}
     * 开头的事件)。
     *
     * <p>NATS 的 {@code >} 是多段通配符(可匹配中间任意段),{@code *}
     * 是单段通配符(只匹配一段)。本工程用 {@code >} 演示全量捕获
     * 业务事件,用于审计/观测类消费者。</p>
     */
    String ORDER_WILDCARD_SUBJECT = "orders.>";

    /**
     * RPC request subject(订单查询,单实例处理)。
     *
     * <p>参见 {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint}
     * 中第一个 registerHandler —— 没有 queueGroup,任意收到该 subject 的
     * 节点都会响应,适合无状态查询。</p>
     */
    String ORDER_RPC_QUERY_SUBJECT = "rpc.order.query";

    /**
     * RPC request subject(订单同步,队列组处理)。
     *
     * <p>参见 {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint}
     * 中第二个 registerHandler —— 加入 {@code order-sync-worker} 队列组,
     * 多实例部署时只有一个实例处理,适合幂等写下游系统的同步任务。</p>
     */
    String ORDER_RPC_SYNC_SUBJECT = "rpc.order.sync";

    /**
     * JetStream stream 名称。
     *
     * <p>Stream 是 JetStream 的逻辑存储单元,本工程声明一个名为 {@code ORDERS}
     * 的文件型 Stream(见 {@code application-nats.yml}),用于持久化关键
     * 订单事件。Stream 名称在 NATS 服务端全局唯一,不可与其他业务共用。</p>
     */
    String ORDER_STREAM = "ORDERS";

    /**
     * JetStream subject 前缀(必须属于 {@link #ORDER_STREAM} 的 subjects 配置)。
     *
     * <p>消息发布到 {@code orders.persistent} 时,NATS 会先匹配 Stream 的
     * subjects 列表,只有命中才会被持久化;否则会被服务端拒绝。</p>
     */
    String ORDER_STREAM_SUBJECT = "orders.persistent";

    /**
     * JetStream 持续(push)消费 consumer 名。
     *
     * <p>被 {@link com.richie.component.nats.sample.jetstream.OrderEventJetStreamConsumer}
     * 用来启动长连接 push 订阅,服务端有新消息时主动推送给当前消费者。
     * 同一个 Stream 下可以声明多个 Consumer,各自独立维护投递位点。</p>
     */
    String ORDER_CONSUMER_CONTINUOUS = "order-consumer-continuous";

    /**
     * JetStream 按需(pull)消费 consumer 名。
     *
     * <p>被 {@link com.richie.component.nats.sample.service.impl.NatsDemoServiceImpl#pullOrderEvents}
     * 通过 {@code JetStreamBus.fetch} 批量拉取,适合批处理/定时任务消费者。
     * Consumer 名相同 + 配置一致 ⇒ 服务端视为同一消费者,不会重复投递。</p>
     */
    String ORDER_CONSUMER_PULL = "order-consumer-pull";
}