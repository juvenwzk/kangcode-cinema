package com.kangcode.framework.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 阿里云 OSS 配置（前缀 aliyun.oss）。
 * <p>密钥一律走环境变量注入：yml 里写 {@code ${OSS_ACCESS_KEY_ID:}}，
 * 只有本地开发才会填默认值，线上必须在部署环境设环境变量，不要把 AK/SK 提交进仓库。</p>
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucket;

    /** 地域节点，如 oss-cn-shenzhen.aliyuncs.com；不要带 https:// */
    private String endpoint;

    /**
     * 自定义域名 / CDN 加速域名，例如 https://img.example.com。
     * 留空时用默认的 https://{bucket}.{endpoint} 拼接。
     */
    private String domain;

    /** 统一目录前缀，所有业务图片都落在这一层下，方便按前缀做生命周期清理 */
    private String dirPrefix = "movie-ticket";

    /** 单文件大小上限（字节），默认 5MB。multipart 的总量限制另外配在 spring.servlet.multipart */
    private long maxSize = 5 * 1024 * 1024;

    /** 允许的图片扩展名（白名单，只认扩展名不够，实现里还会校验文件头魔数） */
    private List<String> allowExtensions = Arrays.asList("jpg", "jpeg", "png", "webp", "gif");

    /** 配置是否完整：缺任意一项就当对象存储不可用，上传接口直接给出明确报错 */
    public boolean isConfigured() {
        return StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret)
                && StringUtils.hasText(bucket)
                && StringUtils.hasText(endpoint);
    }

    /** 对外访问的域名，末尾不带斜杠 */
    public String resolveHost() {
        if (StringUtils.hasText(domain)) {
            return domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        }
        return "https://" + bucket + "." + endpoint;
    }
}
