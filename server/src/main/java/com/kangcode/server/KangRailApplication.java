package com.kangcode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 康轨 KangRail 高并发铁路购票系统 启动类
 *
 * <p>准备工程只开启 @EnableCaching（配合 CacheConfig 的 RedisCacheManager）。
 * 后续需要定时任务时，自己在 P2 任务 2-3 补 @EnableScheduling；
 * 需要异步执行时再补 @EnableAsync。
 */
@Slf4j
@EnableCaching
@SpringBootApplication(scanBasePackages = "com.kangcode")
public class KangRailApplication {

    public static void main(String[] args) {
        SpringApplication.run(KangRailApplication.class, args);
        log.info("KangRail 康轨铁路购票系统启动成功");
    }
}
