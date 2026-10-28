package com.kangcode.framework.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.kangcode.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云 OSS 实现。
 * <p>三道校验缺一不可：大小 → 扩展名 → 文件头魔数。
 * 只信文件名后缀等于把「把木马改成 .jpg」这种最省事的攻击放进来，
 * 而只信 Content-Type 也没用——那个头是客户端随便填的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssServiceImpl implements OssService {

    /** 业务目录白名单：前端传来的 dir 不能直接拼进 key，否则可以构造 ../ 跳出目录 */
    private static final List<String> ALLOWED_DIRS = Arrays.asList("cinema", "movie", "avatar", "common");

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OssProperties properties;
    private final ObjectProvider<OSS> clientProvider;

    @Override
    public boolean isAvailable() {
        return properties.isConfigured() && clientProvider.getIfAvailable() != null;
    }

    @Override
    public String uploadImage(MultipartFile file, String bizDir) {
        OSS client = clientProvider.getIfAvailable();
        if (!properties.isConfigured() || client == null) {
            throw new BusinessException("对象存储未配置，无法上传图片");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw new BusinessException("图片不能超过 " + (properties.getMaxSize() / 1024 / 1024) + "MB");
        }

        String dir = StringUtils.hasText(bizDir) ? bizDir.trim().toLowerCase(Locale.ROOT) : "common";
        if (!ALLOWED_DIRS.contains(dir)) {
            throw new BusinessException("不支持的上传目录：" + bizDir);
        }

        String extension = resolveExtension(file.getOriginalFilename());
        if (!properties.getAllowExtensions().contains(extension)) {
            throw new BusinessException("只支持 " + String.join(" / ", properties.getAllowExtensions()) + " 格式的图片");
        }
        if (!isRealImage(file, extension)) {
            throw new BusinessException("文件内容不是有效的图片");
        }

        // key 里带日期 + UUID：同名文件不会互相覆盖，也方便按天做生命周期清理
        String objectKey = properties.getDirPrefix() + "/" + dir + "/"
                + LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentTypeOf(extension));
        metadata.setContentLength(file.getSize());
        // 文件名带 UUID，内容永不变化，可以放心让浏览器/CDN 长期缓存
        metadata.setCacheControl("public, max-age=31536000");
        // bucket 已开公共读，这里再显式指定对象 ACL：即使以后 bucket 改回私有，
        // 已上传的图片仍然能被 <img> 直接访问（个别 bucket 禁止单独设 ACL，失败不阻断上传）
        try {
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);
        } catch (Exception e) {
            log.warn("设置对象 ACL 失败，沿用 bucket 默认策略：{}", e.getMessage());
        }

        try (InputStream in = file.getInputStream()) {
            client.putObject(properties.getBucket(), objectKey, in, metadata);
        } catch (IOException e) {
            log.error("读取上传文件失败 dir={}", dir, e);
            throw new BusinessException("图片读取失败，请重试");
        } catch (Exception e) {
            log.error("上传 OSS 失败 bucket={} key={}", properties.getBucket(), objectKey, e);
            throw new BusinessException("图片上传失败，请稍后重试");
        }

        String url = properties.resolveHost() + "/" + objectKey;
        log.info("图片已上传 dir={} size={}B url={}", dir, file.getSize(), url);
        return url;
    }

    @Override
    public boolean deleteByUrl(String url) {
        OSS client = clientProvider.getIfAvailable();
        if (!StringUtils.hasText(url) || client == null) {
            return false;
        }
        String host = properties.resolveHost();
        // 只删自己域名下的对象，而且必须落在 dirPrefix 里，避免把别人的 key 删掉
        if (!url.startsWith(host + "/")) {
            return false;
        }
        String objectKey = url.substring(host.length() + 1);
        if (!objectKey.startsWith(properties.getDirPrefix() + "/")) {
            return false;
        }
        try {
            client.deleteObject(properties.getBucket(), objectKey);
            log.info("图片已删除 key={}", objectKey);
            return true;
        } catch (Exception e) {
            // 删图失败不影响业务，只记录：用户改个 logo 不该因为存储抖动而保存失败
            log.warn("删除 OSS 对象失败 key={} err={}", objectKey, e.getMessage());
            return false;
        }
    }

    /** 取扩展名，没有则返回空串 */
    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 校验文件头魔数，确认内容和扩展名对得上。
     * JPEG 需要 3 字节，PNG 需要 8 字节，GIF 需要 6 字节，WEBP 需要 12 字节，统一读 12 字节。
     */
    private boolean isRealImage(MultipartFile file, String extension) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(head);
        } catch (IOException e) {
            log.warn("读取文件头失败", e);
            return false;
        }
        if (read < 4) {
            return false;
        }
        switch (extension) {
            case "jpg":
            case "jpeg":
                return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png":
                return read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "gif":
                return read >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
            case "webp":
                return read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default:
                return false;
        }
    }

    private String contentTypeOf(String extension) {
        switch (extension) {
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }
}
