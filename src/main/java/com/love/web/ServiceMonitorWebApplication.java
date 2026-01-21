package com.love.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Web应用启动类
 * 运行此类的main方法可以启动Web服务器（默认端口8080）
 */
@SpringBootApplication
public class ServiceMonitorWebApplication extends SpringBootServletInitializer {
    
    public static void main(String[] args) {
        SpringApplication.run(ServiceMonitorWebApplication.class, args);
        System.out.println("========================================");
        System.out.println("服务监控器 Web端已启动");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("API文档: http://localhost:8080/api/services");
        System.out.println("========================================");
    }
}

