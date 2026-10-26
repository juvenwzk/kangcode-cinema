package com.kangcode.framework.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 配置：用 Redis 做缓存后端，支持按 cache name 设置不同过期时间。
 * 用法：方法上加 @Cacheable / @CachePut / @CacheEvict
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Key 前缀，避免和业务手写的 key 冲突 */
    private static final String KEY_PREFIX = "railway:cache:";

    /**
     * 缓存值的序列化器：JSON + 类型信息 + Java8 时间支持。
     * 为什么要自己 new：默认的 GenericJackson2JsonRedisSerializer 内部 ObjectMapper
     * 没有注册 JavaTimeModule，遇到 LocalTime/LocalDateTime 会直接抛异常。
     */
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持 LocalDate / LocalTime / LocalDateTime
        mapper.registerModule(new JavaTimeModule());
        // 时间写成 "2026-09-21T08:00:00" 这种可读格式，而不是时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 让 private 字段也能被序列化/反序列化
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 写入 @class 类型信息，读的时候才能还原成原来的对象
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer valueSerializer = jsonSerializer();
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        // 影院数据基本不变，缓存久一点
        configs.put("cinema", base.entryTtl(Duration.ofHours(6)));
        // 场次详情
        configs.put("screening", base.entryTtl(Duration.ofMinutes(30)));
        // 余票等热点数据
        configs.put("ticketStock", base.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
