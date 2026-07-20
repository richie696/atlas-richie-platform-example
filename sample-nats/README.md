# sample-nats

NATS 组件 `@atlas-richie-component/atlas-richie-component-nats` 的最小可运行演示工程。
作为 NATS 组件的:

1. **功能验证** — 启动后会自动连接 NATS、创建 Stream / Consumer，并提供 REST 触发端点。
2. **开发者参考** — 以最小单元演示 `NatsBus`、`NatsEndpoint`、`JetStreamBus` 三大门面的典型用法。

## 模块结构

```
sample-nats
├── pom.xml
├── README.md
└── src/main
    ├── java/com/richie/component/nats/sample
    │   ├── SampleNatsApplication.java          # 启动类
    │   ├── constants/SubjectConstant.java      # 主题/Stream/Consumer 常量
    │   ├── domain/                              # 事件 / 请求 / 响应 POJO
    │   │   ├── OrderEvent.java
    │   │   ├── OrderRequest.java
    │   │   └── OrderResponse.java
    │   ├── service/                             # REST 后端的业务调用
    │   │   ├── NatsDemoService.java
    │   │   └── impl/NatsDemoServiceImpl.java   # 调用 NatsBus / JetStreamBus
    │   ├── consumer/OrderEventConsumer.java    # 三种 NatsBus.subscribe
    │   ├── endpoint/OrderRpcEndpoint.java      # 两种 NatsEndpoint.registerHandler
    │   ├── jetstream/OrderEventJetStreamConsumer.java  # JetStreamBus.consume 持续消费
    │   └── controller/NatsDemoController.java  # REST API
    └── resources/
        ├── application.yml
        ├── application-nats.yml
        └── logback-spring.xml
```

## 演示的 NATS 用法

| 门面 | 方法 | 演示场景 |
|------|------|----------|
| `NatsBus` | `publish(subject, msg)` | 普通发布-订阅、队列组负载均衡、通配符订阅 |
| `NatsBus` | `request(subject, req, respType, timeout)` | 同步 request-reply RPC |
| `NatsBus` | `requestAsync(...)` | 异步 RPC（带超时） |
| `NatsBus` | `subscribe(subject, queueGroup, handler)` | 队列组订阅（负载均衡） |
| `NatsBus` | `subscribe("orders.>", handler)` | 通配符订阅 |
| `NatsEndpoint` | `registerHandler(subject, type, fn)` | 注册 RPC 处理器 |
| `NatsEndpoint` | `registerHandler(subject, queueGroup, type, fn)` | 队列组 RPC 处理器 |
| `JetStreamBus` | `publish(stream, subject, msg)` | 持久化发布（带回执 PublishAck） |
| `JetStreamBus` | `consume(stream, consumer, handler)` | 持续消费（自动 ack/nak） |
| `JetStreamBus` | `fetch(stream, consumer, batchSize)` | 批量拉取 |

## 运行前提

### 1. NATS 服务器（带 JetStream + NUI 管理界面）

推荐使用工程根目录的 `docker-compose.yml` 一键启动（含 NATS + NUI）：

```bash
cd /path/to/atlas-richie-platform-example
docker-compose up -d
```

启动后暴露的端口：

| 端口 | 用途 | 验证方式 |
|------|------|----------|
| `4222` | NATS 客户端连接 | `nc -z localhost 4222` |
| `8222` | NATS HTTP 监控 API | `curl http://localhost:8222/varz` |
| `8222` | JetStream 状态 | `curl http://localhost:8222/jsz` |
| `31311` | NATS NUI Web 管理界面 | 浏览器打开 `http://localhost:31311` |

如果只想要最简的 NATS 实例（不带 NUI）：

```bash
docker run -d --rm --name nats-js \
  -p 4222:4222 \
  -p 8222:8222 \
  nats:2.10-alpine \
  -js -m 8222
```

### 2. 平台组件 install 到本地仓库

sample-nats 依赖 `atlas-richie-component-nats`，首次运行前需要在
`atlas-richie-platform` 根目录执行一次 `install`（触发 flatten 解析 `${revision}`）：

```bash
cd /path/to/atlas-richie-platform
mvn install -DskipTests
```

### 3. 启动 sample-nats

```bash
cd /path/to/atlas-richie-platform-example/sample-nats
mvn spring-boot:run
```

服务监听 `8892` 端口。可通过环境变量切换连接地址：

```bash
NATS_SERVER=nats://remote-host:4222 mvn spring-boot:run
```

启动日志关键标识：

```
NATS Component Started. Connection State: CONNECTED
JetStream Bus auto-provision enabled. Stream=ORDERS, consumers=2
```

## REST 触发示例

```bash
# 1. 普通事件发布（fanout：所有订阅者都会收到）
curl -X POST http://localhost:8892/nats/publish/event \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"O-001","customer":"alice","amount":99.5,"status":"CREATED"}'

# 2. 队列组发布（负载均衡：只会被一个 worker 消费）
curl -X POST http://localhost:8892/nats/publish/queue \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"O-002","customer":"bob","amount":50,"status":"DISPATCHED"}'

# 3. RPC 查询（同步 request-reply，由 OrderRpcEndpoint 处理器响应）
curl -X POST http://localhost:8892/nats/rpc/query \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"O-001","mode":"full"}'

# 4. JetStream 持久化发布
curl -X POST http://localhost:8892/nats/jetstream/publish \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"O-003","customer":"carol","amount":120,"status":"PAID"}'

# 5. JetStream 批量拉取（fetch）
curl http://localhost:8892/nats/jetstream/pull?batchSize=10

# 6. 查看 NATS 连接状态
curl http://localhost:8892/nats/status
```

## 验证 NATS 服务端确实收到消息

启动 sample-nats 并触发几次发布后，**通过 NUI 或 curl 验证**：

### 通过 HTTP 监控 API

```bash
# 查看 server / connection 状态
curl http://localhost:8222/varz | jq '{server_name, version, connections}'

# 查看 JetStream 状态(确认 stream / consumer / messages 计数)
curl http://localhost:8222/jsz | jq '{streams, consumers, messages, bytes}'

# 查看指定 stream 详情
curl 'http://localhost:8222/jsz?streams=true&account=_G' | jq
```

### 通过 NUI Web 管理界面

浏览器打开 `http://localhost:31311`，可以看到：

- **Stream 列表**：`ORDERS` stream 已自动创建
- **Consumer 列表**：`order-consumer-continuous` / `order-consumer-pull` 已自动注册
- **实时消息流**：观察 `orders.persistent` 主题的流入消息
- **连接详情**：sample-nats 的客户端连接、订阅关系

## 主题 / Stream 命名约定

集中在 `SubjectConstant`，避免散落：

- `orders.event.created` — 普通事件（fanout）
- `orders.queue.dispatched` — 队列组事件（负载均衡）
- `orders.>` — 通配符订阅（接收所有 `orders.` 开头的事件）
- `rpc.order.query` — RPC 查询
- `rpc.order.sync` — 队列组 RPC 同步
- Stream `ORDERS` / subject `orders.persistent` — JetStream 持久化
- Consumer `order-consumer-continuous` — 持续消费
- Consumer `order-consumer-pull` — 拉取消费

## 关键设计决策

### 单模块结构
NATS 组件不区分多后端（不像 messaging 有 redis/kafka/rabbit），所有 NATS 能力
集中在一个应用里通过 REST 触发，开发者可一次看到全部用法。

### 显式 lombok 注解处理配置
example 工程父 POM（`atlas-richie-dependencies`）的 pluginManagement
**未配置** `maven-compiler-plugin` 的 `annotationProcessorPaths`，且 example
顶层 pom 也未继承平台根 pom 的 active `<plugins>`。直接编译 sample-* 时
`@Slf4j` / `@RequiredArgsConstructor` 等注解不会生成符号。

sample-nats/pom.xml 在 `<build><plugins>` 中显式声明 lombok 注解处理器，
与平台根 pom 851~883 行保持一致，让本模块独立可编译。

### JetStream 自动装配
需要在配置里开启：
```yaml
platform.nats:
  jetstream:
    enabled: true
    auto-provision: true
    streams:
      - name: ORDERS
        subjects: [orders.persistent]
        consumers:
          - name: order-consumer-continuous
            type: push
          - name: order-consumer-pull
            type: pull
```

## 参考

- 单元测试与集成测试：见 `atlas-richie-component-nats/src/test`
- 蓝本模块：`sample-mqtt-client`（同结构，MQTT 版）
- NATS 官方文档：https://docs.nats.io/
- NUI 管理界面：https://github.com/nats-nui/nui
