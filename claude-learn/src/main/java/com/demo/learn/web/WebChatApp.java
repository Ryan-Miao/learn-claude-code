package com.demo.learn.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 聊天入口。启动 Tomcat 提供 REST API。
 * 与 S01-S12 的 CLI 入口独立，不影响现有命令行交互。
 */
@SpringBootApplication(scanBasePackages = {"com.demo.learn.core", "com.demo.learn.web"})
public class WebChatApp {
    public static void main(String[] args) {
        SpringApplication.run(WebChatApp.class, args);
    }
}
