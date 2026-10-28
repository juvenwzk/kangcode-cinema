package com.kangcode.server.controller.admin;

import com.kangcode.common.core.Result;
import com.kangcode.pojo.dto.CinemaCreateRequest;
import com.kangcode.pojo.dto.CinemaUpdateRequest;
import com.kangcode.pojo.vo.CinemaVO;
import com.kangcode.pojo.vo.HallOptionVO;
import com.kangcode.server.service.CinemaService;
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
import java.util.List;

/**
 * 管理端 · 影院管理（增删改查）
 */
@RestController
@RequestMapping("/api/admin/cinema")
@RequiredArgsConstructor
public class AdminCinemaController {

    private final CinemaService cinemaService;

    /** 查：含停用影院的完整列表，可按名称/编码模糊搜索 */
    @GetMapping("/list")
    public Result<List<CinemaVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(cinemaService.listAllForAdmin(keyword));
    }

    /** 增 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CinemaCreateRequest request) {
        return Result.ok(cinemaService.createCinema(request));
    }

    /** 改 */
    @PutMapping("/{cinemaId}")
    public Result<Void> update(@PathVariable Long cinemaId,
                               @Valid @RequestBody CinemaUpdateRequest request) {
        cinemaService.updateCinema(cinemaId, request);
        return Result.ok();
    }

    /** 删：影院下还有场次时会被拒绝，提示先删场次 */
    @DeleteMapping("/{cinemaId}")
    public Result<Void> delete(@PathVariable Long cinemaId) {
        cinemaService.deleteCinema(cinemaId);
        return Result.ok();
    }

    /** 某影院下的影厅列表：新增场次的表单需要它来填 hallId */
    @GetMapping("/{cinemaId}/halls")
    public Result<List<HallOptionVO>> halls(@PathVariable Long cinemaId) {
        return Result.ok(cinemaService.listHalls(cinemaId));
    }
}
