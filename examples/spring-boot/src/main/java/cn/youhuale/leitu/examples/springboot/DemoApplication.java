package cn.youhuale.leitu.examples.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 金样本：最小 Spring Boot 应用——引入 leitu-spring-boot-starter 依赖即得到全部默认装配
 * （执行上下文 / 判定链 / 观测 / 配置 / 缓存策略 / JDBC 工厂 / 失败投影 / 文档端点）。
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
