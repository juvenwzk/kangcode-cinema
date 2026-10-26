package com.kangcode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 康轨 KangRail 高并发铁路购票系统 启动类
 *
 * <p>已开启：
 * <ul>
 *   <li>{@code @EnableCaching} —— 配合 CacheConfig 的 RedisCacheManager</li>
 *   <li>{@code @EnableScheduling} —— 配合 StockReconcileTask 的 {@code @Scheduled} 定时对账。
 *       <b>少了这个注解，@Scheduled 完全不会执行，而且不报任何错</b>，是很容易漏的一步。</li>
 * </ul>
 * 需要异步执行时再补 {@code @EnableAsync}。
 */
@Slf4j
@EnableCaching
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.kangcode")
public class KangRailApplication {

    public static void main(String[] args) {
        SpringApplication.run(KangRailApplication.class, args);
        log.info("KangRail 康轨铁路购票系统启动成功");
    }
}
