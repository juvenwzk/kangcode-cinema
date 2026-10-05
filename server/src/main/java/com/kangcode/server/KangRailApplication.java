package com.kangcode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 康轨 KangRail 高并发铁路购票系统 启动类
 */
@Slf4j
@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.kangcode")
public class KangRailApplication {

    public static void main(String[] args) {
        SpringApplication.run(KangRailApplication.class, args);
        log.info("KangRail 康轨铁路购票系统启动成功");
    }
}
