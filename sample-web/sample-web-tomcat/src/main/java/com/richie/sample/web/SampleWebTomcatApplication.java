package com.richie.sample.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * sample-web-tomcat 启动类 —— 仅保留 main。
 * 端点逻辑见 {@link com.richie.sample.web.controller.TomcatWebController}。
 */
@SpringBootApplication
public class SampleWebTomcatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleWebTomcatApplication.class, args);
    }
}