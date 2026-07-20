package com.richie.component.nats.sample;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * NATS 组件功能演示程序的启动入口。
 *
 * <h2>模块定位</h2>
 * <p>本类是 {@code sample-nats} 模块的 Spring Boot 启动类,作为
 * {@code atlas-richie-component-nats} 组件的最小可运行参考工程。
 * 它本身不实现任何业务,仅装配以下演示类:</p>
 * <ul>
 *     <li>{@link com.richie.component.nats.sample.consumer.OrderEventConsumer} —
 *         Core NATS 三种订阅模式(普通/队列组/通配符)的演示</li>
 *     <li>{@link com.richie.component.nats.sample.endpoint.OrderRpcEndpoint} —
 *         Core NATS Request-Reply RPC 处理器演示</li>
 *     <li>{@link com.richie.component.nats.sample.jetstream.OrderEventJetStreamConsumer} —
 *         JetStream 持续消费(自动 ack/nak)演示</li>
 *     <li>{@link com.richie.component.nats.sample.controller.NatsDemoController} —
 *         暴露 REST 端点,触发上述三类 NATS 操作</li>
 * </ul>
 *
 * <h2>三类 NATS 核心能力</h2>
 * <ul>
 *     <li><b>NatsBus</b>:基于 Core NATS 的发布订阅(fire-and-forget),
 *         适合无需持久化、追求极致吞吐的实时通知场景</li>
 *     <li><b>NatsEndpoint</b>:基于 Core NATS Request-Reply 模型的同步 RPC,
 *         适合低延迟点对点查询/指令调用</li>
 *     <li><b>JetStreamBus</b>:基于 JetStream 的持久化消息与消费者,
 *         适合需要至少一次投递、消息重放、消费者组管理等场景</li>
 * </ul>
 *
 * <h2>扫描范围说明</h2>
 * <p>{@code @SpringBootApplication(scanBasePackages = "com.richie")} 使用顶层
 * 包路径扫描,目的是让 {@code com.richie.component.nats.sample.*} 下的所有
 * 演示 Bean 与平台组件包 {@code com.richie.component.nats.*} 内的自动装配
 * 类都能被 Spring 注册。</p>
 *
 * <h2>启动顺序</h2>
 * <ol>
 *     <li>读取 {@code application.yml} + {@code application-nats.yml},
 *         解析 NATS 服务端地址、JetStream 流/消费者配置</li>
 *     <li>NATS 组件自动装配:建立连接,按配置声明 Stream 与 Consumer</li>
 *     <li>{@code @PostConstruct} 阶段:Core NATS 订阅者与 RPC handler 注册</li>
 *     <li>{@link com.richie.component.nats.sample.jetstream.OrderEventJetStreamConsumer}
 *         作为 {@code ApplicationRunner} 注册 JetStream 持续消费</li>
 *     <li>打印 banner,服务监听 {@code server.port}(默认 8892)</li>
 * </ol>
 *
 * <h2>运行示例</h2>
 * <pre>
 *   mvn spring-boot:run
 *   # 或切换 NATS 服务端:
 *   NATS_SERVER=nats://remote-host:4222 mvn spring-boot:run
 * </pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.richie")
public class SampleNatsApplication {

    /**
     * Spring Boot 标准启动方法。
     *
     * <p>流程:</p>
     * <ol>
     *   <li>调用 {@link SpringApplication#run(Class, String...)} 引导上下文,
     *       此过程会触发 NATS 组件的 {@code SmartLifecycle.start()} 完成
     *       连接建立、Stream/Consumer 声明;</li>
     *   <li>从 {@link Environment} 解析应用名、端口、上下文路径;</li>
     *   <li>解析本机 IP(供 External URL 展示);</li>
     *   <li>打印启动 banner 块,统一展示本地/外部 URL、Swagger 与 Actuator 入口,
     *       便于开发者快速找到调试地址。</li>
     * </ol>
     *
     * @param args 启动参数,可用于覆盖 {@code spring.application.name}、
     *             {@code server.port} 等 Spring 标准参数
     * @throws UnknownHostException 解析本机 IP 失败时抛出,通常出现在
     *                              容器内未正确配置 /etc/hosts 的场景
     */
    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(SampleNatsApplication.class, args);

        Environment env = application.getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("local.server.port");
        // context-path 可能为 null,使用 Objects.toString 兜底为 "" 再去空白
        String path = Objects.toString(env.getProperty("server.servlet.context-path"), "").trim();

        log.info("""

                ---------------------------------------------------------------
                OS Name\t\t\t\t{}
                Local URL\t\thttp://localhost:{}{}/
                External URL\thttp://{}:{}{}/
                Api Doc URL\t\thttp://{}:{}{}/swagger-ui.html
                Actuator URL\thttp://{}:{}{}/actuator

                Application "{}" is running!
                ---------------------------------------------------------------
                """, System.getProperty("os.name"), port, path, ip, port, path, ip, port, path, ip, port, path, appName);
    }
}