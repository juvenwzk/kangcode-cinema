package com.kangcode.framework.oss;

import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储服务：目前只有阿里云 OSS 一个实现。
 * <p>抽接口是为了以后换 MinIO / COS 时业务代码不用动——
 * Service 层只依赖「给我一个文件，还我一个可访问的 URL」这件事。</p>
 */
public interface OssService {

    /**
     * 上传图片。
     *
     * @param file    前端提交的文件
     * @param bizDir  业务目录，如 cinema / movie / avatar，决定对象 key 的二级路径
     * @return 可直接放进 img src 的完整 URL
     */
    String uploadImage(MultipartFile file, String bizDir);

    /**
     * 按 URL 删除对象（新建时替换旧图会用到）。
     * <p>删除失败只记日志不抛异常：图片删不掉不影响业务，硬抛会让「保存影院」跟着失败。</p>
     *
     * @return 是否真的删掉了
     */
    boolean deleteByUrl(String url);

    /** 对象存储是否已正确配置 */
    boolean isAvailable();
}
