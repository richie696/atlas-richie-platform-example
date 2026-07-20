package com.richie.component.nats.sample.domain;

import java.io.Serializable;

/**
 * 订单查询/同步 RPC 响应 DTO。
 *
 * <h2>用途</h2>
 * <p>在 sample-nats 中,本 DTO 是两类 RPC 端点的统一响应:</p>
 * <ul>
 *     <li>{@code rpc.order.query} — {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint#handleQuery}
 *         收到 {@link OrderRequest} 后返回的订单详情</li>
 *     <li>{@code rpc.order.sync} — {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint#handleSync}
 *         收到 {@link OrderEvent} 后返回的同步结果</li>
 * </ul>
 *
 * <p>字段中除订单基础信息外,还包含 {@link #handledBy} 字段用于演示
 * 多实例部署时由哪个节点处理了本次请求 —— 通过观察该字段的实例名前缀
 * 是否轮换,可以快速验证 queueGroup 负载均衡是否生效。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class OrderResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID(回传请求中的 orderId,便于调用方做关联)。
     */
    private String orderId;

    /**
     * 订单状态(查询时为当前状态;同步时为处理结果状态,如 {@code SYNCED})。
     */
    private String status;

    /**
     * 客户名称。
     */
    private String customer;

    /**
     * 订单金额(以"分"为单位的长整型)。
     */
    private Long amount;

    /**
     * 处理节点标识,格式为 {@code <spring.application.name>/<短UUID>},由
     * {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint}
     * 在响应构造时填充。本字段不参与业务,仅用于演示负载均衡。
     */
    private String handledBy;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String status, String customer, Long amount, String handledBy) {
        this.orderId = orderId;
        this.status = status;
        this.customer = customer;
        this.amount = amount;
        this.handledBy = handledBy;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    @Override
    public String toString() {
        return "OrderResponse{orderId='" + orderId + "', status='" + status
                + "', customer='" + customer + "', amount=" + amount
                + ", handledBy='" + handledBy + "'}";
    }
}