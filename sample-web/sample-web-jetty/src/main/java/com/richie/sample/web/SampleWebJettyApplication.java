package com.richie.sample.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * sample-web-jetty 启动类 —— 仅保留 main。
 * <p>采用 Spring Boot 默认扫描（{@code @SpringBootApplication} 已隐含
 * {@code @ComponentScan(此 .class 所在包)})，与 sample-web-tomcat 行为对齐——
 * 历史曾用过 {@code @ComponentScan(basePackages = "com.richie")}，那是 {@code SseManager}
 * 靠 {@code @Component} 注册时的兼容写法；现已统一由
 * {@code com.richie.component.web.core.config.WebAutoConfiguration} 通过
 * {@code META-INF/spring/...AutoConfiguration.imports} 装配，宽扫不再需要。
 * <p>端点逻辑见 {@link com.richie.sample.web.controller.JettyWebController}。
 */
@SpringBootApplication
public class SampleWebJettyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleWebJettyApplication.class, args);
    }
}