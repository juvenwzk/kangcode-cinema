package com.kangcode.server.controller.user;

import com.kangcode.common.core.Result;
import com.kangcode.pojo.vo.CinemaVO;
import com.kangcode.server.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 影院接口（用户端，只读）
 */
@RestController
@RequestMapping("/api/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    /**
     * 影院列表：未登录也能看（WebMvcConfig 里放行了 /api/cinema/**）。
     * 查库 + 缓存 + 实体转 VO 都在 CinemaService 里，Controller 只管协议。
     */
    @GetMapping("/list")
    public Result<List<CinemaVO>> list() {
        return Result.ok(cinemaService.listCinemas());
    }
}
