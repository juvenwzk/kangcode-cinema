package com.kangcode.server.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.vo.ScreeningSeatVO;
import com.kangcode.pojo.vo.ScreeningVO;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 某场次某天的票种 / 票价 / 余票（前端选票用）。
     * 例：GET /api/screening/1/seats?showDate=2026-11-01
     */
    @GetMapping("/{id}/seats")
    public Result<List<ScreeningSeatVO>> seats(@PathVariable Long id,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate showDate) {
        LocalDate date = showDate == null ? LocalDate.now() : showDate;
        return Result.ok(screeningService.listSeats(id, date));
    }

    /** 某场次已放票的日期列表，前端用它自动定位到有票的日期 */
    @GetMapping("/{id}/dates")
    public Result<List<LocalDate>> dates(@PathVariable Long id) {
        return Result.ok(screeningService.listShowDates(id));
    }
}
