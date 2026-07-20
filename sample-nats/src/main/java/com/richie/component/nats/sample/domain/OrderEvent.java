package com.richie.component.nats.sample.domain;

import java.io.Serializable;

/**
 * 订单事件 POJO。
 *
 * <h2>用途</h2>
 * <p>本类是 sample-nats 中的通用事件载荷,在以下三类场景中复用:</p>
 * <ul>
 *     <li><b>Core NATS 事件</b> — 通过 {@code NatsBus.publish} 发往
 *         {@code orders.event.created} / {@code orders.queue.dispatched}</li>
 *     <li><b>JetStream 持久化事件</b> — 通过 {@code JetStreamBus.publish} 写入
 *         {@code ORDERS} Stream,可被持续消费与按需拉取</li>
 *     <li><b>RPC 同步</b> — 作为 {@code rpc.order.sync} 端点的请求体,
 *         由服务端处理后回写 {@link OrderResponse}</li>
 * </ul>
 *
 * <h2>为什么实现 {@link Serializable}</h2>
 * <p>虽然 NATS 组件默认使用 JSON 序列化(无强类型耦合),实现
 * {@code Serializable} 主要是为了在以下场景保持字节兼容性:</p>
 * <ul>
 *     <li>JetStream 在重投递(re-deliver)场景下保留原始字节,若未来切换
 *         至 JDK 序列化或 Kryo 等可序列化方案,无需再改 POJO</li>
 *     <li>RPC 处理器在未来升级为带类型校验的解码器时,反序列化路径更短</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public class OrderEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID。生产中通常为业务侧雪花/UUID,在 NATS 端无业务含义,
     * 仅作载荷字段参与序列化。
     */
    private String orderId;

    /**
     * 客户名称。仅作演示,生产中应替换为 customerId + 客户主数据服务
     * 反查以减少载荷大小。
     */
    private String customer;

    /**
     * 订单金额(以"分"为单位的长整型,避免浮点精度问题)。
     * 序列化为 JSON 时显示为数字。
     */
    private Long amount;

    /**
     * 订单状态,推荐取值:
     * <ul>
     *     <li>{@code CREATED} — 已创建</li>
     *     <li>{@code PAID} — 已支付</li>
     *     <li>{@code SHIPPED} — 已发货</li>
     *     <li>{@code CANCELLED} — 已取消</li>
     * </ul>
     * 出于演示灵活性保留为 String;生产代码建议改为枚举并通过 Jackson
     * 注解映射,避免拼写错误。
     */
    private String status;

    public OrderEvent() {
    }

    public OrderEvent(String orderId, String customer, Long amount, String status) {
        this.orderId = orderId;
        this.customer = customer;
        this.amount = amount;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OrderEvent{orderId='" + orderId + "', customer='" + customer
                + "', amount=" + amount + ", status='" + status + "'}";
    }
}