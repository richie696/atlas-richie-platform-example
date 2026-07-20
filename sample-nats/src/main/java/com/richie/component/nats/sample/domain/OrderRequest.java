package com.richie.component.nats.sample.domain;

import java.io.Serializable;

/**
 * 订单查询 RPC 请求 DTO。
 *
 * <h2>用途</h2>
 * <p>作为 {@code rpc.order.query} Request-Reply 调用的请求体,被
 * {@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint#handleQuery}
 * 接收并处理,响应返回 {@link OrderResponse}。</p>
 *
 * <p>演示客户端可通过 {@code POST /nats/rpc/query} 传入,也可在 Java
 * 代码中直接调用 {@code NatsBus.request(subject, request, OrderResponse.class, timeout)}。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class OrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID,必填字段。空值时服务端可按业务约定返回错误响应。
     */
    private String orderId;

    /**
     * 查询模式,控制响应字段的丰富度,推荐取值:
     * <ul>
     *     <li>{@code simple}(默认) — 仅返回订单状态、金额等核心字段</li>
     *     <li>{@code full} — 返回完整订单详情(预留扩展位)</li>
     * </ul>
     * 默认值在字段声明处初始化,避免调用方遗漏设置时服务端拿到 null。
     */
    private String mode = "simple";

    public OrderRequest() {
    }

    public OrderRequest(String orderId) {
        this.orderId = orderId;
    }

    public OrderRequest(String orderId, String mode) {
        this.orderId = orderId;
        this.mode = mode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "OrderRequest{orderId='" + orderId + "', mode='" + mode + "'}";
    }
}