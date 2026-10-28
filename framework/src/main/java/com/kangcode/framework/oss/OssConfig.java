package com.kangcode.framework.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储装配。
 * <p>四要素没配齐时不创建 OSSClient，应用照常启动（本地没有密钥也能跑其余功能），
 * 只是上传接口会返回「对象存储未配置」。这比启动即失败更适合学生机器/CI 环境。</p>
 * <p>OSSClient 是线程安全的，官方建议全应用共用一个实例，所以这里做成单例 Bean，
 * 并交给 Spring 在容器关闭时调用 shutdown() 释放连接池。</p>
 * <p>注意返回类型是接口 {@link OSS} 而不是 OSSClient：SDK 3.18 起
 * {@code OSSClientBuilder.build()} 的返回类型改成了 OSS 接口，写成 OSSClient 编译不过。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss",
            name = {"access-key-id", "access-key-secret", "bucket", "endpoint"})
    public OSS ossClient(OssProperties properties) {
        log.info("初始化阿里云 OSS 客户端 bucket={} endpoint={}", properties.getBucket(), properties.getEndpoint());
        return new OSSClientBuilder().build(
                properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
    }
}
