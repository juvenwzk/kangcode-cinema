package com.kangcode.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.vo.ScreeningVO;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场次接口
 */
@RestController
@RequestMapping("/api/screening")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    @GetMapping("/page")
    public Result<IPage<ScreeningVO>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(screeningService.pageScreening(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<ScreeningVO> detail(@PathVariable Long id) {

        return Result.ok(screeningService.getScreeningDetail(id));
    }
}
