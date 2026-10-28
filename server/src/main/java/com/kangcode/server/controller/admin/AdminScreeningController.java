package com.kangcode.server.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.dto.ScreeningCreateRequest;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.pojo.vo.ScreeningVO;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 管理端 · 场次管理（增删改查）
 */
@RestController
@RequestMapping("/api/admin/screening")
@RequiredArgsConstructor
public class AdminScreeningController {

    private final ScreeningService screeningService;

    /** 查：分页 + 场次号/影片名模糊搜索（含已下架场次） */
    @GetMapping("/page")
    public Result<IPage<ScreeningVO>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(screeningService.pageScreening(page, size, keyword));
    }

    /** 增 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ScreeningCreateRequest request) {
        return Result.ok(screeningService.createScreening(request));
    }

    /** 改：目前支持改影片名与上下架状态 */
    @PutMapping("/{screeningId}")
    public Result<Void> update(@PathVariable Long screeningId,
                               @Valid @RequestBody ScreeningUpdateRequest request) {
        screeningService.updateScreening(screeningId, request);
        return Result.ok();
    }

    /** 删：已有订单的场次不允许删除，只能下架 */
    @DeleteMapping("/{screeningId}")
    public Result<Void> delete(@PathVariable Long screeningId) {
        screeningService.deleteScreening(screeningId);
        return Result.ok();
    }
}
