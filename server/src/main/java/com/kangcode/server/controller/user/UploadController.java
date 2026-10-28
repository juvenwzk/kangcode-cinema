package com.kangcode.server.controller.user;

import com.kangcode.common.core.Result;
import com.kangcode.framework.oss.OssService;
import com.kangcode.pojo.vo.UploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传接口。
 * <p>只放一个通用入口：图片存到 OSS，返回 URL，业务表里只记这个 URL。
 * 这样影院 Logo、电影海报、用户头像三种图共用一套上传逻辑，
 * 以后要加「卖品图片」也不用再写接口。</p>
 * <p>为什么不做成管理端专用：头像普通用户也要传。真正的权限边界在
 * 「dir 白名单」——普通用户即使调接口，也只能传 avatar/common 目录之外都进不来。</p>
 * <p>未做频率限制：登录用户可无限上传。上线前应加「每人每天 N 张」的计数限流，
 * 否则对象存储会被刷。</p>
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final OssService ossService;

    /**
     * 上传图片。
     *
     * @param file 图片文件，form-data 字段名必须是 file
     * @param dir  业务目录：cinema（影院 Logo）/ movie（电影海报）/ avatar（头像）
     */
    @PostMapping("/image")
    public Result<UploadVO> image(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "dir", defaultValue = "common") String dir) {
        String url = ossService.uploadImage(file, dir);
        return Result.ok(UploadVO.builder()
                .url(url)
                .name(file.getOriginalFilename())
                .size(file.getSize())
                .build());
    }
}
