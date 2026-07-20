# sample-concurrency

`atlas-richie-component-concurrency` 并发组件的端到端示例工程,覆盖全部公开 API。

## 模块定位

| 项目 | 值 |
|---|---|
| 父模块 | `com.richie.sample:atlas-richie-platform-example` |
| 依赖组件 | `com.richie.component:atlas-richie-component-concurrency` |
| Java 版本 | JDK 25 (使用 `--enable-preview`) |
| 启动类 | `com.richie.component.concurrency.SampleConcurrencyApplication` |
| 编排器 | `com.richie.component.concurrency.SampleOrchestrator` |

## 功能覆盖清单

| Demo | 演示内容 |
|---|---|
| **StructuredConcurrency** | 8 种结构化并发模式:`gatherAll` / `gatherAllSuppliers` / `race` / `raceSuppliers` / `withDeadline` / `gatherBatched` / `gatherAllBestEffort` / `gatherAllBestEffortSuppliers` |
| **VirtualThreadFactory** | `of(namePrefix)` 简单命名工厂;`builder()` 配合 `scopedValue` / `scopedValues` 透传 `ScopedValue`(请求 ID / 租户) |
| **BatchProcessor** | `forEach` 无返回值批量;`mapParallel` 有返回值批量(顺序对齐输入);配合 `Stopwatch` 计时;失败项在结果中保留 `null` 占位 |
| **Retryer** | `execute(Callable)`;`execute(Callable, fallback)` 兜底;`execute(Runnable)` 无返回值;`jitter` 对比 |
| **RateLimiter** | Spring 注入全局 Bean;`tryAcquire()` 非阻塞;`tryAcquire(Duration)` 限时阻塞;`acquire()` 无限阻塞;多 token `tryAcquire(3, Duration)`;8 线程并发抢令牌演示 |
| **CircuitBreaker** | 编程式 `builder()`;CLOSED → OPEN(`execute(fallback)` 兜底)→ HALF_OPEN 探测 → CLOSED;`forceOpen()` / `reset()` 手动控制 |
| **Debouncer** | `of(delay, action)` 工厂;`trigger()` 多次调用合并;`flush()` 立即执行;`cancel()` 取消;`close()` 释放调度器 |
| **DynamicExecutor ⭐** | 4 个构造器重载;`snapshot()` 9 字段快照;`getRejectionCount()` / `resetRejectionCount()`;`onResize(PoolResizeEvent)` 热更新 4 字段(null 字段保持);`setRejectedExecutionHandler` 透明包装;`@Resource` / `@Qualifier` / `Map<String, DynamicExecutor>` 三种注入;`EnvironmentChangeEvent` 配合 `ThreadPoolConfigRefresher` 实现配置中心热更新 |
| **Registry** | `RateLimiterRegistry` / `CircuitBreakerRegistry` 按 key 缓存实例;多租户(clientKey)独立限流 / 独立熔断统计;`find` / `remove` / `keys` / `size` / `clear` 生命周期 |

## 运行方式

### 方式一:Maven 直接运行(推荐开发期)

```bash
cd /Users/richie696/Projects/workspace/atlas-richie-platform-example/sample-concurrency
mvn spring-boot:run
```

### 方式二:打包后运行

```bash
mvn clean package -DskipTests
java --enable-preview -jar target/sample-concurrency.jar
```

### 预期输出

启动后会自动按顺序触发 9 个 demo,每个 demo 用 `╔═╗║ DEMO START: NAME ║╚═╝` 横幅分隔,完成后通过 `System.exit(0)` 退出。

> 注:由于演示熔断 / 重试耗尽 / 拒绝执行是预期路径,会在 `runDemo` 中被捕获并以 `WARN` 级别打印,不会中断后续 demo。

## 配置演示

`application.yml` 演示了三个完整能力域的配置形态:

```yaml
platform:
  concurrency:
    rate-limiter:
      enabled: true
      permits-per-second: 5

    circuit-breaker:
      enabled: true
      failure-rate-threshold: 0.5
      sliding-window-size: 5
      wait-duration: 3s

    thread-pools:
      order-executor:         # 注入: @Resource(name="order-executor")
        core-pool-size: 2
        maximum-pool-size: 4
        queue-capacity: 100
        rejected-handler: "AbortPolicy"

      notification-executor:  # 注入: @Qualifier("notification-executor") + @Autowired
        core-pool-size: 1
        maximum-pool-size: 2
        queue-capacity: 50
        rejected-handler: "CallerRunsPolicy"

      audit-executor:         # 小队列 (20),用于演示拒绝计数
        core-pool-size: 1
        maximum-pool-size: 2
        queue-capacity: 20
        rejected-handler: "AbortPolicy"
```

## 关键设计决策

### 1. JDK 25 + 预览特性

组件使用 `StructuredTaskScope` / `ScopedValue` / `Thread.ofVirtual()`,均为 JDK 21+ 预览 API。Maven 编译 / 运行均强制启用 `--enable-preview`。

### 2. Spring Boot 自动装配复用

- 启用 `rate-limiter.enabled=true` → 自动注册 `RateLimiter` Bean(`destroyMethod=close`)
- 启用 `circuit-breaker.enabled=true` → 自动注册 `CircuitBreaker` Bean
- 配置 `thread-pools.{name}` → 自动为每个命名池注册 `DynamicExecutor` Bean(bean name = key)
- classpath 含 Spring Cloud Context 时,自动注册 `ThreadPoolConfigRefresher` 监听 `EnvironmentChangeEvent`,实现线程池配置中心推送的热更新

### 3. DynamicExecutor 拒绝策略透明包装

`DynamicExecutor.setRejectedExecutionHandler(h)` 会用 `CountingHandler` 包装用户传入的 handler,计数器自增后再委派给用户原始 handler。`getRejectedExecutionHandler()` 返回用户原始 handler,保证外部观察语义一致。

### 4. onResize 的语义

`PoolResizeEvent` 全字段 nullable。调用 `onResize(event)` 时,**只更新非 null 字段**,其余字段保持当前值。这允许运维做"局部热更新",例如只调大 `maximumPoolSize` 而不重置 `corePoolSize`。

### 5. Registry 多租户

`RateLimiterRegistry` / `CircuitBreakerRegistry` 不是单例 bean,而是由调用方持有、按 `clientKey`(IP / 租户 ID / API 路径)缓存独立实例的注册中心。`tenant-A` 触发熔断**不会**影响 `tenant-B` —— 这是与单例 bean 的核心区别。

## 注意事项

- 演示代码中部分场景(如熔断打开 / 重试耗尽 / 拒绝执行)会抛出预期异常,被 `SampleOrchestrator` 捕获并降级为 `WARN` 日志,不会中断整个 demo 流程。
- 由于 `BatchProcessor` / `StructuredConcurrency` 内部使用虚拟线程,运行环境的 `Thread.ofVirtual()` 必须可用(JDK 21+,当前项目锁定 JDK 25)。
- `audit-executor` 的 `queue-capacity=20` 较小,在大量并发任务注入时会触发拒绝,用于演示 `getRejectionCount()` 的计数。

## 与其他 sample 的关系

| Sample | 主题 |
|---|---|
| `sample-cache` | 缓存组件 |
| `sample-threadpool` | 原生 `ThreadPoolExecutor` 用法(对比 `DynamicExecutor` 的差异) |
| **`sample-concurrency`** | **本工程:并发工具箱(虚拟线程 + 限流 + 熔断 + 防抖 + 动态线程池 + Registry)** |
| `sample-logging` | 日志组件 |
| `sample-messaging` | 消息队列组件 |