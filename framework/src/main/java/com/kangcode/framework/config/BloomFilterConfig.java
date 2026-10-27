package com.kangcode.framework.config;


import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器（防缓存穿透）
 *
 *预期元素数与误判率按「场次 × 日期 × 票种」组合数评估：
 * 100万组合、1%误判--->底层约为1.14MB bit串，7个哈希函数
 * 误判的方向是可能存在的
 */
@Configuration
public class BloomFilterConfig {

    @Bean
    public RBloomFilter<String> screeningBloomFilter(RedissonClient redissonClient){
        RBloomFilter<String> filter=redissonClient.getBloomFilter("order:bloom:screening");

        filter.tryInit(1_000_000L,0.01);
        return filter;

    }
}
