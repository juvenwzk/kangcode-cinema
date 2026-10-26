package com.kangcode.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文冒烟测试模板。
 * <p>默认禁用：启动 Spring 容器需要 MySQL / Redis / RabbitMQ 就绪。
 * 中间件就绪后删除 {@code @Disabled}，即可验证整个应用能正常装配、启动。</p>
 */
@Disabled("需要 MySQL/Redis/RabbitMQ 就绪后手动开启")
@SpringBootTest
class MovieTicketApplicationTests {

    @Test
    void contextLoads() {
    }
}
